package pzvr;

import static org.lwjgl.opengl.GL30.*;

/** Destinations must be distinct, complete color FBOs owned by the integration caller. */
public final class GlEyeTargets implements PairHooks.Sink {
    private final int left,right,width,height;
    public GlEyeTargets(int left,int right,int width,int height) {
        if(left<=0 || right<=0 || left==right || width<=0 || height<=0) throw new IllegalArgumentException("Two distinct eye targets required");
        this.left=left; this.right=right; this.width=width; this.height=height;
    }
    public void copy(int eye,int source,int sourceWidth,int sourceHeight) {
        int target=eye==0?left:right;
        if(source==target) throw new IllegalStateException("Eye target aliases renderer scratch framebuffer");
        int read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING), draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER,source);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,target);
            if(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete eye target");
            glBlitFramebuffer(0,0,sourceWidth,sourceHeight,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_NEAREST);
            if(glGetError()!=GL_NO_ERROR) throw new IllegalStateException("Eye copy GL error");
        } finally { glBindFramebuffer(GL_READ_FRAMEBUFFER,read); glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw); }
    }
}
