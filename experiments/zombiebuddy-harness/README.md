# ZombieBuddy stereo capture harness

This local test mod captures **one synthetic left/right pair from the live PZ3D renderer when you press Ctrl+Shift+F10**. It mirrors the left eye for that frame, saves two PNGs and a report, then resumes ordinary PZ3D drawing. No headset, OpenXR session, SteamVR, or launcher flags are needed.

Supported binaries are exactly Project Zomboid **42.20.4**, PZ3D **0.2.2**, and ZombieBuddy **2.3.2**, checked by SHA-256 rather than version strings alone. It supports first-person, on-foot, single-player testing. It is a renderer experiment, not a playable VR mode. Loading and one indoor synthetic stereo capture were confirmed in the user's game on 2026-09-27; see VALIDATION.md for evidence and limits.

Version **0.1.1** fixes the original F8 conflict: F8 opens PZ3D's camera panel and its Lua event can be filtered. Capture now polls **Ctrl+Shift+F10** directly on the render thread, once per press, while the game window is focused. Close the game before replacing the local `PZ3DVRTest` folder with this package; restart and approve the changed JAR if ZombieBuddy prompts. Release the chord before another capture.

## Vanilla UI panel (0.4.0)

When XR is enabled, the vanilla UI is automatically submitted as one transparent, flat, head-following panel in front of the scene. Its center is 1.5 metres ahead, with width at most 2 metres and height at most 1.3 metres, preserving the desktop UI aspect ratio. Layout and keyboard/mouse input remain unchanged. Open inventory and other vanilla windows normally; no extra hotkey or UI configuration is required.

The panel reuses PZ3D's completed `UiLayer` snapshot and copies it once per submitted stereo pair into a separate OpenXR swapchain. UI callbacks are not rerun. It displays the latest completed UI image, which can be one UI refresh behind the current game state. UI refresh rate remains the game's existing setting. The panel is included in SteamVR's Headset Window; the game's own stereo mirror keeps its existing desktop UI overlay.

This first version captures content already in the vanilla UI texture. The OS/software mouse cursor drawn afterward, debug ImGui, and PZ3D world-space captions/crosshair are not added to the VR panel. There is no controller pointer or changed aiming behavior. The panel exists while the first-person XR session is running; it does not extend XR to the main menu or unsupported views. A not-yet-ready UI texture gives a world-only frame, then resumes automatically when available.

Install with the game closed and approve the new JAR if prompted. Enable XR with **Ctrl+Shift+Scroll Lock**, open inventory, and check that text is upright, the transparent regions reveal the scene, and menu updates appear in both eyes. Close/reopen the inventory and toggle XR off/on to check cleanup. The log reports `Vanilla UI panel: ...`, includes `UI_COPY` timings, and reports submitted UI panels on XR shutdown. The user confirmed the UI works in-game with the simulated Headset Window. Physical-headset testing remains pending.

## Timing diagnostics (0.3.2)

Replace the local test-mod folder with `PZ3DVRTest-0.4.0.zip` while the game is closed, then approve the new JAR if prompted. Controls are unchanged. Enable XR with **Ctrl+Shift+Scroll Lock**, remain in the same scene for about 20 seconds, then walk/turn for about 30 seconds. Toggle XR off to flush the final partial report. Reports appear automatically in the game's `console.txt` with `[PZ3D XR Timing]`; there is no extra hotkey.

Each five-second window reports successful stereo submissions per elapsed second (`stereoHz`), XR calls, submitted/skipped/failed counts, the runtime's latest predicted display period, and calls whose total wall time exceeds that period (`overBudget`). This is not a compositor dropped-frame count. `stereoHz` measures application submission, not presentation to the display.

Stages report **average / approximate p95 upper bound / maximum**, in milliseconds:

