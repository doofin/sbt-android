# Proposed future project structure

## Design goals

This proposal reorganizes the plugin source tree around responsibility areas rather than the current flat list of source files. It aims to:

1. make SBT-independent, Android-specific, and SBT integration code easy to find;
2. reduce unnecessary dependencies between those categories where practical, while recognizing that a single SBT project cannot enforce those boundaries at compile time;
3. preserve the published plugin identity, user-facing `android.*` API, task behavior, and Scripted fixtures during migration;
4. retain one SBT project, using source folders and import conventions for organization rather than creating subprojects; and
5. make SBT-version-sensitive code easy to locate and test.

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

Think how current code would fit into these categories
## Responsibilities and placement rules

### `coreLogic`

Contains algorithms and data models that should not import `sbt.*`, `xsbti.*`, or `com.android.*`. It may depend on JDK/Scala APIs and selected libraries such as ASM/Javassist, as needed. “Core logic” does not automatically mean pure: network access, filesystem access, and concurrency still need explicit treatment. Since all folders compile in the same project, this rule is architectural guidance rather than a compiler-enforced restriction.

Initial candidates, after refactoring SBT-specific file/path utilities out where necessary:

- bytecode scanning algorithms from `MethodCounter`, `NativeFinder`, and `ReferenceFinder`;
- jarsigner argument construction from `SignAndroidJar`;
- plain layout/path value types extracted from `ProjectLayout` and `BuildOutput`;
- update response models/decoding from `UpdateChecker`, with network transport supplied behind a small interface.

### `androidTools`

Groups code that operates on Android SDK/build/lint APIs, including resource processing, DEX, packaging, lint, SDK repository access, ProGuard, and Android signing. Keep Android operations here when they can be expressed without SBT task, state, logger, or cache types. Where the current implementation mixes these concerns, leave it in `sbtPlugin` until a safe seam is extracted; do not move a mixed file just to match the folder name.

### `sbtPlugin`

Contains the published SBT plugin implementation and its SBT-facing adapters:

- plugin entry points, public keys, DSL, and compatibility facade;
- settings, task and command definitions, including collection of SBT state and task inputs;
- SBT logging, process, file, repository, and cache integration;
- calls into reusable logic and Android operations;
- SBT/Zinc compatibility code.

Keep `AndroidTestSettings`, `SbtInternals`, `VariantSettings`, and the `sbt.compat` bridge in this category. Retain the existing public `android.*` entry points and key API while reorganizing internal code. Avoid renaming packages solely to match directory names; Scala source directories can be used as responsibility groupings while stable types remain in their current packages.

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

These are source-organization rules within one compilation project, not enforceable module dependencies. The compiler will not prevent imports across the folders. Keep the boundaries visible through folder placement, import review, code review, and focused tests. Allow mixed implementations to remain in `sbtPlugin` until they can be separated without changing behavior.

## Mapping from the current source files

In this table, `coreLogic/`, `androidTools/`, and `sbtPlugin/` are relative to `src/main/scala/android/` unless a full path is shown. These are source directories only; keep existing Scala package declarations stable.

