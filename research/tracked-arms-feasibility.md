# Tracked player arms: initial feasibility audit

Scope: render the existing local player's arms at tracked controller poses. Ordinary gamepad locomotion, controller-scheme changes, gesture combat, multiplayer and interaction design are deferred. This audit does not implement arm posing or claim physical-device validation.

Implementation follow-up: version 0.5.0 implements this visual prototype. See the [harness instructions](../experiments/zombiebuddy-harness/README.md#tracked-arm-prototype-050) and [validation record](../experiments/zombiebuddy-harness/VALIDATION.md#version-050-tracked-arm-prototype). The findings below preserve the original feasibility audit; matrix conventions and the scoped pose boundary have now been exercised in standalone tests. In-game arm appearance and physical controller validation remain pending.

## Verdict

A bounded visual prototype is technically plausible, with concrete access to the existing skinning path. No architectural blocker was found in the pinned Project Zomboid 42.20.4 / PZ3D 0.2.2 binaries. The ability to override the rendered pose is supported by static evidence; a correctly deformed, dressed player arm following a controller remains to be demonstrated in-game.

## Verified local evidence

- `Renderer.Frame` captures the local player model in a `CharacterDraw` with `body=true`. Each skinned body/clothing part has `Part.data` referencing `PoseData`, extending vanilla `AnimatedModelInstanceRenderData`.
- Vanilla `initModel` calls `initMatrixPalette`, copying animation-player skin transforms into a per-render-data FloatBuffer. The buffer is a render snapshot, not a request to advance animation again.
- PZ3D's per-part draw duplicates this buffer and uploads it with `glUniformMatrix4fv(..., true, ...)` to `MatrixPalette`. Its skin vertex shader weights bone matrices against mesh vertices. The shader has 60 bone slots; the renderer rejects oversized palettes.
- `javap` against the installed copied game JAR confirms SkinningData exposes boneIndices, skeletonHierarchy, bindPose, inverseBindPose and boneOffset, plus hierarchy queries. AnimationPlayer exposes model/bone transform getters and getSkinTransforms. These are the required data categories for identifying and posing arm chains, though their exact matrix conventions still need verification.
- `BodyRig.forearm` selects names containing `_Forearm`, `_Hand`, or `_Finger`; it does not select upper arms. First-person body parts get both arms and limbs masks from this selection. The fragment shader discards low mask coverage. Full-arm visibility therefore needs a local-player mask adjustment; changing pose alone will not expose shoulders/upper arms.
- Body/clothing parts are captured recursively. Static hand/weapon attachments use separate parent-bone-derived transforms (`transformToParent`), so changing the skin palette alone will not reposition held items.
- First-person body world transforms include camera aim correction, late look prediction and recoil. Controller targets must be converted through the actual prepared part world matrix, rather than assuming that it is just the character's position.
- PoseData is reference-counted and pooled. Death-image/retained draws can share it. Blind in-place mutation can leak into another render consumer or a future reuse.
- The stereo adapter fingerprints the prepared pose and checks coherence across both eyes. A pose override must be frozen once for the pair and restored, or supplied through an owned per-draw palette override; updating between eyes is invalid.

Evidence locations (local references excluded from Git): `reference/pz3d/vineflower/com/pavelvoronin/pz3d/{Renderer,PoseData,BodyRig,NativeAvatar}.java`; `reference/pz3d/jar-resources/shaders/{skin.vert,model.frag}`; `reference/project-zomboid/decompiled/zombie/core/skinnedmodel/advancedanimation/AnimatedModel.java`. Binary signatures were checked with javap without initializing or executing game/mod classes.

## Proposed implementation boundary

Keep the vanilla simulation and animation as the source of the torso, legs, and fallback arm pose. Build an owned visual override for the local player only, applied after PZ3D's common preparation and before both eye draws. Prefer substituting an owned palette at the draw/upload boundary over mutating the live AnimationPlayer or pooled PoseData. This requires a new narrowly version-gated hook; it is not provided by the existing camera-only adapter.

Read left and right controller grip poses through OpenXR pose actions/action spaces at the frame's predicted display time, using the same LOCAL-to-game/recenter mapping as the eyes. Add calibrated controller-to-hand offsets and convert into each prepared model's coordinate frame. Track valid/active state and fall back to the captured native pose on missing tracking. No controller button or stick remapping is needed for this visual proof.

Controller position/orientation provides hand targets, not elbow/shoulder tracking. Use a two-segment arm IK solver for shoulder/elbow/wrist, with a stable elbow bend preference and reach limits. Keep natural bone lengths; infer elbow placement. This cannot exactly reconstruct the person's complete arm posture from two controllers alone. Fingers may retain native animation in the first proof.

Rebuild affected bone transforms and descendants using verified bind/offset conventions and each mesh's own bone mapping. Confirm float-buffer layout, transpose, handedness, nonuniform transforms and attachment handling before hardware claims. Existing clothing should be tested explicitly, not assumed to work because the bare body does.

## Smallest decisive experiment

1. Enumerate the local player's actual skeleton/mesh bindings and validate both arm chains, hierarchy, lengths and matrix round trips. Reject unsupported skeletons cleanly.
2. Feed a deterministic synthetic hand target into one existing arm's palette override. Render both eyes from the same solution. Preserve the rest of the body and restore normal rendering when disabled or on failure.
3. Test hand translation and wrist rotation, unreachable targets, clothing, session toggling, and retained-frame reuse. Initially leave items unequipped so attachment transforms do not obscure the arm proof.
4. Replace synthetic targets with OpenXR grip poses and test with physical controllers. The simulated runtime currently provides no moving controller poses, but steps 1?3 can be inspected on the desktop.

Success means the actual skinned character arm follows the target without tearing, clothing separation, changes to other actors, or cross-eye disagreement. Stop/reassess if the captured pose cannot be isolated safely, bind transforms cannot be reconciled, or required meshes lack the expected chain. A floating controller marker alone is not sufficient evidence.

## Limits of the conclusion

Visual arm tracking would not automatically change combat hit detection, melee swings, weapon aim, physics interactions, or multiplayer replication. Those remain separate later work. The current prototype has no OpenXR controller action set; headset tracking alone does not supply hand poses. No installed files were changed and no game/mod entry point was run for this investigation.

OpenXR references: [pose action spaces](https://registry.khronos.org/OpenXR/specs/1.1/man/html/XrActionSpaceCreateInfo.html) and [locating spaces and validity flags](https://registry.khronos.org/OpenXR/specs/1.1/man/html/xrLocateSpace.html).
