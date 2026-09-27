import pzvr.harness.RecenterCountdown;

public final class RecenterCountdownTest {
    static int checks;
    static void check(boolean ok,String reason) { checks++; if(!ok) throw new AssertionError(reason); }
    public static void main(String[] args) {
        RecenterCountdown timer=new RecenterCountdown(); long now=10_000_000_000L;
        check(!timer.update(now,true)&&timer.message().isEmpty(),"Idle never calibrates");
        timer.request(now);
        check(timer.message().contains("5"),"Visible five-second countdown");
        check(!timer.update(now+1_000_000_000L,true)&&timer.message().contains("4"),"No immediate calibration");
        check(!timer.update(now+4_999_999_999L,true),"Wait full interval");
        check(!timer.update(now+5_000_000_000L,false)&&timer.message().contains("waiting"),"Wait for focus/tracking");
        check(timer.update(now+6_000_000_000L,true),"Calibrate when ready after deadline");
        check(!timer.update(now+6_000_000_001L,true)&&timer.message().contains("complete"),"Complete once with confirmation");
        check(!timer.update(now+9_000_000_000L,true)&&timer.message().isEmpty(),"Confirmation clears");
        timer.request(now); timer.request(now+4_000_000_000L);
        check(!timer.update(now+5_000_000_000L,true),"Another press restarts countdown");
        check(timer.update(now+9_000_000_000L,true),"Restarted countdown completes");
        timer.request(now); timer.cancel();
        check(!timer.update(now+20_000_000_000L,true)&&timer.message().isEmpty(),"Session stop cancels pending work");
        System.out.println("Recenter countdown checks passed: "+checks);
    }
}
