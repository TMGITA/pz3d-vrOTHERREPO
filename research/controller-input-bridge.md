# OpenXR-to-Zomboid gamepad bridge investigation

Date: 2026-09-27. Current source: 0.7.0, including the local opt-in motion-melee prototype. This investigation does not change that prototype or publish a new package.

## Decision

An internal Java bridge is technically plausible and the preferred direction, but it is **not an isolated write into an existing controller slot**. The inspected build has an internal state abstraction but no synthetic-device registration API. Creating a device without hardware requires version-pinned changes to controller construction/identity, enumeration and polling, with lifecycle coordination across the render-thread/game-thread cache and native Lua activation. This meets the request's stop condition for invasive or uncertain changes. No production bridge was implemented; working VR code is unchanged.

The blocker is synthetic controller identity and lifecycle, not obtaining Quest buttons. This is not a conclusion that an internal bridge is impossible. A separately scoped controller-lifecycle prototype could establish the missing guarantees before integration.

## Evidence and current architecture

Read the repository README, AGENTS, historical final report, current XrHands/OpenXrSession code, and current harness documentation. Inspected locally installed Build 42.20.4 and the matching copied binary. Their SHA256 values both equal `80e405a4bfc42f6072e75b3735f458a6514143da011d3226007ded305a442f44`. Dependencies remain PZ3D 0.2.2 and ZombieBuddy 2.3.2.

Local decompilation evidence is under ignored `reference/project-zomboid/controller-cfr/`; class signatures and thread call sites were also inspected with javap. Decompiled source is evidence, not source to distribute or compile. Installed Lua evidence: `media/lua/client/ISUI/Gamepad/JoyPadSetup.lua`. No game, mod entry point, installer, or native controller driver was run. No installed files or user settings were modified.

`experiments/zombiebuddy-harness/src/pzvr/xr/OpenXrSession.java:initialize()` creates an OpenXR 1.0 instance with XR_KHR_opengl_enable and a Win32 OpenGL-bound session on the existing render thread. The backend uses LWJGL OpenXR 3.4.1 and its bundled Windows loader, alongside the existing game's LWJGL libraries. It constructs XrHands before beginning normal session work. No second runtime/session is needed for input.

`XrHands` already owns action set `pzvr_arms`, pose action `grip_pose` with left/right subaction paths and two action spaces, and float action `melee_enable` for the right trigger. It suggests simple, Touch, Index, Vive and Microsoft motion profiles in one combined binding list per profile and attaches the set once. `locate()` synchronizes while focused, reads grip action activity, locates poses at predicted display time, and reads the right trigger with a 0.65 threshold. Pose validity/tracking flags gate arm tracking. Current output is HandPoses plus combatHeld; analog trigger magnitude is discarded. There is no conventional-controller bridge, stick/face-button acquisition, or active-profile diagnostic today.

## 1. How Touch input reaches the session

For the reported configuration the intended route is Quest hardware -> Steam Link -> SteamVR OpenXR runtime -> action bindings in our existing session. SteamVR seeing controllers does not automatically create a GLFW gamepad for Zomboid, nor prove that every application action has a binding. Our existing Touch grip/trigger suggestions provide the application-side path. No Quest SDK is necessary for the proposed inputs.

The tester reports SteamVR tracking, and earlier reports establish physical arm tracking in a preceding build. We do **not** have a live capture establishing this tester's current interaction profile or button states. Current code cannot log that information. Confirm the runtime name already logged by OpenXrSession, then query each hand's profile and each action's activity. Do not infer profile identity solely from headset brand.

## 2. Required OpenXR acquisition

Use the standard `/interaction_profiles/oculus/touch_controller` profile and `/user/hand/left` / `/user/hand/right` subactions. Suggested component suffixes:

| Input | Left | Right | Action type |
|---|---|---|---|
| Stick | `/input/thumbstick` | `/input/thumbstick` | VECTOR2F_INPUT; x/y components |
| Stick press | `/input/thumbstick/click` | same | BOOLEAN_INPUT |
| Trigger | `/input/trigger/value` | same | FLOAT_INPUT |
| Grip pressure | `/input/squeeze/value` | same | FLOAT_INPUT |
| Lower face button | `/input/x/click` | `/input/a/click` | BOOLEAN_INPUT |
| Upper face button | `/input/y/click` | `/input/b/click` | BOOLEAN_INPUT |
| Menu | `/input/menu/click` | no dependable application menu | BOOLEAN_INPUT |

