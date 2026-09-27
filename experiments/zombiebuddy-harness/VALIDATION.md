# Harness validation — 2026-09-27

The package was built and tested in the workspace. The agent did not launch Project Zomboid or install the harness. The user installed and launched version 0.1.0; its live log confirms successful loading, but no capture was requested. Version 0.1.1 subsequently captured a live stereo pair in the user-run game at 12:36:17 EDT.

| Check | Result |
|---|---|
| Shared adapter bytecode | All three copied PZ3D target classes verify with the JDK classfile verifier |
| Harness lifecycle | 23 checks passed using real JVM instrumentation and original synthetic fixtures |
| Actual copied binaries | Three supported classes defined without initialization and successfully retransformed under `-Xverify:all` |
| Continuous mirror lifecycle | 18 checks passed: toggling/focus, repeated frames, reuse, resize, unsupported views, capture coexistence and failure cleanup |
| Real OpenGL capture and mirror | 25 checks passed on NVIDIA RTX 4090 using a hidden standalone GLFW compatibility context |
| In-game ZombieBuddy loading | Versions 0.1.0 and 0.1.1 loaded successfully |
| Live world stereo images | First indoor pair captured and visually inspected successfully; broader scene/animation coverage remains |

Lifecycle checks include deliberate incompatible bytecode and second-eye copy failure. Their logged errors are expected negative tests. The mismatch rolls back the installation; the copy failure produces a failed report, releases ownership/targets, avoids PZ3D's global failure handler, and resumes `draw(false)` to avoid repeating native preparation. JOML emits its existing Java 25 Unsafe deprecation warning.

OpenGL checks verify persistent red/blue eye images despite scratch-target reuse, green top/yellow bottom orientation, PNG dimensions, allocation bindings, separate read/draw framebuffer restoration, pixel pack/unpack buffers and pack layout, and scissor/sRGB enable restoration. This is the actual packaged `EyeCapture` code, independent of the CPU fixture stand-in.

Local evidence from the final full harness run:

- [Lifecycle log](build/runs/20260927-130656-632/lifecycle.log).
- [Actual copied binary retransformation](build/runs/20260927-130656-632/real-binary.log).
- [GPU test log](build/runs/20260927-130656-632/gpu.log).
- [GPU left image](build/runs/20260927-130656-632/gpu/left.png) and [right image](build/runs/20260927-130656-632/gpu/right.png).

All evidence and built packages are local ignored artifacts. The source, build scripts, and original test fixtures remain reviewable. Generated proprietary target class files and test-agent/stub classes are excluded from the distributed mod ZIP.

## Live input diagnosis and correction

The user's console.txt (2026-09-27, inspected at 12:25) reported the harness Ready message and successful main invocation. No capture request or output directory was present. F8 is owned by PZ3D's camera panel, and Input.blockEvent filters owned Lua key events. Version 0.1.1 removes the Lua handler and polls Ctrl+Shift+F10 through GLFW from the existing render-thread hook. Tests cover missing modifiers, focus loss/refocus, held keys, release/repress, both modifier sides, and pending unsupported views. The Lua file remains as a comment-only replacement to overwrite the previous handler during extraction.

## First successful live capture

User-operated version 0.1.1, 2026-09-27 12:36:17 EDT. Read-only inspection of the game log found the corrected Ready message, a capture request and success at frame 4661, with no capture exception. Output directory: `%USERPROFILE%\Zomboid\PZ3D-VR-Test\20260927-123617-277-c89f0bc5`.

The report records `STEREO_PAIR_CAPTURED`, `preparations=1`, `copies=2`, `leaseBracketCompleted=true`, scene version 44, generation 2, and a fresh frame. Both PNGs decode and show upright 2560x1440 indoor views. Eye positions differ by 0.063999709 scene units with zero vertical offset, matching the requested 0.064 separation. Distinct view matrices and visible depth-dependent horizontal parallax support a successful synthetic stereo capture. The nearby hands/bat shift substantially more than the distant doorway; no gross blank-eye or orientation failure is visible. This is visual inspection of one static pair, not a comfort, animation-coherence, physical-scale, performance or all-scene validation. OpenXR submission and headset validation remain false.

Image SHA-256 values:

- Left: `114c5316cec46f9241232c91066dc3eaba63fc794fd69d6871333d3cbb026c43`.
- Right: `adaf18467b2f1b4e1c3f035491de734ebbfcd139cbde3d3e97e4aee8bdd727d8`.

## Version 0.2.0 live desktop mirror

