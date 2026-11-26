package com.ghostsail;

import java.util.function.Supplier;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.RuneLiteObjectController;

/**
 * Draws one layer of a ghost sail in place of the real one (which is hidden).
 * Layer 0 is the sail itself, faded and brightened. Higher layers are halo
 * layers: the same frame in the sail's own colours, scaled outward and mostly see-through.
 */
class GhostSailRenderer extends RuneLiteObjectController
{
    private static final int MAX_LUM = 127;
    /** Most see-through a sail face gets at 100%, short of the 255 that hides it. */
    private static final int MAX_SAIL_ALPHA = 250;
    /** Faintest a halo layer gets; the outermost layer sits near this. */
    private static final int MAX_HALO_ALPHA = 252;

    private final GameObject sail;
    private final SailModelState state;
    private final Supplier<GhostSailStyle> style;
    private final int layer;

    GhostSailRenderer(GameObject sail, SailModelState state, Supplier<GhostSailStyle> style, int layer)
    {
        this.sail = sail;
        this.state = state;
        this.style = style;
        this.layer = layer;
        setWorldView(sail.getWorldView().getId());
        setLocation(sail.getLocalLocation(), sail.getPlane());
        setZ(sail.getZ());
        setOrientation(sail.getModelOrientation());
    }

    @Override
    public Model getModel()
    {
        Model m = sail.getRenderable().getModel();
        if (m == null)
        {
            return null;
        }

        state.beginVertexEdit(m);
        if (layer == 0)
        {
            styleSail(m);
        }
        else
        {
            styleHalo(m);
        }
        state.endVertexEdit(m);
        return m;
    }

    private void styleSail(Model m)
    {
        GhostSailStyle st = style.get();
        int extra = st.transparency;
        byte[] alphas = m.getFaceTransparencies();
        int n = m.getFaceCount();
        if (alphas != null && extra > 0)
        {
            for (int i = 0; i < n; i++)
            {
                int a = alphas[i] & 0xFF;
                // Only the cloth the game already draws see-through; leave solid and hidden faces alone.
                if (a > 0 && a < 255)
                {
                    alphas[i] = (byte) (a + (MAX_SAIL_ALPHA - a) * extra / 100);
                }
            }
        }

        int boost = st.brightness + st.lumOffset();
        int[] c1 = m.getFaceColors1();
        int[] c2 = m.getFaceColors2();
        int[] c3 = m.getFaceColors3();
        int[] o1 = state.originalColours(c1);
        int[] o2 = state.originalColours(c2);
        int[] o3 = state.originalColours(c3);
        for (int i = 0; i < n; i++)
        {
            if (o3[i] == -2)
            {
                continue;
            }
            c1[i] = shiftLum(o1[i], boost);
            if (o3[i] != -1)
            {
                c2[i] = shiftLum(o2[i], boost);
                c3[i] = shiftLum(o3[i], boost);
            }
        }
    }

    private void styleHalo(Model m)
    {
        GhostSailStyle st = style.get();
        int layers = GhostSailStyle.HALO_LAYERS;
        int n = m.getFaceCount();
        byte[] alphas = m.getFaceTransparencies();
        int[] c1 = m.getFaceColors1();
        int[] c2 = m.getFaceColors2();
        int[] c3 = m.getFaceColors3();
        int[] o1 = state.originalColours(c1);
        int[] o2 = state.originalColours(c2);
        int[] o3 = state.originalColours(c3);

        // Outer shells are bigger and fainter.
        float scale = 1f + st.haloSize / 100f * layer / layers;
        scaleAboutCentre(m, scale);

        int strength = st.haloStrength * (layers - layer + 1) / layers;
        int alpha = MAX_HALO_ALPHA - (MAX_HALO_ALPHA - 120) * strength / 100;
        int boost = st.brightness + st.lumOffset();

        for (int i = 0; i < n; i++)
        {
            int a = alphas == null ? 0 : alphas[i] & 0xFF;
            if (o3[i] == -2 || a == 255)
            {
                continue;
            }
            if (alphas != null)
            {
                alphas[i] = (byte) alpha;
            }
            c1[i] = shiftLum(o1[i], boost);
            if (o3[i] != -1)
            {
                c2[i] = shiftLum(o2[i], boost);
                c3[i] = shiftLum(o3[i], boost);
            }
        }
    }

    private static void scaleAboutCentre(Model m, float scale)
    {
        float[] x = m.getVerticesX();
        float[] y = m.getVerticesY();
        float[] z = m.getVerticesZ();
        int count = m.getVerticesCount();
        if (count == 0)
        {
            return;
        }
        float cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < count; i++)
        {
            cx += x[i];
            cy += y[i];
            cz += z[i];
        }
        cx /= count;
        cy /= count;
        cz /= count;
        for (int i = 0; i < count; i++)
        {
            x[i] = cx + (x[i] - cx) * scale;
            y[i] = cy + (y[i] - cy) * scale;
            z[i] = cz + (z[i] - cz) * scale;
        }
        m.calculateBoundsCylinder();
    }

    /** Shifts the lightness of a packed Jagex HSL colour, clamped to its range. */
    private static int shiftLum(int hsl, int amount)
    {
        int lum = Math.max(0, Math.min(MAX_LUM, (hsl & 0x7F) + amount));
        return (hsl & ~0x7F) | lum;
    }
}
