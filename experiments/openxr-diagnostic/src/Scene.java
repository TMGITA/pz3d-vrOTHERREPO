import static org.lwjgl.opengl.GL33.*;

/** Static geometry in metres. Both eyes consume the same scene; no updates during drawing. */
final class Scene {
    private Scene() {}
    static void draw(float[] projection, float[] view, int width, int height) {
        glViewport(0,0,width,height);
        glDisable(GL_SCISSOR_TEST); glDisable(GL_BLEND); glDisable(GL_CULL_FACE);
        glDisable(GL_LIGHTING); glDisable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LESS); glDepthMask(true);
        glColorMask(true,true,true,true);
        glClearColor(.012f,.018f,.032f,1); glClearDepth(1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_PROJECTION); glLoadMatrixf(projection);
        glMatrixMode(GL_MODELVIEW); glLoadMatrixf(view);
        glColor3f(.10f,.13f,.18f);
        glBegin(GL_QUADS);
        glVertex3f(-5,-1.2f,0); glVertex3f(5,-1.2f,0); glVertex3f(5,-1.2f,-10); glVertex3f(-5,-1.2f,-10);
        glEnd();
        glColor3f(.25f,.33f,.42f); glBegin(GL_LINES);
        for (int i=-5;i<=5;i++) { glVertex3f(i,-1.199f,0); glVertex3f(i,-1.199f,-10); }
        for (int i=0;i<=10;i++) { glVertex3f(-5,-1.199f,-i); glVertex3f(5,-1.199f,-i); }
        glEnd();
        cube(-.65f,-.65f,-2.5f,.45f,.85f,.22f,.06f);
        cube(.85f,-.60f,-4,.60f,.12f,.65f,.18f);
        cube(0,-.5f,-7,.7f,.25f,.30f,.85f);
        // Unique colours allow independent GPU readback checks of landmark positions.
        marker(-.35f,.35f,-1.5f,.055f,0,1,1);
        marker(.35f,.35f,-4,.10f,1,0,1);
    }
    private static void marker(float x,float y,float z,float s,float r,float g,float b) {
        glColor3f(r,g,b); glBegin(GL_QUADS);
        glVertex3f(x-s,y-s,z); glVertex3f(x+s,y-s,z); glVertex3f(x+s,y+s,z); glVertex3f(x-s,y+s,z); glEnd();
    }
    private static void cube(float x,float y,float z,float s,float r,float g,float b) {
        float[][] v={{-1,-1,1},{1,-1,1},{1,1,1},{-1,1,1},{-1,-1,-1},{1,-1,-1},{1,1,-1},{-1,1,-1}};
        int[][] faces={{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2},{3,2,6,7},{4,5,1,0}};
        glBegin(GL_QUADS);
        for (int f=0;f<6;f++) {
            float shade=1-f*.11f; glColor3f(r*shade,g*shade,b*shade);
            for (int i:faces[f]) glVertex3f(x+s*v[i][0],y+s*v[i][1],z+s*v[i][2]);
        }
        glEnd();
    }
}
