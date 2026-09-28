package pzvr.input;

/** Immutable acquisition values, independent of Zomboid and Xbox indices. */
public record VrControllerState(long sequence,long time,boolean focused,Hand left,Hand right) {
    public record Hand(String profile,boolean active,float x,float y,float trigger,float squeeze,
                       boolean primary,boolean secondary,boolean stick,boolean menu) {}
    public static final Hand NONE=new Hand("",false,0,0,0,0,false,false,false,false);
    public static final VrControllerState EMPTY=new VrControllerState(0,0,false,NONE,NONE);
    public boolean usable(long now) {
        return focused && time>0 && now>=time && now-time<=250_000_000L && left.active() && right.active();
    }
    public boolean neutral() {
        return neutral(left)&&neutral(right);
    }
    private static boolean neutral(Hand h) {
        return Math.abs(h.x())<.2f && Math.abs(h.y())<.2f && h.trigger()<.2f && h.squeeze()<.2f
            && !h.primary()&&!h.secondary()&&!h.stick()&&!h.menu();
    }
}
