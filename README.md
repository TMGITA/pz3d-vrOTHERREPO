# PZ3D VR prototype

Experimental OpenXR support for Project Zomboid through PZ3D and ZombieBuddy. Current prototype: **0.4.0**, Windows x64.

Working features include live stereo rendering, OpenXR head-pose camera mapping, a desktop stereo preview, a simple vanilla UI panel in VR, and frame-timing diagnostics. Live game output and the UI panel have been confirmed with SteamVR's simulated headset. Physical-headset tracking, comfort, and world scale still need validation. Controller input is not implemented; movement and interactions use the existing keyboard/mouse controls.

## Requirements

The harness checks exact binary hashes for these versions:

- Project Zomboid **42.20.4**
- PZ3D **0.2.2**
- ZombieBuddy **2.3.2**

Obtain these separately. This repository contains the prototype source and synthetic test fixtures, not copies of the game or either dependency. Start with a disposable single-player save, in first person and on foot.

## Using the prototype

See the [mod instructions](experiments/zombiebuddy-harness/README.md) for installation, runtime setup, controls, and limitations. This initial repository publishes source; build the ZIP using the steps below.

| Shortcut | Action |
|---|---|
| Ctrl+Shift+Scroll Lock | Toggle OpenXR |
| Ctrl+Shift+Alt+Scroll Lock | Recenter |
| Ctrl+Shift+F9 | Toggle independent desktop stereo preview |
| Ctrl+Shift+F10 | Capture stereo PNGs while OpenXR is off |

OpenXR automatically includes the vanilla UI as a transparent, head-following panel. The desktop cursor is not included yet. Desktop stereo needs no VR runtime; headset output needs a working OpenXR runtime. A SteamVR simulated-headset helper is included for development without hardware.

## Building and testing

Use Windows x64, PowerShell, and Python 3. Run this from the repository root to download the pinned workspace-local JDK 25 and LWJGL dependencies:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File experiments/openxr-diagnostic/Setup.ps1
```

Copy the following JARs from your own matching installations into these local paths (they are ignored by Git):

| Dependency | Destination |
|---|---|
| projectzomboid.jar | reference/project-zomboid/binaries/projectzomboid.jar |
| PZ3D-0.2.2.jar | reference/pz3d/42.20.4/media/java/client/PZ3D-0.2.2.jar |
| ZombieBuddy.jar | reference/zombiebuddy/binaries/ZombieBuddy.jar |

Then build and run the local suite:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File experiments/zombiebuddy-harness/Test.ps1
```

This produces `experiments/zombiebuddy-harness/dist/PZ3DVRTest-0.4.0.zip`. The suite uses synthetic fixtures, copied-class inspection/retransformation without initialization, and standalone OpenGL checks. It does not install the mod or launch the game. Native OpenXR smoke tests are separate; see the harness instructions. Do not run a competing test scene while the game is using XR.

## Project layout

- [ZombieBuddy harness](experiments/zombiebuddy-harness/README.md): mod, OpenXR backend, UI panel, packaging and tests.
- [Renderer adapter](experiments/pz3d-adapter/README.md): version-gated stereo instrumentation.
- [Standalone OpenXR diagnostic](experiments/openxr-diagnostic/README.md): runtime experiments and dependency setup.
- [Research report](research/final-report.md): original feasibility investigation; historical findings are not a statement of current implementation status.
- [Validation record](experiments/zombiebuddy-harness/VALIDATION.md): tests, user confirmations, and remaining limitations.

Research notes may link to local evidence and decompiled references. Those files, downloaded tools, logs, screenshots, and build products are intentionally excluded from the repository. Some historical research scripts assume the original author's installation paths; they are not needed to build the mod.

See [third-party dependencies](experiments/zombiebuddy-harness/THIRD_PARTY.md). No license for the original project code has been selected yet; publication is not an additional license grant for third-party game or mod material.
