package com.pavelvoronin.pz3d;
/** Original fixture; no loader entry point. */
public final class Main {
    public static int failures;
    public static boolean enabled=true;
    public static boolean active() { return enabled; }
    public static boolean failed() { return false; }
    public static void fail(String stage,Throwable error) { failures++; }
}
