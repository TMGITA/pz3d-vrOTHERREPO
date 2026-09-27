# Installed environment and provenance

Snapshot: 2026-09-27. Local binaries take precedence over online repositories.

| Component | Confirmed finding | Evidence |
|---|---|---|
| Game | 42.20.4, build string `b0bbce05d5`; Steam build ID 24909800, public branch | Existing console excerpt; installed Steam appmanifest inspected |
| PZ3D | 0.2.2; explicitly restricted to 42.20.4; single player | `reference/pz3d/42.20.4/mod.info`, binary `Main.VERSION` |
| PZ3D release identity | buildId `8aa1920b0cf842569d0ba24e34de4ae8`; commit `19f9d45747b409718a8ca52773484468d91cf533`; `dirty=True` | `reference/pz3d/jar-resources/META-INF/pz3d-build.properties`; historical log agrees. A public checkout of that commit would not establish exact equivalence. |
| ZombieBuddy | 2.3.2 | Installed JAR manifest, Workshop VERSION/mod.info, existing console |
| Loader copies | Installed and Workshop ZombieBuddy JARs are byte-identical; installed and Workshop zbNative.dll copies also match | Binary inventory; DLL SHA-256 `c2ae9335e717ee24b2f4a40d1a3bf77f1519762a72a0459e766a2bbafc077f6c` |
| JVM | Azul Zulu 25.0.1+8-LTS, Windows x86_64 | Bundled `jre64/release` and historical `java.runtime.version` |
| Bytecode | PZ3D classes major 69 (Java 25); inspected game classes major 69; ZombieBuddy classes major 61 (Java 17) | Direct class-file parser, `*-class-signatures.json` |
| LWJGL | 3.4.1 stable, bundled inside projectzomboid.jar; LWJGLX compatibility facade also present | Decompiled `org.lwjgl.Version` constants; archive entries |
| Graphics | OpenGL; existing log: `4.6.0 NVIDIA 581.42` | Existing console. This is driver context evidence from a previous run, not a new GL query. |
| GPU | Log enumerates RTX 4090 (24564 MB) and AMD integrated graphics; OpenGL string identifies NVIDIA | Existing console; runtime GPU selection still needs testing |
| Launch flags | `-agentlib:zbNative`, `--enable-native-access=ALL-UNNAMED`, `--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED`, `-Xmx3072m` | Copied `ProjectZomboid64.json`; alternate launch paths can differ |
| Local PZ3D settings | nativeLootCache, skipIsoPreparation, suppressIsoDraw all true | `research/evidence/PZ3D.properties`, existing log agrees |
| OpenXR bundled? | No `org/lwjgl/openxr/` classes or OpenXR native loader entry in installed game archive | Archive scan |
| Existing native access | WGL and GLFW native access classes present; `Display.getWindow()` is public | Bytecode signatures / vanilla Display decompilation |
| Runtime discovery | 64-bit `HKLM\SOFTWARE\Khronos\OpenXR\1` absent; process `XR_RUNTIME_JSON` unset; default Steam library SteamVR directory absent | `evidence/openxr-runtime-discovery.json`. This does not prove that no runtime exists anywhere on disk. No runtime or HMD was initialized. |

Original locations:

```text
C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid
C:\Program Files (x86)\Steam\steamapps\workshop\content\108600\3807334881\mods\pz3d
C:\Program Files (x86)\Steam\steamapps\workshop\content\108600\3619862853\mods\ZombieBuddy
%USERPROFILE%\Zomboid\console.txt
%USERPROFILE%\Zomboid\Lua\PZ3D.properties
```

Authoritative SHA-256 fingerprints:

| File | SHA-256 |
|---|---|
| projectzomboid.jar | `80e405a4bfc42f6072e75b3735f458a6514143da011d3226007ded305a442f44` |
| PZ3D-0.2.2.jar | `75cf9b39851b2a8f71fd9e3e3d30620ef1f5c5006e0f1af9bf974b4a800486a5` |
| ZombieBuddy.jar | `6dd95cedce60f03bf8b8cefd0d19eb156230e0d54bffa07de9da5212a06c7be6` |

PZ3D mod.info recommends at least 4 GB heap while this launcher JSON specifies 3 GB. No setting was changed. Stereo allocations and scene retention add memory pressure; the actual launch heap must be measured in a later test rather than assumed from one configuration file.
