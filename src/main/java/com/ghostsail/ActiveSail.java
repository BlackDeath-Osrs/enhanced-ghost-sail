package com.ghostsail;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.runelite.api.GameObject;

/** One ghost sail being redrawn, with the layers standing in for it. */
@Getter
class ActiveSail
{
    private final GameObject sail;
    /** The cloth's main colour, used for the glow; null if it couldn't be read. */
    private final Color colour;
    private final List<GhostSailRenderer> renderers = new ArrayList<>();

    ActiveSail(GameObject sail, Color colour)
    {
        this.sail = sail;
        this.colour = colour;
    }
}
