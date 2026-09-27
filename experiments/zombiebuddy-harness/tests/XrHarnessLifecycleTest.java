import java.nio.file.*;
import java.util.*;
import pzvr.*;
import pzvr.harness.*;
import pzvr.xr.OpenXrSession;
import com.pavelvoronin.pz3d.Renderer;
import static org.lwjgl.glfw.GLFW.*;
public final class XrHarnessLifecycleTest {
    static int checks;
    static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    static Renderer.Frame draw() { var frame=new Renderer.Frame(); frame.draw(true); return frame; }
    static void press(int key) { keys.clear(); draw(); keys.add(GLFW_KEY_LEFT_CONTROL); keys.add(GLFW_KEY_LEFT_SHIFT); keys.add(key); draw(); }
    public static void main(String[] args) throws Exception {
        var verified=VersionGate.verify(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]));
        Map<String,byte[]> originals=new HashMap<>();
        for(String target:Installation.TARGETS) originals.put(target,Files.readAllBytes(Path.of(args[3],target+".class")));
        var installed=Installation.install(InstrumentationAgent.instrumentation,XrHarnessLifecycleTest.class.getClassLoader(),originals);
        CaptureHarness.configure(verified,installed,Path.of(args[4]));
        draw(); check(OpenXrSession.opened==0,"XR is opt-in");
        press(GLFW_KEY_F6); check(OpenXrSession.opened==0,"Vanilla time-speed F6 is not an XR trigger");
        press(GLFW_KEY_F7); check(OpenXrSession.opened==0,"Vanilla debug-editor F7 is not an XR trigger");
        keys.clear(); draw(); keys.add(GLFW_KEY_SCROLL_LOCK); draw(); check(OpenXrSession.opened==0,"Bare Scroll Lock does not trigger XR");
        keys.add(GLFW_KEY_LEFT_CONTROL); keys.add(GLFW_KEY_LEFT_SHIFT); draw();
        check(OpenXrSession.opened==0,"Adding modifiers while Scroll Lock is held does not trigger XR");
        OpenXrSession.failCreate=true; press(GLFW_KEY_SCROLL_LOCK);
        check(!XrHarness.active() && com.pavelvoronin.pz3d.Main.failures==0,"Unavailable runtime leaves game renderer healthy");
        OpenXrSession.failCreate=false; press(GLFW_KEY_SCROLL_LOCK);
        check(XrHarness.active() && OpenXrSession.copies==2,"Hotkey creates session and submits both eyes");
        check(OpenXrSession.uiCopies==1,"UI copied once after the two world eyes");
        com.pavelvoronin.pz3d.UiLayer.available(false);
        int uiBefore=OpenXrSession.uiCopies;
        draw();
        check(XrHarness.active() && OpenXrSession.uiCopies==uiBefore,"Missing UI leaves world rendering active");
        com.pavelvoronin.pz3d.UiLayer.available(true);
        draw();
        check(OpenXrSession.uiCopies==uiBefore+1,"UI resumes when snapshot becomes available");
        Renderer.reset(); var frame=draw();
        check(Renderer.prepared==1 && Renderer.visibility==2 && frame.lease.refs==1,"XR uses one coherent pair and releases lease");
        check(OpenXrSession.opened==1 && EyeCapture.allocations==1,"Held key and subsequent draws reuse session and mirror targets");
        check(CaptureHarness.request().contains("Stop OpenXR"),"PNG readback disabled during XR");
        OpenXrSession.render=false; Renderer.reset(); frame=draw();
        check(frame.freshCalls.equals(List.of(true)) && Renderer.visibility==1,"Waiting/shouldRender false uses ordinary draw without duplicate preparation");
        OpenXrSession.render=true;
        for(int alt:new int[]{GLFW_KEY_LEFT_ALT,GLFW_KEY_RIGHT_ALT}) {
            keys.clear(); draw(); keys.add(GLFW_KEY_LEFT_CONTROL); keys.add(GLFW_KEY_LEFT_SHIFT); keys.add(alt); keys.add(GLFW_KEY_SCROLL_LOCK); draw();
            check(XrHarness.active(),"Recenter does not end session");
            keys.remove(alt); draw(); check(XrHarness.active(),"Releasing Alt with Scroll Lock held does not toggle XR");
        }
        press(GLFW_KEY_SCROLL_LOCK); check(!XrHarness.active() && OpenXrSession.closed==1,"Toggle tears down session");
        press(GLFW_KEY_SCROLL_LOCK); var third=new Renderer.Frame(); third.third=true; third.draw(true);
        check(!XrHarness.active() && OpenXrSession.closed==2,"Unsupported camera tears down XR");
        press(GLFW_KEY_SCROLL_LOCK); OpenXrSession.failEnd=true; Renderer.reset(); frame=draw();
        check(!XrHarness.active() && frame.freshCalls.equals(List.of(true,false)),"Runtime loss after pair falls back without repeating fresh preparation");
        check(!PairHooks.active() && frame.lease.refs==1 && OpenXrSession.closed==3,"Runtime loss releases XR and pair ownership");
        check(EyeCapture.closes==EyeCapture.allocations && com.pavelvoronin.pz3d.Main.failures==0,"All mirror targets released; PZ3D failure flag untouched");
        OpenXrSession.failEnd=false; press(GLFW_KEY_SCROLL_LOCK);
        com.pavelvoronin.pz3d.Main.enabled=false; XrHarness.watchdog();
        check(!XrHarness.active() && OpenXrSession.closed==4,"Heartbeat closes XR when PZ3D stops producing frames");
        System.out.println("XR harness lifecycle checks passed: "+checks);
    }
}
