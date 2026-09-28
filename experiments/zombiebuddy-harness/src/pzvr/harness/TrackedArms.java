package pzvr.harness;

import java.lang.reflect.Field;
import java.nio.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pzvr.xr.*;
import pzvr.AttachmentPoses;

/** Scoped render-snapshot substitutions: never writes original palette contents or AnimationPlayer. */
public final class TrackedArms {
    private static volatile float maxReach=1.5f;
    public static void setReachPercent(int percent) { maxReach=Math.max(100,Math.min(175,percent))/100f; }
    private final IdentityHashMap<Object,Rig> rigs=new IdentityHashMap<>();
    private boolean disabled,announced;
    private record Rig(ArmRig solver) {}
    private final List<FloatBuffer> buffers=new ArrayList<>();
    public void reset() { rigs.clear(); buffers.clear(); disabled=false; announced=false; }
    public void recenter() { for(Rig rig:rigs.values()) rig.solver.recenter(); }
    public AutoCloseable apply(Object frame,Matrix4f sceneFromLocal,XrCamera.Pose head,HandPoses hands) {
        return apply(frame,sceneFromLocal,head,hands,new Vector3f());
    }
    public AutoCloseable apply(Object frame,Matrix4f sceneFromLocal,XrCamera.Pose head,HandPoses hands,Vector3f shoulderShift) {
        Scope scope=new Scope();
        float reach=maxReach; // One settings snapshot for every body/clothing part in this pair.
        if(disabled) return scope;
        if(hands.left()==null && hands.right()==null) return scope;
        try {
            if(rigs.size()>128) rigs.clear();
            boolean found=false; int bufferIndex=0;
            Map<Object,Matrix4f> attachments=new IdentityHashMap<>();
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Object actor:(Iterable<?>)get(frame,"characters")) {
                if(!(boolean)get(actor,"body")) continue;
                Map<Object,ArmRig.PoseChanges> changes=new IdentityHashMap<>();
                Map<Object,Matrix4f> worlds=new IdentityHashMap<>();
                for(Object part:(Iterable<?>)get(actor,"parts")) {
                    Object data=get(part,"data"),model=get(data,"model");
                    worlds.put(data,new Matrix4f((Matrix4f)get(part,"world")));
                    if((boolean)get(model,"isStatic")) continue;
                    FloatBuffer original=(FloatBuffer)get(data,"matrixPalette");
                    if(original==null) continue;
                    if(!seen.add(data)) throw new IllegalStateException("Repeated body pose in one frame");
                    Object skin=get(model,"tag");
                    if(skin==null) skin=get(get(model,"mesh"),"skinningData");
                    Rig rig=rigs.get(skin);
                    if(rig==null) { rig=create(skin,original.remaining()/16); rigs.put(skin,rig); }
                    // Distinct clothing parts may share SkinningData; each needs its own output buffer.
                    if(bufferIndex==buffers.size()) buffers.add(null);
                    FloatBuffer output=buffers.get(bufferIndex);
                    if(output==null || output.capacity()<original.remaining()) {
                        output=ByteBuffer.allocateDirect(original.remaining()*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                        buffers.set(bufferIndex,output);
                    }
                    bufferIndex++;
                    Matrix4f inverseWorld=new Matrix4f((Matrix4f)get(part,"world")).invert();
                    Vector3f modelShift=inverseWorld.transformDirection(new Vector3f(shoulderShift));
                    Matrix4f modelFromLocal=inverseWorld.mul(sceneFromLocal);
                    Matrix4f[] targets=new Matrix4f[2]; Vector3f[] poles=new Vector3f[2];
                    for(int side=0;side<2;side++) {
                        XrCamera.Pose pose=hands.get(side);
                        // Reflect the grip's X axis too, yielding a proper rotation in model coordinates.
                        if(pose!=null) targets[side]=new Matrix4f(modelFromLocal).mul(pose.matrix()).scale(-1,1,1);
                        poles[side]=new Matrix4f(modelFromLocal).mul(head.matrix()).transformPosition(new Vector3f(side==0?-.55f:.55f,-.5f,-.1f));
                    }
                    changes.put(data,rig.solver.pose(original,targets,poles,output,modelShift,reach));
                    scope.change(data,"matrixPalette",output);
                    scope.change(part,"arms",rig.solver.mask(hands.left()!=null,hands.right()!=null,(float[])get(part,"arms")));
                    scope.change(part,"limbs",rig.solver.mask(hands.left()!=null,hands.right()!=null,(float[])get(part,"limbs")));
                    found=true;
                }
                Map<Object,Matrix4f> deltas=new IdentityHashMap<>();
                for(Object part:(Iterable<?>)get(actor,"parts")) {
                    Object data=get(part,"data");
                    if(!(boolean)get(get(data,"model"),"isStatic")) continue;
                    Matrix4f delta=attachmentDelta(data,changes,worlds,deltas,Collections.newSetFromMap(new IdentityHashMap<>()));
                    if(delta!=null) {
                        Matrix4f original=(Matrix4f)get(data,"xfrm");
                        // Keep the captured native bone/attachment/mesh alignment, including item scale.
                        Matrix4f world=worlds.get(data);
                        attachments.put(part,new Matrix4f(world).invert().mul(delta).mul(world).mul(new Matrix4f(original).transpose()).transpose());
                    }
                }
            }
            scope.attachments=AttachmentPoses.open(attachments);
            pzvr.contact.ContactCapture.capture(frame,attachments,sceneFromLocal,head);
            if(found&&!announced) { OpenXrSession.log("Arm IK active: palm-centered grips, upper arms visible; held attachment overrides="+attachments.size()); announced=true; }
            return scope;
        } catch(Throwable failure) {
            try { scope.close(); } catch(RuntimeException cleanup) { failure.addSuppressed(cleanup); throw cleanup; }
            if(failure instanceof Error fatal) throw fatal;
            disabled=true;
            OpenXrSession.log("Arm IK disabled; native pose restored: "+failure);
            return ()->{};
        }
    }
    private static Matrix4f attachmentDelta(Object data,Map<Object,ArmRig.PoseChanges> poses,Map<Object,Matrix4f> worlds,Map<Object,Matrix4f> memo,Set<Object> visiting) throws ReflectiveOperationException {
        if(memo.containsKey(data)) return memo.get(data);
        if(!visiting.add(data)) throw new IllegalArgumentException("Cyclic attachment snapshot");
        Object parent=get(data,"parent"); Matrix4f delta=null;
        if(parent!=null) {
            ArmRig.PoseChanges changes=poses.get(parent);
            if(changes!=null) {
                Object instance=get(data,"modelInstance"),parentInstance=get(parent,"modelInstance");
                String bone=(String)get(instance,"parentBoneName");
                Object attachment=parentInstance.getClass().getMethod("getAttachmentById",String.class).invoke(parentInstance,(String)get(instance,"attachmentNameParent"));
                if(attachment==null && bone!=null) attachment=parentInstance.getClass().getMethod("getAttachmentById",String.class).invoke(parentInstance,bone);
                if(attachment!=null) bone=(String)attachment.getClass().getMethod("getBone").invoke(attachment);
                Matrix4f boneDelta=changes.delta(bone);
                if(boneDelta!=null) {
                    // Skin shader ignores data.xfrm. Apply the rendered bone's delta in scene space,
                    // then each static part converts it through its own prepared world transform.
                    Matrix4f parentWorld=worlds.get(parent);
                    delta=new Matrix4f(parentWorld).mul(boneDelta).mul(new Matrix4f(parentWorld).invert());
                }
            } else if((boolean)get(get(parent,"model"),"isStatic")) {
                // Captured nested attachments already contain their parent's full native transform.
                delta=attachmentDelta(parent,poses,worlds,memo,visiting);
            }
        }
        visiting.remove(data); memo.put(data,delta); return delta;
    }
    @SuppressWarnings("unchecked")
    private static Rig create(Object skin,int count) throws ReflectiveOperationException {
        if(skin==null) throw new IllegalArgumentException("Missing skinning data");
        Map<String,Integer> indices=(Map<String,Integer>)get(skin,"boneIndices");
        List<Integer> hierarchy=(List<Integer>)get(skin,"skeletonHierarchy");
        List<?> offsets=(List<?>)get(skin,"boneOffset");
        if(count<1||count>60||hierarchy.size()<count||offsets.size()<count) throw new IllegalArgumentException("Unsupported skinning data");
        String[] names=new String[count]; int[] parents=new int[count]; Matrix4f[] matrices=new Matrix4f[count];
        for(var entry:indices.entrySet()) if(entry.getValue()>=0&&entry.getValue()<count) names[entry.getValue()]=entry.getKey();
        for(int i=0;i<count;i++) {
            parents[i]=hierarchy.get(i);
            float[] values=new float[16];
            for(int c=0;c<4;c++) for(int r=0;r<4;r++) values[c*4+r]=((Number)get(offsets.get(i),"m"+c+r)).floatValue();
            matrices[i]=new Matrix4f().set(values).transpose();
        }
        return new Rig(new ArmRig(names,parents,matrices));
    }
    private record Change(Object object,Field field,Object original) {}
    private static final class Scope implements AutoCloseable {
        private final List<Change> changes=new ArrayList<>();
        private AutoCloseable attachments;
        void change(Object object,String name,Object value) throws ReflectiveOperationException {
            Field field=field(object.getClass(),name);
            changes.add(new Change(object,field,field.get(object))); field.set(object,value);
        }
        public void close() {
            RuntimeException failure=null;
            if(attachments!=null) {
                try { attachments.close(); } catch(Exception e) { failure=new IllegalStateException("Restore attachment scope",e); }
                attachments=null;
            }
            for(int i=changes.size()-1;i>=0;i--) {
                Change c=changes.get(i);
                try { c.field.set(c.object,c.original); }
                catch(IllegalAccessException e) { if(failure==null) failure=new IllegalStateException("Restore arm snapshot",e); else failure.addSuppressed(e); }
            }
            changes.clear(); if(failure!=null) throw failure;
        }
    }
    private static Field field(Class<?> type,String name) throws NoSuchFieldException {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f; } catch(NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(type.getName()+"."+name);
    }
    private static Object get(Object object,String name) throws ReflectiveOperationException { return field(object.getClass(),name).get(object); }
    public static HandPoses synthetic(XrCamera.Pose head,double seconds) {
        XrCamera.Pose[] poses=new XrCamera.Pose[2];
        for(int side=0;side<2;side++) {
            float wave=(float)Math.sin(seconds*1.5+side);
            Matrix4f m=head.matrix().translate(side==0?-.3f:.3f,-.25f+.15f*wave,-.4f).rotateZ(wave*.5f);
            Vector3f p=m.getTranslation(new Vector3f()); org.joml.Quaternionf q=m.getUnnormalizedRotation(new org.joml.Quaternionf());
            poses[side]=new XrCamera.Pose(p.x,p.y,p.z,q.x,q.y,q.z,q.w);
        }
        return new HandPoses(poses[0],poses[1]);
    }
}
