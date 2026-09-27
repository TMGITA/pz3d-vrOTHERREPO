import java.nio.*;
import java.nio.file.*;
import pzvr.xr.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.WGL.*;

/** Tests the packaged XR backend on a caller-owned context; no game or mod entry points. */
public final class OpenXrSmokeTest {
    static int checks;
    static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        if(!glfwInit()) throw new IllegalStateException("GLFW init");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE); glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_COMPAT_PROFILE);
        long window=glfwCreateWindow(320,240,"Packaged OpenXR backend test",0,0);
        if(window==0) throw new IllegalStateException("Window creation");
        try {
            glfwMakeContextCurrent(window); GL.createCapabilities();
            long context=wglGetCurrentContext((IntBuffer)null);
            if(args.length>0 && args[0].equals("missing")) {
                boolean rejected=false;
                try(OpenXrSession session=new OpenXrSession()) { throw new AssertionError("Missing runtime accepted; submissions="+session.submitted()); }
                catch(IllegalStateException expected) { rejected=true; System.out.println("Expected missing runtime: "+expected); }
                check(rejected && wglGetCurrentContext((IntBuffer)null)==context,"Missing runtime preserves caller context");
                glClear(GL_COLOR_BUFFER_BIT); check(glGetError()==GL_NO_ERROR,"Desktop rendering remains available");
            } else {
                int texture=glGenTextures(); glBindTexture(GL_TEXTURE_2D,texture);
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,320,240,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
                int fbo=glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER,fbo);
                glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
                check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"Caller source FBO");
                for(int cycle=0;cycle<2;cycle++) {
                    final boolean[] injected={cycle!=0},uiFailure={cycle!=0};
                    try(OpenXrSession session=new OpenXrSession()) {
                        var handsField=OpenXrSession.class.getDeclaredField("hands"); handsField.setAccessible(true);
                        check(handsField.get(session)!=null,"Real grip action set/spaces created and attached");
                        boolean rejected=false;
                        try { session.copyUiTexture(texture,320,240); } catch(IllegalStateException expected) { rejected=true; }
                        check(rejected,"UI cannot acquire outside an XR frame");
                        long deadline=System.nanoTime()+30_000_000_000L;
                        while(session.submitted()<60 && System.nanoTime()<deadline) {
                            glfwPollEvents();
                            try {
                                session.frame((head,views,sink)-> {
                                    check(handsField.get(session)!=null,"Grip synchronization has not failed");
                                    for(int side=0;side<2;side++) {
                                        var hand=session.hands().get(side);
                                        check(hand==null||hand.matrix().isFinite(),"Missing controller safely native; tracked pose finite");
                                    }
                                    check(views.size()==2 && head.matrix().isFinite(),"Runtime poses valid");
                                    for(int eye=0;eye<2;eye++) {
                                        glBindFramebuffer(GL_FRAMEBUFFER,fbo); glViewport(0,0,320,240);
                                        glDisable(GL_SCISSOR_TEST); glDisable(GL_FRAMEBUFFER_SRGB);
                                        glClearColor(eye==0?1:0,0,eye==1?1:0,1); glClear(GL_COLOR_BUFFER_BIT);
                                        glEnable(GL_SCISSOR_TEST); glScissor(0,230,320,10); glClearColor(0,1,0,1); glClear(GL_COLOR_BUFFER_BIT);
                                        glScissor(0,0,320,10); glClearColor(1,1,0,1); glClear(GL_COLOR_BUFFER_BIT);
                                        glScissor(1,1,1,1); glEnable(GL_FRAMEBUFFER_SRGB);
                                        sink.copy(eye,fbo,320,240);
                                        check(glGetInteger(GL_READ_FRAMEBUFFER_BINDING)==fbo && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==fbo
                                            && glIsEnabled(GL_SCISSOR_TEST) && glIsEnabled(GL_FRAMEBUFFER_SRGB),"XR copy preserves caller GL bindings/enables");
                                        if(!injected[0] && session.submitted()>=10) { injected[0]=true; throw new IllegalArgumentException("injected callback failure"); }
                                    }
                                    long n=session.submitted();
                                    if(n<20 || n>=30) {
                                        int uiWidth=n<30?320:160,uiHeight=n<30?240:120;
                                        // Transparent source with a premultiplied patch and asymmetric top/bottom markers.
                                        glDisable(GL_SCISSOR_TEST); glDisable(GL_FRAMEBUFFER_SRGB);
                                        glClearColor(0,0,0,0); glClear(GL_COLOR_BUFFER_BIT);
                                        glEnable(GL_SCISSOR_TEST); glScissor(10,10,50,50);
                                        glClearColor(.25f,0,0,.5f); glClear(GL_COLOR_BUFFER_BIT);
                                        glScissor(0,uiHeight-8,uiWidth,8); glClearColor(0,1,0,1); glClear(GL_COLOR_BUFFER_BIT);
                                        glScissor(0,0,uiWidth,8); glClearColor(1,1,0,1); glClear(GL_COLOR_BUFFER_BIT);
                                        glEnable(GL_FRAMEBUFFER_SRGB);
                                        session.copyUiTexture(texture,uiWidth,uiHeight);
                                        check(glGetInteger(GL_READ_FRAMEBUFFER_BINDING)==fbo && glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==fbo
                                            && glIsEnabled(GL_SCISSOR_TEST) && glIsEnabled(GL_FRAMEBUFFER_SRGB),"UI copy preserves GL bindings/enables");
                                        try(MemoryStack stack=MemoryStack.stackPush()) {
                                            var method=OpenXrSession.class.getDeclaredMethod("uiLayer",MemoryStack.class); method.setAccessible(true);
                                            XrCompositionLayerQuad quad=(XrCompositionLayerQuad)method.invoke(session,stack);
                                            check(quad.eyeVisibility()==XR_EYE_VISIBILITY_BOTH && quad.layerFlags()==XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT,"UI alpha and both-eye composition");
                                            check(quad.pose().position$().z()==-1.5f && quad.pose().orientation().w()==1,"UI panel faces viewer at readable distance");
                                            check(Math.abs(quad.size().width()/quad.size().height()-4f/3)<.001f,"UI aspect retained");
                                            check(quad.subImage().imageRect().extent().width()==uiWidth,"UI swapchain resize");
                                        }
                                        if(!uiFailure[0] && n>=15) { uiFailure[0]=true; throw new IllegalArgumentException("injected callback failure"); }
                                    }
                                });
                            } catch(IllegalArgumentException failure) {
                                if(!failure.getMessage().equals("injected callback failure")) throw failure;
                                System.out.println("Injected partial-pair failure returned a zero-layer frame; continuing.");
                            }
                            Thread.sleep(1);
                        }
                        check(session.submitted()>=60,"Sixty projection pairs submitted per session");
                        check(injected[0] && uiFailure[0],"Partial-pair and post-UI error paths exercised");
                        check(session.uiSubmitted()==50,"UI omitted on ten world-only frames and failed submissions");
                        session.frame(null); // Unsupported/no-render frame still services runtime lifecycle.
                    }
                    check(wglGetCurrentContext((IntBuffer)null)==context,"XR shutdown preserves caller context");
                    glBindFramebuffer(GL_FRAMEBUFFER,fbo); glDisable(GL_SCISSOR_TEST); glClear(GL_COLOR_BUFFER_BIT);
                    check(glGetError()==GL_NO_ERROR,"Caller can render after XR teardown");
                }
                glBindFramebuffer(GL_FRAMEBUFFER,0); glDeleteFramebuffers(fbo); glDeleteTextures(texture);
            }
            System.out.println("Packaged OpenXR backend checks passed: "+checks+"; gameIntegrationValidated=false; physicalHeadsetValidated=false");
        } finally { glfwDestroyWindow(window); glfwTerminate(); }
    }
}