Workspace tests passed for continuous GPU-only side-by-side rendering in the existing game window. [Mirror lifecycle log](build/runs/20260927-130656-632/mirror.log) covers fresh/retained draws, toggle edges/focus, target reuse, resize, suspension/resumption, PNG capture while mirroring, and injected second-eye failure with ordinary-draw fallback and full target cleanup. The real OpenGL test verifies red-left/blue-right placement, black letterboxing, restored color mask/clear color/framebuffer/enables, and three subsequent pairs using the same targets. The existing 23 one-shot lifecycle checks and actual three-class retransformation also pass.

The user subsequently confirmed continuous mode works smoothly in-game, with distinct eye positions and consistent scene details. No measured FPS or hardware-headset result was supplied. This validates the helper and synthetic lifecycle, not continuous live-world resource consistency, gameplay UI usability, or sustained performance. The agent did not install the package or launch the game.

## Version 0.3.0 OpenXR integration

The package now includes an opt-in OpenXR session backend, runtime eye/head pose conversion, asymmetric projections, recentering, same-context swapchain copies, desktop mirroring and owner-thread teardown. Live game integration remains pending the user's test. The agent did not launch the game or modify installed mods.

Final verification on 2026-09-27:

| Check | Result |
|---|---|
| Existing capture / desktop mirror / GPU checks | 23 / 18 / 25 passed |
| XR game glue with synthetic renderer/runtime | 14 passed: opt-in, unavailable runtime, pair coherence, target reuse, render skipping, recenter, toggle, unsupported view, runtime loss and inactivity heartbeat |
| XR camera math | 39 passed: identity agreement with existing stereo math, axis conversion, translation, yaw, roll, recenter, asymmetric FOV |
| Actual copied PZ3D classes | All three retransformed and verified without initialization or game/mod entry points |
| Packaged XR backend with simulated SteamVR | 371 assertions, 120 submitted projection pairs across two session lifetimes |
| Partial-pair callback failure | A zero-layer frame ended successfully; subsequent projection submissions succeeded |
| Missing-runtime test | Expected initialization failure, caller GL context preserved and subsequent desktop GL rendering succeeded |
| Simulated runtime helper | Start/status/stop passed; separate default-runtime client connected without config-path overrides |
| In-game XR / physical headset | Not yet tested |

Evidence: [XR glue](build/runs/20260927-130656-632/xr-lifecycle.log), [camera math](build/runs/20260927-130656-632/xr-camera.log), [final simulated runtime](build/xr-runs/20260927-130755-140-xr/stdout.log), [missing runtime](build/xr-runs/20260927-130755-074-missing/stdout.log), [default-runtime client](build/xr-runs/20260927-130503-278-xr/stdout.log). The runtime uses fixed null-driver poses; successful submission does not verify binocular comfort, physical tracking, color calibration, or live PZ3D frame scheduling under XR pacing. Session states reached READY/SYNCHRONIZED/VISIBLE/FOCUSED. Tests preserved the caller context across destruction and recreation. The simulated runtime was stopped after testing.

The shipped backend deliberately keeps the process-wide LWJGL XR loader alive, since other components may share it. It destroys its own swapchains, spaces, session and instance. Game UI is desktop-only. One scene unit per metre and game-resolution rendering with scaling to recommended swapchain sizes are provisional policies.

## Version 0.3.1 input correction

Vanilla's installed shared/keyBinding.lua and the user's Zomboid/Lua/keys.ini assign F6 to Fast Forward x3 (LWJGL2 key 64). Vanilla IngameState also hard-codes F7 (65) for debug editors, including Ctrl and Shift combinations. Scroll Lock (LWJGL2 70 / GLFW 281) has no assignment in those bindings or the vanilla/PZ3D input paths inspected. Version 0.3.1 uses Ctrl+Shift+Scroll Lock to toggle XR and Ctrl+Shift+Alt+Scroll Lock to recenter. Neither F6 nor F7 is read as an XR trigger.

The physical Scroll Lock key is latched until release, preventing modifier transitions from turning a recenter press into a toggle. Both Alt keys are accepted. The source, package metadata, startup messages, runtime helper, and instructions agree on these shortcuts.

The complete local suite passed: 23 capture, 18 live mirror, 21 XR glue/input, 39 camera-math, 25 GPU checks, and retransformation of all three actual copied target classes. [XR input/lifecycle evidence](build/runs/20260927-131951-225/xr-lifecycle.log). The XR backend itself is unchanged; the previous native runtime validation still applies. No installed mods, game bindings, or running game were modified.

## Simulated runtime lifetime fix

