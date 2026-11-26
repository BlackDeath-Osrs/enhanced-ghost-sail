package com.ghostsail;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import net.runelite.api.Model;

/**
 * Keeps the sail model's arrays honest while several renderers edit them.
 *
 * The game rebuilds an animated model's frame into scratch buffers, but some
 * arrays (the colours, and the vertices when the animation doesn't move them)
 * belong to the cached base model and survive between frames. Before each edit
 * this puts back the game's values, so edits never pile up, and on shutdown it
 * restores everything it touched.
 */
class SailModelState
{
    private static final class SavedInts
    {
        final int[] live;
        final int[] orig;

        SavedInts(int[] live)
        {
            this.live = live;
            this.orig = live.clone();
        }
    }

    private static final class SavedVertices
    {
        float[] beforeEdit;
        int signature;
    }

    private final Map<int[], SavedInts> colours = new IdentityHashMap<>();
    private final Map<float[], SavedVertices> vertices = new IdentityHashMap<>();

    /** Writes the game's original colours back into the model and returns them. */
    int[] originalColours(int[] live)
    {
        SavedInts saved = colours.computeIfAbsent(live, SavedInts::new);
        System.arraycopy(saved.orig, 0, live, 0, live.length);
        return saved.orig;
    }

    /**
     * Undoes our last vertex edit if the game hasn't rebuilt the array since,
     * then remembers the current values as the clean frame.
     */
    void beginVertexEdit(Model m)
    {
        for (float[] axis : new float[][]{m.getVerticesX(), m.getVerticesY(), m.getVerticesZ()})
        {
            SavedVertices saved = vertices.computeIfAbsent(axis, a -> new SavedVertices());
            if (saved.beforeEdit != null && saved.signature == Arrays.hashCode(axis))
            {
                System.arraycopy(saved.beforeEdit, 0, axis, 0, saved.beforeEdit.length);
            }
            saved.beforeEdit = axis.clone();
        }
    }

    /** Records what we wrote, so the next edit can tell whether the game rebuilt it. */
    void endVertexEdit(Model m)
    {
        for (float[] axis : new float[][]{m.getVerticesX(), m.getVerticesY(), m.getVerticesZ()})
        {
            SavedVertices saved = vertices.get(axis);
            if (saved != null)
            {
                saved.signature = Arrays.hashCode(axis);
            }
        }
    }

    /** Puts the game's values back but keeps tracking, for when some sails are still being redrawn. */
    void restoreLive()
    {
        for (SavedInts saved : colours.values())
        {
            System.arraycopy(saved.orig, 0, saved.live, 0, saved.orig.length);
        }
        for (Map.Entry<float[], SavedVertices> e : vertices.entrySet())
        {
            SavedVertices saved = e.getValue();
            float[] axis = e.getKey();
            if (saved.beforeEdit != null && saved.signature == Arrays.hashCode(axis))
            {
                System.arraycopy(saved.beforeEdit, 0, axis, 0, saved.beforeEdit.length);
                saved.beforeEdit = null;
            }
        }
    }

    void restoreAll()
    {
        restoreLive();
        colours.clear();
        vertices.clear();
    }
}
