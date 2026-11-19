# Proposed future project structure

## Design goals

This proposal reorganizes the plugin around dependency boundaries rather than the current flat list of source files. It aims to:

1. keep SBT and Zinc APIs out of reusable logic;
2. keep Android SDK/build APIs out of the SBT adapter layer where practical;
3. preserve the published plugin identity, user-facing `android.*` API, task behavior, and Scripted fixtures during migration;
4. establish enforceable module boundaries incrementally, instead of moving files into directories and assuming that makes them independent; and
5. make SBT-version-sensitive code easy to locate and test.

This is a target design, not a description of the current checkout. The current structure and per-file responsibilities are in [project-structure.md](project-structure.md).

## Recommended code structure 1

The target is organized into five categories: 
- root build configuration, 
- reusable plugin-neutral core, 
- Android-specific operations, 
- the SBT plugin/integration layer, and 
- shared resources plus consumer-style tests. 

Dependencies should flow upward from core to Android operations to SBT integration; the SBT/Zinc compatibility code stays at the outer edge. The tree below shows the intended locations and boundaries.

```text
.
├── build.sbt                         # Aggregate, shared versions, dependency alignment
├── project/
│   ├── build.properties              # SBT launcher version
│   ├── plugins.sbt                   # Build-definition plugins
│   └── ...                           # Build helpers only; no plugin runtime code
├── modules/
│   ├── plugin-core/                  # Plain Scala/JDK + explicitly selected libraries
│   │   ├── src/main/scala/android/core/
│   │   │   ├── bytecode/             # Bytecode algorithms and result models
│   │   │   ├── signing/              # Jarsigner argument model/builder
│   │   │   ├── layout/               # Pure path/layout model, if separated from SDK/SBT
│   │   │   └── update/               # Update model and decoding, transport behind an API
│   │   └── src/test/scala/android/core/
│   └── android-engine/               # Android APIs, with no SBT dependencies
│       ├── src/main/scala/android/engine/
│       │   ├── sdk/                  # Android SDK-facing operations
│       │   ├── resources/            # Android resource/build operations
│       │   ├── dex/                  # DEX operation inputs/results and implementations
│       │   ├── packaging/            # APK/AAR operations
│       │   ├── lint/                 # Android lint integration
│       │   ├── proguard/             # ProGuard operation code
│       │   └── signing/              # Android signing integration (not SBT task wiring)
│       └── src/test/scala/android/engine/
├── plugin/
│   ├── src/main/scala/android/       # Published SBT plugin and compatibility facade
│   │   ├── AndroidAppPlugins.scala   # Stable AutoPlugin entry points
│   │   ├── Keys.scala                # Stable public keys/facades
│   │   ├── package.scala              # Stable package-level DSL facade
│   │   ├── dsl/                       # SBT setting helpers and macros
│   │   ├── settings/                  # Common, app, library, JAR, and test settings
│   │   ├── tasks/                     # SBT task definitions and orchestration
│   │   ├── commands/                  # SBT commands and parsers
│   │   ├── adapters/                  # SBT Logger/process/cache/filesystem bridges
│   │   └── compatibility/             # SBT internals and Zinc compatibility edge
│   ├── src/main/resources/            # Plugin-shipped templates/configuration
│   ├── src/test/scala/android/        # Plugin-local unit tests, if needed
│   └── src/sbt-test/                  # Or configure the existing root fixture directory
├── resources/                         # Transitional location; move into plugin resources
└── sbt-test/                          # Existing Scripted fixtures retained during migration
    ├── android-sdk-plugin/
    ├── gradle-build/
    └── no-travis/
```

The `modules/plugin-core` and `modules/android-engine` names describe desired dependency boundaries, not a requirement to create both immediately. If analysis shows that a candidate still needs SBT or Android types, keep it in `plugin/` until its API is genuinely independent. The `plugin` directory should be the only published SBT plugin artifact; the other modules, if created, are ordinary library projects.

## Module responsibilities and rules

### `plugin-core`

Contains algorithms and data models that do not import `sbt.*`, `xsbti.*`, or `com.android.*`. It may depend on JDK/Scala APIs and narrowly selected libraries such as ASM/Javassist, as needed. “Core” does not automatically mean pure: network access, filesystem access, and concurrency still need explicit treatment.

