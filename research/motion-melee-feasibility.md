# Motion-controlled armed melee investigation

Investigation date: 2026-09-27. Research only; no gameplay changes or in-game execution.

## Conclusion

Motion-triggered native melee is feasible enough to prototype. A deliberate controller swing can become a native attack request while Zomboid retains weapon damage, character skills, endurance, condition, target filtering, and recovery. It is not yet established that this will feel responsive: native damage occurs at an animation event, and native targeting follows character facing rather than the tracked weapon.

Continuous contact-based melee is a separate, larger undertaking. Our current arm and held-item transforms affect rendering only. Moving that visible weapon through a zombie does not change native hit detection.

Keep native menus and controls. Add an optional motion input source, disabled whenever UI, focus, or another native action owns control. Do not map a swing to a global mouse click or the vanilla `Melee` key: the latter participates in shove selection. Unarmed attacks and firearms are outside this work.

## Evidence and limits

Authoritative inputs were the copied PZ 42.20.4 and PZ3D 0.2.2 binaries. Selected vanilla classes were extracted and decompiled with Vineflower 1.12.0 without executing game code. `javap` independently confirmed combat method signatures and the bytecode delegation from `Main.meleeAttack()` to `NativeAvatar.request(2)`.

| Input | SHA-256 |
| --- | --- |
| `reference/project-zomboid/binaries/projectzomboid.jar` | `80e405a4bfc42f6072e75b3735f458a6514143da011d3226007ded305a442f44` |
| `reference/pz3d/42.20.4/media/java/client/PZ3D-0.2.2.jar` | `75cf9b39851b2a8f71fd9e3e3d30620ef1f5c5006e0f1af9bf974b4a800486a5` |

Generated evidence is local and ignored by Git, under `reference/project-zomboid/melee-vineflower/`; PZ3D evidence is under `reference/pz3d/vineflower/`. Method names below are more useful anchors than decompiler line numbers. These are version-specific observations, not promises of stable modding APIs. No headset measurements, gameplay feel, multiplayer behavior, or runtime patch compatibility were tested in this investigation.

## Native attack sequence

1. PZ3D's `Main.meleeAttack()` delegates to its native-avatar request queue with attack kind 2. Queue acceptance is not confirmation that an attack started or hit anything.
2. `NativeAvatar.beforeAnimation()` faces the character through native locomotion, checks readiness, and consumes the request using `IsoLivingCharacter.AttemptAttack(float)` on the player.
3. `AttemptAttack(float)` calculates attack variables and passes through the native Attack hook. Player `DoAttack` reaches `CombatManager.pressedAttack()`. Native code chooses weapon/attack type and establishes attack state. In this build, the player's `DoAttack(float, String)` returns false even after calling `pressedAttack`; its return value alone is not a reliable success indicator.
4. `SwipeStatePlayer.enter()` recalculates attack variables, runs the swing event/hook, manages action state, and starts the native swing behavior.
5. The `AttackCollisionCheck` animation callback calls `CombatManager.attackCollisionCheck()`. It rebuilds the hit list and applies native combat processing. `SwipeStatePlayer.ATTACKED` prevents repeated collision processing for that attack; CombatManager sets it after processing.
6. Native exit/recovery runs, and PZ3D's exit patch clears its active attack bookkeeping.

Consequences: the hidden native animation still matters under tracked-arm rendering. An immediately completed physical swing may produce damage later. Calling collision processing directly from the renderer would bypass parts of this sequence and risk duplicate or inconsistent effects.

### How a hit is selected

`CombatManager.getNearestMeleeTargetPosAndDot()` compares target positions against the character's forward direction, weapon minimum/maximum angle, and native melee range. `calculateHitListWeapon()` applies additional target and obstruction rules. `calculateHitInfoList()` handles hit counts and sandbox multi-hit rules. Some body-part checks sample along character-forward weapon reach toward native target bones; this is not a swept collision test against our tracked weapon mesh.

Target lists are considered during attack selection and rebuilt at impact. A candidate target at gesture time is not a guaranteed eventual victim. Keep these native rules for the first prototype, including misses, rather than calling a zombie's damage method ourselves.

### One-handed and two-handed weapons

| Family | Native behavior relevant to VR |
| --- | --- |
| Ordinary one-handed melee | Uses the equipped primary weapon and native attack selection. One tracked attacking hand can request that swing. This does not imply independent off-hand attacks or dual wielding. |
| Ordinary two-handed melee | Character weapon classification checks both the two-hand flag and the same item occupying both inventory hand slots. Native damage and endurance logic penalizes improper one-handed use of a two-handed weapon. |
| Knives | Stab classification and close-kill handling make these different from broad swinging weapons. |
| Spears | Have a distinct weapon/attack category; physical thrust recognition deserves separate calibration. |
| Heavy weapons and chainsaws | Have distinct native classifications. Do not assume one generic gesture recognizer adequately models every attack behavior. |

