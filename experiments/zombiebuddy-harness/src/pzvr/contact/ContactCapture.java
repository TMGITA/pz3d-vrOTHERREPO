package pzvr.contact;

import java.lang.reflect.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pzvr.melee.MeleeInput;
import pzvr.xr.XrCamera;

/** Uses the exact static attachment transform already prepared for both eyes. */
public final class ContactCapture {
    private static boolean logged,failed;
    public static void capture(Object frame,Map<Object,Matrix4f> attachments,Matrix4f sceneFromLocal,XrCamera.Pose head) {
        long now=System.nanoTime();var permit=MeleeInput.permit;
        if(MeleeInput.mode<3 || permit==null || !MeleeInput.fresh(MeleeInput.state,now) || now-permit.time()>MeleeInput.FRESH){ContactInput.breakPath();return;}
        try {
            Object found=null;Matrix4f transform=null;
            for(Object actor:(Iterable<?>)get(frame,"characters")) {
                if(!(boolean)get(actor,"body") || get(actor,"character")!=permit.player())continue;
                for(Object part:(Iterable<?>)get(actor,"parts")) {
                    if(!attachments.containsKey(part))continue;
                    Object data=get(part,"data"),instance=get(data,"modelInstance"),script=get(instance,"modelScript");
                    if(script==null || !"BaseballBat".equals(script.getClass().getMethod("getName").invoke(script)))continue;
                    if(found!=null)throw new IllegalStateException("Ambiguous bat attachment");
                    found=part;transform=new Matrix4f((Matrix4f)get(part,"world")).mul(new Matrix4f(attachments.get(part)).transpose());
                }
            }
            if(found==null){ContactInput.breakPath();return;}
            Object scene=get(frame,"scene");
            Vector3f origin=new Vector3f(number(scene,"ox"),number(scene,"oy"),number(scene,"oz"));
            Object mesh=get(get(get(found,"data"),"model"),"mesh");
            Vector3f min=new Vector3f((Vector3f)get(mesh,"minXyz")),max=new Vector3f((Vector3f)get(mesh,"maxXyz"));
            Sweep.Capsule capsule=bounds(min,max,transform,origin);
            Vector3f h=sceneFromLocal.transformPosition(new Vector3f(head.x(),head.y(),head.z())).add(origin);
            ContactInput.publish(new ContactInput.Pose(now,permit,capsule,new Sweep.V(h.x,h.y,h.z)));
            if(!logged){System.out.println("[PZ3D VR Contact] Rendered bat capsule acquired; length="+capsule.a().sub(capsule.b()).length()+" radius="+capsule.radius());logged=true;}
        } catch(ReflectiveOperationException|RuntimeException ex){ContactInput.breakPath();if(!failed){failed=true;System.out.println("[PZ3D VR Contact] Geometry unavailable: "+ex);}}
    }
    public static Sweep.Capsule bounds(Vector3f min,Vector3f max,Matrix4f world,Vector3f origin) {
        Vector3f size=new Vector3f(max).sub(min),center=new Vector3f(min).add(max).mul(.5f);
        int axis=size.x>size.y?(size.x>size.z?0:2):(size.y>size.z?1:2);
        float[] sizes={size.x,size.y,size.z};if(!Float.isFinite(min.x+min.y+min.z+max.x+max.y+max.z+origin.x+origin.y+origin.z)||!world.isFinite()||sizes[0]<0||sizes[1]<0||sizes[2]<0)throw new IllegalArgumentException("Bat mesh bounds");
        Vector3f a=new Vector3f(center),b=new Vector3f(center);
        if(axis==0){a.x=min.x;b.x=max.x;}else if(axis==1){a.y=min.y;b.y=max.y;}else{a.z=min.z;b.z=max.z;}
        world.transformPosition(a).add(origin);world.transformPosition(b).add(origin);
        float radius=0;
        for(int side=0;side<3;side++)if(side!=axis){Vector3f v=new Vector3f(side==0?sizes[side]*.5f:0,side==1?sizes[side]*.5f:0,side==2?sizes[side]*.5f:0);radius+=world.transformDirection(v).lengthSquared();}
        radius=(float)Math.sqrt(radius);float length=a.distance(b);
        if(length<.3f||length>1.6f||radius<.005f||radius>.15f)throw new IllegalArgumentException("Implausible rendered bat size");
        Vector3f inset=new Vector3f(b).sub(a).normalize().mul(Math.min(radius,length*.2f));a.add(inset);b.sub(inset);
        return new Sweep.Capsule(new Sweep.V(a.x,a.y,a.z),new Sweep.V(b.x,b.y,b.z),radius);
    }
    private static float number(Object o,String method)throws ReflectiveOperationException{return ((Number)o.getClass().getMethod(method).invoke(o)).floatValue();}
    private static Object get(Object o,String name)throws ReflectiveOperationException{
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
}