Initial candidates, after refactoring their SBT file/path utilities out:

- bytecode scanning algorithms from `MethodCounter`, `NativeFinder`, and `ReferenceFinder`;
- jarsigner argument construction from `SignAndroidJar`;
- plain layout/path value types extracted from `ProjectLayout` and `BuildOutput`;
- update response models/decoding from `UpdateChecker`, with network transport supplied behind a small interface.

### `android-engine`

Contains operations that use Android SDK/build/lint APIs but do not know about SBT tasks, state, logging, caching, or file abstractions. Prefer explicit input/output models and injected service interfaces for process execution and diagnostics. Android dependencies belong here when their classes are needed by engine code.

Potential contents include Android-specific resource processing, DEX, packaging, lint, SDK repository, ProGuard, and signing operations. This is a later extraction target: current files such as `Resources.scala`, `Packaging.scala`, `androidRepo.scala`, and `sdkInterfaces.scala` mix Android APIs with SBT utilities and must not be moved wholesale into this module.

### `plugin`

Owns all SBT integration and the published AutoPlugin surface:

- public plugins, keys, DSL and compatibility facade;
- settings composition and SBT task/command definitions;
- gathering SBT state, task inputs, classpaths, streams, and caches;
- SBT implementations of logging, process, file, repository, and diagnostics interfaces;
- calls into `plugin-core` and `android-engine`;
- SBT/Zinc compatibility code isolated under `compatibility/`.

`AndroidTestSettings`, `SbtInternals`, `VariantSettings`, and the `sbt.compat` bridge are version-sensitive and should remain clearly inside this boundary. Do not expose SBT internal types from the reusable modules.

## Dependency graph

```text
                  plugin-core
                 ↗            ↘
SBT host APIs → plugin  ← android-engine ← Android SDK/build APIs
                  │
                  └── SBT/Zinc compatibility edge
```

More explicitly:

- `plugin-core` depends on neither SBT nor Android SDK/build APIs.
- `android-engine` may depend on `plugin-core` and Android APIs, but not on SBT.
- `plugin` depends on SBT plus the lower-level libraries/modules and is the only module that defines SBT keys, tasks, commands, and plugin entry points.
- SBT/Zinc internals are accessed only from the plugin compatibility edge; ordinary task code should call a narrow plugin-owned abstraction where feasible.

The diagram is directional by ownership: plugin code adapts SBT values to engine/core inputs, invokes work, then adapts results back into SBT tasks and logs. A package or directory name alone does not enforce this graph; separate SBT subprojects should enforce it once the APIs are stable.

## Mapping from the current source files