The user's 0.3.1 game log confirmed that Ctrl+Shift+Scroll Lock reached the XR hook twice; both calls failed at xrCreateInstance with result -2. The 13:46:05 simulated server log explicitly recorded shutdown after 20 seconds without a client (13:46:26). Steam's xrclient_ProjectZomboid64 log reported HmdNotFound from its client-side presence check. The installed mod hash matched the expected 0.3.1 artifact.

SimulatedRuntime.ps1 now starts RuntimeKeepalive.py, which connects through the installed OpenVR API as a non-rendering overlay client (no overlay window or scene submission). It exits on a stop marker or the original server process ending. Stop verifies the keeper process start time, signals graceful exit, then stops owned SteamVR processes. A trial with SteamVR's monitor was discarded because it restarted the server and launched room setup; those trial processes were stopped.

A longer test also exposed simulated-headset standby: the installed SteamVR defaults place pauseCompositorOnStandby under `power`, not `steamvr`. The isolated profile now sets it in the correct section and extends screen/controller sleep to 24 hours. Game/mod code and bindings are unchanged.

Final runtime started at 13:54:17 and remained available without a scene client beyond the old idle timeout. A separate default-runtime client connected at 13:55:00, reached VISIBLE/FOCUSED, and submitted 120 stereo projection pairs across two sessions (371 checks passed): [delayed-start smoke evidence](build/xr-runs/20260927-135500-427-xr/stdout.log). The corrected runtime is intentionally left running for the user's immediate in-game retry. This remains a standalone backend check, not proof of in-game XR success.