Sources: `WeaponType.getWeaponType()` overloads; `CombatManager.pressedAttack()`, `applyOneHandedDamagePenalty()`, and `applyMeleeEnduranceLoss()`.

Inventory equipment state and physical two-hand contact are separate. Holding two controllers near a bat does not equip that item in both native slots. Initially preserve the native equipment rule and let the primary hand request attacks. Our current held-item rendering follows its owning hand; constraining the support hand to the handle is additional work, not a prerequisite for testing native two-handed damage.

Weapon animation types are selected by native logic, including weighted attack selection and situational cases. A downward controller movement does not automatically select an overhead native attack. Avoid promising direction-specific animations or body-part damage in the first prototype.

## Integration obstacles

### The existing melee request is useful but not plug-and-play

`NativeAvatar.request(2)` requires `Input.attackCombat()` before it queues. Simply calling it when a controller moves will fail outside the existing combat/aim context. It also checks combat access, traversal, vehicle/native-interaction ownership, running/sprinting, current attacks, and queued character actions. Preserve these restrictions.

Add a narrowly scoped VR combat authorization/aim integration on the game thread, or initially require existing native aim for an engineering test. Do not use `requestClick()` as a shortcut: it selects other attack kinds depending on equipment. Explicitly filter eligible armed melee, excluding bare hands, ranged weapons, and throwing weapons.

PZ3D permits a pending melee request to wait up to two seconds. That is unsuitable for replaying an old physical swing. The prototype needs short-lived commands, cancellation, and a way to guarantee expired VR requests cannot subsequently execute inside PZ3D's queue. A short-lived outer mailbox alone does not solve the inner queue's lifetime.

### Facing must agree with combat

`NativeLocomotion.face()` and native aim routing control character facing. Current HMD/arm transforms do not automatically give native combat the controller's direction. For the first controlled experiment, attack along existing native facing and make that limitation explicit. Then investigate a scoped heading bridge if measurements justify it.

Do not use instantaneous hand velocity as attack heading: a sideways arc often strikes a target in front of the player. Any heading bridge must cooperate with PZ3D's facing ownership rather than repeatedly overwrite it from the render thread.

### Prevent automatic unarmed fallback

`CombatManager.calculateAttackVars()` can choose a shove when a standing enemy is inside weapon minimum range, with special handling for knives/close kills. Other input and floor-target logic also affects this decision. Attack variables are recalculated at multiple stages, so setting `doShove=false` once is insufficient.

For VR-owned requests, reject/cancel transitions to an unarmed action throughout attack selection/start. Preserve ordinary keyboard/gamepad behavior. The exact cancellation hook must be validated against the state machine; do not clear flags after an unwanted shove has already begun. Armed attacks against prone enemies remain in scope eventually, but should be tested separately from a first standing-target trial.

### Preserve UI and thread ownership

PZ3D `ControlRouting` already denies combat in inventory/UI modes and when inactive, unfocused, paused, or entering text. Reuse that authoritative game-thread decision. Closing a menu must require a fresh gesture; it must not release a stored attack.

Our current OpenXR hands are sampled on the render thread. Publish immutable, sequenced input to the simulation thread; do not inspect/mutate live inventory, combat state, or damage from rendering. Existing PZ3D patches already surround combat methods, so new instrumentation must account for transformed classes and patch order. The current render adapter's pristine-byte guards cannot simply be assumed valid for these combat targets.

## Recommended bounded prototype