| Current source                                                                                                                                                                     | Proposed destination / treatment                                                                                                                                                       |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AndroidAppPlugins.scala`, `Keys.scala`, `package.scala`, `dsl.scala`                                                                                                              | `plugin` public facade and DSL; retain existing public package/type names initially.                                                                                                   |
| `AndroidGlobalPlugin.scala`, `AndroidProjectSettings.scala`, `AndroidAppSettingsPlugin.scala`, `AndroidLibSettings.scala`, `AndroidJarSettings.scala`, `AndroidTestSettings.scala` | `plugin/settings/`; retain SBT settings and project wiring here.                                                                                                                       |
| `tasks.scala`, `aggregates.scala`, `TaskBase.scala`                                                                                                                                | `plugin/tasks/`; split task input collection from operations only as interfaces are introduced.                                                                                        |
| `commands.scala`, `parsers.scala`                                                                                                                                                  | `plugin/commands/`; keep SBT parser/state integration here. Extract only standalone parsing/model logic if useful.                                                                     |
| `compat.scala`, `SbtInternals.scala`, `VariantSettings.scala`                                                                                                                      | `plugin/compatibility/`; keep SBT internal access isolated. Zinc-specific code from `AndroidTestSettings.scala` belongs at this edge too.                                              |
| `SignAndroidJar.scala`                                                                                                                                                             | `plugin-core/signing/` after preserving API compatibility and adding unit tests.                                                                                                       |
| `MethodCounter.scala`, `NativeFinder.scala`, `ReferenceFinder.scala`                                                                                                               | Separate algorithm implementation into `plugin-core/bytecode/`; retain SBT traversal/caching adapters under `plugin/tasks/` as needed.                                                 |
| `ProjectLayout.scala`                                                                                                                                                              | Split into a plain layout model in `plugin-core/layout/` and an SBT/Android-aware resolver/adapter in `plugin/`. Do not move the current file unchanged.                               |
| `UpdateChecker.scala`                                                                                                                                                              | Separate model/codec from transport; place SBT-independent pieces in `plugin-core/update/`, network execution in a service or plugin adapter according to desired testability.         |
| `androidRepo.scala`, `AndroidLint.scala`, `Resources.scala`, `Dex.scala`, `Packaging.scala`                                                                                        | Keep in `plugin` initially. Extract Android-only operations into `android-engine/` after SBT logging, task streams, file utilities, and caches are passed through explicit boundaries. |
| `dependencies.scala`, `proguard.scala`, `retrolambda.scala`, `signing.scala`, `sdkInterfaces.scala`                                                                                | Treat as mixed/integration code initially; split by actual SBT/Android type usage rather than placing each entire file in a nominal layer.                                             |
| `PluginFail.scala`                                                                                                                                                                 | Keep SBT exception conversion in `plugin`; define neutral error/result types in core only if a real consumer needs them.                                                               |

## Build and test layout

The proposed end state uses conventional source roots (`src/main/scala`, `src/main/resources`, `src/test/scala`) for each project. This should happen only after the current custom `src/` and `resources/` roots are accounted for. The existing Scripted fixtures can remain at repository root initially, with the plugin project explicitly pointing its Scripted task to `../sbt-test`; moving them is not required for code modularity.

A future multi-project build would have an unpublished aggregate root and a published plugin project, with version/dependency definitions centralized in `build.sbt` or a small build helper:

```text
root (aggregate only; not published)
├── pluginCore (ordinary library; no SBT plugin API)
├── androidEngine (ordinary library; Android APIs, no SBT plugin API)
└── plugin (sbtPlugin := true; dependsOn/published dependencies above)
```

Before publishing separate modules, decide how plugin users receive them: either the plugin artifact declares their released artifacts as runtime dependencies, or the build assembles a deliberate self-contained artifact. Avoid relying on local `dependsOn` behavior that does not reproduce the published plugin classpath. Keep module versions aligned, verify SBT plugin cross-version metadata, and test resolution from a clean consumer build.

Test responsibilities:

- `pluginCore` unit tests: bytecode edge cases, argument construction, layout/value logic, and codec behavior without SBT or Android SDK setup.
- `androidEngine` tests: Android-operation adapters and deterministic behavior with injected process/logging services; keep environment-dependent SDK tests explicit.
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
3. **Introduce import/dependency rules.** Mark layer boundaries and add a lightweight check or test preventing `plugin-core` packages from importing SBT/Android APIs. Comments should explain non-obvious integration imports, not annotate every import mechanically.
4. **Extract low-risk core seams.** Start with jarsigner argument construction and bytecode algorithms; replace SBT-specific file traversal/cache/reporting dependencies with explicit input, result, and error interfaces. Add unit tests around the extracted behavior.
5. **Extract Android operations selectively.** Define process, logging, diagnostics, filesystem, and cache interfaces; move only operations that compile without SBT into `android-engine`.
6. **Create subprojects once boundaries compile independently.** Add `pluginCore` first, then `androidEngine` only where it has a stable API. Verify binary/runtime dependency resolution, plugin packaging, and consumer builds after each split.
7. **Isolate SBT/Zinc compatibility.** Move internal API touchpoints behind small adapters and validate them against every supported SBT version before modifying internals.
8. **Run the full regression matrix.** Compile and test all modules, publish locally, run all intended Scripted fixture groups (including `gradle-build` if supported), then test an external consumer project.

## Explicit non-goals for the first restructuring pass

- No immediate rewrite of Android build operations.
- No automatic conversion of every file into a separate module.
- No change to public plugin keys, artifacts, defaults, or Android build outputs solely to fit a directory tree.
- No claim that all Android APIs can be isolated from SBT without adapter work.
- No simultaneous SBT-version migration, dependency modernization, and architecture split in one unreviewable change.