| Stage | Scope |
|---|---|
| INTERVAL | Spacing between calls into XR, including calls that skip rendering |
| OUTSIDE | Previous XR call completion to next entry: game scheduling, desktop presentation, other work, plus reporting overhead |
| TOTAL | Entire XR call, including waits, eye rendering/copies, and submission |
| WAIT_FRAME / BEGIN_FRAME / END_FRAME | Runtime frame pacing and submission calls |
| LOCATE | Runtime view/head pose lookup and associated setup |
| PREPARE | Pair setup, shared preparation and checks through first eye entry |
| LEFT / RIGHT | Each eye's draw and consistency checks, excluding destination copies |
| MIRROR_COPY | Desktop eye texture copy, per eye |
| XR_IMAGE_WAIT | Swapchain acquire and image wait, per eye |
| XR_COPY_RELEASE | XR blit, GL state restoration/flush and image release, per eye |
| UI_COPY | UI swapchain allocation when needed, texture copy, acquire/wait/release, once per pair |
| MIRROR_PRESENT | Drawing the stereo pair into the desktop framebuffer; excludes the game's later window swap |

All values are CPU **wall-clock elapsed time**, including any driver blocking, not GPU execution timings or pure CPU utilization. Copy stages are per-eye averages, while TOTAL is per pair. These stages do not exhaustively partition TOTAL. The histogram uses fixed memory and 0.25 ms buckets; p95 beyond 511.75 ms is explicitly shown as `>511.75`, while mean and maximum retain the actual duration. Only five-second summaries and a final partial summary are printed; no GPU fences, readback or per-frame disk writes are added. Existing SteamVR compositor logs remain the complementary source of GPU/presentation evidence. Physical-headset latency remains unmeasured.

## OpenXR integration (0.3.1)

Version 0.3.1 moves XR off vanilla time-control/debug-editor keys. Hold Ctrl+Shift and press Scroll Lock to toggle XR; add Alt to recenter. Release Scroll Lock before another action. Changing modifiers while it remains held never triggers a second action.

Install this version with the game closed, replacing the existing local test-mod folder. Approve the updated JAR if ZombieBuddy prompts. The package includes pinned LWJGL OpenXR 3.4.1 bindings and the Windows loader; do not install a separate OpenXR SDK or replace the game's LWJGL libraries.

| Shortcut (hold Ctrl+Shift) | Action |
|---|---|
| Scroll Lock | Start/stop OpenXR with the current runtime |
| Alt+Scroll Lock | Recenter the current headset pose onto the game camera |
| F9 | Toggle the independent synthetic desktop stereo mode |
| F10 | Save a synthetic stereo pair while OpenXR is off |

