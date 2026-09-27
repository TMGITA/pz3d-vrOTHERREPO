package pzvr;

import java.lang.reflect.*;
import java.nio.FloatBuffer;
import java.util.*;
import org.joml.Matrix4f;

/** Opt-in render-thread bridge. No agent, entry point, or automatic game hook installation. */
public final class PairHooks {
    private static final ThreadLocal<State> CURRENT=new ThreadLocal<>();
    private PairHooks() {}
    public record Eye(float x,float y,float z,Matrix4f viewProjection) {
        public Eye { viewProjection=new Matrix4f(viewProjection); }
        @Override public Matrix4f viewProjection() { return new Matrix4f(viewProjection); }
    }
    public record Base(float x,float y,float z,float dx,float dy,float dz,float fov,int width,int height) {}
    public interface EyeFactory { List<Eye> create(Base base); }
    /** Must copy synchronously before the scratch target is cleared for the other eye. */
    public interface Sink {
        void copy(int eye,int sourceFramebuffer,int width,int height);
        default void beginEye(int eye) {}
    }
    @FunctionalInterface public interface PoseOverride { AutoCloseable apply(Object frame,Base base) throws Exception; }
    public record Result(int preparations,int copies,long sceneVersion,long frozenNanos,long generation,long preparedFingerprint) {}

    /** Caller owns the existing GL render thread. The original producer/retained scheduler is not replayed here. */
    public static Result render(VersionGate.Verified verified,Object frame,boolean fresh,EyeFactory factory,Sink sink) throws Exception {
        return render(verified,frame,fresh,factory,sink,(f,b)->()->{});
    }
    public static Result render(VersionGate.Verified verified,Object frame,boolean fresh,EyeFactory factory,Sink sink,PoseOverride pose) throws Exception {
        Objects.requireNonNull(verified);
        if(!frame.getClass().getName().equals("com.pavelvoronin.pz3d.Renderer$Frame")) throw new IllegalArgumentException("Wrong frame class");
        if(CURRENT.get()!=null) throw new IllegalStateException("Nested stereo pair");
        if((boolean)get(frame,"third") || get(frame,"rider")!=null) throw new IllegalStateException("Proof supports first-person on foot only");
        ClassLoader loader=frame.getClass().getClassLoader();
        Class<?> renderThread=Class.forName("zombie.core.opengl.RenderThread",false,loader);
        if(getStatic(renderThread,"renderThread")!=Thread.currentThread() || getStatic(renderThread,"contextThread")!=Thread.currentThread())
            throw new IllegalStateException("Must run on the existing game render/context thread");
        State s=new State(frame,factory,sink,pose);
        Object lease=get(frame,"lease");
        call(lease,"retain");
        try {
            CURRENT.set(s);
            frame.getClass().getMethod("draw",boolean.class).invoke(frame,fresh);
            if(s.failure!=null || s.preparations!=1 || s.copies!=2 || !s.finished) throw new IllegalStateException("Pair incomplete: boundary="+s.preparations+", copies="+s.copies+"; original draw may have failed or skipped",s.failure);
            return new Result(s.preparations,s.copies,s.sceneVersion,s.time,((Number)get(frame,"generation")).longValue(),s.fingerprint);
        } finally {
            try { if(s.poseScope!=null) s.poseScope.close(); }
            finally { try { restore(s); } finally { CURRENT.remove(); call(lease,"release"); } }
        }
    }

    public static EyeFactory synthetic(float separation) {
        if(!Float.isFinite(separation) || separation<=0 || separation>.15f) throw new IllegalArgumentException("Invalid diagnostic eye separation");
        return b -> {
            float horizontal=(float)Math.hypot(b.dx,b.dy);
            if(horizontal<.001f) throw new IllegalStateException("Diagnostic camera too close to vertical");
            List<Eye> result=new ArrayList<>();
            for(int eye=0;eye<2;eye++) {
                float offset=(eye==0?-.5f:.5f)*separation;
                float x=b.x-b.dy/horizontal*offset,y=b.y+b.dx/horizontal*offset;
                Matrix4f matrix=new Matrix4f().perspective(b.fov,(float)b.width/b.height,.02f,160)
                    .lookAt(x,-y,b.z,x+b.dx,-y-b.dy,b.z+b.dz,0,0,1).scale(1,-1,1);
                result.add(new Eye(x,y,b.z,matrix));
            }
            return List.copyOf(result);
        };
    }

