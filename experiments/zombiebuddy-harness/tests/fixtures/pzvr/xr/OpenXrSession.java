package pzvr.xr;
import java.util.List;
/** Original deterministic XR stand-in; never included in the distributed mod. */
public final class OpenXrSession implements AutoCloseable {
    @FunctionalInterface public interface Renderer { void render(XrCamera.Pose head,List<XrCamera.View> views,Copy sink) throws Exception; }
    @FunctionalInterface public interface Copy { void copy(int eye,int source,int width,int height); }
    public static boolean failCreate,failEnd,render=true;
    public static int opened,closed,copies,uiCopies;
    public void copyUiTexture(int texture,int width,int height) {
        if(texture!=123 || width!=800 || height!=600) throw new AssertionError("Wrong UI snapshot");
        uiCopies++;
    }
    private boolean done;
    private final FrameTiming timing=new FrameTiming();
    public FrameTiming timing() { return timing; }
    public HandPoses hands() { return HandPoses.NONE; }
    public OpenXrSession() { if(failCreate) throw new IllegalStateException("Injected unavailable runtime"); opened++; }
    public boolean consumeRecenter() { return false; }
    public static void log(String message) { System.out.println("[XR fixture] "+message); }
    public boolean frame(Renderer renderer) throws Exception {
        if(!render || renderer==null) return false;
        var head=new XrCamera.Pose(0,0,0,0,0,0,1);
        var left=new XrCamera.View(new XrCamera.Pose(-.032f,0,0,0,0,0,1),-.6f,.6f,.6f,-.6f);
        var right=new XrCamera.View(new XrCamera.Pose(.032f,0,0,0,0,0,1),-.6f,.6f,.6f,-.6f);
        renderer.render(head,List.of(left,right),(eye,source,w,h)->copies++);
        if(failEnd) throw new IllegalStateException("Injected end-frame/runtime loss");
        return true;
    }
    public void close() { if(!done) { closed++; done=true; } }
}
