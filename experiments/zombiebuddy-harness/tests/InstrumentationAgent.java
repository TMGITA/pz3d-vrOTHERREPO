import java.lang.instrument.Instrumentation;
/** Standalone test agent; never bundled with the mod. */
public final class InstrumentationAgent {
    public static Instrumentation instrumentation;
    public static void premain(String args,Instrumentation value) { instrumentation=value; }
}
