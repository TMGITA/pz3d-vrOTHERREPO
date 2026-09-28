package pzvr.contact;

/** Pure geometry in world metres. Capsules are approximations, not mesh collision. */
public final class Sweep {
    public record V(float x,float y,float z) {
        public V add(V b){return new V(x+b.x,y+b.y,z+b.z);} public V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
        public V mul(float f){return new V(x*f,y*f,z*f);} public float dot(V b){return x*b.x+y*b.y+z*b.z;}
        public float length(){return (float)Math.sqrt(dot(this));} public boolean finite(){return Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z);}
    }
    public record Capsule(V a,V b,float radius) {
        public boolean valid(){return a.finite()&&b.finite()&&Float.isFinite(radius)&&radius>0&&radius<.2f;}
    }
    public static float distanceSquared(V a,V b,V c,V d) {
        V u=b.sub(a),v=d.sub(c),w=a.sub(c);
        float aa=u.dot(u),bb=u.dot(v),cc=v.dot(v),dd=u.dot(w),ee=v.dot(w),s,t;
        if(aa<1e-10f&&cc<1e-10f)return w.dot(w);
        if(aa<1e-10f){s=0;t=clamp(ee/cc);} else if(cc<1e-10f){t=0;s=clamp(-dd/aa);} else {
            float den=aa*cc-bb*bb;s=den>1e-10f?clamp((bb*ee-cc*dd)/den):0;t=(bb*s+ee)/cc;
            if(t<0){t=0;s=clamp(-dd/aa);}else if(t>1){t=1;s=clamp((bb-dd)/aa);}
        }
        V delta=w.add(u.mul(s)).sub(v.mul(t));return delta.dot(delta);
    }
    private static float clamp(float f){return Math.max(0,Math.min(1,f));}
    public static float movement(Capsule a,Capsule b){return Math.max(a.a.sub(b.a).length(),a.b.sub(b.b).length());}
    /** Bounded subdivision with conservative spatial tolerance. Negative means no contact. */
    public static float firstContact(Capsule from,Capsule to,V targetA,V targetB,float radius) {
        float distance=movement(from,to);
        if(!from.valid()||!to.valid()||!targetA.finite()||!targetB.finite()||distance>1.5f||!Float.isFinite(radius)||radius<=0)return -1;
        int steps=Math.max(1,(int)Math.ceil(distance/.02f));
        float r=Math.max(from.radius,to.radius)+radius+.01f;
        for(int i=0;i<=steps;i++){
            float t=(float)i/steps;
            V a=from.a.add(to.a.sub(from.a).mul(t)),b=from.b.add(to.b.sub(from.b).mul(t));
            if(distanceSquared(a,b,targetA,targetB)<=r*r)return t;
        }
        return -1;
    }
}
