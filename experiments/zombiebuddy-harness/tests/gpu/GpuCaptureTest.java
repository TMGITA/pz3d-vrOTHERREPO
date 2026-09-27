import java.nio.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import pzvr.harness.EyeCapture;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.*;
public final class GpuCaptureTest {
    static int checks;
    static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    static int pixel(int x,int y) {
        int pack=glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        ByteBuffer p=memAlloc(4);
        try {
            glBindBuffer(GL_PIXEL_PACK_BUFFER,0); glPixelStorei(GL_PACK_ALIGNMENT,1);
            glPixelStorei(GL_PACK_ROW_LENGTH,0); glPixelStorei(GL_PACK_SKIP_PIXELS,0); glPixelStorei(GL_PACK_SKIP_ROWS,0);
            glReadPixels(x,y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,p);
            return (p.get(0)&255)<<16 | (p.get(1)&255)<<8 | p.get(2)&255;
        } finally { memFree(p); glPopClientAttrib(); glBindBuffer(GL_PIXEL_PACK_BUFFER,pack); }
    }
    public static void main(String[] args) throws Exception {
        if(!glfwInit()) throw new IllegalStateException("GLFW init failed");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_COMPAT_PROFILE);
        long window=glfwCreateWindow(160,128,"Capture helper test",NULL,NULL);
        if(window==NULL) throw new IllegalStateException("Window creation failed");
        try {
            glfwMakeContextCurrent(window); GL.createCapabilities();
            int texture=glGenTextures(); glBindTexture(GL_TEXTURE_2D,texture);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,160,128,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
            int source=glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER,source);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"Source framebuffer");
            glBindFramebuffer(GL_FRAMEBUFFER,0);
            int pack=glGenBuffers(),unpack=glGenBuffers();
            glBindBuffer(GL_PIXEL_PACK_BUFFER,pack); glBufferData(GL_PIXEL_PACK_BUFFER,1024,GL_STREAM_READ);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpack); glBufferData(GL_PIXEL_UNPACK_BUFFER,1024,GL_STREAM_DRAW);
            glPixelStorei(GL_PACK_ROW_LENGTH,7); glPixelStorei(GL_PACK_SKIP_PIXELS,2); glPixelStorei(GL_PACK_SKIP_ROWS,3);
            glActiveTexture(GL_TEXTURE3); int sentinel=glGenTextures(); glBindTexture(GL_TEXTURE_2D,sentinel);
            glEnable(GL_SCISSOR_TEST); glScissor(1,1,1,1); glEnable(GL_FRAMEBUFFER_SRGB);
            try(EyeCapture capture=new EyeCapture(160,128)) {
                check(glGetInteger(GL_ACTIVE_TEXTURE)==GL_TEXTURE3 && glGetInteger(GL_TEXTURE_BINDING_2D)==sentinel,"Allocation texture state restored");
                check(glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING)==unpack,"Unpack buffer restored");
                for(int eye=0;eye<2;eye++) {
                    glBindFramebuffer(GL_FRAMEBUFFER,source); glDisable(GL_SCISSOR_TEST); glDisable(GL_FRAMEBUFFER_SRGB);
                    glClearColor(eye==0?1:0,0,eye==1?1:0,1); glClear(GL_COLOR_BUFFER_BIT);
                    glEnable(GL_SCISSOR_TEST); glScissor(0,120,160,8); glClearColor(0,1,0,1); glClear(GL_COLOR_BUFFER_BIT);
                    glScissor(0,0,160,8); glClearColor(1,1,0,1); glClear(GL_COLOR_BUFFER_BIT);
                    glScissor(1,1,1,1); glEnable(GL_FRAMEBUFFER_SRGB); glBindFramebuffer(GL_FRAMEBUFFER,0);
                    capture.copy(eye,source,160,128);
                    check(glGetInteger(GL_READ_FRAMEBUFFER_BINDING)==0 && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==0,"Copy framebuffer state");
                    check(glIsEnabled(GL_SCISSOR_TEST)&&glIsEnabled(GL_FRAMEBUFFER_SRGB),"Copy enables restored");
                }
                capture.mirrorLeft(); capture.save(Path.of(args[0]));
                check(glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING)==pack,"Pack buffer restored");
                check(glGetInteger(GL_PACK_ROW_LENGTH)==7 && glGetInteger(GL_PACK_SKIP_PIXELS)==2 && glGetInteger(GL_PACK_SKIP_ROWS)==3,"Pack layout restored");
                check(glIsEnabled(GL_SCISSOR_TEST)&&glIsEnabled(GL_FRAMEBUFFER_SRGB),"Readback/mirror enables restored");
                glColorMask(false,false,false,false); glClearColor(.25f,.5f,.75f,1);
                capture.mirrorStereo();
                check(pixel(40,64)==0xff0000 && pixel(120,64)==0x0000ff,"Stereo mirror places left and right in correct halves");
                check(pixel(40,0)==0 && pixel(120,127)==0,"Stereo mirror letterboxes without stretching");
                check(!glGetBoolean(GL_COLOR_WRITEMASK),"Stereo mirror restores color mask");
                float[] clear=new float[4]; glGetFloatv(GL_COLOR_CLEAR_VALUE,clear);
                check(clear[0]==.25f && clear[1]==.5f && clear[2]==.75f,"Stereo mirror restores clear color");
                check(glGetInteger(GL_READ_FRAMEBUFFER_BINDING)==0 && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==0
                    && glIsEnabled(GL_SCISSOR_TEST)&&glIsEnabled(GL_FRAMEBUFFER_SRGB),"Stereo mirror restores bindings and enables");
                glColorMask(true,true,true,true);
                for(int repeat=0;repeat<3;repeat++) {
                    capture.beginPair(); capture.copy(0,source,160,128); capture.copy(1,source,160,128); capture.mirrorStereo();
                    check(pixel(40,64)==0x0000ff && pixel(120,64)==0x0000ff,"Reused targets receive new pair");
                }
            }
            for(int eye=0;eye<2;eye++) {
                var image=ImageIO.read(Path.of(args[0],eye==0?"left.png":"right.png").toFile());
                check(image.getWidth()==160&&image.getHeight()==128,"Image dimensions");
                check((image.getRGB(80,64)&0xffffff)==(eye==0?0xff0000:0x0000ff),"Eye identity and scratch copy persistence");
                check((image.getRGB(80,2)&0xffffff)==0x00ff00 && (image.getRGB(80,126)&0xffffff)==0xffff00,"PNG orientation");
            }
            check(glGetError()==GL_NO_ERROR,"Final GL state has no errors");
            glDeleteFramebuffers(source); glDeleteTextures(texture); glDeleteTextures(sentinel); glDeleteBuffers(pack); glDeleteBuffers(unpack);
            System.out.println("Real OpenGL capture checks passed: "+checks+"; "+glGetString(GL_RENDERER));
        } finally { glfwDestroyWindow(window); glfwTerminate(); }
    }
}
