package org.lwjgl.glfw;
public final class GLFW {
    public static final int GLFW_FOCUSED=0x00020001, GLFW_TRUE=1, GLFW_PRESS=1;
    public static final int GLFW_KEY_F6=295, GLFW_KEY_F7=296, GLFW_KEY_F9=298, GLFW_KEY_F10=299, GLFW_KEY_LEFT_CONTROL=341, GLFW_KEY_RIGHT_CONTROL=345,
        GLFW_KEY_LEFT_SHIFT=340, GLFW_KEY_RIGHT_SHIFT=344;
    public static final int GLFW_KEY_SCROLL_LOCK=281;
    public static boolean focused=true;
    public static final int GLFW_KEY_LEFT_ALT=342,GLFW_KEY_RIGHT_ALT=346;
    public static final java.util.Set<Integer> keys=new java.util.HashSet<>();
    public static int glfwGetKey(long window,int key) { return keys.contains(key)?GLFW_PRESS:0; }
    public static int glfwGetWindowAttrib(long window,int attr) { return focused?GLFW_TRUE:0; }
}