OpenVR application-type semantics are defined in the [official header](https://github.com/ValveSoftware/openvr/blob/master/headers/openvr.h). Background clients explicitly do not keep SteamVR running, so the keeper uses the non-scene overlay type instead.

## Draggable simulated headset window

HeadsetWindow.py targets only the exact `Headset Window` title owned by the installed SteamVR `bin/win64/vrcompositor.exe`. It replaces the popup frame with a caption, system menu and minimize button, then applies SWP_FRAMECHANGED without changing activation or stacking. DPI-aware frame sizing preserves the client extent. The keepalive checks once per second and applies the frame only when missing, leaving manual positioning alone.

Applied to the user's existing compositor window on the interactive desktop (PID 15364, HWND 658038). Readback verified style `0x16ca0000` and an unchanged 1280x720 client area. A subsequent WM_NCHITTEST query in the caption returned HTCAPTION (2), confirming Windows recognizes a draggable title bar. No game/runtime restart was performed. Future helper starts apply it automatically; the currently running older keeper was left uninterrupted.

API references: [SetWindowLongW](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowlongw), [SetWindowPos](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-setwindowpos).


## In-game OpenXR confirmation and dashboard fix

The user confirmed working game output in SteamVR's Headset Window. The live game console recorded two 940x940 swapchains, VISIBLE session state, and increasing projection-pair counts through 6,600 before the game exited normally. This confirms in-game submission to the simulated runtime; physical tracking and headset comfort remain untested.

The simulated profile now explicitly disables dashboard.enableDashboard and dashboard.autoShowGameTheater. Disabling startDashboardFromAppLaunch alone did not prevent Steam from starting its system UI. The game was already closed when the isolated runtime was restarted. No mod JAR changes or reinstall are required.

Read-only OpenVR queries against the restarted runtime returned enableDashboard=false, autoShowGameTheater=false, and IsDashboardVisible=false. Evidence: build/simulated-runtime/20260927-141519-343/dashboard-verification.json; query source: build/verify_dashboard.py. The first probe from the restricted desktop failed (121) and a subsequent client stalled; those probe processes were cleared and verification succeeded after restarting the isolated runtime on the interactive desktop. The helper is left running for the user's next game test; gameplay after this settings change is not yet verified. Function signatures/slots were checked against Valve's official openvr_capi.h, IVRSettings_003 and IVROverlay_028.


## Version 0.3.2 frame timing instrumentation

Adds render-thread wall-clock summaries every five seconds and on session close: successful stereo submission rate, skipped/failed counts, call intervals, time outside XR, runtime waits, shared preparation, separate eye draws, desktop/XR copies, and frame submission. A default no-op Sink.beginEye observer marks the existing eye-loop boundary without changing transforms or rendering order. Fixed-size histograms provide 0.25 ms p95 upper bounds and explicit overflow; no GPU synchronization/readback was added. CPU wall time is not GPU execution time, and submission rate is not display refresh.

Test.ps1 passed: 23 capture lifecycle, 18 live mirror, 21 XR glue/input, 39 camera-math, 25 real OpenGL capture checks, all three actual copied-class retransforms, and 13 new deterministic timing checks covering rates, skipped/failed accounting, spikes, overflow, window reset and timed/final flush. Evidence: build/runs/20260927-142557-270/. Native successful-session testing of this version was deferred because the user's game is running; no competing scene client or game restart was attempted. New in-game timing results remain pending.

The packaged missing-runtime test also passed both checks (expected initialization failure, caller context preserved): build/xr-runs/20260927-142704-567-missing/. Archive CRCs, version, timing class, exclusion of project test classes and SHA-256 were verified. JAR SHA-256: 53a7817cfeeec1f18c11fac6fbfea128857ea0c50c4af9f7927864071887f2a1.


## First live 0.3.2 timing review

Latest user test reached FOCUSED and logged 19 full timing windows totaling approximately 95.19 seconds: 4,213 submitted pairs, zero skipped/failed calls, approximately 44.26 submissions/second (window range 37.66?54.36). All recorded calls exceeded the runtime's 11.11 ms period. Weighted wall-clock averages: XR total 19.73 ms, outside XR 2.87 ms, preparation 3.51 ms, left eye 6.54 ms, right eye 6.29 ms, xrWaitFrame 0.02 ms. Stereo frame intervals averaged 22.60 ms; largest was 119.58 ms in the startup window. Destination-copy averages are per eye.

The matching SteamVR scene process (PID 14396, 14:39:15?14:40:52) reported application CPU 22.124 ms / GPU 2.837 ms, compositor CPU 0.376 ms / GPU 0.145 ms. This supports a render-thread-side bottleneck rather than xrWaitFrame pacing or GPU saturation; wall time includes driver waits and prototype consistency checks and does not isolate pure renderer CPU work. Null-driver dropped/present counters are not treated as literal physical-display statistics. Separate Knoxify Lua exceptions were present and may add overhead; their contribution is unmeasured.

Filtered timing evidence and aggregate calculations saved to build/timing-review/latest-timing.txt and latest-summary.json. No code or runtime settings were changed during this review. Next profiling should separate per-eye renderer work from diagnostic consistency checking; use a minimal-mod comparison to isolate the unrelated Lua errors.


## Version 0.4.0 vanilla UI quad

Reuses the completed, version-gated PZ3D UiLayer texture after the stereo pair. Cached reflection reads only its texture/width/height fields, verified against the copied binary without initialization. Copies the premultiplied-alpha image once into a separate XR swapchain, then submits a VIEW-space quad after the world projection layer. Panel is centered 1.5 m ahead, at most 2 m wide / 1.3 m high with preserved aspect ratio. Copy preserves GL read/draw FBO bindings and scissor/sRGB enables. Runtime dimensions limit allocation; resize replaces owned swapchains. A missing snapshot omits UI for that frame. Frame failure submits no layers; all owned resources are released with the session.

The game still updates its native UI once. The snapshot can lag by one UI refresh; cursor/ImGui drawn outside the texture and PZ3D world-space annotations are excluded. Input is unchanged. Main-menu VR and controller interaction are outside this change. UI_COPY wall timing is separate from eye copies.

Test.ps1 passed: 23 capture, 18 mirror, 24 XR glue (including one UI copy per pair, absent snapshot and recovery), 39 camera, 25 GPU capture, 13 timing checks and all three actual-class retransforms plus UiLayer field checks. Evidence: build/runs/20260927-151736-942/.

Packaged native backend passed 883 checks against simulated SteamVR, submitting 120 projection pairs and 100 UI panels across two session lifetimes. Tests exercise premultiplied-alpha flags, both-eye visibility, aspect and placement, 320x240 to 160x120 resizing, world-only frames, GL binding/enables preservation, out-of-frame rejection, partial-eye and post-UI injected failures, and context reuse after teardown. Evidence: build/xr-runs/20260927-151842-702-xr/. Initial native attempt failed because the earlier simulated server was no longer running; the workspace helper was started and the test passed. No game or existing mod entry point was launched, and no installed files were modified. Runtime is left available for the user. Actual in-game UI appearance and physical-headset comfort remain unverified.

Composition contract: https://registry.khronos.org/OpenXR/specs/1.1/man/html/XrCompositionLayerQuad.html . Premultiplied alpha is retained (BLEND_TEXTURE_SOURCE_ALPHA without UNPREMULTIPLIED_ALPHA).


## User confirmation of 0.4.0 UI

The user confirmed that the vanilla UI panel works in-game with the simulated Headset Window. Physical-headset validation remains pending.
