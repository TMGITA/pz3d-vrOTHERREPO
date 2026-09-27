# 3D stereo scene — milestone 2

Run from the workspace root with SteamVR closed:

```powershell
.\experiments\openxr-diagnostic\Run.ps1 -Mode xr -Content scene -NullRuntime -Seconds 30
```

The desktop mirror shows the two eyes side by side. The scene contains a metre-spaced floor grid, three shaded cubes at different distances, and cyan/magenta landmarks at 1.5 and 4 metres. Escape closes it. `-Content charts` retains the original diagnostic.

For a desktop-only comparison without starting SteamVR:

```powershell
.\experiments\openxr-diagnostic\Run.ps1 -Mode preview -Content scene
.\experiments\openxr-diagnostic\Run.ps1 -Mode preview -Content scene -PreviewPose turned
```

The turned preset combines translation and quaternion rotation; it is a fixed synthetic camera pose, not tracked headset movement. Preview uses deliberately asymmetric eye frusta. Synthetic poses are rejected outside scene preview so they cannot silently replace runtime poses during submission.

## Rendering contract

`StereoMath.java` uses column-major OpenGL matrices, right-handed coordinates, metres, -Z forward, and depth range [-1,1]. Each runtime eye gets a projection from its four FOV angles and a view matrix from the inverse of its complete position/quaternion pose. No additional eye offset is applied to the runtime views. The projection layer receives those same unmodified poses and FOVs. Conventions follow [Khronos' projection helper](https://github.com/KhronosGroup/OpenXR-SDK/blob/main/src/common/xr_linear.h) and [projection-view contract](https://registry.khronos.org/OpenXR/specs/1.0/man/html/XrCompositionLayerProjectionView.html).

The scene is positioned once at the initial eye midpoint and oriented with the initial left-eye quaternion. It stays fixed in LOCAL space afterward. This is a convenient initial scene placement, not recentering or floor calibration. On this null driver, the initial quaternion is (0,0,-1,0), a 180-degree roll; retaining that rotation explains the apparently reversed X eye positions seen in milestone 1. A real headset with canted eyes may need a head-centred anchor instead.

`Scene.java` draws immutable geometry with compatibility-profile OpenGL. One application-owned depth renderbuffer is cleared and reused sequentially for each eye. Runtime color textures are acquired, rendered, captured/mirrored, and released independently. Depth is used locally for occlusion; no OpenXR depth layer is submitted. There is no simulation or animation update between the two eyes. This establishes a minimal drawing contract, not a renderer ready to inject into PZ3D's GL state.

## Validation

On 2026-09-27, compile passed without warnings. Thirty analytic checks passed: asymmetric frustum boundaries, near/far depth mapping, rigid-transform inversion, independent yaw/pitch/roll direction checks, and distance-dependent stereo disparity.

GPU captures passed twelve landmark checks across neutral preview, translated/rotated preview, and SteamVR OpenXR. Each eye's cyan and magenta image centroids were within one pixel of their expected projected positions. This connects the tested CPU math to the actual OpenGL output and catches matrix upload, image orientation, and eye-selection errors. It is not a compositor-output check.

The OpenXR run used PZ's Java 25.0.1 as a standalone JVM, submitted **661 stereo frames / 1,322 eye images**, reached FOCUSED, and exited cleanly. No SteamVR processes remained. No game classes were loaded, and Project Zomboid was not launched.

Local evidence:

- [XR result](runs/20260927-114135-947-xr/result.json), [left capture](runs/20260927-114135-947-xr/left.png), [right capture](runs/20260927-114135-947-xr/right.png), [GPU checks](runs/20260927-114135-947-xr/scene-checks.json).
- [Neutral preview checks](runs/20260927-114123-045-preview/scene-checks.json).
- [Translated/rotated preview checks](runs/20260927-114133-560-preview/scene-checks.json).

To verify a new scene capture:

```powershell
.\experiments\openxr-diagnostic\Verify-Scene.ps1 -RunDirectory '<path printed by Run.ps1>'
```

The verifier expects visible, unobscured markers and is intended for these controlled test poses. Hardware tracking, stereo comfort, motion latency, and game-renderer consistency remain unvalidated. The default preview uses RGBA8 and the tested XR runtime uses sRGB8-alpha8, so their saved images have different brightness; colour matching is not part of this milestone.
