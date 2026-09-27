package pzvr.harness;

import java.util.Arrays;
import java.util.function.IntPredicate;
import org.lwjglx.input.KeyCodes;

/** Immutable settings cross from Lua to rendering; physical-key latches belong to rendering. */
public final class Hotkeys {
    public static final int XR=0, RECENTER=1, PREVIEW=2, CAPTURE=3;
    private record Binding(int key,int modifiers) {}
    private static volatile Binding[] configured={new Binding(281,3),new Binding(281,7),new Binding(281,5),new Binding(299,3)};
    private static volatile boolean blocked;
    private Binding[] applied=configured;
    private final boolean[] held=new boolean[4];

    private static Binding binding(int key,int modifiers) {
        int glfw=key<=0?-1:KeyCodes.toGlfwKey(key);
        // A modifier alone cannot be an action key. Unknown/unbound codes never reach GLFW.
        if(glfw<32 || glfw>348 || (glfw>=340 && glfw<=347)) glfw=-1;
        return new Binding(glfw,modifiers>=0 && modifiers<=7?modifiers:0);
    }
    public static void configure(int xr,int xrMods,int recenter,int recenterMods,int preview,int previewMods,int capture,int captureMods) {
        Binding[] next={binding(xr,xrMods),binding(recenter,recenterMods),binding(preview,previewMods),binding(capture,captureMods)};
        if(!Arrays.equals(configured,next)) configured=next;
    }
    public static void block(boolean value) { blocked=value; }

    /** Bit mask of actions; ambiguous identical chords deliberately trigger nothing. */
    public int poll(boolean focused,int modifiers,IntPredicate down) {
        Binding[] bindings=configured;
        if(bindings!=applied) {
            applied=bindings;
            Arrays.fill(held,true); // Applying settings never activates a key already held.
        }
        if(!focused || blocked) {
            Arrays.fill(held,true);
            return 0;
        }
        int actions=0;
        for(int i=0;i<bindings.length;i++) {
            Binding b=bindings[i];
            boolean pressed=b.key>=0 && down.test(b.key);
            boolean duplicate=false;
            for(int j=0;j<bindings.length;j++) if(j!=i && b.equals(bindings[j])) duplicate=true;
            if(pressed && !held[i] && modifiers==b.modifiers && !duplicate) actions|=1<<i;
            // Latch the physical key, not the chord, including when modifiers do not match.
            held[i]=pressed;
        }
        return actions;
    }
}
