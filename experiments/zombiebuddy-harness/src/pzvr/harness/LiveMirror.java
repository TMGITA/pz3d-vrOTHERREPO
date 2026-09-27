package pzvr.harness;

import pzvr.*;

/** Render-thread-owned continuous desktop preview. No readback, files, or extra GL context. */
public final class LiveMirror {
    private static boolean enabled,failed;
    private static EyeCapture targets;
    private static int width,height;
    private static long pairs;
    private LiveMirror() {}
    public static boolean enabled() { return enabled; }
    public static void toggle() {
        if(failed) { log("Disabled after failure; restart before retrying"); return; }
        enabled=!enabled;
        if(!enabled) release();
        log(enabled?"ON: left eye | right eye; first person, on foot":"OFF; completed pairs="+pairs);
    }
    public static void suspend() { release(); }
    public static void stop() { enabled=false; release(); }
    private static void release() { if(targets!=null) { targets.close(); targets=null; } }
    private static void log(String text) { System.out.println("[PZ3D VR Mirror] "+text); }
    public static boolean draw(VersionGate.Verified verified,Object frame,boolean fresh,int w,int h) {
        if(!enabled) return false;
        boolean invoked=false;
        try {
            if(targets==null || width!=w || height!=h) {
                release(); targets=new EyeCapture(w,h); width=w; height=h;
            }
            targets.beginPair();
            invoked=true;
            PairHooks.Result result=PairHooks.render(verified,frame,fresh,PairHooks.synthetic(.064f),targets);
            targets.mirrorStereo();
            pairs++;
            if(pairs==1 || pairs%600==0) log("Rendered pairs="+pairs+"; preparations="+result.preparations()+"; copies="+result.copies()+"; eye size="+w+"x"+h);
            return true;
        } catch(Throwable error) {
            failed=true; stop();
            log("FAILED; ordinary rendering resumes; completed pairs="+pairs+"; "+error);
            error.printStackTrace();
            if(invoked) {
                try { frame.getClass().getMethod("draw",boolean.class).invoke(frame,false); }
                catch(Throwable fallback) { fallback.printStackTrace(); }
                return true;
            }
            return false;
        }
    }
}
