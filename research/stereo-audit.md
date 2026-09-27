# Stereo replay audit

**Finding: repeated mono rendering is supported; coherent stereo replay is not currently exposed.** `RetainedRender` already redraws a retained `Renderer.Frame` without invoking `GameWindow.logic` again. This is the strongest local evidence in favor of feasibility. However, repeated `draw(false)` deliberately refreshes presentation state. Calling `draw(true)` and then `draw(false)` without additional interception would not ensure identical world state between eyes.

The following audit describes the installed 0.2.2 binary. References use the [preferred source tree](../reference/pz3d/vineflower/com/pavelvoronin/pz3d); full fields/signatures are in `evidence/pz3d-class-signatures.json`.

| State / subsystem | Concrete evidence | Problem on a second eye | Required treatment |
|---|---|---|---|
| Simulation | `Patches.Tick`, `Main.tick`, vanilla `IngameState.updateInternal` | Reentering producer/update doubles input/simulation work | Keep ordinary producer cadence; render both eyes from one retained capture |
| Scene replacement | `Frame.draw`: `scene = uploads.advance(scene)`; `SceneUpload.step` can commit `pk = pj = newScene` | New geometry/origin or GPU buffers can appear between eyes even on the same Frame object | Adopt/pin scene and backing resources once per pair; do not advance uploads between eyes |
| Camera | `Frame.ad()` resamples `LookState.vehicle`, `Input.latest`, nanoTime and `MotionPrediction.update` | Mouse state/prediction and mutable `x/y/z/dx/dy/dz` differ | Freeze body/base transform and predicted presentation state, then derive the two eye transforms |
| Head pose | No headset input exists | Sampling separately could mix predictions | Locate the pair together for one OpenXR predicted display time; retain both returned poses/FOVs |
| Vehicle motion | `VehicleRenderer.update`, `VehicleMotion.view`, `LookState.vehicle/limitPitch` | Vehicle pose, seat view and shared yaw/pitch can advance between eyes | Predict once; keep vehicle/body transforms fixed; eye camera remains separate |
| Body/recoil | `Frame.ad` rebuilds `Part.world` from `capturedWorld`, first-person look quaternion, `ShotEffects.recoil(capturedAt,nanoTime)` and `CameraBodyFade.update` | Player body/weapon and fade can differ, or follow head turning unexpectedly | Freeze non-eye body matrices/recoil/fade once; do not feed headset rotation into the body's look quaternion |
| Bone animation | `Frame` constructor uses `PoseData.initModel`; palette stored by inherited animated render data | Creating a second Frame could sample a newer animation; live references still exist | Retain same palette/model references across pair; verify texture/model lifecycle in prototype |
| Resource ownership | `FrameLease.retain/release/drain`, `Frame.postRender` guard `nI`, `Frame.ac` clears characters; `CharacterDraw.release`, `Part.finishNative` | Premature retirement can invalidate eye two; double release throws | One ownership bracket around both eye draws; preserve game-thread cleanup semantics; never retire after eye one |
| Texture readiness | `TextureWork.begin/run`, `CharacterDraw.prepareTexture/prepare`, `Part.prepare/reuse`, `VehicleRenderer.Draw.prepare` | Texture/actor readiness changes during drawing | Prepare native textures once; share readiness flags across eyes |
| Actor state mutation | `Frame.draw` sets `corpse.rendered = true`; `CharacterDraw.release` changes native creator/refcounts | Deferred fallback visibility and native resource state can change | Defer end-of-pair lifecycle changes; preserve original once-only behavior |
| Actor list | `ZombieModels.update` sorts/caps visible list and manipulates native culling | Recapturing independently can change membership | Capture once; initial prototype uses current radius/cap, not a second player slot |
| World visibility | Static `Renderer.nn`; `WorldBuffers.Draw.viewReady`, `visibleFirst/visibleCount`; `WorldBuffers.beginView` resets flags | Reusing eye-one draw lists clips eye two; parallel views race | Recompute per-eye frustum and call beginView for each eye; preserve shared geometry |
| Native camera/sprites | `IsoCamera.frameState`, player camera array, `IsoSprite.globalOffsetX`, `SpriteRenderer` state queues | Treating eyes as split-screen players reenters shared game/UI state | Avoid native render replay; only intercept PZ3D consumer |
| Lighting | `Frame.lighting/lights` captured; `Frame.torch` predicted; `Enclosure.capture(scene)` in draw | Scene changes and torch following can cause lighting disagreement | One common lighting/enclosure/torch snapshot, per-eye view-dependent uniforms |
| Shadows | `ShadowMap.render` budgets work by nanoTime; `PointShadows.render` increments `mt`, exposes `frameId`; torch/actor shadow update paths | Eye two could see newly committed maps or consume twice the update budget | Perform common shadow updates once before either eye; ensure shadow selection covers both views |
| Streaming fade | `StreamFade.update` gets clock time, updates shared ages/texture; uniforms use shared fade time | New chunk fade and actor readiness may differ | Freeze fade time/coverage for the pair |
| Tracers / effects | `ShotEffects.snapshot(long)` removes expired deque entries; draw takes a fresh snapshot and effects helper samples time again | Trace can expire or appear in only one eye | Snapshot once, use one effect time; merely freezing the Frame constructor list is insufficient |
| Other particles | No general PZ3D particle replay API or particle loop was found in the inspected main renderer; vanilla cell rendering remains complex | Cannot certify every weather/fire/third-party effect | Bound prototype to PZ3D's supported visuals; audit any extra native effect path when exercised |
| World GPU buffers | `WorldBuffers.shared`, `SceneUpload`, static `Renderer.ny`, upload/deletion queues | Upload/retirement can replace geometry between eyes | Pin one scene/buffer generation through both eyes; upload outside pair |
| Color/depth | Static `nw/nx/depth/width/height`; depth cleared each draw | Eye two overwrites color and depth | Sequential scratch reuse plus immediate eye copy; or explicit per-eye targets. Never share uncleared eye depth |
| Outlines/glass/captions | `TargetOutline.draw`, `ObjectHighlightRenderer.draw`, `VehicleRenderer.glass`, `DeviceCaptionLayer.draw` | Size, projection and eye position are view-dependent | Recompute per eye or disable optional overlays in the first milestone |
| Shader state | `viewProjection`, `eye`, `viewportSize`, `meshOrigin`; multiple cached programs and texture units | Replacing only the main projection leaves stale related uniforms | Explicit eye context passed throughout view draw; restore state before vanilla resumes |
| Depth prepass / telemetry | `DepthPrepass.Policy/Stability/Sample`, `Telemetry.Gpu.begin/mark/end`, `Renderer.fps/frameMs`, `FrameTrace` | Counters/queries treat each eye as a full frame, distort budgets and may conflict with wrapped queries | Distinguish pair-level and eye-level timing; conservative disabled adaptive prepass initially is an option |
| UI transitions | Vanilla `UIManager.render -> UITransition.UpdateAll`, Lua callbacks; `Core.EndFrameUI` blink/accumulator | Two UI draws can advance UI and side effects twice | Render/capture UI once and composite its texture for both eyes |
| Scheduling | `RetainedRender.begin` fixed ~60 Hz pacing; `end` and `Display.update` | Duplicate OpenXR submission, desktop vsync blocking, wait-queue deadlock | One owner of OpenXR frame loop; bypass retained mono scheduling only while VR session is active; preserve fallback |

