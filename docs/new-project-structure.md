# Proposed future project structure

## Design goals

This proposal reorganizes the plugin source tree around responsibility areas rather than the current flat list of source files. It aims to:

1. make SBT-independent, Android-specific, and SBT integration code easy to find;
2. reduce unnecessary dependencies between those categories where practical, while recognizing that a single SBT project cannot enforce those boundaries at compile time;
3. preserve the published plugin identity, user-facing `android.*` API, task behavior, and Scripted fixtures during migration;
4. retain one SBT project, using source folders and import conventions for organization rather than creating subprojects; and
5. make SBT-version-sensitive code easy to locate and test.

Use lower camelCase for Scala source and test filenames. Keep Scala declarations, package names, and public APIs unchanged when renaming files.

This is a target design, not a description of the current checkout. The current structure and per-file responsibilities are in [project-structure.md](project-structure.md).

## Recommended code structure

Only one SBT project is needed. Use three folders under `src/main/scala/android/` to separate the main responsibilities:

- `coreLogic` for SBT- and Android-independent logic, mostly Scala/JDK code, reusable algorithms, and data models.
- `androidTools` for Android-related operations such as resource processing, DEX, packaging, lint, SDK repository access, ProGuard, signing, and other Android SDK/build APIs.
- `sbtPlugin` for SBT integration.

```text
.
├── src/
│   ├── main/
│   │   ├── scala/android/
│   │   │   ├── coreLogic/         # Reusable logic; avoid SBT and Android APIs
│   │   │   ├── androidTools/      # Android SDK/build operations
│   │   │   └── sbtPlugin/         # SBT plugins, settings, tasks, commands, adapters
│   │   └── resources/          # Plugin templates and configuration
│   └── test/scala/android/
│       ├── coreLogic/          # Tests for reusable logic
│       ├── androidTools/       # Tests for Android-facing operations
│       └── sbtPlugin/          # Tests for SBT wiring and adapters
└── sbt-test/                     # Existing Scripted consumer fixtures
```

The three directories are organizational source folders in one project, not separate Scala packages, projects, or artifacts. Existing files can remain declared in package `android` while residing in these nested source directories; do not mechanically change package declarations to `android.coreLogic`, `android.androidTools`, or `android.sbtPlugin`. This preserves existing public `android.*` types. The folder names communicate intended ownership, but do not constrain imports or classpaths.

## Responsibilities and placement rules

### `coreLogic`

Contains algorithms and data models that should not import `sbt.*`, `xsbti.*`, or `com.android.*`. It may depend on JDK/Scala APIs and selected libraries such as ASM/Javassist, as needed. “Core logic” does not automatically mean pure: network access, filesystem access, and concurrency still need explicit treatment. Since all folders compile in the same project, this rule is architectural guidance rather than a compiler-enforced restriction.

Currently placed here are `SignAndroidJar`, `UpdateChecker`, `MethodCounter`, `NativeFinder`, and `ReferenceFinder`. The bytecode scanners now use JDK file/stream APIs rather than SBT traversal utilities; ASM/Javassist remain external dependencies. `UpdateChecker` is SBT-independent but performs network I/O and uses Argonaut. A plain layout model extracted from `ProjectLayout`/`BuildOutput` is a possible later addition.

### `androidTools`

Groups Android-focused implementation files: `AndroidLint`, `androidRepo`, `Dex`, `Packaging`, `ProjectLayout`, `Resources`, `proguard`, `retrolambda`, and `signing`. Several of these still import SBT APIs for task streams, logging, caching, or file operations. Their placement reflects their primary Android operation, not a claim that the folder is already SBT-independent. Extract SBT-specific adapters to `sbtPlugin` in later steps where that can be done without changing behavior.

### `sbtPlugin`

Contains the published SBT plugin implementation and SBT-facing adapters. It currently holds the plugin entry points, keys, DSL, settings, task orchestration, commands/parsers, dependencies, and SBT compatibility helpers:

- plugin entry points, public keys, DSL, and compatibility facade;
- settings, task and command definitions, including collection of SBT state and task inputs;
- SBT logging, process, file, repository, and cache integration;
- calls into reusable logic and Android operations;
- SBT/Zinc compatibility code.

In particular, `AndroidAppPlugins`, `AndroidAppSettingsPlugin`, `AndroidGlobalPlugin`, `AndroidJarSettings`, `AndroidLibSettings`, `AndroidProjectSettings`, `AndroidTestSettings`, `Keys`, `PluginFail`, `SbtInternals`, `TaskBase`, `VariantSettings`, `aggregates`, `commands`, `compat`, `dependencies`, `dsl`, `package`, `parsers`, and `tasks` are here. Retain the existing public `android.*` entry points and key API. Avoid renaming packages solely to match directory names; source folders are organizational and stable types remain in their current packages.

## Dependency direction

```text
sbtPlugin  ──uses──> androidTools  ──may use──> coreLogic
    └─────────────────────────────────────────> coreLogic
androidTools ──────> Android SDK/build APIs
```

- `coreLogic` should avoid SBT and Android SDK/build APIs. It may use Scala/JDK APIs and selected third-party libraries.
- `androidTools` owns Android-facing operations; task wiring, SBT state, and SBT-specific logging remain in `sbtPlugin`.
- `sbtPlugin` can call both categories and adapts their inputs/results to SBT tasks and logs.
- Keep SBT/Zinc internal APIs inside clearly marked compatibility code in `sbtPlugin`.
- The current `androidTools` folder still contains direct SBT imports in several files. The arrows describe intended responsibility flow, not a fully achieved dependency boundary; extract those adapters incrementally.

These are source-organization rules within one compilation project, not enforceable module dependencies. The compiler will not prevent imports across the folders. Keep the boundaries visible through folder placement, import review, code review, and focused tests. Allow mixed implementations to remain in `sbtPlugin` until they can be separated without changing behavior.

## Mapping from the current source files

In this table, `coreLogic/`, `androidTools/`, and `sbtPlugin/` are relative to `src/main/scala/android/` unless a full path is shown. These are source directories only; keep existing Scala package declarations stable.

| Current source                                                                                                                                                                   | Proposed destination / treatment                                                                                             |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `signAndroidJar.scala`, `updateChecker.scala`, `methodCounter.scala`, `nativeFinder.scala`, `referenceFinder.scala`                                                              | `src/main/scala/android/coreLogic/`; no direct SBT or Android imports after replacing scanner file traversal/stream helpers. |
| `androidLint.scala`, `androidRepo.scala`, `dex.scala`, `packaging.scala`, `projectLayout.scala`, `resources.scala`                                                               | `src/main/scala/android/androidTools/`; Android-focused code, some still coupled to SBT APIs.                                |
| `proguard.scala`, `retrolambda.scala`, `signing.scala`                                                                                                                           | `src/main/scala/android/androidTools/`; Android build/signing operations with remaining SBT integration.                     |
| `androidAppPlugins.scala`, `androidAppSettingsPlugin.scala`, `androidGlobalPlugin.scala`, `androidJarSettings.scala`, `androidLibSettings.scala`, `androidProjectSettings.scala` | `src/main/scala/android/sbtPlugin/`; public plugin composition and project settings.                                         |
| `androidTestSettings.scala`, `keys.scala`, `pluginFail.scala`, `sbtInternals.scala`, `taskBase.scala`, `variantSettings.scala`                                                   | `src/main/scala/android/sbtPlugin/`; SBT keys, test integration, task helpers, and compatibility code.                       |
| `aggregates.scala`, `commands.scala`, `compat.scala`, `dependencies.scala`, `dsl.scala`, `package.scala`, `parsers.scala`, `tasks.scala`                                         | `src/main/scala/android/sbtPlugin/`; SBT task inputs, commands, dependency wiring, DSL, and orchestration.                   |
| `sdkInterfaces.scala`                                                                                                                                                            | `src/main/scala/android/sbtPlugin/`; adapters bridging Android interfaces to SBT process/logging APIs.                       |

