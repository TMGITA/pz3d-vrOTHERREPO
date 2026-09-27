import java.nio.file.*;
import java.util.*;
import pzvr.*;
import pzvr.harness.*;
import com.pavelvoronin.pz3d.Renderer;
import static org.lwjgl.glfw.GLFW.*;
public final class LiveMirrorLifecycleTest {
    static int checks;
    static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    static void draw() { new Renderer.Frame().draw(true); }
    static void press(int key) { keys.clear(); draw(); keys.add(GLFW_KEY_LEFT_CONTROL); keys.add(key==GLFW_KEY_SCROLL_LOCK?GLFW_KEY_LEFT_ALT:GLFW_KEY_LEFT_SHIFT); keys.add(key); draw(); }
    public static void main(String[] args) throws Exception {
        var verified=VersionGate.verify(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]));
        Map<String,byte[]> originals=new HashMap<>();
        for(String target:Installation.TARGETS) originals.put(target,Files.readAllBytes(Path.of(args[3],target+".class")));
        Installation installed=Installation.install(InstrumentationAgent.instrumentation,LiveMirrorLifecycleTest.class.getClassLoader(),originals);
        CaptureHarness.configure(verified,installed,Path.of(args[4]));
        draw(); check(EyeCapture.allocations==0,"Disabled mirror allocates nothing");
        press(GLFW_KEY_F9); check(!LiveMirror.enabled(),"Old F9 chord no longer triggers desktop stereo");
        keys.clear(); draw();
        keys.add(GLFW_KEY_SCROLL_LOCK); draw(); check(!LiveMirror.enabled(),"Scroll Lock alone does not toggle");
        keys.add(GLFW_KEY_LEFT_CONTROL); keys.add(GLFW_KEY_LEFT_ALT); focused=false; draw();
        focused=true; draw(); check(!LiveMirror.enabled(),"Focus return cannot toggle held chord");
        press(GLFW_KEY_SCROLL_LOCK);
        check(LiveMirror.enabled() && EyeCapture.stereoMirrors==1,"Chord enables continuous mirror");
        for(int i=0;i<4;i++) draw();
        check(LiveMirror.enabled() && EyeCapture.stereoMirrors==5,"Held toggle stays on and renders every draw");
        check(EyeCapture.allocations==1 && EyeCapture.begins==5 && EyeCapture.saves==0,"GPU targets reused without PNG writes");
        var third=new Renderer.Frame(); third.third=true; third.draw(true);
        check(EyeCapture.closes==1 && EyeCapture.stereoMirrors==5,"Unsupported view releases targets and draws normally");
        draw(); check(EyeCapture.allocations==2 && EyeCapture.stereoMirrors==6,"Supported view resumes");
        org.lwjglx.opengl.Display.width=Renderer.width=1024;
        draw(); check(EyeCapture.allocations==3 && EyeCapture.closes==2,"Resize replaces targets");
        Renderer.reset(); var retained=new Renderer.Frame(); retained.draw(false);
        check(Renderer.visibility==2 && retained.freshCalls.equals(List.of(false)),"Retained draw also mirrors without replaying producer");
        press(GLFW_KEY_F10);
        check(EyeCapture.saves==1 && EyeCapture.mirrors==0 && LiveMirror.enabled(),"One-shot capture preserves stereo preview");
        press(GLFW_KEY_SCROLL_LOCK);
        check(!LiveMirror.enabled(),"Second toggle returns to mono");
        int copies=EyeCapture.copies; draw(); check(EyeCapture.copies==copies,"No eye rendering after disabling");
        press(GLFW_KEY_SCROLL_LOCK); EyeCapture.failSecond=true;
        Renderer.reset(); var broken=new Renderer.Frame(); broken.draw(true);
        check(!LiveMirror.enabled() && !PairHooks.active(),"Failure disables mirror and clears pair state");
        check(broken.freshCalls.equals(List.of(true,false)) && Renderer.visibility==3,"Failure resumes ordinary retained draw");
        check(broken.lease.refs==1 && com.pavelvoronin.pz3d.Main.failures==0,"Failure releases lease without global renderer failure");
        LiveMirror.toggle(); check(!LiveMirror.enabled(),"Failure cannot loop on toggle");
        check(EyeCapture.closes==EyeCapture.allocations,"All targets released");
        System.out.println("Live mirror lifecycle checks passed: "+checks);
    }
}
