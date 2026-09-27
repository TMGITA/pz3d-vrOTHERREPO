package pzvr.harness;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import pzvr.*;
import static org.lwjgl.glfw.GLFW.*;

/** One-shot requests. Called on the existing render thread by the transformed draw entry. */
public final class CaptureHarness {
    private static final AtomicBoolean pending=new AtomicBoolean();
    private static volatile Installation installation;
    private static VersionGate.Verified verified;
    private static Path root;
    private static volatile String status="Not configured";
    private static volatile boolean faulted;
    private static boolean chordHeld;
    private static boolean xrKeyHeld;
    private static void pollCaptureKey() {
        long window=org.lwjglx.opengl.Display.getWindow();
        if(window==0 || glfwGetWindowAttrib(window,GLFW_FOCUSED)!=GLFW_TRUE) {
            chordHeld=true; // Require release before accepting a chord after focus returns.
            xrKeyHeld=true;
            return;
        }
        boolean ctrl=glfwGetKey(window,GLFW_KEY_LEFT_CONTROL)==GLFW_PRESS || glfwGetKey(window,GLFW_KEY_RIGHT_CONTROL)==GLFW_PRESS;
        boolean shift=glfwGetKey(window,GLFW_KEY_LEFT_SHIFT)==GLFW_PRESS || glfwGetKey(window,GLFW_KEY_RIGHT_SHIFT)==GLFW_PRESS;
        boolean alt=glfwGetKey(window,GLFW_KEY_LEFT_ALT)==GLFW_PRESS || glfwGetKey(window,GLFW_KEY_RIGHT_ALT)==GLFW_PRESS;
        boolean modifiers=ctrl&&shift;
        boolean down=glfwGetKey(window,GLFW_KEY_F10)==GLFW_PRESS && modifiers;
        if(down && !chordHeld) System.out.println("[PZ3D VR Test] "+request());
        chordHeld=down;
        boolean xrDown=glfwGetKey(window,GLFW_KEY_SCROLL_LOCK)==GLFW_PRESS;
        if(xrDown && !xrKeyHeld && ctrl) {
            if(shift) {
                if(alt) XrHarness.recenter();
                else { pending.set(false); XrHarness.toggle(); }
            } else if(alt) {
                if(XrHarness.active()) XrHarness.toggleArmPreview(); else LiveMirror.toggle();
            }
        }
        // Latch the physical key, so changing modifiers while Scroll Lock is held cannot toggle XR.
        xrKeyHeld=xrDown;
    }
    public static void configure(VersionGate.Verified v,Installation i,Path output) { verified=v; installation=i; root=output; status="Ready"; }
    public static String status() { return status; }
    public static String request() {
        if(XrHarness.active()) return "Stop OpenXR with Ctrl+Shift+Scroll Lock before saving a diagnostic PNG pair";
        if(faulted) return "Capture disabled after failure; restart after reviewing report";
        if(!pending.compareAndSet(false,true)) return "A capture is already pending";
        status="Capture requested; enter first-person PZ3D on foot";
        return status;
    }
    public static boolean intercept(Object frame,boolean fresh) {
        if(PairHooks.active() || faulted || installation==null || !installation.ready()) return false;
        // Read GLFW state on the existing render thread: PZ3D can swallow Lua key events.
        pollCaptureKey();
        // Leave unsupported contexts on the ordinary renderer without consuming the request.
        if(zombie.network.GameClient.client || zombie.network.GameServer.server || !com.pavelvoronin.pz3d.Main.active()
            || com.pavelvoronin.pz3d.Main.failed() || !PairHooks.eligible(frame)) {
            LiveMirror.suspend(); if(XrHarness.active()) XrHarness.stop("Requires first person, on foot, single player"); return false;
        }
        int width=org.lwjglx.opengl.Display.getFramebufferWidth(), height=org.lwjglx.opengl.Display.getFramebufferHeight();
        if(width<2 || height<1) { LiveMirror.suspend(); if(XrHarness.active()) XrHarness.stop("Window minimized"); return false; }
        if(XrHarness.active()) return XrHarness.draw(verified,frame,fresh,width,height);
        if(!pending.compareAndSet(true,false)) return LiveMirror.draw(verified,frame,fresh,width,height);
        Path output=root.resolve(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now())+"-"+UUID.randomUUID().toString().substring(0,8));
        Properties report=new Properties();
        report.setProperty("status","FAILED"); report.setProperty("gameVersion","42.20.4"); report.setProperty("pz3dVersion","0.2.2");
        report.setProperty("headsetValidated","false"); report.setProperty("openxrSubmission","false");
        report.setProperty("freshFrame",Boolean.toString(fresh)); report.setProperty("separationSceneUnits","0.064");
        report.setProperty("renderThread",Thread.currentThread().getName());
        boolean pairInvoked=false;
        try(EyeCapture targets=new EyeCapture(width,height)) {
            Files.createDirectories(output);
            pairInvoked=true;
            PairHooks.Result result=PairHooks.render(verified,frame,fresh,base -> {
                var eyes=PairHooks.synthetic(.064f).create(base);
                report.setProperty("baseCamera",base.toString());
                for(int i=0;i<2;i++) {
                    var eye=eyes.get(i);
                    report.setProperty("eye"+i+"Position",eye.x()+","+eye.y()+","+eye.z());
                    report.setProperty("eye"+i+"ViewProjection",Arrays.toString(eye.viewProjection().get(new float[16])));
                }
                return eyes;
            },targets);
            // The pair has restored the game's inherited GL state. Mirror one completed eye.
            if(LiveMirror.enabled()) targets.mirrorStereo(); else targets.mirrorLeft();
            targets.save(output);
            report.setProperty("preparations",Integer.toString(result.preparations())); report.setProperty("copies",Integer.toString(result.copies()));
            report.setProperty("sceneVersion",Long.toString(result.sceneVersion())); report.setProperty("frozenNanos",Long.toString(result.frozenNanos()));
            report.setProperty("generation",Long.toString(result.generation())); report.setProperty("preparedFingerprint",Long.toString(result.preparedFingerprint()));
            report.setProperty("leaseBracketCompleted","true");
            report.setProperty("width",Integer.toString(width)); report.setProperty("height",Integer.toString(height));
            report.setProperty("status","STEREO_PAIR_CAPTURED");
            status="Captured stereo pair: "+output;
            return true;
        } catch(Throwable error) {
            report.setProperty("status","FAILED");
            faulted=true; report.setProperty("error",error.toString());
            LiveMirror.stop();
            status="Capture failed; ordinary rendering resumes. Report: "+output;
            error.printStackTrace();
            // Reuse the prepared native resources; never repeat fresh-frame native preparation.
            if(pairInvoked) {
                try { frame.getClass().getMethod("draw",boolean.class).invoke(frame,false); }
                catch(Throwable fallback) { report.setProperty("fallbackError",fallback.toString()); fallback.printStackTrace(); }
                return true;
            }
            // Allocation failed before entering the pair: the original fresh draw remains safe.
            return false;
        } finally {
            try {
                Files.createDirectories(output);
                try(var out=Files.newOutputStream(output.resolve("capture.properties"))) { report.store(out,"PZ3D synthetic stereo test; inspect images before claiming visual correctness"); }
            } catch(Exception error) { System.err.println("[PZ3D VR Test] Could not save report: "+error); }
            System.out.println("[PZ3D VR Test] "+status);
        }
    }
}
