package com.ghostsail;

/**
 * The look of the ghost sail. 117 HD and the standard renderers draw
 * transparency and brightness differently, so each has its own values.
 */
final class GhostSailStyle
{
    /** See-through, enlarged copies of the sail drawn around it. Same for every renderer. */
    static final int HALO_LAYERS = 2;

    static final GhostSailStyle HD = new GhostSailStyle("117 HD", 85, 60, 50, 27, 8, 42, 10, 9000);
    static final GhostSailStyle STANDARD = new GhostSailStyle("standard", 95, 25, 35, 27, 8, 25, 10, 9000);

    final String name;
    /** How much more see-through the cloth gets, in percent of the way to invisible. */
    int transparency;
    /** Lightness added to the cloth and halo colours (Jagex lightness runs 0-127). */
    int brightness;
    /** Soft glow around the sail, in percent. */
    int glowStrength;
    /** How far the glow spreads, in percent of the sail's on-screen size. */
    int glowSize;
    /** How far the outermost halo layer reaches past the sail, in percent. */
    int haloSize;
    /** How visible the halo layers are, in percent. */
    int haloStrength;
    /** How far the brightness rises and falls. */
    int pulse;
    /** Milliseconds for one full rise and fall. */
    int pulseMillis;

    private GhostSailStyle(String name, int transparency, int brightness, int glowStrength, int glowSize,
        int haloSize, int haloStrength, int pulse, int pulseMillis)
    {
        this.name = name;
        this.transparency = transparency;
        this.brightness = brightness;
        this.glowStrength = glowStrength;
        this.glowSize = glowSize;
        this.haloSize = haloSize;
        this.haloStrength = haloStrength;
        this.pulse = pulse;
        this.pulseMillis = pulseMillis;
    }

    /** Current position in the pulse, from -1 to 1. */
    double wave()
    {
        double phase = (System.currentTimeMillis() % pulseMillis) / (double) pulseMillis;
        return Math.sin(phase * 2 * Math.PI);
    }

    /** Current lightness offset for the sail and halo colours. */
    int lumOffset()
    {
        return (int) Math.round(pulse * wave());
    }

    /** Sets a value by name, for tuning in the developer client. Returns false for an unknown name. */
    boolean set(String key, int value)
    {
        switch (key)
        {
            case "transparency": transparency = value; return true;
            case "brightness": brightness = value; return true;
            case "glow": glowStrength = value; return true;
            case "glowsize": glowSize = value; return true;
            case "halo": haloStrength = value; return true;
            case "halosize": haloSize = value; return true;
            case "pulse": pulse = value; return true;
            case "pulseseconds": pulseMillis = Math.max(1, value) * 1000; return true;
            default: return false;
        }
    }

    @Override
    public String toString()
    {
        return name + ": transparency=" + transparency + " brightness=" + brightness
            + " glow=" + glowStrength + " glowsize=" + glowSize + " halo=" + haloStrength
            + " halosize=" + haloSize + " pulse=" + pulse + " pulseseconds=" + pulseMillis / 1000;
    }
}