1. **Motion telemetry and dry run.** Extend grip samples with timestamps, validity, and velocities. Core OpenXR offers linear/angular velocity through `XrSpaceVelocity`; each validity bit must be respected. Our current `HandPoses` record carries poses only. A filtered finite-difference fallback is possible when sufficient valid pose history exists. See the [Khronos reference](https://registry.khronos.org/OpenXR/specs/1.0/man/html/XrSpaceVelocity.html).
2. **Deliberate swing detection.** Use a temporary explicit combat-enable action plus minimum travel, speed, hysteresis, and return-to-ready behavior. Detect from physical tracking-space samples, not transformed game-world hands, so locomotion/camera turns do not create fake swings. Reset on tracking loss, focus loss, recenter, equipment change, or long sampling gaps. Body movement and wrist-only rotations need recorded headset trials; do not claim tuned thresholds yet. Synthetic arm previews must never issue live attacks.
3. **One gesture, at most one native request.** Send a bounded, expiring command with sequence, player/session generation, and equipment identity. Revalidate everything on the game thread, including native eligibility and cooldowns. Do not buffer gestures through recovery or UI. Initially retain native damage and recovery rather than scaling damage with controller speed.
4. **Native combat bridge.** Begin with one-handed swing weapons against standing targets, existing facing, and explicit aim authorization. Integrate request cancellation and the armed-only guard before enabling real attacks. Then test a two-handed bat/axe equipped in both slots using the same native pipeline. This establishes gameplay feasibility before introducing support-hand constraints or knife/spear recognizers.
5. **Measure timing before deeper collision work.** Log gesture detection, queue acceptance/rejection, actual attack start, native collision event, and completion using correlated IDs. Include weapon family, equip state, rejection reason, and available target/hit outcome. Distinguish a collision event from a successful damaging hit. Convert XR and host timing deliberately; do not subtract timestamps from unrelated clock domains.

The decisive experiment is whether gesture-to-native-impact delay and facing restrictions are acceptable. If not, investigate moving native collision timing under VR ownership with strict event deduplication before considering a full tracked weapon sweep. Continuous contact would additionally require weapon geometry, obstruction handling, target selection, multi-hit policy, and network behavior. It should not be smuggled into a simple input prototype.

## Validation before claiming success

| Area | Required checks |
| --- | --- |
| Detector | Rest/jitter, one swing/one command, rapid reverse movement, wrist rotation, thrust versus arc, walking, camera rotation, recenter, missing samples, synthetic preview. |
| Ownership | Menus, inventory, text entry, pause, focus loss, tracking loss, disconnect, weapon swap, timed actions, traversal, native controls still working. |
| Native combat | Accepted versus actually started, recovery rejection, misses, obstructions, close enemies without VR-generated shoves, native condition/endurance behavior, one-/two-hand equipment differences. |
| Timing | Physical swing versus attack start versus collision event; low/variable FPS; no stale queued attack after a mode change. |
| Compatibility | Copied-class transformation without initialization first; user-run single-player headset tests next. Multiplayer requires separate validation and is not established by reusing native calls. |

No runtime code was changed as part of this investigation. The next deliverable should be an opt-in diagnostic melee prototype with a clear record of accepted/rejected gestures and native attack timing.

## Implemented prototype follow-up: 0.7.0

The first prototype is implemented in `experiments/zombiebuddy-harness/src/pzvr/melee/`. See the harness README for settings and test instructions, and VALIDATION.md for evidence and remaining hardware/in-game checks.

A right-trigger-gated translational swing requests an immediate native `AttemptAttack(0)` on the simulation thread. It bypasses PZ3D's delayed request queue, while reading the pinned NativeAvatar pending/active fields to reject conflicting native requests. Trigger-held aim participates in the existing native aiming path. Scoped guards reject shove, grapple, and floor fallbacks only for VR-owned attacks, including later attack-variable recalculation and animation collision callbacks. Ordinary one-handed, two-handed, and heavy swing weapon families are supported; native character-facing targeting and animation collision timing remain authoritative. Default mode is Off, with Diagnostics and Live options. No physical contact damage, support-hand constraint, or motion-selected attack direction is implemented.

## Contact-timed pilot follow-up: 0.9.0

The user chose to proceed directly to contact timing. `pzvr/contact` now captures the rendered plain baseball bat's attachment transform and mesh bounds as an approximate world-space capsule. A bounded render-to-simulation mailbox carries geometry without accessing the live world on the render thread. The simulation thread sweeps against approximate standing-zombie capsules, checks reach and conservative obstructions, and selects the earliest eligible contact per right-trigger hold.

For this owned attack, `CombatManager.calculateHitInfoList` supplies only the contacted zombie. Resolution calls native `attackCollisionCheck` after `SwipeStatePlayer.enter`, or on a subsequent simulation update if needed, instead of waiting for the animation event. A processed flag and scoped animation callback guard prevent duplicate collision. The native `processTreeHit` path is skipped and its cached object/tree references cleared so character-facing scenery cannot receive an unrelated hit. Native recovery and damage processing remain in place. Pending contacts expire after 150 ms and reject target movement beyond 25 cm; no attack is buffered through recovery.

The pilot is deliberately limited to `Base.BaseballBat`, standing zombies and single-player. It does not implement exact mesh/per-bone hitboxes, headshot bonuses, scenery damage, multi-hit or network behavior. Misses currently initiate no native attack and incur no missed-swing cost. Diagnostics and live contact modes are separate from the existing animation-timed mode. Fixture and copied-bytecode tests passed; actual headset contact fit and native combat outcomes require user testing. See harness README and VALIDATION.md for the precise test protocol and evidence.
