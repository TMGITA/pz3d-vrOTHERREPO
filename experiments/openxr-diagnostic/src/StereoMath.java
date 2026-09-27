/** Column-major OpenGL matrices: right-handed, metres, -Z forward, NDC depth [-1,1]. */
final class StereoMath {
    private StereoMath() {}
    static float[] identity() { return new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}; }
    static float[] projection(float left, float right, float up, float down, float near, float far) {
        if (!(near > 0 && far > near)) throw new IllegalArgumentException("Invalid clip distances");
        float l = (float)Math.tan(left), r = (float)Math.tan(right);
        float u = (float)Math.tan(up), d = (float)Math.tan(down);
        if (!(r > l && u > d)) throw new IllegalArgumentException("Invalid FOV");
        float[] m = new float[16];
        m[0] = 2/(r-l); m[5] = 2/(u-d); m[8] = (r+l)/(r-l); m[9] = (u+d)/(u-d);
        m[10] = -(far+near)/(far-near); m[11] = -1; m[14] = -2*far*near/(far-near);
        return m;
    }
    static float[] pose(float px, float py, float pz, float x, float y, float z, float w) {
        float n = (float)Math.sqrt(x*x+y*y+z*z+w*w);
        if (!(n > 0) || !Float.isFinite(n)) throw new IllegalArgumentException("Invalid quaternion");
        x/=n; y/=n; z/=n; w/=n;
        return new float[]{1-2*(y*y+z*z),2*(x*y+z*w),2*(x*z-y*w),0,
            2*(x*y-z*w),1-2*(x*x+z*z),2*(y*z+x*w),0,
            2*(x*z+y*w),2*(y*z-x*w),1-2*(x*x+y*y),0,px,py,pz,1};
    }
    static float[] inverseRigid(float[] pose) {
        float[] m = identity();
        for (int c=0;c<3;c++) for (int r=0;r<3;r++) m[c*4+r] = pose[r*4+c];
        for (int r=0;r<3;r++) m[12+r] = -(m[r]*pose[12]+m[4+r]*pose[13]+m[8+r]*pose[14]);
        return m;
    }
    static float[] multiply(float[] a, float[] b) {
        float[] m = new float[16];
        for (int c=0;c<4;c++) for (int r=0;r<4;r++) for (int k=0;k<4;k++) m[c*4+r]+=a[k*4+r]*b[c*4+k];
        return m;
    }
    static float[] transform(float[] m, float x, float y, float z, float w) {
        return new float[]{m[0]*x+m[4]*y+m[8]*z+m[12]*w, m[1]*x+m[5]*y+m[9]*z+m[13]*w,
            m[2]*x+m[6]*y+m[10]*z+m[14]*w, m[3]*x+m[7]*y+m[11]*z+m[15]*w};
    }
}
