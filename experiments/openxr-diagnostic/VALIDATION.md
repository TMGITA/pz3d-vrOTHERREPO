# Validation — 2026-09-27

This page records milestone 1 (eye charts). See [milestone 2](SCENE.md) for the subsequent 3D scene and projection/pose validation.

Host: Windows x64, NVIDIA RTX 4090, NVIDIA OpenGL 4.6.0 / driver 581.42. SteamVR server log reports 2.17.10. LWJGL 3.4.1; portable compiler Temurin 25.0.4.1.

| Check | Observed result |
|---|---|
| Compile | `javac --release 17 -Xlint:all` passes without warnings |
| Desktop preview | `DESKTOP_PREVIEW_OK`; distinct red L / blue R with correct top/bottom markers; no OpenXR session |
| Initial null-driver OpenXR run | 62 projection frames accepted on Temurin 25.0.4.1; initial wrapper exit-code bug fixed afterward |
| Final null-driver OpenXR run | `XR_STEREO_SUBMISSION_OK`, wrapper exit 0, using PZ's Java 25.0.1 without game classes or agents |
| Final frame counts | 1,362 frames begun; 1,254 stereo projection submissions; 2,508 eye renders; 108 frames skipped |
| Runtime images | Two 768×768 GL_SRGB8_ALPHA8 swapchains, three images per eye |
| Lifecycle | READY → SYNCHRONIZED → VISIBLE → FOCUSED; explicit exit → STOPPING → IDLE → EXITING |
| Missing-runtime negative test | Deliberately nonexistent runtime manifest produces `SETUP_REQUIRED`, wrapper exit 2 |
| Cleanup | No SteamVR processes remained after the final isolated run |

Final XR evidence: [application report](runs/20260927-113440-072-xr/result.json), [left image](runs/20260927-113440-072-xr/left.png), [right image](runs/20260927-113440-072-xr/right.png), [SteamVR OpenXR client log](runs/20260927-113440-072-xr/steamvr-logs/xrclient_java.txt), [server log](runs/20260927-113440-072-xr/steamvr-logs/vrserver.txt). These are local, ignored artifacts and will not accompany a clean source checkout.

Preview/negative-test evidence: [preview report](runs/20260927-113542-150-preview/result.json), [missing-runtime report](runs/20260927-113544-574-probe/result.json).

SteamVR logs confirm configuration and log paths under the run directory, null-driver loading, and both OpenGL eye textures reaching the submission path. SteamVR's fresh profile emits ancillary input-manifest/chaperone warnings; no action input or room calibration is implemented. These warnings are not a claim of fully clean runtime validation.

The first launcher used `vrstartup`, which opened first-run room setup and allowed a client to restart SteamVR during cleanup. Those test processes were stopped. The final launcher starts `vrserver` directly, disables monitor auto-launch, and stops clients before compositor/server. The final run did not leave runtime processes behind.

SteamVR registered itself as the system OpenXR runtime during initial startup. Normal OpenVR path registration still points to the normal Steam folders; the normal Steam settings file remained absent. No game was launched. Project Zomboid/PZ3D/ZombieBuddy installation binaries and copied references are checked separately by `scripts/verify_research.py`.

This milestone verifies the standalone submission path only. The null driver returned fixed eye positions (+0.0315 and −0.0315 metres on X, in runtime view order); no claim about physical stereo geometry follows from these synthetic values. Hardware tracking, compositor image orientation, binocular comfort, and game-frame consistency need later tests.
