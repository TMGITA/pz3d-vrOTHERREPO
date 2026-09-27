package com.pavelvoronin.pz3d;

import java.util.*;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import com.pavelvoronin.pz3d.WorldMirror.Scene;

/** Original test fixture, not copied game/mod code. Only the boundary's shapes are modelled. */
public final class Renderer {
    public static int width=800,height=600,nw=42;
    public static final List<String> events=new ArrayList<>();
    public static int prepared,shadows,visibility,tail,effects;
    public static final class Frame {
        public boolean third,skip,mutate;
        public Object rider;
        public Scene scene=new Scene();
        public long generation=1;
        public final Lease lease=new Lease();
        public final SceneUpload uploads=new SceneUpload();
        public float x=10,y=20,z=1.7f,dx=1,dy=0,dz=0,fov=1.2f;
        public final List<Actor> characters=new ArrayList<>(List.of(new Actor()));
        public final List<Actor> vehicles=new ArrayList<>();
        public final Corpse corpse=new Corpse();
        public Throwable caught;
        public final List<Float> eyeY=new ArrayList<>();
        public final List<Long> fade=new ArrayList<>();
        public final List<Object> environments=new ArrayList<>();
        public final List<Boolean> freshCalls=new ArrayList<>();
        private void ad() { prepared++; events.add("prepare"); x=11; }
        public void draw(boolean fresh) {
            if(skip) return;
            freshCalls.add(fresh);
            try {
                scene=uploads.advance(scene);
                ad();
                ActorShadows.render();
                WorldBuffers.beginView();
                Matrix4f matrix=ViewMath.projection((float)width/height,x,y,z,dx,dy,dz,fov);
                if(matrix==null) throw new AssertionError("No matrix");
                eyeY.add(y); fade.add(StreamFade.clock());
                environments.add(TreeRenderer.environment());
                events.add("draw"+eyeY.size());
                if(mutate) characters.getFirst().parts.getFirst().world.translate(1,0,0);
                corpse.rendered=true;
                a(matrix,ShotEffects.snapshot(System.nanoTime()),scene);
                GL30.glBlitFramebuffer(0,0,width,height,0,0,width,height,16384,9728);
                tail++;
            } catch(Throwable ex) { caught=ex; Main.fail("OpenGL",ex); }
        }
    }
    static void a(Matrix4f m,List<?> traces,Scene scene) { effects++; }
    public static final class Lease {
        public int refs=1,retains,releases;
        public void retain() { refs++; retains++; }
        public void release() { refs--; releases++; if(refs<1) throw new AssertionError("Premature retirement"); }
    }
    public static final class Corpse { public boolean rendered; }
    public static final class Actor { public final List<Part> parts=new ArrayList<>(List.of(new Part())); }
    public static final class Part { public Matrix4f world=new Matrix4f(); public Pose data=new Pose(); }
    public static final class Pose { public FloatBuffer matrixPalette=FloatBuffer.wrap(new float[]{1,2,3,4}); }
    public static void reset() { events.clear(); prepared=shadows=visibility=tail=effects=0; RetainedRender.current=1; GL30.blits=0; }
    public static void invalidate() { RetainedRender.current++; }
    public static void weatherChange() { TreeRenderer.change(); }
}
final class SceneUpload { Scene advance(Scene scene) { Renderer.events.add("adopt"); return scene; } }
final class ActorShadows { static void render() { Renderer.shadows++; Renderer.events.add("shadows"); } }
final class WorldBuffers { static void beginView() { Renderer.visibility++; } }
final class ViewMath {
    static Matrix4f projection(float a,float x,float y,float z,float dx,float dy,float dz,float fov) {
        return new Matrix4f().perspective(fov,a,.02f,160).lookAt(x,-y,z,x+dx,-y-dy,z+dz,0,0,1).scale(1,-1,1);
    }
}
final class ShotEffects { static List<?> snapshot(long now) { return List.of(now); } }
final class RetainedRender { static long current=1; static long generation() { return current; } }