    public static boolean active() { return CURRENT.get()!=null; }
    public static boolean eligible(Object frame) { return !(boolean)get(frame,"third") && get(frame,"rider")==null; }
    public static void renderFailure(String stage,Throwable failure) { required().failure=failure; }
    public static boolean firstEye() { State s=CURRENT.get(); return s!=null && s.eye==0; }
    public static long fadeClock() { State s=CURRENT.get(); return s==null?System.nanoTime():s.time; }
    public static Object treeEnvironment(Object current) {
        State s=CURRENT.get(); if(s==null) return current;
        if(s.treeEnvironment==null) s.treeEnvironment=Objects.requireNonNull(current);
        return s.treeEnvironment;
    }
    public static Matrix4f projection(float a,float x,float y,float z,float dx,float dy,float dz,float fov) {
        State s=required(); return s.eyes.get(s.eye).viewProjection();
    }
    /** Inserted once, after common shadow preparation and before the eye loop. */
    public static void prepared(Object frame) {
        State s=CURRENT.get(); if(s==null) return;
        if(s.frame!=frame || ++s.preparations!=1) throw new IllegalStateException("Preparation ownership violation");
        s.base=new float[]{number(frame,"x"),number(frame,"y"),number(frame,"z")};
        s.scene=get(frame,"scene"); s.sceneVersion=((Number)call(s.scene,"version")).longValue();

        Class<?> renderer=frame.getClass().getEnclosingClass();
        s.width=(int)getStatic(renderer,"width"); s.height=(int)getStatic(renderer,"height");
        Base base=new Base(s.base[0],s.base[1],s.base[2],number(frame,"dx"),number(frame,"dy"),number(frame,"dz"),number(frame,"fov"),s.width,s.height);
        s.eyes=List.copyOf(s.factory.create(base));
        try { s.poseScope=s.pose.apply(frame,base); }
        catch(Exception failure) { throw new IllegalStateException("Pose override failed",failure); }
        s.fingerprint=fingerprint(frame);
        if(s.eyes.size()!=2) throw new IllegalArgumentException("Exactly two views required");
        for(Eye e:s.eyes) if(!Float.isFinite(e.x)||!Float.isFinite(e.y)||!Float.isFinite(e.z)||!e.viewProjection.isFinite()) throw new IllegalArgumentException("Invalid eye");
    }
    /** Loop header, executed before any eye-dependent renderer call. */
    public static void enterEye(Object frame) {
        State s=CURRENT.get(); if(s==null) return;
        coherent(s);
        Eye e=s.eyes.get(s.eye); set(frame,"x",e.x); set(frame,"y",e.y); set(frame,"z",e.z);
        s.sink.beginEye(s.eye);
    }
    /** Replaces only the final color blit. The caller supplies two destinations. */
    public static void copyEye() {
        State s=required(); coherent(s);
        int source=(int)getStatic(s.frame.getClass().getEnclosingClass(),"nw");
        s.sink.copy(s.eye,source,s.width,s.height);
        s.copies++;
    }
    public static boolean nextEye() {
        State s=CURRENT.get(); if(s==null) return false;
        coherent(s);
        if(s.copies!=s.eye+1) throw new IllegalStateException("Eye not copied before scratch reuse");
        if(s.eye++==0) return true;
        s.finished=true; restore(s); return false;
    }

    private static void coherent(State s) {
        if(get(s.frame,"scene")!=s.scene || fingerprint(s.frame)!=s.fingerprint) throw new IllegalStateException("Scene/body/palette changed within pair");
        try {
            Class<?> retained=Class.forName("com.pavelvoronin.pz3d.RetainedRender",false,s.frame.getClass().getClassLoader());
            Method generation=retained.getDeclaredMethod("generation"); generation.setAccessible(true);
            if(!generation.invoke(null).equals(get(s.frame,"generation"))) throw new IllegalStateException("Frame generation changed during pair");
        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static long fingerprint(Object frame) {
        long hash=1;
        for(String group:List.of("characters","vehicles")) for(Object actor:(Iterable<?>)get(frame,group)) {
            for(Object part:(Iterable<?>)get(actor,"parts")) {
                hash=31*hash+get(part,"world").hashCode();
                if(group.equals("characters")) {
                    Object data=get(part,"data");
                    FloatBuffer palette=(FloatBuffer)get(data,"matrixPalette");
                    hash=31*hash+(palette==null?0:palette.duplicate().hashCode());
                }
            }
        }
        return hash;
    }
    private static void restore(State s) { if(s.base!=null) { set(s.frame,"x",s.base[0]); set(s.frame,"y",s.base[1]); set(s.frame,"z",s.base[2]); } }
    private static State required() { return Objects.requireNonNull(CURRENT.get(),"No pair"); }
    private static float number(Object o,String name) { return ((Number)get(o,name)).floatValue(); }
    private static Field field(Class<?> c,String name) {
        for(Class<?> t=c;t!=null;t=t.getSuperclass()) try { Field f=t.getDeclaredField(name); f.setAccessible(true); return f; } catch(NoSuchFieldException ignored) {}
        throw new IllegalStateException("Missing field "+c.getName()+"."+name);
    }
    private static Object get(Object o,String name) { try { return field(o.getClass(),name).get(o); } catch(IllegalAccessException e) { throw new IllegalStateException(e); } }
    private static Object getStatic(Class<?> c,String name) { try { return field(c,name).get(null); } catch(IllegalAccessException e) { throw new IllegalStateException(e); } }
    private static void set(Object o,String name,float v) { try { field(o.getClass(),name).setFloat(o,v); } catch(IllegalAccessException e) { throw new IllegalStateException(e); } }
    private static Object call(Object o,String name) { try { Method m=o.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(o); } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); } }
    private static final class State {
        final Object frame; final EyeFactory factory; final Sink sink; final PoseOverride pose; AutoCloseable poseScope; final long time=System.nanoTime();
        List<Eye> eyes; float[] base; Object scene,treeEnvironment; Throwable failure; long sceneVersion,fingerprint; int width,height,eye,preparations,copies; boolean finished;
        State(Object frame,EyeFactory factory,Sink sink,PoseOverride pose) { this.pose=Objects.requireNonNull(pose); this.frame=frame; this.factory=Objects.requireNonNull(factory); this.sink=Objects.requireNonNull(sink); }
    }
}