The right `/input/system/click` is listed but may be unavailable to applications. Never depend on intercepting it or suppressing the dashboard. Grip pressure is distinct from the existing grip **pose**. [Khronos Touch profile](https://registry.khronos.org/OpenXR/specs/1.0-khr/html/xrspec.html#_oculus_touch_controller_profile).

After xrSyncActions, use xrGetActionStateBoolean/Float/Vector2f. Boolean currentState means held; changedSinceLastSync plus currentState distinguishes sampled press/release, with lastChangeTime available. Floats preserve analog trigger/grip values, vectors preserve stick coordinates. Keep per-action isActive, timestamps and session focus in a normalized immutable snapshot. Boolean sampling cannot recover arbitrarily fast transitions between synchronizations. [Boolean state contract](https://registry.khronos.org/OpenXR/specs/1.0/man/html/XrActionStateBoolean.html), [float state](https://registry.khronos.org/OpenXR/specs/1.0/man/html/XrActionStateFloat.html), [vector state](https://registry.khronos.org/OpenXR/specs/1.0/man/html/XrActionStateVector2f.html).

OpenXR core does not offer a universal physical-controller connected boolean. Track input availability from action activity, pose activity/tracking, focus, freshness, and profile information separately. xrGetCurrentInteractionProfile can retain the last profile when no controllers are active, so a non-null profile is not proof of connection. Query on interaction-profile changes and at startup after attachment. [Profile query contract](https://registry.khronos.org/OpenXR/specs/1.0/man/html/xrGetCurrentInteractionProfile.html).

All desired actions must be created and included before session action-set attachment; integrate with the existing owner rather than attempting a later second attachment. Assemble a complete suggested binding list per profile including existing pose/melee bindings. Unsupported profiles should not disable working grip tracking. [Attachment contract](https://registry.khronos.org/OpenXR/specs/1.0/man/html/xrAttachSessionActionSets.html).

## 3. Exact conventional input path in this build

The application-facing native API is **GLFW through LWJGL**, wrapped by `org.lwjglx.input`, not a Java JInput/SDL/XInput API. GLFW may use platform backends internally; that is not the injection boundary here. SDL-format mapping files do not mean SDL receives input. GLFW documents its standardized gamepad state and mapping database separately. [GLFW input guide](https://www.glfw.org/docs/latest/input_guide.html#gamepad).

| Class / method or field | Observed role |
|---|---|
| `org.lwjglx.input.Controllers.create()` | Loads mappings, installs glfwSetJoystickCallback, enumerates glfwJoystickPresent for IDs 0..15, constructs Controller objects. |
| `Controllers.controllers`, `getController(int)` | Private fixed 16-element registry. Count is capacity, not number connected. |
| `Controllers.updateControllersCount(int,int)` | Private connect/disconnect handler; replaces/removes registry entries and calls optional callbacks. |
| `Controller(int)` | Final class, only constructor; calls GLFW for name, GUID, gamepad status, axes/buttons/hats. Metadata fields are final. No disconnected/synthetic constructor or interface. |
| `Controller.poll(GamepadState)` | Calls glfwGetGamepadState; fills hat state separately through native joystick hats; sets polled=false on failure. |
| `GamepadState` | Public polled flag, final allocated GLFWGamepadState axesButtons, hat buffer/hatState. Mutable values but not a registered device. |
| `zombie.input.ControllerState.poll()` | Polls a private 16-element GamepadState array through Controllers.poll. |
| `ControllerStateCache.poll()/swap()` | Lock-protected double buffer plus cached Controller references. Copies identity before polling; swaps states and assigns active gamepadState references. |
| `ControllerState.onStateActive()/onStatePolling()` | Binds controller references to active state; copies prior state into next polling buffer. |
| `zombie.core.input.Input.poll()` | Invoked from RenderThread; enters controller cache polling. |
| `Input.updateGameThread()` | Invoked from GameWindow on game thread; detects connections, updates current/previous button arrays, sends activity notifications, then swaps cache. |
| `Input.checkConnectDisconnect()` | Compares identities; rejects objects whose isGamepad() is false. |
| `Input.controllerPressed/controllerWasPressed` | 16x15 booleans; isButtonStartPress/isButtonReleasePress derive edges. |
| `JoypadManager.checkJoypad/addJoypad` | Lazily builds game-level Joypad using GUID/name and loads config. |
| `JoypadManager.assignJoypad(int,int)` | Assigns controller ID to player; registration alone is not assignment. |
| `JoypadManager.activeControllerGuids`, `setControllerActive/syncActiveControllers` | Maintains enabled device set/list. |
| `JoypadManager.onControllerConnected/Disconnected` | Drives joypad reactivation/deactivation events. Input also emits OnGamepadConnect/Disconnect. |
| `JoyPadSetup.lua` | Handles those events, joypad focus and activation/UI state. Bypassing it can leave gameplay and menus disagreeing. |
| `JoypadAxis1d`, `JoypadAxis2d`, `JoypadButton` | Higher-level suppliers to native controller bindings; reuse rather than duplicate. |

Mapped gamepads have 6 float axes and 15 byte buttons. Axis order: left X/Y, right X/Y, LT, RT. Button indices: A=0, B=1, X=2, Y=3, LB=4, RB=5, Back=6, Start=7, Guide=8, L3=9, R3=10, D-pad up/right/down/left=11..14. Native POV is also represented by hatState bits up=1/right=2/down=4/left=8.

Controller dead zones default to 0.2. Joypad configuration can select/flip axes, buttons and triggers. Trigger processing expects -1 at rest and +1 pressed, applies its dead zone, and isLTPressed/isRTPressed compare the processed value with 0.7. Movement/aiming dead-zone settings remain native. Do not feed OpenXR's 0..1 triggers straight into these axes.

Mapping layers: `Controllers.readGameControllerDB()` loads `media/gamecontrollerdb.txt` then the user cache `joypads/gamecontrollerdb.txt` via glfwUpdateGamepadMappings. `JoypadManager.doControllerFile()` loads per-GUID `joypads/<guid>.config`, and may write settings through saveFile. A synthetic device needs a stable distinct GUID; no installed files were created during this investigation.

## 4. Internal injection boundary and blocker

The cleanest **state** boundary is filling one GamepadState inside native polling before the cache publishes it, retaining Input's existing edge detection and all consumers above it. Directly writing Controller.gamepadState on the render thread is not safe: onStateActive replaces that reference and the two buffers have different owners. Hooking only JoypadManager getters leaves enumeration, Lua UI, connection events and low-level Input consumers inconsistent.

A complete internal design would need:

1. A valid synthetic Controller with stable metadata, six axes, fifteen buttons, dead zones and hats. Ordinary construction for an absent GLFW device produces an unusable identity; final class prevents an adapter subclass. Constructor bytecode interception could initialize the existing fields without changing class shape, but changes a foundational class and needs separate verification. Do not use unchecked Unsafe allocation or reflective final-field rewriting as a shortcut.
2. A registry policy exposed through `Controllers.getController(int)` or its private registry, coordinated **before** ControllerStateCache copies identities. Reserve an actually free 0..15 slot without stealing a physical controller. Handle real device hotplug at that slot, full capacity and shutdown. Never use ID 16 or silently overwrite a physical controller.
3. A `Controller.poll(GamepadState)` branch restricted to the exact owned synthetic identity that fills all fields and skips native GLFW polling. Alternatively a coordinated Controllers.poll hook must publish both identity and state consistently. Neutral triggers must be -1, not zero. Physical controllers must retain their original path.
4. Native game-thread connect/disconnect, enabled GUID, and normal player assignment/UI behavior. A render-thread hook must not call Lua activation directly. Return-to-menu, reload, save changes, disconnect and reconnection all need tests.

These are plausible instrumentation targets using the project's existing version gate and retransformation facilities, but there is no public insertion method that supplies all four. Bytecode verification alone would not prove cache and UI lifecycle correctness. Hence the explicit decision to stop before production modifications under this request.

## 5. Approaches compared

| Approach | Benefits | Costs / decision |
|---|---|---|
| Internal synthetic gamepad | Reuses native bindings, menus, movement and combat; no driver; preserves normalized VR input for later systems. | Preferred architecture, conditional on proving the lifecycle above. Requires low-level, version-pinned instrumentation. |
| Windows virtual Xbox controller | Zomboid enumerates a real OS-visible gamepad through its normal path. | Requires a virtual bus/device driver plus a user-mode client/native interop and distribution/support for installation and updates. Broader privileges and dependency surface than a normal mod. No drivers installed. |
| Direct action translation | Easy to experiment with a few actions; useful for eventual specifically VR interactions. | Reimplements movement/aiming/UI/edge/context rules and misses native controller assignment. Not a good replacement for conventional gamepad support. |

ViGEmBus/ViGEmClient is an example of the second architecture, **not a recommended new default dependency**: the upstream driver repository is archived and retired. XInput itself reads controllers; calling it does not create one. A maintained alternative would need a separate dependency/licensing/support review. [Upstream ViGEmBus](https://github.com/nefarius/ViGEmBus).

## 6. Proposed temporary mapping

| Touch input | Emulated Zomboid gamepad |
|---|---|
| Left stick X/Y | Axes 0/1; negate OpenXR Y to match forward/up-negative convention |
| Right stick X/Y | Axes 2/3; negate Y; retains vanilla aiming/ready stance, not camera turning |
| A/B/X/Y | Buttons 0/1/2/3 |
| Left/right trigger | Axes 4/5, `2 * clamp(value,0,1) - 1` |
| Left/right squeeze | LB/RB 4/5; proposed hysteresis press 0.65, release 0.45 |
| Left/right stick click | L3/R3 9/10 |
| Left menu, if delivered | Start 7 |
| Right system button | Leave to runtime; no dependable Guide or Back mapping |
| Back and D-pad | No direct physical equivalent; explicit configurable alternate mapping required |

Mapping is approximate until the missing Back/D-pad controls have a deliberate assignment. A configurable modifier layer could provide them, but would consume another existing control and is not silently assumed here. Both D-pad button values and hat state should agree if an alternate is implemented, because native default D-pad mappings use POV. Do not claim complete Xbox parity without this.

The existing motion-melee prototype consumes the right trigger too. Initial conventional-gamepad mode should be mutually exclusive with motion-melee input, otherwise normal RT attacks can compete with swing requests. This is an input ownership requirement, not new melee work. Preserve the existing prototype while Off.

## 7. Separation, safety and future compatibility

Acquire once per OpenXR synchronization into immutable `VrControllerState`: sequence, monotonic receive time, focus, per-hand profile, per-action availability, sticks, analog triggers/grips, buttons, and separately pose/tracking validity. No game classes or Xbox indices in acquisition. Maintain existing pose output independently.

A separate pure `GamepadMapper` converts a snapshot into six axes/fifteen buttons/hat state. A distinct version-gated `ZomboidControllerAdapter` handles lifecycle and publishing in the native polling boundary. Future VR locomotion/turning/interaction consumers can read the same snapshot with explicit input ownership without rewriting acquisition. No tracked aiming, turning or physical interaction is proposed for this task.

On focus loss, inactive actions, stale input, session teardown or disable: publish neutral buttons/sticks and -1 triggers; prevent held inputs from generating new presses on resume until released. Drain/preserve press-release ordering across render/game rates or explicitly acknowledge taps shorter than consumption can be lost. Avoid arbitrary cross-thread game-state writes. Do not suppress all input when menus open: the purpose of gamepad emulation includes native menus. Let native UI route controls; distinguish runtime focus loss from in-game UI focus. Do not require positional tracking merely to read valid buttons, but fail neutral when action state itself is unavailable. Keep synthetic device identity stable during brief dashboard interruptions to avoid repeated disconnect dialogs.

Current XR startup is tied to the working in-game harness. This investigation does not establish controller-only navigation before the session exists (startup/main menu) or across every return-to-menu transition. Initial enablement/assignment may still require desktop input. It does not solve PZ3D camera turning; the requested conventional mapping preserves the right stick's existing function.

## 8. Diagnostics and acceptance work before shipping

Proposed optional modes: Off, acquisition diagnostics, and gamepad bridge. Diagnostics should log profile/activity/focus changes immediately and rate-limit analog/button snapshots (for example 2 Hz); temporary edge tracing may be enabled for missed-button diagnosis. Include sequence/age, left/right action availability, stick X/Y, trigger/grip values, button masks, chosen synthetic ID, translated values, and the sequence observed by the game-thread consumer. Do not label a tracked-pose bit as a physical connection status.

Before integrating: copied-class transformation/verification plus fixture tests for synthetic construction, registry capacity/hotplug collision, double buffering, edge delivery, neutralization, trigger range and native-controller preservation. Test native Lua lifecycle with original I/O fixtures, not live settings. Then the user tests profile/button diagnostics on Quest/Steam Link, activation and assignment, menu navigation, normal gameplay, dashboard/resume, disconnect/reconnect, mixed physical gamepads, and return to menu. No such runtime behavior is claimed proven here.

Investigation complete; implementation remains gated on resolving that lifecycle risk. No new installable build was generated for this investigation.


## Implementation follow-up: 0.8.0

Following explicit user authorization, the internal bridge is now implemented. The initial investigation above records the earlier stopping decision. `pzvr.xr.XrControllerInput` acquires standard Touch actions into immutable `pzvr.input.VrControllerState`; `GamepadMapper` owns conventional mapping and neutral rearming. `ControllerInstallation` adds schema-preserving hooks to Controller(int), Controllers.getController/poll, and Input.updateGameThread. `ControllerBridge` overlays slot 15 without mutating the physical registry, fills only its owned polling buffer, and lets the native cache publish it. Native controller configuration/activation remains user-controlled.

A constructor branch initializes the existing metadata fields only during bridge-owned construction; ordinary physical construction remains unchanged. Focus/session loss and Off keep a neutral device identity. Physical collision triggers neutral draining, native disconnect, scoped Lua/native assignment cleanup, and handback. Optional diagnostics correlate acquisition and published game-thread state. Menu is a modifier for Back/D-pad with Start on an unused tap; this depends on the runtime delivering Menu. Gamepad mode excludes motion-melee input. See the harness README and VALIDATION.md for test evidence and limitations; this is not a claim of physical-headset or in-game validation.
