# Proposed future project structure

## Design goals

This proposal reorganizes the plugin around dependency boundaries rather than the current flat list of source files. It aims to:

1. keep SBT and Zinc APIs out of reusable logic;
2. keep Android SDK/build APIs out of the SBT adapter layer where practical;
3. preserve the published plugin identity, user-facing `android.*` API, task behavior, and Scripted fixtures during migration;
4. establish enforceable module boundaries incrementally, instead of moving files into directories and assuming that makes them independent; and
5. make SBT-version-sensitive code easy to locate and test.

This is a target design, not a description of the current checkout. The current structure and per-file responsibilities are in [project-structure.md](project-structure.md).

## Recommended code structure
Use this section as a reference, and rewrite other parts of this document.

Only one SBT project is needed. Use three folders under `src/main/scala/android/` to separate the three main responsibilities.
- `coreLogic` for SBT- and Android-independent logic, almost pure Scala/JDK code, and reusable algorithms and data models.
- `androidTools` for Android related operations like resource processing, DEX, packaging, lint, SDK repository, ProGuard, signing, and other Android SDK/build APIs. 
- `sbtPlugin` for SBT integration.

proposed code structure:
```text
src/main/scala/android/
├── coreLogic/           # Reusable Scala/JDK logic; no SBT or Android APIs
├── androidTools/                          # Android SDK/build operations; no SBT APIs
├── sbtPlugin/                             # Published SBT plugin and compatibility facade

src/test/scala/android/
├── coreLogic/           # Unit tests for coreLogic
├── androidTools/       # Unit tests for androidTools
├── sbtPlugin/          # Unit tests for sbtPlugin
```

## Module responsibilities and rules

### `coreLogic`

Contains algorithms and data models that do not import `sbt.*`, `xsbti.*`, or `com.android.*`. It may depend on JDK/Scala APIs and narrowly selected libraries such as ASM/Javassist, as needed. “CoreLogic” does not automatically mean pure: network access, filesystem access, and concurrency still need explicit treatment.

Initial candidates, after refactoring their SBT file/path utilities out:

- bytecode scanning algorithms from `MethodCounter`, `NativeFinder`, and `ReferenceFinder`;
- jarsigner argument construction from `SignAndroidJar`;
- plain layout/path value types extracted from `ProjectLayout` and `BuildOutput`;
- update response models/decoding from `UpdateChecker`, with network transport supplied behind a small interface.

### `android`

Contains operations that use Android SDK/build/lint APIs but do not know about SBT tasks, state, logging, caching, or file abstractions. Prefer explicit input/output models and injected service interfaces for process execution and diagnostics. Android dependencies belong here when their classes are needed by engine code.

Potential contents include Android-specific resource processing, DEX, packaging, lint, SDK repository, ProGuard, and signing operations. This is a later extraction target: current files such as `Resources.scala`, `Packaging.scala`, `androidRepo.scala`, and `sdkInterfaces.scala` mix Android APIs with SBT utilities and must not be moved wholesale into this module.

### `plugin`

Owns all SBT integration and the published AutoPlugin surface:

- public plugins, keys, DSL and compatibility facade;
- settings composition and SBT task/command definitions;
- gathering SBT state, task inputs, classpaths, streams, and caches;
- SBT implementations of logging, process, file, repository, and diagnostics interfaces;
- calls into `core` and `android`;
- SBT/Zinc compatibility code isolated under `compatibility/`.

`AndroidTestSettings`, `SbtInternals`, `VariantSettings`, and the `sbt.compat` bridge are version-sensitive and should remain clearly inside this boundary. Do not expose SBT internal types from the reusable modules.

## Dependency graph

```text
plugin ──depends on──> android ──depends on──> core
    ├─────depends on───────────────────────────> core
    └─────uses SBT/Zinc APIs
android ──uses Android SDK/build APIs
```

More explicitly:

- `core` depends on neither SBT nor Android SDK/build APIs.
- `android` may depend on `core` and Android APIs, but not on SBT.
- `plugin` depends on SBT plus the lower-level libraries/modules and is the only module that defines SBT keys, tasks, commands, and plugin entry points.
- SBT/Zinc internals are accessed only from the plugin compatibility edge; ordinary task code should call a narrow plugin-owned abstraction where feasible.

The diagram shows project dependencies: the plugin calls the Android and core libraries, and the Android library can reuse core. The plugin adapts SBT values to their inputs and converts results back into SBT tasks and logs. Separate SBT subprojects enforce these boundaries more strongly than directory names alone.

## Mapping from the current source files