Start the runtime before enabling XR. With a physical headset, use its working OpenXR runtime. For the current no-headset test, use the workspace's [simulated SteamVR helper](SimulatedRuntime.ps1):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ".\experiments\zombiebuddy-harness\SimulatedRuntime.ps1" -Action Start
```

Run that from the project workspace, then launch the game normally yourself, enter first-person PZ3D on foot, and press **Ctrl+Shift+Scroll Lock**. The helper starts only SteamVR, writes its simulation settings/logs under its own `build` directory (the workspace when run from here), and restores its process-local environment afterward. Close any existing SteamVR session before using it. SteamVR is already the registered OpenXR runtime on this machine; the helper does not change the registry. It was tested with a separate client using that default runtime and no configuration-path override. The helper uses the installed Python interpreter and SteamVR API DLL to keep a non-rendering overlay client connected until `-Action Stop`. This prevents the server's 20-second idle exit while the game loads. The isolated profile also keeps the simulated headset awake and disables the SteamVR dashboard and automatic game theater so they cannot cover the scene. These settings apply only to this simulated runtime. Keep `RuntimeKeepalive.py` and `HeadsetWindow.py` next to the PowerShell script. The keeper adds a normal draggable title bar to SteamVR's simulated Headset Window, including windows recreated later. It preserves the rendering-area dimensions. `-WindowX` and `-WindowY` set its initial position; afterward, drag the title bar normally. A `Status` check reports a missing keepalive.

The log should show `[PZ3D OpenXR] Runtime: SteamVR/OpenXR`, `Session created on existing context`, and increasing `Projection pairs submitted` counts. The desktop shows the actual runtime-view pair. The null driver supplies fixed simulated poses; physical head movement is not expected. Ctrl+Shift+Scroll Lock again tears down the session; F9 still controls the independent desktop preview. After testing, run the same helper with `-Action Stop` (or `Status` to inspect it).

The adapter borrows the game's WGL context, locates both eyes and the head at predicted display time, maps them relative to a recentered game-camera anchor, draws one coherent pair and submits a projection layer. Runtime poses/FOV in layer metadata remain unchanged. It reuses GPU mirror targets and performs no PNG readback in XR mode. The prototype renders at the existing game framebuffer resolution, then scales into each runtime-recommended swapchain extent. This is a temporary rendering-resolution policy, not native-resolution VR optimization.

XR stops on unsupported views, minimization, runtime errors/stopping, or a heartbeat detecting that PZ3D stopped drawing. Failure attempts ordinary rendering without repeating fresh-frame native preparation. The native runtime can block in frame timing/image waits; forced termination or runtime stalls cannot be made interruption-proof by this prototype. No extra graphics context, render thread, or game simulation loop is created.

Head movement changes the visual camera only. Existing keyboard/mouse movement, body aiming, and torch direction remain game-controlled. Scale is provisionally one scene unit per metre; hardware calibration, latency, comfort, gamma, head-related culling/shadow coverage, and performance remain unvalidated. Version 0.4.0 also submits the vanilla UI as the simple head-following panel described above. Use this as a world-rendering test, not a complete VR gameplay interface.

## Continuous desktop stereo preview (0.2.0)

Press **Ctrl+Shift+F9** in first-person PZ3D, on foot, to toggle continuous side-by-side stereo in the **existing game window**. Left eye is on the left; right eye is on the right. Press the same chord again to return to the ordinary view. Both full-aspect images are fitted into the window with black bars, without stretching. Windowed mode is convenient for desktop inspection. No headset or SteamVR is needed.

This mode renders a new stereo pair on each eligible fresh or retained draw and reuses two GPU targets. It performs no PNG encoding or CPU image readback unless you press **Ctrl+Shift+F10** to save a pair. Resizing recreates the targets. Unsupported camera/vehicle contexts use ordinary rendering and release targets when the draw hook next runs; supported draws resume stereo. The preview starts off after each game launch. A draw failure disables it until restart and attempts ordinary retained rendering.

The game UI remains one desktop overlay, so this is a world-rendering preview rather than a VR UI. The same omitted effects listed below stay omitted while stereo is enabled. Two scene views and diagnostic consistency checks add cost; sustained in-game performance is unmeasured. This is not headset-tracked output or a separate floating spectator window. The user confirmed smooth live stereo and consistent scene details in version 0.2.0. Version 0.3.1 has now submitted more than 6,600 stereo projection pairs from the live game to simulated SteamVR, with working output confirmed by the user. Physical-headset testing remains pending.

The log reports `[PZ3D VR Mirror] ON`, the first completed pair, progress every 600 pairs, and `OFF` with the completed count. Close the game before replacing the local mod folder; approve the changed JAR if prompted after restart.

## Install for your test

1. Close Project Zomboid. Extract `PZ3DVRTest-0.4.0.zip` into your local Zomboid mods directory, normally `%USERPROFILE%\Zomboid\mods`. The resulting descriptor should be `mods\PZ3DVRTest\42.20.4\mod.info`, with a sibling `PZ3DVRTest\common` directory. Do not put it in the Steam game directory or replace either existing mod JAR.
2. Enable **ZombieBuddy**, **PZ3D**, and **PZ3D Stereo Capture Test [Java]**, in that order, for a new disposable single-player test save. Keep other mods disabled for this first test. ZombieBuddy and PZ3D remain the existing installations.
3. The harness JAR is unsigned local development code. If ZombieBuddy presents its Java-mod approval dialog, review and approve this particular `PZ3DVRTest.jar`. The adjacent package `SHA256.txt` identifies the built JAR. No preload permission or global policy change is required. If your loader policy blocks unsigned code outright, the harness will remain unavailable; the package does not bypass that policy.
4. Launch the save yourself. Enter PZ3D with **Insert**, use first person, and remain on foot. Look at a nearby object with more distant scenery behind it.
5. Press **Ctrl+Shift+F10 once**. The capture can briefly stall while PNGs are written. Watch the console for `[PZ3D VR Test] Captured stereo pair:` and its directory. A Ctrl+Shift+F10 request made before entering first person stays pending until a supported draw occurs.

Outputs are under the game's configured cache directory, normally:

```text
%USERPROFILE%\Zomboid\PZ3D-VR-Test\<timestamp-id>\
    left.png
    right.png
    capture.properties
