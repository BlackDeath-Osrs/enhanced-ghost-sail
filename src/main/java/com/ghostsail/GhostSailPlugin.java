package com.ghostsail;

import java.awt.Color;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Ghost Sail",
    description = "Makes the Sailing ghost sail more see-through and gives it a glow",
    tags = {"sailing", "ghost", "sail", "boat", "transparent", "glow"}
)
public class GhostSailPlugin extends Plugin
{
    private static final String SAIL_NAME = "Sails";

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private RenderCallbackManager renderCallbackManager;

    @Inject
    private BoatScanner boatScanner;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SailGlowOverlay glowOverlay;

    @Inject
    @Named("developerMode")
    private boolean developerMode;

    private final SailModelState modelState = new SailModelState();

    /** Every ghost sail in view being redrawn, keyed by the real sail object. */
    private final Map<GameObject, ActiveSail> sails = new IdentityHashMap<>();

    /** The real sails' renderables, for renderers that hide by renderable. */
    private final Set<Renderable> hiddenRenderables = Collections.newSetFromMap(new IdentityHashMap<>());

    private final RenderCallback hideRealSail = new RenderCallback()
    {
        @Override
        public boolean drawObject(Scene scene, TileObject object)
        {
            return !(object instanceof GameObject) || !sails.containsKey(object);
        }

        @Override
        public boolean addEntity(Renderable renderable, boolean ui)
        {
            return !hiddenRenderables.contains(renderable);
        }
    };

    @Override
    protected void startUp()
    {
        renderCallbackManager.register(hideRealSail);
        overlayManager.add(glowOverlay);
    }

