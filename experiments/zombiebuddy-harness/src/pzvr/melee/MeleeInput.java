package pzvr.melee;

import java.util.concurrent.atomic.AtomicReference;
import pzvr.xr.XrCamera;

/** Render-to-game mailbox. Permit identities prevent stale swings crossing UI/equipment changes. */
public final class MeleeInput {
    public static final long FRESH=150_000_000L;
    public record State(long time,boolean held,boolean valid) {}
    public record Permit(long epoch,Object player,Object weapon,long time) {}
    public record Swing(long id,Permit permit,long time) {}
    public static volatile int mode;
    public static volatile boolean uiBlocked;
    public static volatile State state=new State(0,false,false);
    public static volatile Permit permit;
    public static final AtomicReference<Swing> pending=new AtomicReference<>();
    private static final SwingDetector detector=new SwingDetector();
    private static long sequence,lastEpoch=-1;
    private MeleeInput() {}
    public static boolean fresh(State s,long now) { return s.valid && now>=s.time && now-s.time<=FRESH; }
    /** Only the render owner calls sample/reset. */
    public static void reset() { detector.reset(); state=new State(0,false,false); pending.set(null); lastEpoch=-1; }
    public static void sample(long now,long poseTime,XrCamera.Pose hand,XrCamera.Pose head,boolean held,boolean valid) {
        valid=valid && hand!=null && head!=null && mode!=0 && !uiBlocked;
        state=new State(now,held,valid);
        if(mode>=3) { detector.reset(); pending.set(null); return; }
        Permit p=permit;
        if(!valid || p==null || now<p.time || now-p.time>FRESH) { detector.reset(); pending.set(null); return; }
        if(lastEpoch!=p.epoch) { detector.reset(); pending.set(null); lastEpoch=p.epoch; }
        if(detector.sample(new SwingDetector.Sample(poseTime,hand.x(),hand.y(),hand.z(),head.x(),head.y(),head.z(),held,true))) {
            Swing swing=new Swing(++sequence,p,now);
            pending.compareAndSet(null,swing);
            System.out.println("[PZ3D VR Melee] gesture id="+swing.id+" mode="+mode);
        }
    }
}