```

The properties file records the scene version, frame generation, prepared body/palette fingerprint, eye positions/matrices, one preparation, two copies, and successful completion of the extra lease bracket. It distinguishes failed captures from successful image writes. These counters are diagnostic evidence, not a claim of visual correctness.

Compare the PNGs: nearby objects should move horizontally more than distant ones; actors should be in the same animation pose. The capture intentionally omits sky rendering, outlines, contact-shadow overlays, tracers, crosshair, captions, and chunk debug. These features remain available on ordinary frames. Existing game UI is not included in the eye PNGs.

After a successful capture you can press Ctrl+Shift+F10 again. After a capture failure the harness disables further captures until restart, records the error, and attempts ordinary retained-style drawing without repeating fresh native preparation. Preserve the entire capture directory and the relevant `console.txt` lines for review.

To remove: close the game, disable the test mod, and remove its local `PZ3DVRTest` folder. Restarting removes the in-memory transformations. Captures can be kept or deleted separately. No game/Workshop files are replaced by this package.

## Build and verification

In this source workspace:

```powershell
.\experiments\zombiebuddy-harness\Build.ps1
.\experiments\zombiebuddy-harness\Test.ps1
```

The builder produces `dist\PZ3DVRTest-0.4.0.zip`, an unpacked `dist\PZ3DVRTest` folder, and `dist\SHA256.txt`. It uses the portable JDK and copied reference JARs already present. It never installs the mod or launches the game. `Test-XR.ps1 -Mode xr -NullRuntime` exercises the packaged XR backend in isolation after `Test.ps1`; `-Mode missing` checks unavailable-runtime fallback. Test fixtures and transformed proprietary reference classes are excluded from the mod JAR.

The tests exercise real JVM retransformation with an original synthetic renderer, including all-target activation, mismatch rollback, inactive rendering, one-shot requests, unsupported views, recursive entry protection, success/failure reports, lease cleanup, and capture failure isolation. A separate process defines and retransforms the three actual copied PZ3D classes **without initializing them, constructing a Frame, or invoking any game/mod entry point**. A standalone hidden OpenGL context tests the real capture helper: separate eye copies, image orientation, PNG writing, and texture/framebuffer/pixel-buffer state restoration.

These checks do not replace your in-game test. The original package loaded through ZombieBuddy successfully, but its F8 handler produced no capture request or images. The corrected trigger subsequently produced a live 2560x1440 stereo pair with one preparation and two copies; both images were visually inspected. No physical-headset validation is claimed.

The source workspace has `experiments/zombiebuddy-harness/VALIDATION.md` with the test counts and local evidence paths.

## Implementation and boundaries

`Main` obtains the existing ZombieBuddy instrumentation handle using the same loader field used by PZ3D's own `ChunkProbe`. It verifies the three JARs from their actual code-source locations, then installs one schema-preserving transformer for `Renderer$Frame`, `StreamFade`, and `TreeRenderer`. Activation requires all three transformations to succeed. Failed installation removes the transformer and retransforms the targets to roll back its changes.

Incoming class bytes are compared with normalized originals, preserving executable instructions while ignoring debug/stack-map attributes and JVM constant-pool/method ordering. Mismatches visible to this transformer disable captures. This cannot detect a different transformer installed later that changes bytes after this transformer has seen them; keep the first test limited to the three listed mods.

An enabled live mirror or a pending capture intercepts `Frame.draw`; the original `Frame.render`/retained consumer and frame acceptance remain in place. The bridge's active-pair guard allows the inner draw to reach the once-per-pair/per-eye adapter without recursive capture. Native simulation, producer capture, and UI are not replayed. On a capture exception, the adapted draw records the failure instead of calling PZ3D's global failure handler; ordinary draws retain that handler.

Eye separation is 0.064 **PZ3D scene units**, not calibrated metres. The test uses the original camera direction/FOV and synthetic parallel-eye offsets. OpenXR pose conversion and swapchain submission are now implemented experimentally; controller UI interaction, hardware calibration, optimized pacing, broad live resource-lifetime confidence, and performance measurement remain later work.
