package zombie.core.opengl;
/** Original fixture. Never packaged into the hook artifact. */
public final class RenderThread {
    public static void queueInvokeOnRenderContext(Runnable action) { action.run(); }
    public static Thread renderThread=Thread.currentThread();
    private static Thread contextThread=Thread.currentThread();
}
