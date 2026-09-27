package com.pavelvoronin.pz3d;
public final class StreamFade {
    private static float aB() { return (float)System.nanoTime(); }
    public static long clock() { return Float.floatToIntBits(aB()); }
}
