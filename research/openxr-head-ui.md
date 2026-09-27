# OpenXR, headset pose, and existing UI

Research only; no OpenXR instance/session, graphics context, native loader, or headset was initialized. Library files below are isolated reference downloads. API documentation was consulted on 2026-09-27. The downloaded **3.4.1 source** is preferred over the live LWJGL javadoc, which currently identifies itself as a newer snapshot.

## Java binding choice

| Option | Fit to installed environment | Recommendation |
|---|---|---|
| `org.lwjgl:lwjgl-openxr:3.4.1` | Existing bundled core is 3.4.1, GLFW/OpenGL/WGL APIs already present. Downloaded module POM depends only on `org.lwjgl:lwjgl:3.4.1`. | First choice; add OpenXR module and matching Windows x64 native artifact, reuse the game's core/native libraries. |
| Small JNI bridge to Khronos OpenXR loader | Can avoid extending the LWJGL module set; requires native build, struct marshalling and lifecycle code. | Fallback if real binding compatibility fails. Keep API vendor-independent. |
| Java 25 Foreign Function & Memory API | Installed JVM supports it, but struct layouts, handles, callbacks and function pointers still need generated/maintained bindings. | Viable technically, larger engineering surface than matching LWJGL. [Oracle API guide](https://docs.oracle.com/en/java/javase/25/core/foreign-function-and-memory-api.html). |

The installed game archive contains no OpenXR binding classes or loader DLL. Static comparison of the downloaded binding found no overlapping non-module class entries with the game archive; its base OpenXR classes use class major 52 (Java 8), compatible in bytecode level with Java 25. Do not add a second `lwjgl`, `lwjgl-opengl`, or `lwjgl-glfw` version to the system classpath: duplicate `org.lwjgl.*` classes and incompatible native glue would be a risk. Introduce the version-matched OpenXR module only; confirm class/method resolution and native loading during the first authorized diagnostic run. This phase did not run Java linkage/native smoke tests.

Reference files: `external/openxr/lwjgl-openxr-3.4.1.jar`, `-sources.jar`, `-natives-windows.jar`, `.pom`, and extracted `sources/org/lwjgl/openxr/`. The native JAR contains **`windows/x64/org/lwjgl/openxr/openxr-loader.dll`**. `XR.create()` uses LWJGL `Library.loadNative` and that Windows bundled name; no separate headset-vendor Java SDK is required. This loader dispatches to an installed runtime; it is not itself a VR runtime. [LWJGL XR loader API](https://javadoc.lwjgl.org/org/lwjgl/openxr/XR.html).

## Existing OpenGL context

The promising path is to use the game's current render context on **`zombie.core.opengl.RenderThread`**. `org.lwjglx.opengl.Display.getWindow()` exposes the GLFW window. Installed `org.lwjgl.opengl.WGL` includes current context/DC accessors; GLFW native WGL/Win32 classes are also present. Obtain HGLRC/HDC while the correct context is current, rather than creating another GLFW window/context.

Enumerate instance extensions, require `XR_KHR_opengl_enable`, query `xrGetOpenGLGraphicsRequirementsKHR`, check the actual context version, and pass `XrGraphicsBindingOpenGLWin32KHR(hDC,hGLRC)` in `XrSessionCreateInfo.next`. The runtime and game must use a compatible GPU; a mismatch can return `XR_ERROR_GRAPHICS_DEVICE_INVALID`. The logged NVIDIA GL context is encouraging, but a machine with two GPUs needs actual validation. [Windows graphics binding](https://registry.khronos.org/OpenXR/specs/1.1/man/html/XrGraphicsBindingOpenGLWin32KHR.html), [graphics requirements](https://registry.khronos.org/OpenXR/specs/1.1/man/html/xrGetOpenGLGraphicsRequirementsKHR.html).

Keep context-bound OpenXR session/swapchain/frame work on the render thread. The OpenGL extension forbids the supplied context being bound on another thread during specified operations. `xrWaitFrame` may run on a different thread, but the first implementation should favor one well-defined frame owner rather than inventing cross-context resource sharing. Preserve vanilla invocation-queue servicing and avoid holding game/render locks while waiting. [OpenGL extension threading contract](https://registry.khronos.org/OpenXR/specs/1.0-khr/html/xrspec.html#khr_opengl_enable-threading).

## Eye images and swapchains

**Existing PZ3D texture IDs cannot simply be submitted to standard OpenXR.** OpenXR creates swapchains and exposes their image handles. For OpenGL, `XrSwapchainImageOpenGLKHR.image` is a runtime-provided GL texture; the origin is bottom-left. [OpenGL swapchain image contract](https://registry.khronos.org/OpenXR/specs/1.1/man/html/XrSwapchainImageOpenGLKHR.html).

Two practical paths:

1. **Initial prototype:** render each eye through PZ3D's adapted scratch FBO, then copy/blit or draw a conversion triangle into the acquired runtime image. PZ3D already blits to the caller's draw FBO, so an add-on-owned FBO attached to the acquired OpenXR image is a promising destination. The present desktop-size assumption must still be replaced. Copy eye one before drawing eye two.
2. **Later simplification:** explicit renderer target injection draws directly into runtime-owned color textures with app-owned depth attachments. Never resize/delete runtime-owned color storage. Existing private allocator and post-effect FBO assumptions make this a larger renderer contract change.

Create two simple 2D color swapchains first, one per eye, with one sample and explicit per-eye extents. Enumerate runtime formats and view configuration recommendations/maxima; choose a supported color format and usage flags. Rendering/blitting needs color attachment usage, and the chosen copy path must respect transfer usage requirements. Do not assume the existing `GL_RGBA8` output has the same transfer/color-space behavior as an sRGB runtime format. Test gamma with a known color ramp; if needed use a shader conversion rather than an unexamined bit copy. A color-only projection layer is sufficient initially; depth composition is optional. [Swapchain creation](https://registry.khronos.org/OpenXR/specs/1.1/man/html/xrCreateSwapchain.html), [downloaded LWJGL `XrSwapchainCreateInfo`](../external/openxr/sources/org/lwjgl/openxr/XrSwapchainCreateInfo.java).

One OpenXR frame consists of event/state handling, `xrWaitFrame`, `xrBeginFrame`, one paired `xrLocateViews`, acquire/wait on needed swapchain images, render/copy, release, and `xrEndFrame` containing one projection layer with two `XrCompositionLayerProjectionView` entries. Honor `shouldRender=false`, valid pose flags, session READY/STOPPING/LOSS_PENDING and application focus; do not stop normal game updates merely because the headset session is unavailable. Match release/acquire order, and end begun frames correctly on recoverable failures. [Frame timing](https://registry.khronos.org/OpenXR/specs/1.1/man/html/xrWaitFrame.html), [release ordering](https://registry.khronos.org/OpenXR/specs/1.1/man/html/xrReleaseSwapchainImage.html).

The two eye poses and FOVs must use the same predicted display time and reference space. Runtime-provided eye poses include eye separation and potentially orientation offsets; do not invent a hard-coded IPD or use converging/toe-in cameras. `xrLocateViews` can change its prediction on successive calls even for the same time, so retrieve and keep the pair together. [View location](https://registry.khronos.org/OpenXR/specs/1.1/man/html/xrLocateViews.html).

Construct OpenGL asymmetric frusta from each eye's four tangent angles (left/right/up/down), using consistent near/far distances and conventional OpenGL clip depth. PZ3D's near=0.02/far=160 is a starting point, not a VR comfort or scale guarantee. Do not keep ADS narrowing of the headset FOV; the submitted FOV must match the rendered image. [FOV definition](https://registry.khronos.org/OpenXR/specs/1.1/man/html/XrFovf.html).

## Head tracking and body separation

Keep these transforms distinct:

| Transform | Existing source / proposed ownership |
|---|---|
| Player/body position and facing | Existing simulation, `Controller`, `NativeAvatar`, conventional inputs |
| Base camera pivot | `Camera.eyes` / captured `Frame.pivot`; vehicle seat handling postponed for first milestone |
| Mouse/gamepad turn offset | Conventional body/base yaw. Mouse pitch may continue aiming, but should not pitch the entire VR horizon by default. |
| Tracked head pose | OpenXR LOCAL reference space, recentered to base pivot; no changes to player collision position |
| Per-eye view pose | The corresponding `xrLocateViews` result, including eye separation |

Conceptual world-eye transform:

```text
worldEye = worldFromRecenteredXRReference * runtimeReferenceEyePose
clipPosition = eyeProjection * inverse(worldEye) * worldPosition
```

Scale tracked translations into game units consistently, including eye separation. If using runtime eye poses directly, **do not multiply by the headset pose a second time**. Head pose and eye-relative offsets are useful conceptual factors, but `xrLocateViews` already composes them in the selected reference space. Rotation uses full quaternions, including roll. Freeze the body/base part once per stereo pair.

PZ3D uses X/Y horizontal, Z up, and mirrors Y in `ViewMath`. A clean conversion is to work in its reflected GL world `(x,-y,z)`. At base yaw zero, XR right/up/back axes map to GL-world `(0,-1,0)`, `(0,0,1)`, `(-1,0,0)` respectively; equivalently an XR vector maps to `(-z,-x,y)` in that base. Compose base yaw and recentering there, then apply the existing game-to-GL Y reflection to mesh coordinates exactly once. This is a derived mapping, to be checked with labeled axes, known translations, roll tests and stereo screenshots before relying on it.

Do not overwrite `Controller.yaw/pitch` with the headset on every render. That would also affect movement, `NativeLocomotion` aiming, `NativeCombat.camera/muzzle`, interaction targeting, torch aiming and first-person body correction. Keep conventional combat/interaction ownership; head look changes viewing only. Initially suppress the mono center crosshair in the headset or put feedback on the UI panel; do not imply that head gaze controls a mouse-aimed attack.

A seated/standing LOCAL origin with explicit recentering avoids requiring room-scale or floor-height gameplay. Calibrate world-unit/metre scale and base eye height; the vanilla floor multiplier and model scale are not calibration. Small head translation is likely feasible without moving the character, but wall penetration, head/body intersection, model clipping and recentering need tests. Keep third-person orbit, ADS zoom and vehicle camera transitions outside the first acceptance milestone rather than applying an incomplete headset transform to all of them.

## Simplest usable UI

`RetainedRender.enter()` already forces `UIManager.useUiFbo=true` and restores it on leave. `Frame` captures the `UIManager.uiFbo` texture reference; `UiLayer.capture(Texture)` GPU-blits it into private static `texture`, with its own FBO, width and height. `UiLayer.draw` currently stretches it over the desktop using premultiplied-alpha blending (`ONE`, `ONE_MINUS_SRC_ALPHA`). These are concrete native UI-to-texture facilities.

Initial choice: copy the completed UI texture into a third OpenXR swapchain and submit an **`XrCompositionLayerQuad`** at a readable virtual-monitor distance/size, initially view-relative. Set appropriate alpha composition flags consistent with premultiplied pixels. A reference-space fixed panel is also possible; per-eye rendering of a textured plane is a fallback. A full-screen flat overlay pasted identically into each eye is easiest mechanically but does not define a useful finite reading distance. [Quad layer contract](https://registry.khronos.org/OpenXR/specs/1.1/man/html/XrCompositionLayerQuad.html).

Keep UI layout at its existing logical resolution and existing mouse pixel coordinates. A mouse cursor can be rendered into the panel at those coordinates; no VR pointer/controller is necessary. Preserve `FocusState` / `ControlRouting` / `Input.pointerReleased` behavior when inventory opens so mouse motion stops turning the body and resumes normal UI interaction.

There are important capture boundaries:

- `Main.enqueue` is ordered before the new native UI commands, so its UI copy can represent the previously completed UI image. Capture once **after the current UI FBO is completed** for predictable latency; an `EndFrameUI` game-thread hook must enqueue the GPU copy, not execute GL there.
- `Core.EndFrameUI` may draw the software cursor after compositing the UI FBO; hardware/desktop cursors are not captured. Add a cursor draw into the VR panel or a matching overlay in a later authorized prototype.
- `DeviceCaptionLayer`, world text and PZ3D crosshair/interaction dots are not all in `UIManager.uiFbo`. Audit these separately; disable optional diagnostics in the first milestone.
- Run native UI rendering and callbacks once. Do not call `UIManager.render` for each eye: it updates transitions and executes Lua events.

## Runtime/headset compatibility

Target ordinary PC OpenXR through an active runtime that actually exposes `XR_KHR_opengl_enable` and the selected stereo configuration. SteamVR is a reasonable first runtime: Valve documented its OpenGL OpenXR support, and continues to recommend OpenXR. SteamVR is an alternative runtime in this architecture, not an extra layer that must always follow a vendor runtime. [Valve OpenXR announcement](https://store.steampowered.com/news/posts/?appids=250820&enddate=1595641955&feed=steam_community_announcements), [SteamVR page](https://store.steampowered.com/app/250820/SteamVR/).

Other PC headset runtimes can work if their graphics extension, GPU and swapchain capabilities match. OpenXR compatibility alone does not guarantee OpenGL support. No headset/model-specific success is claimed. A standalone headset needs a PC connection/runtime path; this is not an Android port.

Windows loader discovery normally uses an active runtime manifest registration, with defined override mechanisms. The read-only local check found no active 64-bit registration or process override, so runtime setup is a prerequisite for the future test, not a renderer blocker. Do not change global runtime selection automatically. [Khronos loader design](https://registry.khronos.org/OpenXR/specs/1.1/loader.html).
