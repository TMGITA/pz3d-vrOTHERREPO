package pzvr.harness;
import java.nio.file.Path;
import pzvr.PairHooks;
/** CPU lifecycle stand-in. Actual OpenGL class is tested separately on a real context. */
public final class EyeCapture implements PairHooks.Sink,AutoCloseable {
    public static int copies,mirrors,saves,closes,allocations,begins,stereoMirrors;
    public static boolean failSecond;
    public EyeCapture(int w,int h) { allocations++; }
    public void beginPair() { begins++; }
    public void mirrorStereo() { stereoMirrors++; }
    public void copy(int eye,int source,int w,int h) { if(eye==1 && failSecond) throw new IllegalStateException("Injected copy failure"); copies++; }
    public void mirrorLeft() { mirrors++; }
    public void save(Path path) { saves++; }
    public void close() { closes++; }
}
