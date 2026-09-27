package org.lwjgl.opengl;
/** Deliberately no native OpenGL. Separate fixture classpath only. */
public final class GL30 {
    public static int blits;
    public static int read=12,draw=13,lastSource,lastTarget;
    public static boolean complete=true;
    public static int glGetInteger(int name) { return name==36010?read:draw; }
    public static void glBindFramebuffer(int target,int value) { if(target==36008) read=value; else draw=value; }
    public static int glCheckFramebufferStatus(int target) { return complete?36053:0; }
    public static int glGetError() { return 0; }
    public static void glBlitFramebuffer(int a,int b,int c,int d,int e,int f,int g,int h,int mask,int filter) { blits++; lastSource=read; lastTarget=draw; }
}
