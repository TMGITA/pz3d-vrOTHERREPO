import java.nio.file.*;
import java.util.*;
import pzvr.*;
import pzvr.harness.*;
import com.pavelvoronin.pz3d.Renderer;
import static org.lwjgl.glfw.GLFW.*;
public final class HarnessLifecycleTest {
    static int checks;
    static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var verified=VersionGate.verify(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]));
        var loader=HarnessLifecycleTest.class.getClassLoader();
        Map<String,byte[]> originals=new HashMap<>();
        for(String target:Installation.TARGETS) originals.put(target,Files.readAllBytes(Path.of(args[3],target+".class")));
        // First attempt deliberately disagrees with one loaded class. Nothing may activate.
        Map<String,byte[]> incompatible=new HashMap<>(originals);
        incompatible.put(Installation.TARGETS.get(1),originals.get(Installation.TARGETS.get(2)));
        boolean rejected=false;
        try { Installation.install(InstrumentationAgent.instrumentation,loader,incompatible); } catch(Exception expected) { rejected=true; }
        check(rejected,"Mismatch must reject entire installation");
        Renderer.reset(); new Renderer.Frame().draw(true);
        check(Renderer.visibility==1,"Rollback restores single-view behavior");
        Installation installed=Installation.install(InstrumentationAgent.instrumentation,loader,originals);
        check(installed.ready(),"All three loaded targets accepted");
        CaptureHarness.configure(verified,installed,Path.of(args[4]));
        check(zombie.core.opengl.RenderThread.renderThread==Thread.currentThread(),"Render thread fixture owner");
        Renderer.reset(); new Renderer.Frame().draw(true);
        check(Renderer.visibility==1 && EyeCapture.copies==0,"No capture without request");
        keys.add(GLFW_KEY_F10); new Renderer.Frame().draw(true);
        check(EyeCapture.copies==0,"F10 alone does not capture");
        keys.add(GLFW_KEY_RIGHT_CONTROL); keys.add(GLFW_KEY_RIGHT_SHIFT); focused=false;
        new Renderer.Frame().draw(true);
        check(EyeCapture.copies==0,"Unfocused chord does not capture");
        focused=true; new Renderer.Frame().draw(true);
        check(EyeCapture.copies==0,"Refocusing with chord held does not capture");
        keys.clear(); new Renderer.Frame().draw(true);
        keys.add(GLFW_KEY_F10); keys.add(GLFW_KEY_LEFT_CONTROL); keys.add(GLFW_KEY_LEFT_SHIFT);
        Renderer.Frame third=new Renderer.Frame(); third.third=true; third.draw(true);
        check(CaptureHarness.request().contains("already pending"),"Raw chord queues request without Lua; requests coalesce");
        check(EyeCapture.copies==0,"Unsupported camera keeps ordinary rendering and request pending");
        Renderer.reset(); Renderer.Frame frame=new Renderer.Frame(); frame.draw(true);
        check(Renderer.prepared==1 && Renderer.visibility==2,"Entry recursion yields one preparation and two eyes");
        check(EyeCapture.copies==2 && EyeCapture.mirrors==1 && EyeCapture.saves==1 && EyeCapture.closes==1,"Capture lifecycle");
        check(frame.lease.refs==1 && !PairHooks.active(),"Pair ownership released");
        check(CaptureHarness.status().startsWith("Captured stereo pair:"),"Success status");
        new Renderer.Frame().draw(true);
        check(EyeCapture.copies==2,"Holding chord captures only once");
        keys.clear(); new Renderer.Frame().draw(true);
        keys.add(GLFW_KEY_F10); keys.add(GLFW_KEY_RIGHT_CONTROL); keys.add(GLFW_KEY_RIGHT_SHIFT);
        EyeCapture.failSecond=true; Renderer.reset(); Renderer.Frame broken=new Renderer.Frame(); broken.draw(true);
        check(broken.freshCalls.equals(List.of(true,false)),"Failure fallback does not repeat fresh native preparation");
        check(com.pavelvoronin.pz3d.Main.failures==0,"Capture exception isolated from PZ3D failure handler");
        check(Renderer.visibility==3,"Failed pair unwinds and ordinary draw resumes");
        check(EyeCapture.closes==2 && !PairHooks.active(),"Failure cleans targets and thread-local state");
        check(CaptureHarness.request().contains("disabled after failure"),"No repeat failure loop");
        int successes=0,failures=0;
        try(var paths=Files.walk(Path.of(args[4]))) {
            for(Path p:paths.filter(p->p.getFileName().toString().equals("capture.properties")).toList()) {
                Properties result=new Properties(); try(var in=Files.newInputStream(p)) { result.load(in); }
                if("STEREO_PAIR_CAPTURED".equals(result.getProperty("status"))) {
                    successes++; check("1".equals(result.getProperty("preparations")) && "2".equals(result.getProperty("copies")),"Report counts");
                    check("true".equals(result.getProperty("leaseBracketCompleted")),"Report cleanup evidence");
                } else failures++;
            }
        }
        check(successes==1 && failures==1,"Distinct success/failure reports");
        System.out.println("Harness lifecycle checks passed: "+checks+"; real JVM retransformation, synthetic renderer, no game execution.");
    }
}
