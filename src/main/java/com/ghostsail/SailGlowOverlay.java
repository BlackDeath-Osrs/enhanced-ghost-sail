package com.ghostsail;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Bloom around the sail: its on-screen outline, blurred and drawn in the
 * sail's colour. Worked at quarter size and scaled up, which also softens it.
 */
class SailGlowOverlay extends Overlay
{
    private static final int DOWNSCALE = 4;
    private static final int BLUR_PASSES = 3;
    /** An outline this many times wider or taller than the viewport is a broken projection, not a sail. */
    private static final int MAX_VIEWPORT_MULTIPLE = 2;

    private final GhostSailPlugin plugin;
    private final Client client;

    /** Reused between frames so the glow doesn't allocate a new image every frame. */
    private BufferedImage img;
    private int[] alpha = new int[0];
    private int[] tmp = new int[0];

    @Inject
    SailGlowOverlay(GhostSailPlugin plugin, Client client)
    {
        this.plugin = plugin;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        for (ActiveSail active : plugin.getSails())
        {
            if (active.getColour() != null)
            {
                drawGlow(g, active.getSail(), active.getColour());
            }
        }
        return null;
    }

    private void drawGlow(Graphics2D g, GameObject sail, Color colour)
    {
        Shape hull = sail.getConvexHull();
        if (hull == null)
        {
            return;
        }
        GhostSailStyle st = plugin.getStyle();
        Rectangle viewport = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
            client.getViewportWidth(), client.getViewportHeight());
        Rectangle full = hull.getBounds();
        if (viewport.isEmpty()
            || full.width > viewport.width * MAX_VIEWPORT_MULTIPLE
            || full.height > viewport.height * MAX_VIEWPORT_MULTIPLE)
        {
            return;
        }
        // Only the visible part needs a glow; this also bounds the image to the viewport.
        Rectangle shape = full.intersection(viewport);
        if (shape.width < 4 || shape.height < 4)
        {
            return;
        }

        // Blur reach in small-image pixels, relative to the sail's on-screen size.
        int reach = Math.max(1, Math.max(shape.width, shape.height) * st.glowSize / 100 / DOWNSCALE);
        int pad = reach * 2;
        int w = shape.width / DOWNSCALE + pad * 2;
        int h = shape.height / DOWNSCALE + pad * 2;

        if (img == null || img.getWidth() != w || img.getHeight() != h)
        {
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        Arrays.fill(px, 0);
        Graphics2D ig = img.createGraphics();
        ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform toSmall = new AffineTransform();
        toSmall.translate(pad, pad);
        toSmall.scale(1.0 / DOWNSCALE, 1.0 / DOWNSCALE);
        toSmall.translate(-shape.x, -shape.y);
        ig.setTransform(toSmall);
        ig.setColor(Color.WHITE);
        ig.fill(hull);
        ig.dispose();

        if (alpha.length < px.length)
        {
            alpha = new int[px.length];
            tmp = new int[px.length];
        }
        for (int i = 0; i < px.length; i++)
        {
            alpha[i] = px[i] >>> 24;
        }
        for (int pass = 0; pass < BLUR_PASSES; pass++)
        {
            boxBlur(alpha, tmp, w, h, reach, true);
            boxBlur(tmp, alpha, w, h, reach, false);
        }

        double pulse = 1 + 0.4 * (st.pulse / 40.0) * st.wave();
        double strength = Math.min(1.0, st.glowStrength / 100.0 * pulse);
        int rgb = colour.getRGB() & 0xFFFFFF;
        for (int i = 0; i < px.length; i++)
        {
            int a = (int) (alpha[i] * strength);
            px[i] = (a << 24) | rgb;
        }

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img,
            shape.x - pad * DOWNSCALE, shape.y - pad * DOWNSCALE,
            w * DOWNSCALE, h * DOWNSCALE, null);
    }

    /** One box-blur pass along rows or columns, from src into dst. */
    private static void boxBlur(int[] src, int[] dst, int w, int h, int r, boolean horizontal)
    {
        int lines = horizontal ? h : w;
        int len = horizontal ? w : h;
        int step = horizontal ? 1 : w;
        int span = r * 2 + 1;
        for (int line = 0; line < lines; line++)
        {
            int start = horizontal ? line * w : line;
            int sum = 0;
            for (int i = -r; i <= r; i++)
            {
                sum += src[start + clamp(i, len) * step];
            }
            for (int i = 0; i < len; i++)
            {
                dst[start + i * step] = sum / span;
                sum += src[start + clamp(i + r + 1, len) * step] - src[start + clamp(i - r, len) * step];
            }
        }
    }

    private static int clamp(int i, int len)
    {
        return i < 0 ? 0 : i >= len ? len - 1 : i;
    }
}
