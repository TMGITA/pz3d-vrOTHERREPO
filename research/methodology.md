# Methodology, verification, and resuming research

## Boundaries honored

All research outputs are inside `<workspace>`. Installation, Workshop and user-data locations were read only. JARs, launcher files, mod source/resources and native loader were copied before binary inspection. Existing console text was filtered into pertinent environment excerpts. No game launch, agent startup, mod main method, installer, VR runtime, OpenXR session or GPU test was run. External binding/native JARs are stored only as reference material. `experiments` contains only a README.

Standalone decompilers were run using the explicit bundled Java 25 executable, because PATH Java resolves to an older Java 8 launcher. This does not run Project Zomboid: arguments use `-jar` with the decompiler and copied reference paths, with no agent flags and no game classpath as executable code. The decompilers read dependencies as class-file input.

## Artifacts and reproducibility

- `scripts/collect_reference.py`: derives hashes/archive listings, selected class archive, metadata, PZ3D embedded resources, and filtered historical log evidence. It reads installed sources and updates research outputs. Preserve the initial inventory before rerunning after a game update.
- `scripts/decompile-reference.ps1`: reproduces the standalone decompilation against saved archives; requires the already downloaded tools and saved derived subset archives.
- `scripts/index_bytecode.py`: parses binary class metadata without executing classes. Produces actual access flags/descriptors and 170 patch-target mappings. Every declared patch has at least one installed method-name target; this is not a runtime assertion that all overloads are transformed.
- `scripts/verify_research.py`: compares installed/source files with their copies, verifies original binary baseline hashes, inventories external tools/libraries, checks class overlaps and documentation links. No game execution.

Important evidence files:

| File | Purpose |
|---|---|
| `evidence/binary-inventory.json` | Initial copied/installed SHA-256 values and original paths |
| `evidence/reference-file-verification.json` | Full original mod tree and launcher/native-copy comparison |
| `evidence/verification.json` | Final verification result and timestamp |
| `evidence/pz3d-class-signatures.json` | Actual installed PZ3D class major versions, access flags, fields/method descriptors |
| `evidence/vanilla-class-signatures.json` | Targeted installed vanilla/GL access metadata |
| `evidence/zombiebuddy-class-signatures.json` | Installed loader metadata |
| `evidence/patch-inventory.json` | Parsed PZ3D annotations and target candidate descriptors |
| `evidence/existing-console-excerpts.txt` | Historical version, driver, binary-load, build and framebuffer evidence |
| `evidence/PZ3D.properties` | Read-only snapshot of relevant existing mod settings |
| `evidence/openxr-runtime-discovery.json` | Limited read-only Windows runtime discovery |
| `evidence/openxr-static-compatibility.json` | Module overlap and class-version check; not a native linkage test |
| `evidence/external-artifacts.json` | Hashes of decompilers and downloaded LWJGL artifacts |

## Decompilation quality

[CFR 0.152](https://www.benf.org/other/cfr/) gave useful first-pass output but could not structure all of `Renderer.Frame.draw` and produced local-variable/type-clash artifacts. [Vineflower 1.12.0](https://github.com/Vineflower/vineflower/releases/) was then used on the complete installed PZ3D archive with the copied game/loader JARs as dependencies. Prefer its `reference/pz3d/vineflower` output. The central draw and camera paths are readable there, and key conclusions were compared across both views.

Vineflower logged failures in `FurniturePhoto.plane(...)` and `FurnitureGeometry.label(...)`, plus a repeated-processing warning. Those particular furniture helper bodies are not claimed to have been reverse engineered fully; CFR is available for further comparison. Vanilla/ZombieBuddy CFR output also contains inferred locals and occasional structuring warnings. Decompiled Java is a navigation aid and is **not claimed to compile or reproduce the binary**. Exact method identity/access comes from the separate binary parser.

The shipped ZombieBuddy Java/C source was inspected alongside decompiled installed Java. The installed and Workshop JARs/DLLs match byte-for-byte, but the shipped source was not rebuilt, so source-to-binary reproducibility is not claimed. PZ3D's embedded `dirty=True` makes replacing the binary with a public commit especially unreliable. No public PZ3D source was substituted for local inspection.

## Scope of confidence

The main renderer, frame/resource lifecycle, camera, scene upload, visibility buffers, actor capture, vehicle prediction, lighting/shadow entry points, UI texture path, vanilla scheduling/UI side effects, and ZombieBuddy loader/patch machinery were traced. Individual geometry generators, every native library, every possible particle/effect path, and every shader branch were not exhaustively verified. Those limits are explicitly carried into the stereo audit and prototype acceptance criteria.

No runtime benchmarks or safety tests were possible within the user's no-launch research boundary. Static checks establish file provenance and code capabilities, not headset operation. All proposed integration APIs and phase milestones are designs only.

## First reads for a future session

Read `final-report.md`, `stereo-audit.md`, and `prototype-plan.md`. Then open preferred `Renderer.java` at `Frame.draw`, `Frame.ad`, `Frame.postRender`; `RetainedRender.java`; `SceneUpload.java`; `ViewMath.java`; and `UiLayer.java`. Consult the binary signatures before patching any obfuscated member. If installed hashes changed, repeat the relevant binary comparison before adapting code. Do not begin implementation without the user's new approval.
