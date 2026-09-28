import pzvr.melee.*;
import pzvr.xr.XrCamera;

public final class SwingDetectorTest {
    static int checks;
    static void check(boolean ok,String why) { checks++; if(!ok) throw new AssertionError(why); }
    static SwingDetector.Sample s(int n,float x,float hx,boolean held) { return new SwingDetector.Sample(1_000_000_000L+n*20_000_000L,x,0,0,hx,0,0,held,true); }
    public static void main(String[] args) {
        SwingDetector d=new SwingDetector();
        for(int i=0;i<8;i++) check(!d.sample(s(i,i*.05f,0,true)),"Held-at-start never arms");
        d.sample(s(8,0,0,false));
        check(!d.sample(s(9,.05f,0,true)),"Require travel");
        check(!d.sample(s(10,.10f,0,true)),"Still below travel threshold");
        check(d.sample(s(11,.15f,0,true)),"Deliberate swing fires");
        for(int i=12;i<20;i++) check(!d.sample(s(i,.15f-(i-11)*.05f,0,true)),"Follow-through and reverse motion cannot repeat");
        d.sample(s(20,0,0,false)); d.sample(s(21,.05f,0,true)); d.sample(s(22,.1f,0,true));
        check(d.sample(s(23,.15f,0,true)),"Release enables next swing");
        d.reset(); d.sample(s(0,0,0,false));
        for(int i=1;i<20;i++) check(!d.sample(s(i,i*.05f,i*.05f,true)),"Whole-body movement is not a swing");
        d.reset(); d.sample(s(0,0,0,false));
        for(int i=1;i<20;i++) check(!d.sample(s(i,0,i*.05f,true)),"Head movement alone is not a swing");
        d.reset(); d.sample(s(0,0,0,false));
        for(int i=1;i<20;i++) check(!d.sample(s(i,i*.002f,0,true)),"Slow motion/jitter is not a swing");
        d.reset(); d.sample(s(0,0,0,false));
        check(!d.sample(s(1,1,0,true)),"Tracking jump rejected");
        for(int i=2;i<8;i++) check(!d.sample(s(i,i*.05f,0,true)),"Tracking jump requires new trigger release");
        d.reset(); d.sample(s(0,0,0,false));
        check(!d.sample(s(10,.15f,0,true)),"Frame gap rejected");
        check(!d.sample(s(11,Float.NaN,0,true)),"Invalid sample rejected");
        d.reset(); d.sample(s(3,0,0,false));
        check(!d.sample(s(2,.15f,0,true)),"Out-of-order timestamps rejected");

        Object player=new Object(),weapon=new Object(); long now=System.nanoTime();
        MeleeInput.mode=2; MeleeInput.uiBlocked=false; MeleeInput.reset();
        MeleeInput.permit=new MeleeInput.Permit(1,player,weapon,now);
        var head=new XrCamera.Pose(0,0,0,0,0,0,1);
        for(int i=0;i<4;i++) MeleeInput.sample(now+i*20_000_000L,1_000_000_000L+i*20_000_000L,
            new XrCamera.Pose(i*.05f,0,0,0,0,0,1),head,i!=0,true);
        check(MeleeInput.pending.get()!=null,"Fresh permitted swing enters mailbox");
        MeleeInput.uiBlocked=true;
        MeleeInput.sample(now+80_000_000L,1_080_000_000L,head,head,true,true);
        check(MeleeInput.pending.get()==null&&!MeleeInput.state.valid(),"Opening settings discards pending swing");
        MeleeInput.uiBlocked=false; MeleeInput.permit=new MeleeInput.Permit(2,player,new Object(),now+80_000_000L);
        for(int i=5;i<9;i++) MeleeInput.sample(now+i*20_000_000L,1_000_000_000L+i*20_000_000L,
            new XrCamera.Pose(i*.05f,0,0,0,0,0,1),head,true,true);
        check(MeleeInput.pending.get()==null,"Equipment epoch change requires release");
        MeleeInput.reset(); check(!MeleeInput.state.valid()&&MeleeInput.pending.get()==null,"Session/recenter reset clears input");
        check(!MeleeInput.fresh(new MeleeInput.State(now,true,true),now+MeleeInput.FRESH+1),"Heartbeat expiry");
        MeleeInput.mode=0; MeleeInput.permit=null;
        System.out.println("Swing detector/mailbox checks passed: "+checks);
    }
}