## Proposed safe conceptual boundary

This is a design contract, not implemented code:

```text
Game thread: normal simulation/update + normal capture/queue
Render thread:
  obtain/retain newest valid Renderer.Frame
  OpenXR wait/begin frame, obtain predicted display time and both views
  once: adopt scene, upload/prepare resources, predict body/vehicles,
        freeze effects/fade/time, update shared shadows
  left: set eye position + full matrix + size; reset visibility; clear depth;
        draw world; copy/convert color into acquired left swapchain image
  right: same frozen non-eye state; distinct eye position/matrix/culling;
         draw world; copy/convert into acquired right image
  one UI capture -> optional UI swapchain/quad
  release acquired images; submit one projection layer with two views
  end pair ownership; retire according to existing lifecycle
```

Simply setting `Frame.x` before calling `draw` fails because `ad()` overwrites it. Simply patching `Camera.view()` affects capture but not all render-time camera recomputation. Simply intercepting the final blit gives two copies of the same camera unless the view was changed before drawing. These are distinct integration points, not interchangeable shortcuts.

## Performance evidence and limits

No FPS benchmark was run and no headset performance is claimed. The logged GPU does not prove that this renderer can sustain VR refresh rates. The engine already separates captured native frames from retained rendering, reducing the need to simulate twice, but world rasterization, actor drawing, transparent layers and view-dependent effects still execute twice.

The code includes GPU timers, bounded uploads, retained resource pools, depth-prepass policy and incremental shadow budgets. A naive full `draw` pair duplicates those budgets and substantial preparation. Shared once-per-pair preparation is a correctness requirement as well as a likely performance improvement. The retained 60 Hz limiter is a concrete scheduling obstacle; it is not evidence of an inherent 60 Hz GPU ceiling.

First evaluate pair GPU time, render-thread CPU time, producer time, queue age and missed runtime frames separately. Reference budgets are 13.89 ms at 72 Hz, 11.11 ms at 90 Hz and 8.33 ms at 120 Hz, before allowing compositor overhead. These are arithmetic budgets, not predictions for this machine.

Straightforward controls: start below runtime-recommended eye resolution, expose a size scale, remove the third full-resolution desktop world render (mirror an eye), reuse common shadows, and allow optional costly outlines/shadows to be reduced for diagnostic runs. Scaling each dimension by 0.7 gives 0.49 times the pixels, but does not halve geometry/CPU costs. OpenXR recommendations are inputs to application-created swapchain dimensions, not a universal `resolutionScale` API. Avoid multiview/foveation/upscaling research until basic stereo correctness and bottlenecks are measured.

**Not established:** whole-game determinism under render-thread load, native cleanup races, comfort/latency, stereo correctness of all props/interiors, and attainable headset refresh. Static architecture makes a prototype justified, not proven successful.
