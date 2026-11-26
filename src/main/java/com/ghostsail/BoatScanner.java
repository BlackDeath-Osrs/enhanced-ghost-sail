package com.ghostsail;

import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.DynamicObject;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.VarbitID;

/**
 * Developer tool: logs every object on the boat the player is standing on,
 * with the model data needed to find and restyle the sail.
 */
@Slf4j
class BoatScanner
{
    private static final int[] SAIL_PATTERN_VARBITS = {
        VarbitID.SAILING_BOAT_1_SAIL_PATTERN,
        VarbitID.SAILING_BOAT_2_SAIL_PATTERN,
        VarbitID.SAILING_BOAT_3_SAIL_PATTERN,
        VarbitID.SAILING_BOAT_4_SAIL_PATTERN,
        VarbitID.SAILING_BOAT_5_SAIL_PATTERN,
    };

    @Inject
    private Client client;

    void scan()
    {
        StringBuilder patterns = new StringBuilder("Sail patterns by boat slot:");
        for (int i = 0; i < SAIL_PATTERN_VARBITS.length; i++)
        {
            patterns.append(' ').append(i + 1).append('=').append(client.getVarbitValue(SAIL_PATTERN_VARBITS[i]));
        }
        log.info("[ghostscan] {}", patterns);

        Player player = client.getLocalPlayer();
        WorldView wv = player == null ? null : player.getWorldView();
        if (wv == null || wv.isTopLevel())
        {
            chat("Ghost Sail scan: stand on your boat first.");
            return;
        }

        int count = 0;
        Scene scene = wv.getScene();
        for (Tile[][] plane : scene.getTiles())
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
                        if (obj != null)
                        {
                            logObject(obj);
                            count++;
                        }
                    }
                }
            }
        }
        chat("Ghost Sail scan: logged " + count + " boat objects to client.log");
    }

    private void logObject(GameObject obj)
    {
        ObjectComposition comp = client.getObjectDefinition(obj.getId());
        String name = comp == null ? "?" : comp.getName();
        Renderable r = obj.getRenderable();
        Model model = null;
        if (r instanceof Model)
        {
            model = (Model) r;
        }
        else if (r instanceof DynamicObject)
        {
            model = r.getModel();
        }

        String extra = "";
        if (comp != null && comp.getImpostorIds() != null)
        {
            ObjectComposition imp = comp.getImpostor();
            extra = " impostor=" + (imp == null ? "null" : imp.getId() + "/'" + imp.getName() + "'");
        }
        if (r instanceof DynamicObject && ((DynamicObject) r).getAnimation() != null)
        {
            extra += " anim=" + ((DynamicObject) r).getAnimation().getId();
        }

        log.info("[ghostscan] object id={} name='{}' renderable={}{} model={}",
            obj.getId(), name, r == null ? "null" : r.getClass().getSimpleName(), extra, describe(model));
    }

    private static String describe(Model m)
    {
        if (m == null)
        {
            return "none";
        }
        byte[] alphas = m.getFaceTransparencies();
        short[] textures = m.getFaceTextures();
        short[] colors = m.getUnlitFaceColors();
        return "faces=" + m.getFaceCount()
            + " alphaArray=" + (alphas != null)
            + " alphas=" + histogram(alphas, m.getFaceCount())
            + " textures=" + histogram(textures, m.getFaceCount())
            + " colors=" + histogram(colors, m.getFaceCount())
            + " c1=" + histogram(m.getFaceColors1(), m.getFaceCount())
            + " c3=" + histogram(m.getFaceColors3(), m.getFaceCount())
            + " arrays=" + System.identityHashCode(m.getFaceColors1()) + "/" + System.identityHashCode(alphas);
    }

    private static String histogram(byte[] values, int n)
    {
        if (values == null)
        {
            return "null";
        }
        Map<Integer, Integer> h = new TreeMap<>();
        for (int i = 0; i < Math.min(n, values.length); i++)
        {
            h.merge(values[i] & 0xFF, 1, Integer::sum);
        }
        return h.toString();
    }

    private static String histogram(int[] values, int n)
    {
        if (values == null)
        {
            return "null";
        }
        Map<Integer, Integer> h = new TreeMap<>();
        for (int i = 0; i < Math.min(n, values.length); i++)
        {
            h.merge(values[i], 1, Integer::sum);
        }
        return h.size() > 12 ? h.size() + " distinct" : h.toString();
    }

    private static String histogram(short[] values, int n)
    {
        if (values == null)
        {
            return "null";
        }
        Map<Integer, Integer> h = new TreeMap<>();
        for (int i = 0; i < Math.min(n, values.length); i++)
        {
            h.merge((int) values[i], 1, Integer::sum);
        }
        return h.toString();
    }

    private void chat(String msg)
    {
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
    }
}