## Build and test layout

Keep one SBT project and use conventional source roots: `src/main/scala` for the three source categories, `src/main/resources` for templates/configuration, and `src/test/scala` for unit tests. This changes the current custom `src/` and `resources/` roots, so update `build.sbt` deliberately and verify resource lookup paths. Keep Scripted fixtures at the repository root in `sbt-test/` and configure the existing plugin build to use that directory.

Test responsibilities:

- `coreLogic` unit tests: bytecode edge cases, argument construction, layout/value logic, and decoding behavior that does not require Android SDK or SBT setup.
- `androidTools` tests: Android-operation behavior with injected process/logging services; keep environment-dependent SDK tests explicit.
- `sbtPlugin` tests: key/settings/task wiring and SBT-specific adapters.
- Scripted tests: continue to validate actual plugin behavior in consumer builds across the existing `android-sdk-plugin`, `gradle-build`, and `no-travis` fixture groups. Record any Android SDK/device prerequisites per fixture instead of making them implicit.

## Public API and compatibility constraints

- Preserve the plugin artifact coordinates and public `android.*` plugin entry points unless a separately reviewed breaking release is intended.
- Keep existing key names, key types, DSL syntax, and package-level aliases stable while moving implementations. `keys.scala` contains Android types as well as SBT keys, so it cannot be split just by moving the file.
- Keep macro definitions and implementations together initially; Scala macro compilation and visibility can be sensitive to package/project moves.
- Maintain plugin resource names and lookup paths while moving templates from the root `resources/` directory.
- Avoid introducing SBT or Zinc types into `coreLogic` APIs. Keep category boundaries meaningful even though the folders share one compilation classpath.
- Keep compatibility code specific to SBT versions behind a narrow, documented edge and test the supported launcher/plugin matrix.

## Suggested migration sequence

1. **Stabilize behavior first.** Record the existing build, compile, unit-test, `publishLocal`, and Scripted results before moving sources. Keep the SBT-version migration and source reorganization as separate changes where possible.
2. **Normalize source roots.** Move the current flat `src/` Scala files to `src/main/scala` and resources to `src/main/resources`; update the custom source/resource settings in `build.sbt` and verify resource lookup paths.
3. **Organize by responsibility.** Move files or extract smaller components into `src/main/scala/android/coreLogic`, `src/main/scala/android/androidTools`, and `src/main/scala/android/sbtPlugin`. Keep package declarations unchanged unless a package/API change is separately justified; preserve public `android.*` entry points and use compatibility facades where needed.
4. **Separate mixed code gradually.** Keep JDK-only signing argument construction and bytecode algorithms in `coreLogic`. For Android-focused files already in `androidTools`, move SBT traversal, task streams, caching, and logging into `sbtPlugin` adapters where practical; do not treat folder placement alone as dependency isolation.
5. **Add focused tests and import review.** Test reusable logic without SBT/Android setup, and test Android and SBT adapters at their respective boundaries. Review imports to catch accidental dependency direction changes; add brief comments for non-obvious integration imports rather than commenting every import. Folders alone do not enforce these rules.
6. **Keep the build single-project.** Do not add SBT subprojects or separately published artifacts as part of this reorganization. Retain the existing root Scripted fixture layout and verify all intended groups, including `gradle-build` if supported.
7. **Isolate SBT/Zinc compatibility.** Keep internal API touchpoints identifiable within `sbtPlugin`, behind small adapters where feasible, and validate them against supported SBT versions.
8. **Run the regression matrix.** Compile and test, publish locally, run the intended Scripted fixtures, and test an external consumer build after source moves.

## Explicit non-goals for the first restructuring pass

- No immediate rewrite of Android build operations.
- No creation of separate SBT subprojects or published artifacts.
- No change to public plugin keys, artifacts, defaults, or Android build outputs solely to fit a directory tree.
- No claim that source folders enforce dependency boundaries or that all Android APIs can be isolated from SBT without adapter work.
- No simultaneous SBT-version migration, dependency modernization, and architecture split in one unreviewable change.
