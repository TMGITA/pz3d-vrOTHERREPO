package pzvr.xr;

import java.util.Locale;
import java.util.function.Consumer;

/** Render-thread wall-clock diagnostics. No GPU synchronization or per-frame output. */
public final class FrameTiming {
    public enum Stage { INTERVAL, OUTSIDE, TOTAL, WAIT_FRAME, BEGIN_FRAME, LOCATE,
        PREPARE, LEFT, RIGHT, MIRROR_COPY, XR_IMAGE_WAIT, XR_COPY_RELEASE, MIRROR_PRESENT, UI_COPY, END_FRAME }
    private static final long WINDOW=5_000_000_000L, BIN=250_000L;
    private static final int BINS=2048;
    private final long[][] histogram=new long[Stage.values().length][BINS+1];
    private final long[] count=new long[Stage.values().length],sum=count.clone(),max=count.clone();
    private long start,lastStart,lastEnd,attempts,submitted,skipped,failed,overBudget,period;
    private boolean started,previous;
    public void begin(long now) {
        if(!started) { start=now; started=true; }
        if(previous) { add(Stage.INTERVAL,now-lastStart); add(Stage.OUTSIDE,now-lastEnd); }
        lastStart=now; attempts++;
    }
    public void period(long nanos) { period=nanos; }
    public void add(Stage stage,long nanos) {
        nanos=Math.max(0,nanos); int i=stage.ordinal();
        count[i]++; sum[i]+=nanos; max[i]=Math.max(max[i],nanos);
        histogram[i][(int)Math.min(BINS,(nanos+BIN-1)/BIN)]++;
    }
    public void end(long now,boolean rendered,boolean success,Consumer<String> output) {
        add(Stage.TOTAL,now-lastStart);
        if(period>0 && now-lastStart>period) overBudget++;
        if(!success) failed++; else if(rendered) submitted++; else skipped++;
        lastEnd=now; previous=true;
        if(now-start>=WINDOW) report(now,output);
    }
    public void report(long now,Consumer<String> output) {
        if(attempts==0) return;
        double seconds=Math.max(1,now-start)/1e9;
        StringBuilder line=new StringBuilder(String.format(Locale.ROOT,
            "[PZ3D XR Timing] window=%.2fs stereoHz=%.2f calls=%d submitted=%d skipped=%d failed=%d periodMs=%.2f overBudget=%d wallMs(avg/p95upper/max):",
            seconds,submitted/seconds,attempts,submitted,skipped,failed,period/1e6,overBudget));
        for(Stage stage:Stage.values()) {
            int i=stage.ordinal(); if(count[i]==0) continue;
            long target=(count[i]*95+99)/100,total=0; int bin=0;
            for(;bin<BINS;bin++) { total+=histogram[i][bin]; if(total>=target) break; }
            line.append(String.format(Locale.ROOT," %s=%.2f/%s%.2f/%.2f",stage,
                sum[i]/(double)count[i]/1e6,bin==BINS?">":"",bin==BINS?(BINS-1)*BIN/1e6:bin*BIN/1e6,max[i]/1e6));
        }
        output.accept(line.toString());
        for(int i=0;i<count.length;i++) { count[i]=sum[i]=max[i]=0; java.util.Arrays.fill(histogram[i],0); }
        attempts=submitted=skipped=failed=overBudget=0; start=now;
    }
}
