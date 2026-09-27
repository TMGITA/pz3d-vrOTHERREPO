import java.util.ArrayList;
import pzvr.xr.FrameTiming;

public final class FrameTimingTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        FrameTiming timing=new FrameTiming();
        ArrayList<String> lines=new ArrayList<>();
        timing.report(0,lines::add);
        check(lines.isEmpty(),"No report without frames");
        for(int i=0;i<250;i++) {
            timing.begin(i*20_000_000L);
            timing.period(11_111_111L);
            timing.add(FrameTiming.Stage.LEFT,i==249?60_000_000L:2_000_000L);
            timing.end(i*20_000_000L+10_000_000L,i<248,i!=249,lines::add);
        }
        check(lines.isEmpty(),"No per-frame output before five seconds");
        timing.report(5_000_000_000L,lines::add);
        String line=lines.get(0);
        check(line.contains("stereoHz=49.60"),"Rate uses successful submissions and elapsed time");
        check(line.contains("calls=250 submitted=248 skipped=1 failed=1"),"Separate skipped and failed frames");
        check(line.contains("INTERVAL=20.00/20.00/20.00"),"Frame spacing");
        check(line.contains("OUTSIDE=10.00/10.00/10.00"),"Time outside XR");
        check(line.contains("LEFT=2.23/2.00/60.00"),"Spike preserves max without distorting p95");
        check(line.contains("overBudget=0"),"XR total below runtime period");
        timing.report(6_000_000_000L,lines::add);
        check(lines.size()==1,"Empty flush produces no duplicate");
        timing.begin(6_000_000_000L);
        timing.add(FrameTiming.Stage.WAIT_FRAME,700_000_000L);
        timing.end(6_700_000_000L,true,true,lines::add);
        timing.report(7_000_000_000L,lines::add);
        line=lines.get(1);
        check(line.contains("calls=1 submitted=1"),"Window counters reset");
        check(line.contains("overBudget=1"),"Long frame exceeds period");
        check(line.contains("WAIT_FRAME=700.00/>511.75/700.00"),"Histogram overflow is explicit");
        timing.begin(12_000_000_000L);
        timing.end(12_010_000_000L,true,true,lines::add);
        check(lines.size()==3,"Automatic timed report");
        System.out.println("Frame timing checks passed: "+checks);
    }
}
