package pzvr.xr;

import java.util.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pzvr.PairHooks;

/** OpenXR RH Y-up/-Z-forward to PZ3D's reflected Y, Z-up scene convention. */
public final class XrCamera {
    public record Pose(float x,float y,float z,float qx,float qy,float qz,float qw) {
        public Matrix4f matrix() {
            if(!Float.isFinite(x+y+z+qx+qy+qz+qw) || qx*qx+qy*qy+qz*qz+qw*qw<.0001f)
                throw new IllegalArgumentException("Invalid pose");
            return new Matrix4f().translationRotate(x,y,z,new Quaternionf(qx,qy,qz,qw).normalize());
        }
    }
    public record View(Pose pose,float left,float right,float up,float down) {}
    private Matrix4f anchorInverse,sceneFromLocal;
    private float anchorHeight;
    private final Vector3f shoulderShift=new Vector3f();
    public Matrix4f sceneFromLocal() { return new Matrix4f(Objects.requireNonNull(sceneFromLocal)); }
    public Vector3f shoulderShift() { return new Vector3f(shoulderShift); }
    public void recenter() { anchorInverse=null; }
    public PairHooks.EyeFactory eyes(Pose head,List<View> views) {
        if(views.size()!=2) throw new IllegalArgumentException("Two eyes required");
        if(anchorInverse==null) { anchorInverse=head.matrix().invert(); anchorHeight=head.y(); }
        Matrix4f anchor=new Matrix4f(anchorInverse);
        float heightDelta=head.y()-anchorHeight;
        return base -> {
            Matrix4f baseCamera=new Matrix4f().lookAt(base.x(),-base.y(),base.z(),
                base.x()+base.dx(),-base.y()-base.dy(),base.z()+base.dz(),0,0,1).invert();
            sceneFromLocal=new Matrix4f().scaling(1,-1,1).mul(baseCamera).mul(anchor);
            // Physical height only: native camera/crouch motion already moves the body.
            // Use the same basis as the tracked hands, including recenter rotation.
            sceneFromLocal.transformDirection(shoulderShift.set(0,heightDelta,0));
            List<PairHooks.Eye> result=new ArrayList<>();
            for(View eye:views) {
                // Prototype scale: one scene unit per metre. Physical calibration remains pending.
                Matrix4f camera=new Matrix4f(baseCamera).mul(anchor).mul(eye.pose().matrix());
                Vector3f position=camera.getTranslation(new Vector3f());
                float n=.02f;
                if(!(eye.left()<eye.right() && eye.down()<eye.up())) throw new IllegalArgumentException("Invalid eye FOV");
                Matrix4f vp=new Matrix4f().frustum((float)Math.tan(eye.left())*n,(float)Math.tan(eye.right())*n,
                    (float)Math.tan(eye.down())*n,(float)Math.tan(eye.up())*n,n,160)
                    .mul(camera.invert()).scale(1,-1,1);
                result.add(new PairHooks.Eye(position.x,-position.y,position.z,vp));
            }
            return List.copyOf(result);
        };
    }
}
