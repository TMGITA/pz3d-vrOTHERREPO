import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import pzvr.PairHooks;
import pzvr.xr.XrCamera;
public final class XrCameraTest {
    static int checks;
    static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    static XrCamera.Pose pose(float x,float y,float z) { return new XrCamera.Pose(x,y,z,0,0,0,1); }
    static XrCamera.View view(XrCamera.Pose p) { return new XrCamera.View(p,-.6f,.6f,.6f,-.6f); }
    static boolean near(float a,float b) { return Math.abs(a-b)<.0001f; }
    public static void main(String[] args) {
        XrCamera camera=new XrCamera();
        var base=new PairHooks.Base(10,20,1.7f,1,0,0,1.2f,800,800);
        var views=List.of(view(pose(-.032f,0,0)),view(pose(.032f,0,0)));
        var eyes=camera.eyes(pose(0,0,0),views).create(base);
        var synthetic=PairHooks.synthetic(.064f).create(base);
        for(int i=0;i<2;i++) {
            check(near(eyes.get(i).x(),synthetic.get(i).x()) && near(eyes.get(i).y(),synthetic.get(i).y()),"Neutral XR eye axes agree with tested synthetic eyes");
            float[] actual=eyes.get(i).viewProjection().get(new float[16]),expected=synthetic.get(i).viewProjection().get(new float[16]);
            for(int j=0;j<16;j++) check(near(actual[j],expected[j]),"Neutral projection matrix element "+j);
        }
        var translated=camera.eyes(pose(.2f,.1f,-.3f),List.of(view(pose(.168f,.1f,-.3f)),view(pose(.232f,.1f,-.3f)))).create(base);
        check(near(translated.get(0).x(),10.3f) && near(translated.get(0).y(),20.168f) && near(translated.get(0).z(),1.8f),"Tracked translation uses right/up/forward scene axes");
        float q=(float)Math.sqrt(.5);
        var rotated=new XrCamera.Pose(0,0,0,0,q,0,q);
        var turned=camera.eyes(rotated,List.of(view(rotated),view(rotated))).create(base);
        Vector4f centre=turned.get(0).viewProjection().transform(new Vector4f(10,19,1.7f,1));
        check(Math.abs(centre.x/centre.w)<.0001f && Math.abs(centre.y/centre.w)<.0001f,"Head yaw rotates view without changing base body direction");
        var roll=new XrCamera.Pose(0,0,0,0,0,q,q);
        var rolled=camera.eyes(roll,List.of(view(roll),view(roll))).create(base);
        Vector4f up=rolled.get(0).viewProjection().transform(new Vector4f(11,20,2.7f,1));
        check(Math.abs(up.x/up.w)>.5f && Math.abs(up.y/up.w)<.0001f,"Head roll affects projection");
        camera.recenter();
        var recentered=camera.eyes(rotated,List.of(view(rotated),view(rotated))).create(base);
        centre=recentered.get(0).viewProjection().transform(new Vector4f(11,20,1.7f,1));
        check(Math.abs(centre.x/centre.w)<.0001f && Math.abs(centre.y/centre.w)<.0001f,"Recenter aligns current head pose with body camera");
        camera.recenter();
        var asym=new XrCamera.View(pose(0,0,0),-.4f,.8f,.7f,-.5f);
        var asymmetric=camera.eyes(pose(0,0,0),List.of(asym,asym)).create(base);
        centre=asymmetric.get(0).viewProjection().transform(new Vector4f(11,20,1.7f,1));
        check(Math.abs(centre.x/centre.w)>.1f && Math.abs(centre.y/centre.w)>.1f,"Runtime asymmetric FOV is preserved");
        System.out.println("XR camera checks passed: "+checks);
    }
}