| Current source                                                                                                                                                                     | Proposed destination / treatment                                                                                                                |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| `AndroidAppPlugins.scala`, `Keys.scala`, `package.scala`, `dsl.scala`                                                                                                              | `plugin` public facade and DSL; retain existing public package/type names initially.                                                            |
| `AndroidGlobalPlugin.scala`, `AndroidProjectSettings.scala`, `AndroidAppSettingsPlugin.scala`, `AndroidLibSettings.scala`, `AndroidJarSettings.scala`, `AndroidTestSettings.scala` | `plugin/settings/`; retain SBT settings and project wiring here.                                                                                |
| `tasks.scala`, `aggregates.scala`, `TaskBase.scala`                                                                                                                                | `plugin/tasks/`; split task input collection from operations only as interfaces are introduced.                                                 |
| `commands.scala`, `parsers.scala`                                                                                                                                                  | `plugin/commands/`; keep SBT parser/state integration here. Extract only standalone parsing/model logic if useful.                              |
| `compat.scala`, `SbtInternals.scala`, `VariantSettings.scala`                                                                                                                      | `plugin/compatibility/`; keep SBT internal access isolated. Zinc-specific code from `AndroidTestSettings.scala` belongs at this edge too.       |
| `SignAndroidJar.scala`                                                                                                                                                             | `core` after preserving API compatibility and adding unit tests.                                                                                |
| `MethodCounter.scala`, `NativeFinder.scala`, `ReferenceFinder.scala`                                                                                                               | Move SBT-independent algorithms to `core`; retain any SBT traversal/caching adapters in `plugin`.                                               |
| `ProjectLayout.scala`                                                                                                                                                              | Split plain layout values into `core`; keep Android/SBT-aware resolution in `android` or `plugin` according to its remaining dependencies.      |
| `UpdateChecker.scala`                                                                                                                                                              | Put the independent model/codec in `core`; keep network or plugin-specific integration in the appropriate boundary.                             |
| `androidRepo.scala`, `AndroidLint.scala`, `Resources.scala`, `Dex.scala`, `Packaging.scala`                                                                                        | Move Android-only operations to `android` after separating SBT logging, task streams, file utilities, and caches; keep task wiring in `plugin`. |
| `dependencies.scala`, `proguard.scala`, `retrolambda.scala`, `signing.scala`, `sdkInterfaces.scala`                                                                                | Treat as mixed/integration code initially; split by actual SBT/Android type usage rather than placing each entire file in a nominal layer.      |
| `PluginFail.scala`                                                                                                                                                                 | Keep SBT exception conversion in `plugin`; define neutral error/result types in core only if a real consumer needs them.                        |

## Build and test layout

The proposed end state uses conventional source roots (`src/main/scala`, `src/main/resources`, `src/test/scala`) for each project. This should happen only after the current custom `src/` and `resources/` roots are accounted for. The existing Scripted fixtures can remain at repository root initially, with the plugin project explicitly pointing its Scripted task to `../sbt-test`; moving them is not required for code modularity.

A multi-project build has an unpublished aggregate root and three subprojects, with version/dependency definitions centralized in `build.sbt` or a small build helper:

```text
root (aggregate only; not published)
├── core (ordinary library; no SBT or Android SDK APIs)
├── android (ordinary library; Android APIs, no SBT API; depends on core)
└── plugin (sbtPlugin := true; depends on core and android)
```

Before publishing separate modules, decide how plugin users receive them: either the plugin artifact declares their released artifacts as runtime dependencies, or the build assembles a deliberate self-contained artifact. Avoid relying on local `dependsOn` behavior that does not reproduce the published plugin classpath. Keep module versions aligned, verify SBT plugin cross-version metadata, and test resolution from a clean consumer build.

Test responsibilities:

- `core` unit tests: bytecode edge cases, argument construction, layout/value logic, and codec behavior without SBT or Android SDK setup.
- `android` tests: Android-operation behavior with injected process/logging services; keep environment-dependent SDK tests explicit.
- `plugin` unit tests: key/settings/task wiring and SBT-specific adapters.
- Scripted tests: continue to validate actual plugin behavior in consumer builds across the existing `android-sdk-plugin`, `gradle-build`, and `no-travis` fixture groups. Record any Android SDK/device prerequisites per fixture instead of making them implicit.

## Public API and compatibility constraints

- Preserve the plugin artifact coordinates and public `android.*` plugin entry points unless a separately reviewed breaking release is intended.
- Keep existing key names, key types, DSL syntax, and package-level aliases stable while moving implementations. `Keys.scala` contains Android types as well as SBT keys, so it cannot be split just by moving the file.
- Keep macro definitions and implementations together initially; Scala macro compilation and visibility can be sensitive to package/project moves.
- Maintain plugin resource names and lookup paths while moving templates from the root `resources/` directory.
- Avoid introducing cross-project references to SBT or Zinc types through shared DTOs or service interfaces.
- Keep compatibility code specific to SBT versions behind a narrow, documented edge and test the supported launcher/plugin matrix.

## Suggested migration sequence

1. **Stabilize behavior first.** Record the existing build, compile, unit-test, `publishLocal`, and Scripted results before changing source roots or module boundaries. Keep the SBT migration and structural refactor as separate changes where possible.
2. **Normalize layout without semantic changes.** Move plugin files under a conventional plugin source root and create package-aligned directories. Update source/resource roots and verify generated resource lookups and all fixture groups.
3. **Introduce import/dependency rules.** Add lightweight checks preventing `core` from importing SBT or Android APIs and preventing `android` from importing SBT APIs. Comments should explain non-obvious integration imports, not annotate every import mechanically.
4. **Extract low-risk core seams.** Start with jarsigner argument construction and bytecode algorithms; replace SBT-specific file traversal/cache/reporting dependencies with explicit input, result, and error interfaces. Add unit tests around the extracted behavior.
5. **Extract Android operations selectively.** Define process, logging, diagnostics, filesystem, and cache interfaces; move only operations that compile without SBT into `android`.
6. **Establish the three subprojects.** Once the boundaries compile independently, configure the aggregate root and `core`, `android`, and `plugin` projects. Verify binary/runtime dependency resolution, plugin packaging, and consumer builds.
7. **Isolate SBT/Zinc compatibility.** Move internal API touchpoints behind small adapters and validate them against every supported SBT version before modifying internals.
8. **Run the full regression matrix.** Compile and test all modules, publish locally, run all intended Scripted fixture groups (including `gradle-build` if supported), then test an external consumer project.

## Explicit non-goals for the first restructuring pass

- No immediate rewrite of Android build operations.
- No automatic conversion of every file into a separate module.
- No change to public plugin keys, artifacts, defaults, or Android build outputs solely to fit a directory tree.
- No claim that all Android APIs can be isolated from SBT without adapter work.
- No simultaneous SBT-version migration, dependency modernization, and architecture split in one unreviewable change.
