package pzvr.input;

/** Temporary conventional gamepad mapping. Future VR consumers can bypass this entirely. */
public final class GamepadMapper {
    public record Pad(float[] axes,boolean[] buttons,int hat) {}
    private boolean armed,lb,rb;
    public void reset() { armed=false; lb=false; rb=false; menuWasHeld=false; alternate=false; }
    public static Pad neutral() { return new Pad(new float[]{0,0,0,0,-1,-1},new boolean[15],0); }
    public Pad map(VrControllerState state,long now,boolean enabled) {
        if(!enabled || !state.usable(now) || !finite(state.left()) || !finite(state.right())) { reset(); return neutral(); }
        if(!armed) { armed=state.neutral(); return neutral(); }
        var l=state.left(); var r=state.right();
        float[] axes={clamp(l.x()),-clamp(l.y()),clamp(r.x()),-clamp(r.y()),2*unit(l.trigger())-1,2*unit(r.trigger())-1};
        boolean[] b=new boolean[15];
        b[0]=r.primary(); b[1]=r.secondary(); b[2]=l.primary(); b[3]=l.secondary();
        lb=l.squeeze()>=(lb?.45f:.65f); rb=r.squeeze()>=(rb?.45f:.65f);
        b[4]=lb; b[5]=rb; b[9]=l.stick(); b[10]=r.stick();
        // Menu alone is Start. Menu held is an alternate layer: left stick -> D-pad, X -> Back.
        // Start is emitted on menu release only if no alternate was used (see below).
        int hat=0;
        if(l.menu()) {
            if(!menuWasHeld) alternate=false;
            if(l.y()>.55f) { b[11]=true; hat|=1; }
            if(l.x()>.55f) { b[12]=true; hat|=2; }
            if(l.y()<-.55f) { b[13]=true; hat|=4; }
            if(l.x()<-.55f) { b[14]=true; hat|=8; }
            b[6]=l.primary(); b[2]=false; axes[0]=0; axes[1]=0;
            alternate|=hat!=0||b[6];
        } else if(menuWasHeld && !alternate) b[7]=true;
        menuWasHeld=l.menu();
        return new Pad(axes,b,hat);
    }
    private boolean menuWasHeld,alternate;
    public void invalidate() { reset(); menuWasHeld=false; alternate=false; }
    private static float clamp(float f) { return Math.max(-1,Math.min(1,f)); }
    private static float unit(float f) { return Math.max(0,Math.min(1,f)); }
    private static boolean finite(VrControllerState.Hand h) {
        return Float.isFinite(h.x())&&Float.isFinite(h.y())&&Float.isFinite(h.trigger())&&Float.isFinite(h.squeeze());
    }
}

