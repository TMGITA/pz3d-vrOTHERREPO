/** Analytic invariants independent of OpenXR, native libraries, and rendering. */
public final class StereoMathTest {
    static int checks;
    static void near(float actual, float expected) {
        checks++;
        if (!Float.isFinite(actual) || Math.abs(actual-expected)>0.00002f)
            throw new AssertionError(actual+" != "+expected);
    }
    static float[] ndc(float[] m,float x,float y,float z) {
        float[] p=StereoMath.transform(m,x,y,z,1);
        return new float[]{p[0]/p[3],p[1]/p[3],p[2]/p[3]};
    }
    public static void main(String[] args) {
        float l=-.65f,r=.9f,u=.8f,d=-.5f,n=.05f,f=30;
        float[] projection=StereoMath.projection(l,r,u,d,n,f);
        near(ndc(projection,(float)Math.tan(l)*2,0,-2)[0],-1);
        near(ndc(projection,(float)Math.tan(r)*2,0,-2)[0],1);
        near(ndc(projection,0,(float)Math.tan(u)*2,-2)[1],1);
        near(ndc(projection,0,(float)Math.tan(d)*2,-2)[1],-1);
        near(ndc(projection,0,0,-n)[2],-1); near(ndc(projection,0,0,-f)[2],1);
        float[] pose=StereoMath.pose(.3f,-.2f,1,.2f,.3f,.4f,.8f);
        float[] product=StereoMath.multiply(StereoMath.inverseRigid(pose),pose);
        for(int i=0;i<16;i++) near(product[i],i%5==0?1:0);
        // +90 degree yaw transforms forward (-Z) to left (-X); roll rotates +X to +Y.
        float q=(float)Math.sqrt(.5);
        float[] p=StereoMath.transform(StereoMath.pose(0,0,0,0,q,0,q),0,0,-1,0);
        near(p[0],-1); near(p[2],0);
        p=StereoMath.transform(StereoMath.pose(0,0,0,0,0,q,q),1,0,0,0);
        near(p[0],0); near(p[1],1);
        p=StereoMath.transform(StereoMath.pose(0,0,0,q,0,0,q),0,0,-1,0);
        near(p[1],1); near(p[2],0);
        // Parallel-eye near disparity must be twice disparity at twice the distance.
        float[] sym=StereoMath.projection(-.7853982f,.7853982f,.7853982f,-.7853982f,.05f,30);
        float[] left=StereoMath.multiply(sym,StereoMath.inverseRigid(StereoMath.pose(-.032f,0,0,0,0,0,1)));
        float[] right=StereoMath.multiply(sym,StereoMath.inverseRigid(StereoMath.pose(.032f,0,0,0,0,0,1)));
        float disparity=ndc(left,0,0,-1)[0]-ndc(right,0,0,-1)[0];
        near(disparity,.064f);
        near(disparity,2*(ndc(left,0,0,-2)[0]-ndc(right,0,0,-2)[0]));
        System.out.println("Stereo math: "+checks+" analytic checks passed.");
    }
}
