import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.opengl.WGL.*;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.openxr.KHROpenGLEnable.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;

/** Standalone diagnostic only. No game classes, agent, or installation writes. */
public final class Diagnostic implements AutoCloseable {
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final String mode;
    private final Path output;
    private final int seconds;
    private final boolean hidden;
    private final boolean scene;
    private final String previewPose;
    private float[] sceneAnchor;
    private int depthBuffer, depthWidth, depthHeight;
    private long window;
    private boolean glfwStarted, xrLoaded, running, exit, exitRequested;
    private GLFWErrorCallback glfwError;
    private XrInstance instance;
    private XrSession session;
    private XrSpace space;
    private long system;
    private Eye[] eyes = new Eye[2];
    private int fbo, frames, submitted, renderedEyes, skipped;
    private int sessionState;
    private long colorFormat;

    private static final class Eye {
        XrSwapchain handle;
        XrSwapchainImageOpenGLKHR.Buffer images;
        int width, height;
    }

    private static final class SetupRequired extends RuntimeException {
        private static final long serialVersionUID = 1L;
        SetupRequired(String message) { super(message); }
    }

    private Diagnostic(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (!arg.startsWith("--") || eq < 3) throw new IllegalArgumentException("Expected --name=value: " + arg);
            String key = arg.substring(2, eq);
            if (!Set.of("mode", "output", "seconds", "hidden", "content", "preview-pose").contains(key)) throw new IllegalArgumentException("Unknown option: " + key);
            options.put(key, arg.substring(eq + 1));
        }
        mode = options.getOrDefault("mode", "preview");
        if (!Set.of("preview", "probe", "xr").contains(mode)) throw new IllegalArgumentException("mode must be preview, probe, or xr");
        seconds = Integer.parseInt(options.getOrDefault("seconds", "15"));
        if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("seconds must be 1..300");
        hidden = Boolean.parseBoolean(options.getOrDefault("hidden", "false"));
        String content = options.getOrDefault("content", "charts");
        if (!Set.of("charts", "scene").contains(content)) throw new IllegalArgumentException("Invalid content");
        scene = content.equals("scene");
        previewPose = options.getOrDefault("preview-pose", "neutral");
        if (!Set.of("neutral", "turned").contains(previewPose)) throw new IllegalArgumentException("Invalid preview pose");
        if (!previewPose.equals("neutral") && (!mode.equals("preview") || !scene))
            throw new IllegalArgumentException("Synthetic pose applies only to scene preview");
        output = Path.of(options.getOrDefault("output", "runs/manual")).toAbsolutePath();
        report.put("mode", mode);
        report.put("content", content);
        report.put("previewPose", previewPose);
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("runtimeManifest", System.getenv("XR_RUNTIME_JSON"));
        report.put("steamConfigOverride", System.getenv("VR_CONFIG_PATH"));
        report.put("physicalHeadsetValidated", false);
        report.put("gameIntegrationValidated", false);
    }

    public static void main(String[] args) throws Exception {
        int code = 1;
        Diagnostic app = new Diagnostic(args);
        Files.createDirectories(app.output);
        try (app) {
            if (app.mode.equals("preview")) app.preview();
            else {
                app.initializeXR();
                if (app.mode.equals("probe")) app.report.put("status", "XR_SYSTEM_DISCOVERED");
                else { app.initializeSession(); app.loop(); }
            }
            code = 0;
        } catch (SetupRequired ex) {
            app.report.put("status", "SETUP_REQUIRED");
            app.report.put("error", ex.getMessage());
            System.err.println("SETUP_REQUIRED: " + ex.getMessage());
            code = 2;
        } catch (Throwable ex) {
            app.report.put("status", "FAILED");
            app.report.put("error", ex.toString());
            ex.printStackTrace();
        } finally {
            app.report.put("framesBegun", app.frames);
            app.report.put("projectionFramesSubmitted", app.submitted);
            app.report.put("eyeImagesRendered", app.renderedEyes);
            app.report.put("framesSkipped", app.skipped);
            app.report.put("exitCode", code);
            String json = json(app.report);
            Files.writeString(app.output.resolve("result.json"), json);
            System.out.println(json);
        }
        System.exit(code);
    }

    private void initializeGL(int major, int minor) {
        glfwError = GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed");
        glfwStarted = true;
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, major);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, minor);
        // A compatibility context resembles PZ3D's use of glPushAttrib.
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, hidden ? GLFW_FALSE : GLFW_TRUE);
        window = glfwCreateWindow(1200, 600, "PZ3D VR | " + (scene ? "3D stereo scene" : "LEFT red / RIGHT blue") + " | Esc closes", NULL, NULL);
        if (window == NULL) throw new IllegalStateException("GLFW OpenGL window creation failed");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(0);
        GL.createCapabilities();
        report.put("glVersion", glGetString(GL_VERSION));
        report.put("glRenderer", glGetString(GL_RENDERER));
        report.put("glVendor", glGetString(GL_VENDOR));
        System.out.println("OpenGL: " + glGetString(GL_VERSION) + " / " + glGetString(GL_RENDERER));
        fbo = glGenFramebuffers();
    }

    private void initializeXR() {
        // XR initializes its bundled loader on first use; no runtime registry writes.
        XR.getFunctionProvider();
        xrLoaded = true;
        try (MemoryStack stack = stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            int result = xrEnumerateInstanceExtensionProperties((ByteBuffer)null, count, null);
            if (result == XR_ERROR_RUNTIME_UNAVAILABLE) throw new SetupRequired("No usable OpenXR runtime. Check XR_RUNTIME_JSON and SteamVR setup.");
            check(result, "enumerate extensions");
            XrExtensionProperties.Buffer extensions = XrExtensionProperties.calloc(count.get(0), stack);
            for (int i = 0; i < extensions.capacity(); i++) extensions.get(i).type$Default();
            check(xrEnumerateInstanceExtensionProperties((ByteBuffer)null, count, extensions), "read extensions");
            boolean gl = false;
            List<String> names = new ArrayList<>();
            for (int i = 0; i < extensions.capacity(); i++) {
                String name = extensions.get(i).extensionNameString(); names.add(name);
                gl |= name.equals(XR_KHR_OPENGL_ENABLE_EXTENSION_NAME);
            }
            report.put("extensions", names);
            if (!gl) throw new SetupRequired("Runtime does not expose XR_KHR_opengl_enable");
            XrInstanceCreateInfo info = XrInstanceCreateInfo.calloc(stack).type$Default()
                .enabledExtensionNames(stack.pointers(stack.UTF8(XR_KHR_OPENGL_ENABLE_EXTENSION_NAME)));
            info.applicationInfo().applicationName(stack.UTF8("PZ3D VR Standalone Diagnostic"))
                .applicationVersion(1).engineName(stack.UTF8("Standalone LWJGL"))
                .engineVersion(1).apiVersion(XR_MAKE_VERSION(1, 0, 0));
            PointerBuffer ptr = stack.mallocPointer(1);
            check(xrCreateInstance(info, ptr), "create instance");
            instance = new XrInstance(ptr.get(0), info);
            XrInstanceProperties props = XrInstanceProperties.calloc(stack).type$Default();
            check(xrGetInstanceProperties(instance, props), "instance properties");
            report.put("runtime", props.runtimeNameString());
            System.out.println("OpenXR runtime: " + props.runtimeNameString());
            LongBuffer id = stack.mallocLong(1);
            result = xrGetSystem(instance, XrSystemGetInfo.calloc(stack).type$Default().formFactor(XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY), id);
            if (result == XR_ERROR_FORM_FACTOR_UNAVAILABLE) throw new SetupRequired("Runtime found, but no HMD system. Start the isolated SteamVR null-driver profile.");
            check(result, "get HMD system");
            system = id.get(0);
            XrSystemProperties systemProps = XrSystemProperties.calloc(stack).type$Default();
            check(xrGetSystemProperties(instance, system, systemProps), "system properties");
            report.put("system", systemProps.systemNameString());
            report.put("positionTrackingSupported", systemProps.trackingProperties().positionTracking());
            report.put("orientationTrackingSupported", systemProps.trackingProperties().orientationTracking());
            System.out.println("HMD system: " + systemProps.systemNameString());
        }
    }

    private void initializeSession() {
        try (MemoryStack stack = stackPush()) {
            XrGraphicsRequirementsOpenGLKHR req = XrGraphicsRequirementsOpenGLKHR.calloc(stack).type$Default();
            check(xrGetOpenGLGraphicsRequirementsKHR(instance, system, req), "OpenGL requirements");
            long minimum = Math.max(XR_MAKE_VERSION(3, 3, 0), req.minApiVersionSupported());
            initializeGL((int)XR_VERSION_MAJOR(minimum), (int)XR_VERSION_MINOR(minimum));
            long actual = XR_MAKE_VERSION(glGetInteger(GL_MAJOR_VERSION), glGetInteger(GL_MINOR_VERSION), 0);
            if (actual < req.minApiVersionSupported() || actual > req.maxApiVersionSupported())
                throw new SetupRequired("Created OpenGL context is outside runtime graphics requirements");
            XrGraphicsBindingOpenGLWin32KHR binding = XrGraphicsBindingOpenGLWin32KHR.calloc(stack).type$Default()
                .hDC(wglGetCurrentDC()).hGLRC(org.lwjgl.glfw.GLFWNativeWGL.glfwGetWGLContext(window));
            if (binding.hDC() == NULL || binding.hGLRC() == NULL) throw new IllegalStateException("No current WGL context/DC");
            PointerBuffer ptr = stack.mallocPointer(1);
            XrSessionCreateInfo sessionInfo = XrSessionCreateInfo.calloc(stack).type$Default().systemId(system).next(binding.address());
            check(xrCreateSession(instance, sessionInfo, ptr), "create OpenGL session");
            session = new XrSession(ptr.get(0), instance);
            XrReferenceSpaceCreateInfo spaceInfo = XrReferenceSpaceCreateInfo.calloc(stack).type$Default().referenceSpaceType(XR_REFERENCE_SPACE_TYPE_LOCAL);
            spaceInfo.poseInReferenceSpace().orientation().w(1);
            check(xrCreateReferenceSpace(session, spaceInfo, ptr), "create LOCAL space");
            space = new XrSpace(ptr.get(0), session);
            createSwapchains(stack);
        }
    }

    private void createSwapchains(MemoryStack stack) {
        IntBuffer count = stack.mallocInt(1);
        check(xrEnumerateViewConfigurationViews(instance, system, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, null), "count stereo views");
        if (count.get(0) != 2) throw new SetupRequired("Expected PRIMARY_STEREO with two views");
        XrViewConfigurationView.Buffer config = XrViewConfigurationView.calloc(2, stack);
        for (int i = 0; i < 2; i++) config.get(i).type$Default();
        check(xrEnumerateViewConfigurationViews(instance, system, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, config), "stereo view configuration");
        check(xrEnumerateEnvironmentBlendModes(instance, system, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, null), "count blend modes");
        IntBuffer modes = stack.mallocInt(count.get(0));
        check(xrEnumerateEnvironmentBlendModes(instance, system, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO, count, modes), "blend modes");
        boolean opaque = false;
        for (int i = 0; i < modes.capacity(); i++) opaque |= modes.get(i) == XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        if (!opaque) throw new SetupRequired("Diagnostic currently requires OPAQUE environment blend mode");
        check(xrEnumerateSwapchainFormats(session, count, null), "count color formats");
        LongBuffer formats = stack.mallocLong(count.get(0));
        check(xrEnumerateSwapchainFormats(session, count, formats), "color formats");
        for (long preferred : new long[]{GL_SRGB8_ALPHA8, GL_RGBA8}) {
            for (int i = 0; i < formats.capacity(); i++) if (formats.get(i) == preferred) colorFormat = preferred;
            if (colorFormat != 0) break;
        }
        if (colorFormat == 0) throw new SetupRequired("Neither GL_SRGB8_ALPHA8 nor GL_RGBA8 swapchain format supported");
        report.put("swapchainFormat", colorFormat);
        for (int i = 0; i < 2; i++) {
            Eye eye = new Eye(); eyes[i] = eye;
            // Small diagnostic targets, independent of desktop and headset recommendation.
            eye.width = Math.min(768, config.get(i).recommendedImageRectWidth());
            eye.height = Math.min(768, config.get(i).recommendedImageRectHeight());
            XrSwapchainCreateInfo info = XrSwapchainCreateInfo.calloc(stack).type$Default()
                .usageFlags(XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT)
                .format(colorFormat).sampleCount(1).width(eye.width).height(eye.height).faceCount(1).arraySize(1).mipCount(1);
            PointerBuffer ptr = stack.mallocPointer(1);
            check(xrCreateSwapchain(session, info, ptr), "create eye " + i + " swapchain");
            eye.handle = new XrSwapchain(ptr.get(0), session);
            check(xrEnumerateSwapchainImages(eye.handle, count, null), "count eye images");
            eye.images = XrSwapchainImageOpenGLKHR.calloc(count.get(0));
            for (int j = 0; j < eye.images.capacity(); j++) eye.images.get(j).type$Default();
            check(xrEnumerateSwapchainImages(eye.handle, count, XrSwapchainImageBaseHeader.create(eye.images)), "enumerate eye textures");
            report.put("eye" + i + "Size", eye.width + "x" + eye.height);
            report.put("eye" + i + "SwapchainImages", eye.images.capacity());
        }
    }

    private void pollEvents() {
        try (MemoryStack stack = stackPush()) {
            XrEventDataBuffer event = XrEventDataBuffer.calloc(stack);
            for (;;) {
                event.clear(); event.type$Default();
                int result = xrPollEvent(instance, event);
                if (result == XR_EVENT_UNAVAILABLE) return;
                check(result, "poll events");
                if (event.type() == XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING) { exit = true; return; }
                if (event.type() != XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) continue;
                XrEventDataSessionStateChanged changed = XrEventDataSessionStateChanged.create(event.address());
                if (session == null || changed.session() != session.address()) continue;
                sessionState = changed.state();
                System.out.println("Session state: " + stateName(sessionState));
                report.put("lastSessionState", stateName(sessionState));
                switch (sessionState) {
                    case XR_SESSION_STATE_READY -> {
                        check(xrBeginSession(session, XrSessionBeginInfo.calloc(stack).type$Default().primaryViewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO)), "begin session");
                        running = true;
                    }
                    case XR_SESSION_STATE_STOPPING -> { check(xrEndSession(session), "end session"); running = false; }
                    case XR_SESSION_STATE_EXITING, XR_SESSION_STATE_LOSS_PENDING -> { exit = true; running = false; }
                }
            }
        }
    }

    private void loop() throws Exception {
        long deadline = System.nanoTime() + seconds * 1_000_000_000L;
        while (!exit && System.nanoTime() < deadline && !glfwWindowShouldClose(window)) {
            glfwPollEvents();
            if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) break;
            pollEvents();
            if (exit) break;
            if (!running) { Thread.sleep(10); continue; }
            renderXRFrame();
        }
        if (submitted == 0) throw new SetupRequired("No valid stereo projection frame was submitted; inspect session states, shouldRender, and view validity in logs");
        report.put("status", "XR_STEREO_SUBMISSION_OK");
        report.put("imageContent", scene ? "Static 3D scene using runtime per-eye FOV and pose; no hardware tracking validation" : "Diagnostic eye charts only; no 3D world or tracking validation");
    }

    private void renderXRFrame() throws Exception {
        try (MemoryStack stack = stackPush()) {
            XrFrameState state = XrFrameState.calloc(stack).type$Default();
            check(xrWaitFrame(session, XrFrameWaitInfo.calloc(stack).type$Default(), state), "wait frame");
            check(xrBeginFrame(session, XrFrameBeginInfo.calloc(stack).type$Default()), "begin frame");
            frames++;
            XrFrameEndInfo end = XrFrameEndInfo.calloc(stack).type$Default().displayTime(state.predictedDisplayTime()).environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE);
            boolean content = false;
            try {
                if (state.shouldRender()) {
                    XrView.Buffer views = XrView.calloc(2, stack);
                    for (int i = 0; i < 2; i++) views.get(i).type$Default();
                    XrViewState validity = XrViewState.calloc(stack).type$Default();
                    IntBuffer count = stack.mallocInt(1);
                    check(xrLocateViews(session, XrViewLocateInfo.calloc(stack).type$Default()
                        .viewConfigurationType(XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO).displayTime(state.predictedDisplayTime()).space(space), validity, count, views), "locate views");
                    long required = XR_VIEW_STATE_ORIENTATION_VALID_BIT | XR_VIEW_STATE_POSITION_VALID_BIT;
                    if (count.get(0) == 2 && (validity.viewStateFlags() & required) == required) {
                        if (scene && sceneAnchor == null) {
                            // Place the scene once in LOCAL space, facing the initial left-eye orientation.
                            // Runtime eye poses remain unchanged, including compositor layer metadata.
                            sceneAnchor = eyePose(views.get(0));
                            for (int axis=0;axis<3;axis++) sceneAnchor[12+axis] = (eyePose(views.get(0))[12+axis]+eyePose(views.get(1))[12+axis])*.5f;
                            report.put("sceneAnchorColumnMajor", floats(sceneAnchor));
                        }
                        XrCompositionLayerProjectionView.Buffer projectionViews = XrCompositionLayerProjectionView.calloc(2, stack);
                        for (int i = 0; i < 2; i++) {
                            renderEye(i, views.get(i), submitted == 0, stack);
                            XrCompositionLayerProjectionView view = projectionViews.get(i).type$Default().pose(views.get(i).pose()).fov(views.get(i).fov());
                            view.subImage().swapchain(eyes[i].handle).imageArrayIndex(0);
                            view.subImage().imageRect().offset().set(0, 0);
                            view.subImage().imageRect().extent().set(eyes[i].width, eyes[i].height);
                            if (submitted == 0) {
                                XrVector3f p = views.get(i).pose().position$();
                                report.put("eye" + i + "PoseMetres", List.of(p.x(), p.y(), p.z()));
                                XrQuaternionf q = views.get(i).pose().orientation();
                                report.put("eye" + i + "QuaternionXYZW", List.of(q.x(),q.y(),q.z(),q.w()));
                                report.put("viewStateFlags", validity.viewStateFlags());
                            }
                        }
                        XrCompositionLayerProjection layer = XrCompositionLayerProjection.calloc(stack).type$Default().space(space).views(projectionViews);
                        end.layers(stack.pointers(layer.address()));
                        content = true;
                    }
                }
            } finally {
                // End even if image rendering failed; default is a zero-layer frame.
                check(xrEndFrame(session, end), "end frame");
            }
            if (content) {
                submitted++;
                if (submitted == 1 || submitted % 300 == 0) System.out.println("Stereo projection frames submitted: " + submitted);
                glfwSwapBuffers(window);
            } else skipped++;
        }
    }

    private void renderEye(int index, XrView view, boolean capture, MemoryStack stack) throws Exception {
        Eye eye = eyes[index];
        IntBuffer imageIndex = stack.mallocInt(1);
        check(xrAcquireSwapchainImage(eye.handle, XrSwapchainImageAcquireInfo.calloc(stack).type$Default(), imageIndex), "acquire eye " + index);
        check(xrWaitSwapchainImage(eye.handle, XrSwapchainImageWaitInfo.calloc(stack).type$Default().timeout(XR_INFINITE_DURATION)), "wait eye " + index);
        try {
            attach(eye.images.get(imageIndex.get(0)).image(), eye.width, eye.height);
            if (colorFormat == GL_SRGB8_ALPHA8) glEnable(GL_FRAMEBUFFER_SRGB); else glDisable(GL_FRAMEBUFFER_SRGB);
            if (scene) {
                XrFovf f = view.fov();
                float[] projection = StereoMath.projection(f.angleLeft(),f.angleRight(),f.angleUp(),f.angleDown(),.05f,30);
                float[] camera = StereoMath.multiply(StereoMath.inverseRigid(eyePose(view)), sceneAnchor);
                Scene.draw(projection,camera,eye.width,eye.height);
                if (capture) recordMatrices(index,projection,camera,eye.width,eye.height);
            } else chart(index, eye.width, eye.height);
            if (capture) capture(index, eye.width, eye.height);
            mirror(index, eye.width, eye.height);
            glCheck("eye " + index);
            // Flush submitted GL commands before returning the runtime image.
            glFlush();
            renderedEyes++;
        } finally {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, 0, 0);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            check(xrReleaseSwapchainImage(eye.handle, XrSwapchainImageReleaseInfo.calloc(stack).type$Default()), "release eye " + index);
        }
    }

    private void attach(int texture, int width, int height) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (scene) {
            if (depthBuffer == 0) depthBuffer = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, depthBuffer);
            if (depthWidth != width || depthHeight != height) {
                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
                depthWidth = width; depthHeight = height;
            }
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthBuffer);
        }
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Eye framebuffer incomplete");
    }

    private static void chart(int eye, int w, int h) {
        glViewport(0, 0, w, h);
        glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND); glDisable(GL_SCISSOR_TEST);
        glColorMask(true, true, true, true);
        glClearColor(eye == 0 ? 0.45f : 0.025f, 0.025f, eye == 1 ? 0.45f : 0.025f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);
        // Fixed, asymmetric orientation markers: green top, yellow bottom, white L/R.
        rect(0, h - h / 16, w, h / 16, 0, 1, 0);
        rect(0, 0, w, h / 16, 1, 1, 0);
        int[][] glyph = eye == 0 ? new int[][]{{1,0,0},{1,0,0},{1,0,0},{1,0,0},{1,1,1}}
            : new int[][]{{1,1,0},{1,0,1},{1,1,0},{1,0,1},{1,0,1}};
        int unit = Math.min(w / 8, h / 8), x = (w - unit * 3) / 2, y = (h - unit * 5) / 2;
        for (int row = 0; row < 5; row++) for (int col = 0; col < 3; col++)
            if (glyph[row][col] != 0) rect(x + col * unit, y + (4 - row) * unit, unit - 2, unit - 2, 1, 1, 1);
        glDisable(GL_SCISSOR_TEST);
    }

    private static void rect(int x, int y, int w, int h, float r, float g, float b) {
        glScissor(x, y, w, h); glClearColor(r, g, b, 1); glClear(GL_COLOR_BUFFER_BIT);
    }

    private void mirror(int eye, int w, int h) {
        try (MemoryStack stack = stackPush()) {
            IntBuffer width = stack.mallocInt(1), height = stack.mallocInt(1);
            glfwGetFramebufferSize(window, width, height);
            if (width.get(0) <= 0 || height.get(0) <= 0) return;
            glDisable(GL_FRAMEBUFFER_SRGB);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
            glDrawBuffer(GL_BACK);
            int half = width.get(0) / 2;
            glBlitFramebuffer(0, 0, w, h, eye * half, 0, (eye + 1) * half, height.get(0), GL_COLOR_BUFFER_BIT, GL_NEAREST);
        }
    }

    private void capture(int eye, int w, int h) throws Exception {
        ByteBuffer pixels = memAlloc(w * h * 4);
        try {
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int at = (y * w + x) * 4;
                int rgba = 0xff000000 | (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | (pixels.get(at + 2) & 255);
                image.setRGB(x, h - y - 1, rgba);
            }
            ImageIO.write(image, "png", output.resolve(eye == 0 ? "left.png" : "right.png").toFile());
        } finally { memFree(pixels); }
    }

    private void preview() throws Exception {
        initializeGL(3, 3);
        int[] textures = {glGenTextures(), glGenTextures()};
        try {
            for (int texture : textures) {
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 640, 640, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer)null);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            }
            long deadline = System.nanoTime() + seconds * 1_000_000_000L;
            do {
                glfwPollEvents();
                for (int i = 0; i < 2; i++) {
                    attach(textures[i],640,640);
                    if (scene) {
                        float[] head = previewPose.equals("turned") ? StereoMath.pose(.12f,.06f,.08f,.05f,.10f,.075f,.99f) : StereoMath.identity();
                        float[] eye = StereoMath.multiply(head,StereoMath.pose(i == 0 ? -.0315f : .0315f,0,0,0,0,0,1));
                        // Deliberately asymmetric preview frusta catch symmetric-FOV assumptions.
                        float[] projection = StereoMath.projection(i==0?-.85f:-.7f,i==0?.7f:.85f,.8f,-.65f,.05f,30);
                        float[] camera = StereoMath.inverseRigid(eye);
                        Scene.draw(projection,camera,640,640);
                        if (renderedEyes < 2) recordMatrices(i,projection,camera,640,640);
                    } else chart(i, 640, 640);
                    if (renderedEyes < 2) capture(i, 640, 640);
                    mirror(i, 640, 640); renderedEyes++;
                }
                glCheck("desktop preview");
                glfwSwapBuffers(window);
                if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) break;
                Thread.sleep(16);
            } while (System.nanoTime() < deadline && !glfwWindowShouldClose(window));
            report.put("status", "DESKTOP_PREVIEW_OK");
            report.put("imageContent", "Preview only: no OpenXR instance/session created");
        } finally { for (int texture : textures) glDeleteTextures(texture); }
    }

    private static void check(int result, String operation) {
        if (XR_FAILED(result)) throw new IllegalStateException(operation + " failed: XrResult=" + result);
    }

    private static void glCheck(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException(operation + ": GL error 0x" + Integer.toHexString(error));
    }

    @Override public void close() {
        // Best-effort destruction, retaining evidence if a cleanup operation fails.
        if (session != null && running && !exitRequested) {
            exitRequested = true;
            try {
                int result = xrRequestExitSession(session);
                if (XR_SUCCEEDED(result)) {
                    long deadline = System.nanoTime() + 2_000_000_000L;
                    while (running && System.nanoTime() < deadline) { pollEvents(); Thread.sleep(10); }
                }
            } catch (Exception ex) { report.put("exitRequestWarning", ex.toString()); }
        }
        if (window != NULL) {
            glfwMakeContextCurrent(window);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            if (fbo != 0) glDeleteFramebuffers(fbo);
            if (depthBuffer != 0) glDeleteRenderbuffers(depthBuffer);
        }
        for (Eye eye : eyes) if (eye != null) {
            if (eye.handle != null) cleanup(xrDestroySwapchain(eye.handle), "destroy swapchain");
            if (eye.images != null) eye.images.free();
        }
        if (space != null) cleanup(xrDestroySpace(space), "destroy space");
        if (session != null) cleanup(xrDestroySession(session), "destroy session");
        if (instance != null) cleanup(xrDestroyInstance(instance), "destroy instance");
        if (xrLoaded) XR.destroy();
        if (window != NULL) { GL.setCapabilities(null); glfwDestroyWindow(window); }
        if (glfwStarted) glfwTerminate();
        if (glfwError != null) { glfwSetErrorCallback(null); glfwError.free(); }
    }

    private void cleanup(int result, String operation) {
        if (XR_FAILED(result)) { report.put("cleanupWarning_" + operation, result); System.err.println(operation + ": " + result); }
    }

    private static float[] eyePose(XrView view) {
        XrVector3f p=view.pose().position$(); XrQuaternionf q=view.pose().orientation();
        return StereoMath.pose(p.x(),p.y(),p.z(),q.x(),q.y(),q.z(),q.w());
    }

    private static List<Float> floats(float[] values) {
        List<Float> result=new ArrayList<>(); for(float value:values) result.add(value); return result;
    }

    private void recordMatrices(int eye,float[] projection,float[] camera,int w,int h) {
        report.put("eye"+eye+"ProjectionColumnMajor",floats(projection));
        report.put("eye"+eye+"ViewSceneColumnMajor",floats(camera));
        float[] mvp=StereoMath.multiply(projection,camera);
        float[][] landmarks={{-.35f,.35f,-1.5f},{.35f,.35f,-4}};
        for(int i=0;i<2;i++) {
            float[] p=StereoMath.transform(mvp,landmarks[i][0],landmarks[i][1],landmarks[i][2],1);
            report.put("eye"+eye+"Marker"+i+"ExpectedPixel",List.of((p[0]/p[3]+1)*w*.5f,(1-p[1]/p[3])*h*.5f));
        }
    }

    private static String stateName(int state) {
        return switch (state) {
            case XR_SESSION_STATE_IDLE -> "IDLE";
            case XR_SESSION_STATE_READY -> "READY";
            case XR_SESSION_STATE_SYNCHRONIZED -> "SYNCHRONIZED";
            case XR_SESSION_STATE_VISIBLE -> "VISIBLE";
            case XR_SESSION_STATE_FOCUSED -> "FOCUSED";
            case XR_SESSION_STATE_STOPPING -> "STOPPING";
            case XR_SESSION_STATE_EXITING -> "EXITING";
            case XR_SESSION_STATE_LOSS_PENDING -> "LOSS_PENDING";
            default -> "UNKNOWN_" + state;
        };
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, item) -> entries.add("  " + json(key.toString()) + ": " + json(item)));
            return "{\n" + String.join(",\n", entries) + "\n}";
        }
        if (value instanceof Iterable<?> items) {
            List<String> entries = new ArrayList<>(); for (Object item : items) entries.add(json(item));
            return "[" + String.join(", ", entries) + "]";
        }
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
