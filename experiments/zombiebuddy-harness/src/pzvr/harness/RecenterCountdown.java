package pzvr.harness;

/** Render-thread timer; only its status text crosses to the Lua UI thread. */
public final class RecenterCountdown {
    private long deadline,noticeUntil;
    private boolean pending;
    private volatile String message="";
    public void request(long now) {
        pending=true; deadline=now+5_000_000_000L;
        message="Recenter in 5 - face forward and hold controllers neutrally";
    }
    public void cancel() { pending=false; message=""; noticeUntil=0; }
    public String message() { return message; }
    public boolean pending() { return pending; }
    public boolean update(long now,boolean ready) {
        if(!pending) {
            if(now>=noticeUntil) message="";
            return false;
        }
        if(now<deadline) {
            message="Recenter in "+((deadline-now+999_999_999L)/1_000_000_000L)+" - face forward and hold controllers neutrally";
            return false;
        }
        if(!ready) { message="Recenter waiting for VR focus and controller tracking"; return false; }
        pending=false; noticeUntil=now+3_000_000_000L; message="Recenter complete";
        return true;
    }
}