| Current source                                                                                                                                                                     | Proposed destination / treatment                                                                                                           |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `AndroidAppPlugins.scala`, `Keys.scala`, `package.scala`, `dsl.scala`                                                                                                              | `src/main/scala/android/sbtPlugin/`; preserve existing public `android.*` types and DSL surface.                                           |
| `AndroidGlobalPlugin.scala`, `AndroidProjectSettings.scala`, `AndroidAppSettingsPlugin.scala`, `AndroidLibSettings.scala`, `AndroidJarSettings.scala`, `AndroidTestSettings.scala` | `src/main/scala/android/sbtPlugin/`; SBT plugin composition and settings.                                                                  |
| `tasks.scala`, `aggregates.scala`, `TaskBase.scala`                                                                                                                                | `src/main/scala/android/sbtPlugin/`; task wiring and input assembly. Extract operations into `androidTools` only after removing SBT types. |
| `commands.scala`, `parsers.scala`                                                                                                                                                  | `src/main/scala/android/sbtPlugin/`; SBT command/parser and state integration.                                                             |
| `compat.scala`, `SbtInternals.scala`, `VariantSettings.scala`                                                                                                                      | `src/main/scala/android/sbtPlugin/`; isolate version-sensitive SBT internals within this category.                                         |
| `SignAndroidJar.scala`                                                                                                                                                             | `src/main/scala/android/coreLogic/`; JDK-only argument construction; preserve public API if required.                                      |
| `MethodCounter.scala`, `NativeFinder.scala`, `ReferenceFinder.scala`                                                                                                               | Put standalone scanning logic in `coreLogic/`; retain SBT traversal, caching, and reporting in `sbtPlugin/`.                               |
| `ProjectLayout.scala`                                                                                                                                                              | Split plain layout values into `coreLogic/`; keep Android/SBT-aware resolution in `androidTools/` or `sbtPlugin/`.                         |
| `UpdateChecker.scala`                                                                                                                                                              | Separate reusable model/decoding into `coreLogic/`; place network integration according to its dependencies.                               |
| `androidRepo.scala`, `AndroidLint.scala`, `Resources.scala`, `Dex.scala`, `Packaging.scala`                                                                                        | Place separable Android-only operations in `androidTools/`; leave SBT wiring, streams, caches, and logging in `sbtPlugin/`.                |
| `dependencies.scala`, `proguard.scala`, `retrolambda.scala`, `signing.scala`, `sdkInterfaces.scala`                                                                                | Treat as mixed code initially; split by actual type dependencies, keeping SBT-facing adapters in `sbtPlugin/`.                             |
| `PluginFail.scala`                                                                                                                                                                 | Keep SBT exception conversion in `sbtPlugin/`; define neutral error types in `coreLogic/` only if needed.                                  |

## Build and test layout

Keep one SBT project and use conventional source roots: `src/main/scala` for the three source categories, `src/main/resources` for templates/configuration, and `src/test/scala` for unit tests. This changes the current custom `src/` and `resources/` roots, so update `build.sbt` deliberately and verify resource lookup paths. Keep Scripted fixtures at the repository root in `sbt-test/` and configure the existing plugin build to use that directory.

Test responsibilities:

- `coreLogic` unit tests: bytecode edge cases, argument construction, layout/value logic, and decoding behavior that does not require Android SDK or SBT setup.
- `androidTools` tests: Android-operation behavior with injected process/logging services; keep environment-dependent SDK tests explicit.
- `sbtPlugin` tests: key/settings/task wiring and SBT-specific adapters.
- Scripted tests: continue to validate actual plugin behavior in consumer builds across the existing `android-sdk-plugin`, `gradle-build`, and `no-travis` fixture groups. Record any Android SDK/device prerequisites per fixture instead of making them implicit.

## Public API and compatibility constraints

- Preserve the plugin artifact coordinates and public `android.*` plugin entry points unless a separately reviewed breaking release is intended.
- Keep existing key names, key types, DSL syntax, and package-level aliases stable while moving implementations. `Keys.scala` contains Android types as well as SBT keys, so it cannot be split just by moving the file.
- Keep macro definitions and implementations together initially; Scala macro compilation and visibility can be sensitive to package/project moves.
- Maintain plugin resource names and lookup paths while moving templates from the root `resources/` directory.
- Avoid introducing SBT or Zinc types into `coreLogic` APIs. Keep category boundaries meaningful even though the folders share one compilation classpath.
- Keep compatibility code specific to SBT versions behind a narrow, documented edge and test the supported launcher/plugin matrix.

## Suggested migration sequence

1. **Stabilize behavior first.** Record the existing build, compile, unit-test, `publishLocal`, and Scripted results before moving sources. Keep the SBT-version migration and source reorganization as separate changes where possible.
2. **Normalize source roots.** Move the current flat `src/` Scala files to `src/main/scala` and resources to `src/main/resources`; update the custom source/resource settings in `build.sbt` and verify resource lookup paths.
3. **Organize by responsibility.** Move files or extract smaller components into `src/main/scala/android/coreLogic`, `src/main/scala/android/androidTools`, and `src/main/scala/android/sbtPlugin`. Keep package declarations unchanged unless a package/API change is separately justified; preserve public `android.*` entry points and use compatibility facades where needed.
4. **Separate mixed code gradually.** Start with JDK-only signing argument construction and bytecode algorithms. Move SBT traversal, task streams, caching, and reporting to `sbtPlugin`; move Android operations only after removing their SBT dependencies.
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
