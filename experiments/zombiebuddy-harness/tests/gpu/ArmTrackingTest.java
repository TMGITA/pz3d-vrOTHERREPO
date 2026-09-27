import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pzvr.harness.TrackedArms;
import pzvr.xr.*;
import pzvr.AttachmentPoses;

/** Numerical, legacy storage, and borrowed-snapshot tests; no game/mod entry points. */
@SuppressWarnings("try")
public final class ArmTrackingTest {
    static int checks;
    static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    static void near(Vector3f a,Vector3f b,String message) { check(a.distance(b)<.0002f,message+": "+a+" / "+b); }
    static void near(Matrix4f a,Matrix4f b,String message) {
        float[] x=a.get(new float[16]),y=b.get(new float[16]);
        for(int i=0;i<16;i++) check(Math.abs(x[i]-y[i])<.0002f,message+" element "+i);
    }
    interface Work { void run(); }
    static void rejected(Work work) { try { work.run(); } catch(IllegalArgumentException expected) { checks++; return; } throw new AssertionError("Expected invalid input rejection"); }
    static String[] names={"Bip01_Spine","Bip01_L_UpperArm","Bip01_L_Forearm","Bip01_L_Hand","Bip01_L_Finger1","Bip01_R_UpperArm","Bip01_R_Forearm","Bip01_R_Hand","Bip01_R_Finger1"};
    static int[] parents={-1,0,1,2,3,0,5,6,7};
    static Matrix4f[] model=new Matrix4f[names.length],offsets=new Matrix4f[names.length];
    static FloatBuffer buffer() { return ByteBuffer.allocateDirect(names.length*64).order(ByteOrder.nativeOrder()).asFloatBuffer(); }
    static org.lwjgl.util.vector.Matrix4f legacy(Matrix4f shader) {
        // Vanilla uses row-vector matrices. Load the transpose into the real legacy class.
        FloatBuffer raw=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer(); new Matrix4f(shader).transpose().get(raw);
        var result=new org.lwjgl.util.vector.Matrix4f(); result.load(raw); return result;
    }
    static FloatBuffer original() {
        FloatBuffer out=buffer();
        for(int i=0;i<names.length;i++) {
            // Actual legacy mul/store, matching AnimationPlayer.getSkinTransforms.
            org.lwjgl.util.vector.Matrix4f.mul(legacy(offsets[i]),legacy(model[i]),null).store(out);
        }
        return out.flip();
    }
    static Matrix4f decode(FloatBuffer palette,int i) {
        FloatBuffer b=palette.duplicate(); b.position(i*16);
        return new Matrix4f(b).transpose().mul(new Matrix4f(offsets[i]).invert());
    }
    static ArmRig rig() { return new ArmRig(names,parents,offsets); }
    static Vector3f pos(Matrix4f m) { return m.getTranslation(new Vector3f()); }
    static XrCamera.Pose pose(Matrix4f m) {
        Vector3f p=pos(m); Quaternionf q=m.getUnnormalizedRotation(new Quaternionf()).normalize();
        return new XrCamera.Pose(p.x,p.y,p.z,q.x,q.y,q.z,q.w);
    }
    public static final class Skin {
        public Map<String,Integer> boneIndices=new HashMap<>();
        public List<Integer> skeletonHierarchy=new ArrayList<>();
        public List<org.lwjgl.util.vector.Matrix4f> boneOffset=new ArrayList<>();
        Skin() { for(int i=0;i<names.length;i++) { boneIndices.put(names[i],i); skeletonHierarchy.add(parents[i]); boneOffset.add(legacy(offsets[i])); } }
    }
    public static final class Model { public boolean isStatic; public Object tag=new Skin(); }
    public static final class Attachment {
        private final String bone; Attachment(String bone) { this.bone=bone; }
        public String getBone() { return bone; }
    }
    public static final class Instance {
        public String parentBoneName,attachmentNameParent;
        public Map<String,Attachment> attachments=new HashMap<>();
        public Attachment getAttachmentById(String name) { return attachments.get(name); }
    }
    public static final class Data {
        public Model model=new Model(); public FloatBuffer matrixPalette=original();
        public Data parent; public Instance modelInstance=new Instance(); public final Matrix4f xfrm=new Matrix4f();
    }
    public static final class Part {
        public Data data=new Data(); public Matrix4f world=new Matrix4f().translation(3,4,5).rotateY(.4f).scale(1.5f);
        public float[] arms=new float[60],limbs=new float[60]; public int textureId=77;
    }
    public static final class Actor { public boolean body; public List<Part> parts=new ArrayList<>(); }
    public static final class Frame { public List<Actor> characters=new ArrayList<>(); }
    public static void main(String[] args) throws Exception {
        model[0]=new Matrix4f().translation(0,1,0);
        for(int side=0;side<2;side++) {
            int i=side==0?1:5; float sign=side==0?-1:1;
            model[i]=new Matrix4f().translation(sign*.2f,1.4f,0).rotateY(.2f);
            model[i+1]=new Matrix4f().translation(sign*.45f,1.2f,0).rotateX(.3f);
            model[i+2]=new Matrix4f().translation(sign*.6f,1,.1f).rotateZ(.4f);
            model[i+3]=new Matrix4f(model[i+2]).translate(.04f,0,0);
        }
        for(int i=0;i<names.length;i++) offsets[i]=new Matrix4f().translation(.2f*i,.1f,-.2f).rotateY(.13f*i).rotateX(.2f);
        FloatBuffer source=original(),saved=buffer(); saved.put(source.duplicate()).flip();
        ArmRig rig=rig(); FloatBuffer out=buffer();
        Vector3f[] poles={new Vector3f(-1,1,0),new Vector3f(1,1,0)};
        rejected(()->rig.pose(FloatBuffer.allocate(names.length*16),new Matrix4f[2],poles,out));
        rig.pose(source,new Matrix4f[2],poles,out);
        for(int i=0;i<names.length;i++) near(decode(out,i),model[i],"Legacy palette round trip bone "+i);
        Matrix4f target=new Matrix4f().translation(-.5f,1.3f,.25f);
        rig.pose(source,new Matrix4f[]{target,null},poles,out);
        near(decode(out,3).transformPosition(rig.palmLocal(0)),pos(target),"Tracked palm reaches grip target");
        check(pos(decode(out,3)).distance(pos(target))>.01f,"Grip no longer sits at wrist joint");
        near(decode(out,0),model[0],"Spine stays native");
        for(int i=5;i<names.length;i++) near(decode(out,i),model[i],"Untracked right arm stays native");
        check(Math.abs(pos(decode(out,1)).distance(pos(decode(out,2)))-pos(model[1]).distance(pos(model[2])))<.0002f,"Upper bone length preserved");
        check(Math.abs(pos(decode(out,2)).distance(pos(decode(out,3)))-pos(model[2]).distance(pos(model[3])))<.0002f,"Forearm length preserved");
        near(new Matrix4f(decode(out,3)).invert().mul(decode(out,4)),new Matrix4f(model[3]).invert().mul(model[4]),"Finger relative transform preserved");
        Matrix4f firstHand=decode(out,3);
        target.rotateY(.7f); rig.pose(source,new Matrix4f[]{target,null},poles,out);
        Matrix4f expected=new Matrix4f().translation(pos(target)).rotateY(.7f).mul(new Matrix4f(firstHand).setTranslation(0,0,0));
        expected.setTranslation(new Vector3f(pos(target)).sub(expected.transformDirection(rig.palmLocal(0))));
        near(decode(out,3),expected,"Grip rotates hand around palm after calibration");
        near(decode(out,3).transformPosition(rig.palmLocal(0)),pos(target),"Palm remains at grip while wrist rotates");
        String[] noFingers=names.clone(); noFingers[4]="OtherLeft"; noFingers[8]="OtherRight";
        ArmRig fallbackRig=new ArmRig(noFingers,parents,offsets);
        FloatBuffer fallbackOut=buffer(); fallbackRig.pose(source,new Matrix4f[]{target,null},poles,fallbackOut);
        check(fallbackRig.palmLocal(0).length()>.001f,"Missing finger bases use a nonzero anatomical fallback");
        near(decode(fallbackOut,3).transformPosition(fallbackRig.palmLocal(0)),pos(target),"Fallback palm stays at grip target");
        check(source.equals(saved)&&source.position()==0&&source.limit()==names.length*16,"Original bytes and buffer cursor remain untouched");
        float[] mask=rig.mask(true,false,new float[60]);
        check(mask[1]==1&&mask[2]==1&&mask[3]==1&&mask[4]==1&&mask[0]==0&&mask[5]==0,"Upper arm visibility limited to tracked side");
        Vector3f shoulder=new Vector3f(),elbow=new Vector3f(1,0,0),wrist=new Vector3f(2,0,0);
        for(Vector3f t:List.of(new Vector3f(10,0,0),new Vector3f(),new Vector3f(-1,0,0),new Vector3f(0,1,0))) {
            ArmIk.Solution s=ArmIk.solve(shoulder,elbow,wrist,t,new Vector3f(1,0,0));
            check(Math.abs(s.elbow().distance(shoulder)-1)<.0002f&&Math.abs(s.elbow().distance(s.wrist())-1)<.0002f,"Clamped/singular IK retains lengths");
        }
        check(ArmIk.solve(shoulder,elbow,wrist,new Vector3f(10,0,0),new Vector3f(0,1,0)).clamped(),"Unreachable target clamps");
        rejected(()->ArmIk.solve(shoulder,shoulder,wrist,wrist,wrist));
        rejected(()->ArmIk.solve(shoulder,elbow,wrist,new Vector3f(Float.NaN),wrist));
        int[] invalid=parents.clone(); invalid[0]=1; rejected(()->new ArmRig(names,invalid,offsets));
        rig.pose(source,new Matrix4f[2],poles,out);
        near(decode(out,3),model[3],"Tracking loss restores native hand");

        Frame frame=new Frame(); Actor body=new Actor(),other=new Actor(); body.body=true;
        Part a=new Part(),clothes=new Part(),weapon=new Part(),accessory=new Part(),otherHand=new Part(),npc=new Part();
        clothes.data.model.tag=a.data.model.tag; weapon.data.model.isStatic=true;
        weapon.data.parent=a.data; weapon.data.modelInstance.parentBoneName="Bip01_Prop1";
        weapon.data.modelInstance.attachmentNameParent="leftGrip";
        a.data.modelInstance.attachments.put("leftGrip",new Attachment("Bip01_Prop2"));
        weapon.data.xfrm.set(model[3]).translate(.03f,-.08f,.1f).rotateY(.45f).scale(.7f).transpose();
        accessory.data.model.isStatic=true; accessory.data.parent=weapon.data;
        accessory.data.xfrm.set(new Matrix4f(weapon.data.xfrm).transpose().translate(0,.12f,.05f).rotateZ(.2f)).transpose();
        otherHand.data.model.isStatic=true; otherHand.data.parent=a.data; otherHand.data.modelInstance.parentBoneName="Bip01_Prop1";
        otherHand.data.xfrm.set(model[7]).transpose();
        Matrix4f nativeWeapon=new Matrix4f(weapon.data.xfrm),nativeAccessory=new Matrix4f(accessory.data.xfrm);
        body.parts.addAll(List.of(accessory,otherHand,a,clothes,weapon)); other.parts.add(npc); frame.characters.addAll(List.of(body,other));
        FloatBuffer nativeA=a.data.matrixPalette,nativeClothes=clothes.data.matrixPalette,nativeNpc=npc.data.matrixPalette;
        float[] nativeMask=a.arms; Matrix4f world=new Matrix4f(a.world),scene=new Matrix4f().scaling(-1,1,1);
        // Physical poses are rigid; use unit-scale world for the bridge test.
        a.world.scale(1/1.5f); clothes.world.set(a.world); world.set(a.world);
        XrCamera.Pose grip=pose(new Matrix4f(scene).invert().mul(world).mul(new Matrix4f(target).scale(-1,1,1)));
        HandPoses hands=new HandPoses(grip,null); TrackedArms bridge=new TrackedArms();
        XrCamera.Pose head=new XrCamera.Pose(0,0,0,0,0,0,1);
        FloatBuffer reusable;
        try(AutoCloseable scope=bridge.apply(frame,scene,head,hands)) {
            check(a.data.matrixPalette!=nativeA&&clothes.data.matrixPalette!=nativeClothes,"Own body/clothing palettes substituted");
            check(a.data.matrixPalette!=clothes.data.matrixPalette,"Shared skin has distinct live output buffers");
            near(decode(a.data.matrixPalette,3).transformPosition(new Vector3f(.02f,0,0)),pos(target),"World/local/model palm conversion");
            check(a.arms[1]==1&&a.limbs[1]==1&&weapon.textureId==77,"Upper masks exposed and held item stays visible");
            Matrix4f delta=new Matrix4f(decode(a.data.matrixPalette,3)).mul(new Matrix4f(model[3]).invert());
            Matrix4f sceneDelta=new Matrix4f(a.world).mul(delta).mul(new Matrix4f(a.world).invert());
            near(new Matrix4f(weapon.world).mul(new Matrix4f(AttachmentPoses.resolve(weapon.data.xfrm,weapon)).transpose()),new Matrix4f(sceneDelta).mul(weapon.world).mul(new Matrix4f(nativeWeapon).transpose()),"Named attachment follows tracked hand with native grip/scale and separate world preserved");
            near(new Matrix4f(accessory.world).mul(new Matrix4f(AttachmentPoses.resolve(accessory.data.xfrm,accessory)).transpose()),new Matrix4f(sceneDelta).mul(accessory.world).mul(new Matrix4f(nativeAccessory).transpose()),"Nested attachment inherits hand motion exactly once, regardless of part order");
            check(AttachmentPoses.resolve(otherHand.data.xfrm,otherHand)==otherHand.data.xfrm,"Untracked opposite-hand item remains native and visible");
            near(weapon.data.xfrm,nativeWeapon,"Original item matrix remains untouched during override");
            near(accessory.data.xfrm,nativeAccessory,"Original accessory matrix remains untouched");
            check(npc.data.matrixPalette==nativeNpc&&npc.arms[1]==0,"Other character untouched");
            reusable=a.data.matrixPalette;
        }
        check(a.data.matrixPalette==nativeA&&clothes.data.matrixPalette==nativeClothes&&a.arms==nativeMask&&weapon.textureId==77,"All borrowed references restored");
        check(AttachmentPoses.resolve(weapon.data.xfrm,weapon)==weapon.data.xfrm,"Attachment upload scope removed after stereo draw");
        try(AutoCloseable scope=bridge.apply(frame,scene,head,hands)) { check(a.data.matrixPalette==reusable,"Output buffers reused across frames"); }
        try(AutoCloseable scope=bridge.apply(frame,scene,head,HandPoses.NONE)) { check(a.data.matrixPalette==nativeA&&weapon.textureId==77,"Tracking loss skips overrides"); }
        Matrix4f rightTarget=new Matrix4f().translation(.5f,1.3f,.25f);
        XrCamera.Pose rightGrip=pose(new Matrix4f(scene).invert().mul(world).mul(new Matrix4f(rightTarget).scale(-1,1,1)));
        try(AutoCloseable scope=bridge.apply(frame,scene,head,new HandPoses(grip,rightGrip))) {
            near(decode(a.data.matrixPalette,7).transformPosition(new Vector3f(.02f,0,0)),pos(rightTarget),"Right palm is centered independently");
            check(AttachmentPoses.resolve(otherHand.data.xfrm,otherHand)!=otherHand.data.xfrm&&AttachmentPoses.resolve(weapon.data.xfrm,weapon)!=weapon.data.xfrm,"Both held items follow their own hand");
        }
        try(AutoCloseable scope=bridge.apply(frame,scene,head,new HandPoses(null,rightGrip))) {
            near(decode(a.data.matrixPalette,3),model[3],"Left tracking loss restores native hand while right remains tracked");
            check(AttachmentPoses.resolve(weapon.data.xfrm,weapon)==weapon.data.xfrm&&AttachmentPoses.resolve(otherHand.data.xfrm,otherHand)!=otherHand.data.xfrm,"Tracking loss restores only that hand's attachments");
        }
        Part broken=new Part(); broken.data.model.tag=new Object(); body.parts.add(broken);
        try(AutoCloseable scope=bridge.apply(frame,scene,head,hands)) {
            check(a.data.matrixPalette==nativeA&&a.arms==nativeMask&&weapon.textureId==77,"Partial failure rolls back earlier parts");
        }
        body.parts.remove(broken);
        try(AutoCloseable scope=bridge.apply(frame,scene,head,hands)) { check(a.data.matrixPalette==nativeA,"Bad rig disables IK for session without repeated failure"); }
        bridge.reset();
        try(AutoCloseable scope=bridge.apply(frame,scene,head,hands)) { check(a.data.matrixPalette!=nativeA,"New session can retry after reset"); }
        accessory.data.parent=accessory.data;
        try(AutoCloseable scope=bridge.apply(frame,scene,head,hands)) {
            check(a.data.matrixPalette==nativeA&&AttachmentPoses.resolve(weapon.data.xfrm,weapon)==weapon.data.xfrm,"Invalid attachment graph rolls back arm and item overrides");
        }
        accessory.data.parent=weapon.data;
        check(nativeA.equals(saved),"Original palette remains byte-for-byte unchanged after all paths");
        System.out.println("Arm tracking checks passed: "+checks);
    }
}
