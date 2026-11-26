package com.ghostsail;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class GhostSailTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(GhostSailPlugin.class);
        RuneLite.main(args);
    }
}
