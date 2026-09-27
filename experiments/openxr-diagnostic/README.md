# Standalone OpenXR diagnostic

First prototype milestone, tested on 2026-09-27: submit distinct left/right OpenGL images to SteamVR OpenXR without a physical headset. This is an independent Java program, with no Project Zomboid classes, mod loader, or game installation writes.

The second milestone now adds a [3D stereo scene and camera-math checks](SCENE.md). Run it with `-Content scene`; the original charts remain available.

## Run

From the workspace root, with SteamVR closed:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\experiments\openxr-diagnostic\Run.ps1 -Mode xr -NullRuntime -Seconds 15
```

The desktop window shows a red **L** and blue **R**, green at the top and yellow at the bottom. Escape closes the diagnostic. The script starts a simulated SteamVR headset and stops its own runtime processes afterward. It refuses to start that profile while another SteamVR session or tool is running.

Other modes:

```powershell
# Desktop rendering only; does not initialize OpenXR or start SteamVR.
powershell -NoProfile -ExecutionPolicy Bypass -File .\experiments\openxr-diagnostic\Run.ps1 -Mode preview

# Discover the runtime and synthetic HMD, without creating graphics swapchains.
powershell -NoProfile -ExecutionPolicy Bypass -File .\experiments\openxr-diagnostic\Run.ps1 -Mode probe -NullRuntime
```

`-Hidden` hides the diagnostic's desktop window for automated tests. `-SteamVR 'D:\path\SteamVR'` supports other Steam library locations. `-Java 'C:\path\bin\java.exe'` selects a different Java 17+ runtime; the bundled PZ Java 25.0.1 was tested as a standalone JVM. Omitting `-NullRuntime` uses the caller's normal OpenXR environment for an eventual physical-headset test; that path has not yet been validated.

## Build and dependencies

Dependencies are already present locally. For a fresh workspace:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\experiments\openxr-diagnostic\Setup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\experiments\openxr-diagnostic\Build.ps1
```

Setup downloads a portable Temurin 25 JDK and LWJGL 3.4.1 core, GLFW, OpenGL, and OpenXR libraries/native JARs. URLs and SHA-256 values are pinned in [dependencies.json](dependencies.json). No global JDK installation or PATH edit is needed. `Run.ps1` checks library hashes and compiles before each run. The compiler emits Java 17-compatible bytecode. Sources and dependency downloads are independent of the game archive.

SteamVR supplies the OpenXR runtime; LWJGL supplies the loader and Java bindings. A separate system-wide OpenXR SDK installation is unnecessary for this milestone.

## Results and isolation

Each invocation writes a timestamped directory beneath `runs/` containing stdout/stderr, `result.json`, and, for rendering modes, `left.png` and `right.png`. Null-headset runs also contain SteamVR config/log files and a record of runtime processes stopped by the wrapper. Exit codes: **0** success, **1** failure, **2** setup required, **3** native-call watchdog timeout. A timeout produces `runner-result.json`; an application crash may leave only console logs.

The wrapper sets `XR_RUNTIME_JSON`, `VR_CONFIG_PATH`, `VR_LOG_PATH`, and `VR_PATHREG_OVERRIDE` only for its process and children, restoring inherited values afterward. Each null profile is disposable and separate from the normal Steam config. It launches `vrserver` directly with the monitor, dashboard, and Home disabled to avoid first-run room setup. The Java session requests a normal OpenXR shutdown; the wrapper then terminates the isolated runtime helper processes it owns. Close the window or wait for completion rather than forcibly terminating the PowerShell wrapper, which can prevent its cleanup.

These overrides isolate test configuration, **not every SteamVR side effect**. SteamVR registered itself as the system OpenXR runtime during the initial test startup. The scripts do not write that registry value or change the normal OpenVR path registration. The ordinary Steam `steamvr.vrsettings` remained absent on this machine after testing.

## What this proves

See [validation results](VALIDATION.md). A real OpenXR instance/session, WGL binding, two runtime-owned swapchains, stereo view location, image acquisition/wait/release, and projection-layer submission work on this PC with the null driver. The screenshots are read back from the eye render targets before submission; they are not compositor-output or headset captures.

The default images are diagnostic charts. The optional 3D scene additionally tests projection and pose math, depth rendering, and stereo landmark positions. Hardware head movement, controllers, recentering, UI, game integration, latency, and comfort remain untested. Null-driver tracking flags describe simulated poses. Frame counts are evidence of submission, not a performance benchmark. No OpenXR API validation layer was enabled.

The next milestone now has an [offline-tested PZ3D renderer adapter](../pz3d-adapter/README.md). A loader-backed harness and user-run in-game test remain necessary before trusting its two-view rendering.

API references: [LWJGL's OpenXR/OpenGL example](https://github.com/LWJGL/lwjgl3/blob/3.4.1/modules/samples/src/test/java/org/lwjgl/demo/openxr/HelloOpenXRGL.java), [OpenXR specification](https://registry.khronos.org/OpenXR/specs/1.1/html/xrspec.html), and the installed SteamVR null-driver defaults. The implementation here is a separate minimal diagnostic.