    @Override
    protected void shutDown()
    {
        renderCallbackManager.unregister(hideRealSail);
        overlayManager.remove(glowOverlay);
        clientThread.invoke(this::clearAll);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            clearAll();
        }
    }

    /** The look for whichever renderer is drawing the game right now. */
    GhostSailStyle getStyle()
    {
        DrawCallbacks dc = client.getDrawCallbacks();
        return dc != null && dc.getClass().getName().startsWith("rs117.") ? GhostSailStyle.HD : GhostSailStyle.STANDARD;
    }

    /** Ghost sails currently being redrawn, for the glow overlay. */
    Collection<ActiveSail> getSails()
    {
        return sails.values();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        Set<GameObject> found = new HashSet<>();
        for (WorldEntity boat : client.getTopLevelWorldView().worldEntities())
        {
            WorldView wv = boat.getWorldView();
            if (wv != null)
            {
                findGhostSails(wv, found);
            }
        }

        boolean removed = false;
        for (Iterator<ActiveSail> it = sails.values().iterator(); it.hasNext(); )
        {
            ActiveSail active = it.next();
            if (!found.contains(active.getSail()))
            {
                stop(active);
                it.remove();
                removed = true;
            }
        }
        if (removed)
        {
            // Put the game's colours back in case a sail we stopped is drawn by the game again.
            modelState.restoreLive();
        }

        for (GameObject sail : found)
        {
            if (!sails.containsKey(sail))
            {
                sails.put(sail, start(sail));
            }
        }
    }

    private ActiveSail start(GameObject sail)
    {
        ActiveSail active = new ActiveSail(sail, clothColour(sail));
        // Halo layers first, outermost first, then the sail itself on top.
        for (int layer = GhostSailStyle.HALO_LAYERS; layer >= 0; layer--)
        {
            GhostSailRenderer renderer = new GhostSailRenderer(sail, modelState, this::getStyle, layer);
            active.getRenderers().add(renderer);
            client.registerRuneLiteObject(renderer);
        }
        hiddenRenderables.add(sail.getRenderable());
        log.debug("Redrawing ghost sail {} in world view {} colour={}", sail.getId(),
            sail.getWorldView().getId(), active.getColour());
        return active;
    }

    private void stop(ActiveSail active)
    {
        for (GhostSailRenderer renderer : active.getRenderers())
        {
            client.removeRuneLiteObject(renderer);
        }
        hiddenRenderables.remove(active.getSail().getRenderable());
    }

    private void findGhostSails(WorldView wv, Set<GameObject> out)
    {
        for (Tile[][] plane : wv.getScene().getTiles())
        {
            for (Tile[] row : plane)
            {
                for (Tile tile : row)
                {
                    if (tile == null)
                    {
                        continue;
                    }
                    for (GameObject obj : tile.getGameObjects())
                    {
                        if (obj != null && isSail(obj) && isGhostly(obj))
                        {
                            out.add(obj);
                        }
                    }
                }
            }
        }
    }

    private boolean isSail(GameObject obj)
    {
        ObjectComposition comp = client.getObjectDefinition(obj.getId());
        return comp != null && SAIL_NAME.equals(comp.getName());
    }

    /** The ghost sail is the only sail whose cloth the game draws see-through. */
    private static boolean isGhostly(GameObject obj)
    {
        Model m = obj.getRenderable().getModel();
        byte[] alphas = m == null ? null : m.getFaceTransparencies();
        if (alphas == null)
        {
            return false;
        }
        int n = m.getFaceCount();
        int seeThrough = 0;
        for (int i = 0; i < n; i++)
        {
            int a = alphas[i] & 0xFF;
            if (a > 0 && a < 255)
            {
                seeThrough++;
            }
        }
        return seeThrough * 2 > n;
    }

    /**
     * The most common colour among the sail's see-through cloth faces, averaged
     * over its shades. Read before any renderer edits the model's colours.
     */
    private static Color clothColour(GameObject obj)
    {
        Model m = obj.getRenderable().getModel();
        if (m == null || m.getFaceTransparencies() == null)
        {
            return null;
        }
        byte[] alphas = m.getFaceTransparencies();
        int[] c1 = m.getFaceColors1();
        Map<Integer, int[]> byHueSat = new HashMap<>();
        for (int i = 0; i < m.getFaceCount(); i++)
        {
            int a = alphas[i] & 0xFF;
            if (a == 0 || a == 255 || c1[i] < 0)
            {
                continue;
            }
            int[] lumTotal = byHueSat.computeIfAbsent(c1[i] & 0xFF80, k -> new int[2]);
            lumTotal[0] += c1[i] & 0x7F;
            lumTotal[1]++;
        }
        Map.Entry<Integer, int[]> best = null;
        for (Map.Entry<Integer, int[]> e : byHueSat.entrySet())
        {
            if (best == null || e.getValue()[1] > best.getValue()[1])
            {
                best = e;
            }
        }
        if (best == null)
        {
            return null;
        }
        // The glow should brighten what it covers, so never use a dark shade.
        int lum = Math.max(96, best.getValue()[0] / best.getValue()[1]);
        return jagexHslToColour(best.getKey() | lum);
    }

    /** Converts a packed Jagex HSL colour (6-bit hue, 3-bit saturation, 7-bit lightness) to RGB. */
    private static Color jagexHslToColour(int hsl)
    {
        double h = ((hsl >> 10) & 0x3F) / 64.0 + 1 / 128.0;
        double s = ((hsl >> 7) & 0x7) / 8.0 + 1 / 16.0;
        double l = (hsl & 0x7F) / 128.0;
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        return new Color(
            (float) hueToChannel(p, q, h + 1 / 3.0),
            (float) hueToChannel(p, q, h),
            (float) hueToChannel(p, q, h - 1 / 3.0));
    }

    private static double hueToChannel(double p, double q, double t)
    {
        if (t < 0)
        {
            t += 1;
        }
        if (t > 1)
        {
            t -= 1;
        }
        if (t < 1 / 6.0)
        {
            return p + (q - p) * 6 * t;
        }
        if (t < 1 / 2.0)
        {
            return q;
        }
        if (t < 2 / 3.0)
        {
            return p + (q - p) * (2 / 3.0 - t) * 6;
        }
        return p;
    }

    private void clearAll()
    {
        for (ActiveSail active : sails.values())
        {
            stop(active);
        }
        sails.clear();
        hiddenRenderables.clear();
        modelState.restoreAll();
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        if (!developerMode)
        {
            return;
        }
        if ("ghostscan".equalsIgnoreCase(event.getCommand()))
        {
            boatScanner.scan();
        }
        else if ("ghoststyle".equalsIgnoreCase(event.getCommand()))
        {
            tuneStyle(event.getArguments());
        }
    }

    /** Developer tuning: ::ghoststyle shows the current look, ::ghoststyle <name> <value> changes it. */
    private void tuneStyle(String[] args)
    {
        GhostSailStyle style = getStyle();
        if (args.length == 2)
        {
            try
            {
                if (!style.set(args[0].toLowerCase(), Integer.parseInt(args[1])))
                {
                    chat("Unknown value: " + args[0]);
                    return;
                }
            }
            catch (NumberFormatException e)
            {
                chat("Not a number: " + args[1]);
                return;
            }
        }
        chat(style.toString());
        log.info("[ghoststyle] {}", style);
    }

    private void chat(String msg)
    {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
    }
}
