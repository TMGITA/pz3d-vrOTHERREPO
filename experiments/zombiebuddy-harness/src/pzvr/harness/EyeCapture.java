package pzvr.harness;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import javax.imageio.ImageIO;
import pzvr.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.*;

/** Render-thread-only targets, readback, and one-eye desktop mirror. */
@SuppressWarnings("try") // Binding scopes intentionally restore state through close().
public final class EyeCapture implements PairHooks.Sink,AutoCloseable {
    private final int width,height;
    private final int[] textures=new int[2],fbos=new int[2];
    private final boolean[] copied=new boolean[2];
    public void beginPair() { copied[0]=false; copied[1]=false; }
    public EyeCapture(int width,int height) {
        if(width<1 || height<1) throw new IllegalArgumentException("Invalid capture size");
        this.width=width; this.height=height;
        int active=glGetInteger(GL_ACTIVE_TEXTURE); glActiveTexture(GL_TEXTURE0);
        int texture=glGetInteger(GL_TEXTURE_BINDING_2D),unpack=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        try(Bindings bindings=new Bindings()) {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
            for(int i=0;i<2;i++) {
                textures[i]=glGenTextures(); glBindTexture(GL_TEXTURE_2D,textures[i]);
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
                fbos[i]=glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER,fbos[i]);
                glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[i],0);
                glReadBuffer(GL_COLOR_ATTACHMENT0); glDrawBuffer(GL_COLOR_ATTACHMENT0);
                if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete capture framebuffer");
            }
            check("allocate targets");
        } catch(Throwable failure) { close(); throw failure; }
        finally { glBindTexture(GL_TEXTURE_2D,texture); glActiveTexture(active); glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack); }
    }
    @Override public void copy(int eye,int source,int sourceWidth,int sourceHeight) {
        if(eye<0||eye>1||copied[eye]||sourceWidth!=width||sourceHeight!=height) throw new IllegalStateException("Unexpected eye/dimensions");
        try(Bindings bindings=new Bindings()) {
            glDisable(GL_SCISSOR_TEST); glDisable(GL_FRAMEBUFFER_SRGB);
            new GlEyeTargets(fbos[0],fbos[1],width,height).copy(eye,source,sourceWidth,sourceHeight);
            copied[eye]=true;
        }
    }
    public void mirrorLeft() {
        complete();
        try(Bindings bindings=new Bindings()) {
            glDisable(GL_SCISSOR_TEST); glDisable(GL_FRAMEBUFFER_SRGB);
            glBindFramebuffer(GL_READ_FRAMEBUFFER,fbos[0]);
            // Keep the inherited draw framebuffer, viewport, and destination extent.
            glBlitFramebuffer(0,0,width,height,0,0,width,height,GL_COLOR_BUFFER_BIT,GL_NEAREST);
            check("mirror left eye");
        }
    }
    public void mirrorStereo() {
        complete();
        try(Bindings bindings=new Bindings()) {
            glDisable(GL_SCISSOR_TEST); glDisable(GL_FRAMEBUFFER_SRGB);
            glPushAttrib(GL_COLOR_BUFFER_BIT);
            try {
                glColorMask(true,true,true,true); glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
                // Fit each full-aspect eye inside its half of the desktop; never stretch it.
                int eyeWidth=width/2,eyeHeight=Math.max(1,(int)((long)height*eyeWidth/width));
                int y=(height-eyeHeight)/2;
                for(int eye=0;eye<2;eye++) {
                    glBindFramebuffer(GL_READ_FRAMEBUFFER,fbos[eye]);
                    int x=eye==0?0:width-eyeWidth;
                    glBlitFramebuffer(0,0,width,height,x,y,x+eyeWidth,y+eyeHeight,GL_COLOR_BUFFER_BIT,GL_LINEAR);
                }
                check("mirror stereo pair");
            } finally { glPopAttrib(); }
        }
    }
    public void save(Path output) throws Exception {
        complete(); Files.createDirectories(output);
        int pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try(Bindings bindings=new Bindings()) {
            glBindBuffer(GL_PIXEL_PACK_BUFFER,0); glPixelStorei(GL_PACK_ALIGNMENT,1);
            glPixelStorei(GL_PACK_ROW_LENGTH,0); glPixelStorei(GL_PACK_SKIP_PIXELS,0); glPixelStorei(GL_PACK_SKIP_ROWS,0);
            for(int eye=0;eye<2;eye++) {
                ByteBuffer pixels=memAlloc(Math.multiplyExact(Math.multiplyExact(width,height),4));
                try {
                    glBindFramebuffer(GL_READ_FRAMEBUFFER,fbos[eye]);
                    glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,pixels); check("eye readback");
                    BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                    for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                        int at=(y*width+x)*4;
                        int argb=0xff000000 | (pixels.get(at)&255)<<16 | (pixels.get(at+1)&255)<<8 | pixels.get(at+2)&255;
                        image.setRGB(x,height-y-1,argb);
                    }
                    if(!ImageIO.write(image,"png",output.resolve(eye==0?"left.png":"right.png").toFile())) throw new IllegalStateException("PNG writer unavailable");
                } finally { memFree(pixels); }
            }
        } finally { glPopClientAttrib(); glBindBuffer(GL_PIXEL_PACK_BUFFER,pack); }
    }
    private void complete() { if(!copied[0]||!copied[1]) throw new IllegalStateException("Both eyes must be copied"); }
    private static void check(String stage) { int e=glGetError(); if(e!=GL_NO_ERROR) throw new IllegalStateException(stage+": GL error "+e); }
    @Override public void close() {
        for(int i=0;i<2;i++) {
            if(fbos[i]!=0) { glDeleteFramebuffers(fbos[i]); fbos[i]=0; }
            if(textures[i]!=0) { glDeleteTextures(textures[i]); textures[i]=0; }
        }
    }
    private static final class Bindings implements AutoCloseable {
        final int read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        final boolean scissor=glIsEnabled(GL_SCISSOR_TEST),srgb=glIsEnabled(GL_FRAMEBUFFER_SRGB);
        public void close() {
            glBindFramebuffer(GL_READ_FRAMEBUFFER,read); glBindFramebuffer(GL_DRAW_FRAMEBUFFER,draw);
            if(scissor) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
            if(srgb) glEnable(GL_FRAMEBUFFER_SRGB); else glDisable(GL_FRAMEBUFFER_SRGB);
        }
    }
}
