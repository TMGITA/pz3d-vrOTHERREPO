# OpenXR dependencies

The release includes upstream license texts in `licenses/`: `LWJGL-LICENSE.md` (LWJGL 3.4.1) and `OpenXR-SDK-LICENSE.txt` (Apache 2.0). These apply to the bundled dependencies, not a license grant for game/mod material or the original prototype code.

This local prototype bundles the existing pinned LWJGL OpenXR 3.4.1 Java bindings and Windows x64 native-loader artifact. The game's LWJGL core, GLFW, OpenGL, and JOML are reused and are not redistributed in the mod JAR. No proprietary game or PZ3D class is packaged.

| Artifact | SHA-256 |
|---|---|
| lwjgl-openxr-3.4.1.jar | `184ff11f6140bc48b722b5dfadb1a9611dd9f7b4f18863f627581cdda8a055f2` |
| lwjgl-openxr-3.4.1-natives-windows.jar | `2884e3449ac10e366cf80f9a2676e822d54bda7f50ff9a61d2d2d32821ee7ffd` |

Sources and licensing: [LWJGL](https://github.com/LWJGL/lwjgl3), [LWJGL license](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md), [Khronos OpenXR SDK](https://github.com/KhronosGroup/OpenXR-SDK), [OpenXR SDK license](https://github.com/KhronosGroup/OpenXR-SDK/blob/main/LICENSE). Original runtime metadata and native-loader hash resources are retained in the bundle. See `experiments/openxr-diagnostic/dependencies.json` for the pinned Maven download URLs.

Implementation references: [session destruction](https://registry.khronos.org/OpenXR/specs/1.1/man/html/xrDestroySession.html), [swapchain wait semantics](https://registry.khronos.org/OpenXR/specs/1.1/man/html/xrWaitSwapchainImage.html), [OpenGL binding threading](https://registry.khronos.org/OpenXR/specs/1.1/man/html/XR_KHR_opengl_enable-threading.html). The backend destroys sessions on the owner thread, ends begun frames even when rendering fails, and never treats a swapchain wait timeout as image ownership.
