# Project structure and import map

Basics:
- build the project: sbt -java-home /usr/lib/jvm/java-8-openjdk/ compile


## Project at a glance

This repository is a single SBT plugin project that integrates Android SDK/build tooling with SBT projects. It is not laid out as a conventional application: plugin implementation sources are directly under `src/`, rather than `src/main/scala/`. The build explicitly points Compile at `src/`, Test at `test/`, and Compile resources at `resources/`.

The source set is predominantly SBT- and Android-API-dependent. Some algorithms and helpers may be separable in a future modularization, but this document describes the current layout and imports; it does not imply that these parts are already independent modules.

## current pros and cons
The design is a pragmatic, feature-rich SBT plugin: plugin entry points and domain helpers give it recognizable boundaries, but most code still belongs to one source set and shares SBT/Android build context. These are tradeoffs of the current structure, not claims that the helpers are independently buildable modules.

### Pros

- **Clear plugin entry points.** App, project, library, and JAR plugins are separate, with explicit `requires` relationships. This makes the supported project types and their layering relatively easy to discover.
- **A centralized configuration contract.** `Keys.scala` collects public settings/tasks alongside internal wiring keys, while `package.scala` and `dsl.scala` provide a user-facing configuration surface. This avoids scattering key definitions across task implementations.
- **Responsibilities are identifiable.** Despite the flat source directory, files broadly separate layout detection, resources, packaging, dexing, signing, SDK operations, and command handling. This gives maintainers natural places to investigate feature behavior.
- **Practical layout and workflow coverage.** The plugin accounts for both Ant- and Gradle-style Android layouts and coordinates end-to-end Android build steps that would otherwise need to be assembled by each SBT project.
- **Real-project integration fixtures.** Scripted examples exercise a range of project shapes and features, including resources, JNI, multidex, ProGuard, Retrolambda, and tests. These fixtures are useful for checking interactions that isolated unit tests would miss.
- **Some seams for focused logic.** Helpers such as `SignAndroidJar.scala` keep argument construction separate from process execution, and bytecode scanning responsibilities are distinct from the main settings assembly, even though these are not separate modules.

### Cons

- **Flat source and package structure.** The plugin is compiled as one source set, with most implementation files in a flat `src/` directory and the `android` package. File-level boundaries help navigation, but do not enforce architectural boundaries or prevent unrelated features from depending on one another.
- **Large orchestration surfaces.** Central task and settings files wire many workflows together. This makes cross-feature behavior harder to follow and raises the chance that a change to shared configuration has effects beyond the feature being edited.
- **Tight coupling to SBT and Android APIs.** Most files depend directly on SBT, Android build/lint APIs, or both. Reusing or testing domain logic outside the plugin host is therefore difficult without extracting and defining stable interfaces around those dependencies.
- **SBT internals are compatibility hotspots.** The use of internal SBT loading/structure APIs and Zinc/xsbti compiler interfaces means some behavior depends on implementation details rather than only stable public APIs. The pinned SBT version and recorded newer-version difficulties reinforce this upgrade risk.
- **Limited unit-level isolation.** The checkout has no conventional `test/` tree; validation is primarily through Scripted fixtures. That is valuable for integration, but can make small algorithms and edge cases slower to test, and fixture coverage may not provide the same diagnostic precision as focused unit tests.
- **Build lifecycle has side effects.** Global hooks perform update checks, and project configuration can generate files. Such behavior can make loading less predictable and can complicate offline, repeatable, or narrowly scoped builds.
- **Dependency ownership is not always obvious.** Some imported APIs are supplied transitively rather than declared as clear direct dependencies, so dependency upgrades or exclusions can cause surprising compile/runtime changes. The import catalog above is a source-level map, not a resolved dependency graph.

Overall, the design favors broad compatibility with existing Android/SBT workflows and centralized coordination over modularity and isolation. The most significant maintenance risks are the SBT-internal integration points, the large orchestration files, and the lack of enforced module boundaries; the clearest strengths are the explicit plugin layers, centralized keys, and realistic integration fixtures.


## Repository layout

| Path                                                                    | Purpose                                                                                                                                                     |
| ----------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `build.sbt`                                                             | Plugin build definition, version and Scala settings, dependencies, packaging/publishing settings, and Scripted test configuration.                          |
| `project/`                                                              | SBT meta-build: launcher version, meta-build plugins, Bintray settings, and generated/local build tooling state.                                            |
| `src/`                                                                  | Main plugin implementation. The Scala files are in one flat source directory and use the `android` package, with a few `sbt` package compatibility helpers. |
| `resources/`                                                            | Android project templates, generated Scala templates, and ProGuard configuration copied or consumed by plugin tasks.                                        |
| `sbt-test/`                                                             | SBT Scripted fixture projects grouped under `android-sdk-plugin/`, `gradle-build/`, and `no-travis/`.                                                       |
| `README.md`, `GRADLE.md`, `CHANGES.md`, `migri.md`                      | User, Gradle-layout, release/change, and migration documentation.                                                                                           |
| `.github/`, `.travis.yml`, `.gitignore`, `.scalafmt.conf`, `.vscode/`   | CI, repository/editor configuration, and formatting settings.                                                                                               |
| `target/`, `project/target/`, `project/project/`, `.bloop/`, `.metals/` | Generated SBT or local IDE state; not primary plugin source.                                                                                                |

The build currently configures `test/` as the Test Scala source root and `libs/` as the unmanaged JAR directory, but neither directory is present in this checkout. `resources/` is present and includes `android-project-template/`, `android-proguard.config`, `tr.scala.template`, and `viewHolders.scala.template`.

### Build configuration snapshot

- The plugin build uses Scala 2.12.19 and sets `sbtPlugin := true`.
- `project/build.properties` pins the launcher to SBT 1.3.13; comments there record unsuccessful attempts with 1.4.9 and 1.9.9.
- `project/plugins.sbt` adds sbt-buildinfo and the scripted plugin. `project/bintray.sbt` configures the legacy sbt-bintray plugin.
- `build.sbt` declares Android build/lint libraries and other dependencies, configures Scripted fixtures, and makes scripted dependency setup depend on `publishLocal`.
- `project/metals.sbt` and nested project/target directories are meta-build or IDE state, not runtime plugin modules.

## Main source catalog and imports

The import tags summarize direct source imports, not a fully resolved dependency graph:

- **SBT** — SBT public or internal APIs; **Zinc** is called out separately where compile internals are involved.
- **Android** — Android SDK, Android Gradle/build, lint, or repository APIs.
- **JDK/Scala** — Java/Scala standard library APIs, including XML, concurrency, and process/file utilities.
- **External** — other imported third-party APIs. Project-local imports are not repeated in the tag column.

| Source                                                                 | Responsibility                                                                                                                      | Import groups                                       |
| ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| [`aggregates.scala`](src/aggregates.scala)                             | Task input records shared by dex, resource, manifest, packaging, test, and ProGuard tasks.                                          | SBT, Android, JDK/Scala                             |
| [`AndroidAppPlugins.scala`](src/AndroidAppPlugins.scala)               | Public plugin entry points (`AndroidApp`, `AndroidProject`, `AndroidLib`, `AndroidJar`) and their requirements.                     | SBT, Android                                        |
| [`AndroidAppSettingsPlugin.scala`](src/AndroidAppSettingsPlugin.scala) | App-specific defaults and task wiring for running, installing, packaging, dexing, signing, and ProGuard.                            | SBT, Android, JDK/Scala                             |
| [`AndroidGlobalPlugin.scala`](src/AndroidGlobalPlugin.scala)           | Global Android keys/commands, load and unload hooks, SDK target discovery, project dependencies, and variant handling.              | SBT, Android                                        |
| [`AndroidJarSettings.scala`](src/AndroidJarSettings.scala)             | Android-JAR project settings, placeholder manifest handling, source packaging, and lint configuration.                              | SBT, JDK/Scala                                      |
| [`AndroidLibSettings.scala`](src/AndroidLibSettings.scala)             | Android library project settings and AAR artifact packaging.                                                                        | SBT                                                 |
| [`AndroidLint.scala`](src/AndroidLint.scala)                           | Runs/adapts Android lint against SBT task streams and project/class inputs.                                                         | SBT, Android, JDK/Scala                             |
| [`AndroidProjectSettings.scala`](src/AndroidProjectSettings.scala)     | Shared Android project settings: source/layout setup, compilation, lint, classpaths, SDK/tool configuration, and generators.        | SBT, Android, JDK/Scala                             |
| [`AndroidTestSettings.scala`](src/AndroidTestSettings.scala)           | Instrumentation-test settings, test compilation/classpaths, and generated test sources; customizes incremental compilation.         | SBT, Zinc                                           |
| [`androidRepo.scala`](src/androidRepo.scala)                           | Android SDK repository package discovery, installation/update, and download integration.                                            | SBT, Android, JDK/Scala                             |
| [`commands.scala`](src/commands.scala)                                 | SBT command registration, parsers/actions for ADB/device operations, logcat, project generation, SDK management, and variants.      | SBT, Android, JDK/Scala                             |
| [`compat.scala`](src/compat.scala)                                     | Compatibility alias for an SBT internal `Load` API.                                                                                 | SBT internal                                        |
| [`dependencies.scala`](src/dependencies.scala)                         | AAR/APKLib/local-library abstractions, dependency identity/deduplication, and SBT project/module helpers.                           | SBT (including internal), Android, JDK/Scala        |
| [`Dex.scala`](src/Dex.scala)                                           | DEX input processing, dex/predex execution, multidex roots/main-dex lists, and method-count integration.                            | SBT, Android, JDK/Scala                             |
| [`dsl.scala`](src/dsl.scala)                                           | User-facing setting helpers, project scoping/compiler flags, and Scala macros for list construction.                                | SBT, Scala                                          |
| [`Keys.scala`](src/Keys.scala)                                         | Public Android task/setting keys and internal aggregate keys for SDK, variants, resources, dependencies, packaging, dex, and tests. | SBT, Android, JDK/Scala                             |
| [`MethodCounter.scala`](src/MethodCounter.scala)                       | Counts distinct methods and method references in JAR bytecode.                                                                      | SBT, JDK/Scala, External (ASM/Javassist)            |
| [`NativeFinder.scala`](src/NativeFinder.scala)                         | Scans class files for native methods.                                                                                               | SBT, JDK/Scala, External (ASM/Javassist)            |
| [`package.scala`](src/package.scala)                                   | Package-level Android DSL facade for variants, flavors/build types, build config/resource values, signing, and packaging options.   | SBT                                                 |
| [`Packaging.scala`](src/Packaging.scala)                               | APK packaging, incremental file handling, resource/JAR collection, and ZIP/APK utilities.                                           | SBT, Android, JDK/Scala, External (Guava)           |
| [`parsers.scala`](src/parsers.scala)                                   | Command parsers for manifest activities and SDK package operations; loads values from SBT state.                                    | SBT, Android, JDK/Scala, External (`sjsonnew`)      |
| [`PluginFail.scala`](src/PluginFail.scala)                             | Converts plugin failures to SBT message-only exceptions.                                                                            | SBT                                                 |
| [`proguard.scala`](src/proguard.scala)                                 | ProGuard inputs/cache JAR handling, execution, and classpath/reflection integration.                                                | SBT, Android, JDK/Scala, External (Scalaz)          |
| [`ProjectLayout.scala`](src/ProjectLayout.scala)                       | Detects Ant versus Gradle Android layouts and defines source/output, SDK, and build-output paths.                                   | SBT, Android, JDK/Scala                             |
| [`ReferenceFinder.scala`](src/ReferenceFinder.scala)                   | Scans bytecode for references matching class/package prefixes.                                                                      | SBT, JDK/Scala, External (ASM/Javassist)            |
| [`Resources.scala`](src/Resources.scala)                               | Resource collection/merging, AAPT execution, and typed-resource/view-holder generation.                                             | SBT, Android, JDK/Scala, External (Scalaz)          |
| [`retrolambda.scala`](src/retrolambda.scala)                           | Detects Java 8 JARs and runs Retrolambda processing.                                                                                | SBT, JDK/Scala, External (Retrolambda)              |
| [`sdkInterfaces.scala`](src/sdkInterfaces.scala)                       | Adapts Android progress, logging, process output/execution, and error-reporting interfaces to SBT.                                  | SBT, Android, JDK/Scala                             |
| [`SbtInternals.scala`](src/SbtInternals.scala)                         | Exposes the compiler reporter from SBT compile settings.                                                                            | SBT, Zinc                                           |
| [`SignAndroidJar.scala`](src/SignAndroidJar.scala)                     | Builds `jarsigner` arguments and delegates process execution to a supplied callback.                                                | JDK only (process execution is supplied externally) |
| [`signing.scala`](src/signing.scala)                                   | APK signing configuration types and Android signing-config adaptation.                                                              | SBT, Android, JDK/Scala                             |
| [`TaskBase.scala`](src/TaskBase.scala)                                 | Shared task helper for property-file loading.                                                                                       | SBT, JDK/Scala                                      |
| [`tasks.scala`](src/tasks.scala)                                       | Central task orchestration for dependencies, resources, manifest, NDK, AAR/APK packaging, signing, dex, ProGuard, and tests.        | SBT, Android, JDK/Scala                             |
| [`UpdateChecker.scala`](src/UpdateChecker.scala)                       | Asynchronous package/version update lookup and JSON decoding.                                                                       | JDK/Scala, External (Argonaut)                      |
| [`VariantSettings.scala`](src/VariantSettings.scala)                   | Applies, clears, and reapplies build-type/flavor settings to SBT project state.                                                     | SBT, including internal APIs                        |

### Reading the import map correctly

“JDK only” or “no SBT import” identifies a source-level seam, not proof that a file is already a separately buildable library. For example, `SignAndroidJar.scala` is a small JDK-only argument builder, while `UpdateChecker.scala` uses standard concurrency and Argonaut but is still in the plugin source package. Most bytecode helpers import SBT file/IO APIs even though their scanning algorithms may be candidates for extraction. Conversely, many Android-facing files also import SBT because they are task implementations or settings.

The tags are based on imports in source. They must not be interpreted as Maven coordinates. SBT supplies the host plugin API; Android SDK/build/lint packages can arrive through declared or transitive artifacts. `build.sbt` declares ASM, jsr305, Javassist, Android builder/manifest-merger/gradle-core/lint, BouncyCastle, Argonaut, Retrolambda, and Scalaz artifacts. Some directly imported APIs, including Guava, `sjsonnew`, Scala XML, and Zinc interfaces, may be supplied transitively or by SBT. Some declared libraries have no direct imports visible in this source set. Consult the resolved dependency graph before changing or documenting artifact ownership.

## Current architecture and main call paths

1. `AndroidAppPlugins.scala` assembles plugin variants. `AndroidApp` builds on `AndroidProject` and adds app/test features; library and JAR plugins also reuse the shared project plugin.
2. `AndroidProjectSettings.scala`, `AndroidAppSettingsPlugin.scala`, `AndroidLibSettings.scala`, `AndroidJarSettings.scala`, and `AndroidTestSettings.scala` configure the common project and specialized project types.
3. `Keys.scala` is the settings/task contract used throughout the plugin. `package.scala` and `dsl.scala` expose a user-facing configuration DSL.
4. `AndroidGlobalPlugin.scala` connects project loading, commands, and variants. `VariantSettings.scala` mutates/reapplies project settings; `compat.scala` supplies a compatibility bridge to an SBT internal API.
5. `ProjectLayout.scala` detects project layout and maps build outputs. `tasks.scala` orchestrates work across `Resources.scala`, `dependencies.scala`, `Packaging.scala`, `Dex.scala`, `proguard.scala`, `retrolambda.scala`, `signing.scala`, and SDK helpers. `aggregates.scala` provides shared task input records.
6. `commands.scala` registers command workflows and uses `parsers.scala` and `androidRepo.scala` for command input and SDK operations. `sdkInterfaces.scala` adapts Android interfaces to SBT logging/process APIs.
7. `AndroidTestSettings.scala` and `SbtInternals.scala` are the clearest points of direct SBT/Zinc compile-internals coupling; `VariantSettings.scala`, `dependencies.scala`, and `compat.scala` also use SBT internals.

This is a conceptual responsibility map, not a proposed completed module split. A future extraction should validate dependencies from actual symbols and build classpaths, not filenames alone.

## Resources and generated content

- `resources/android-project-template/` contains the starter Android project files, including its manifest, sample activity, layouts, strings, and test templates.
- `resources/android-proguard.config` provides ProGuard configuration.
- `resources/tr.scala.template` and `resources/viewHolders.scala.template` are templates for generated Scala resource/view-holder code.
- `build.sbt` sets `resources/` as the Compile resource directory.

## Scripted tests

There is no conventional `test/` unit-test tree in this checkout. The build configures `test/`, but testing is primarily SBT Scripted through `sbt-test/`. The repository has 29 fixture roots containing scripted `test` scripts: 24 in `android-sdk-plugin/`, four in `gradle-build/`, and one in `no-travis/`. Fixtures cover Android app/library/JAR layouts, resources, JNI/NDK, multidex, ProGuard caching, RenderScript, Retrolambda, multiproject dependencies, and test integration. Some commands exercise device/instrumentation workflows, so prerequisites vary by fixture. The current `build.sbt` `scriptedDependencies` selection includes `android-sdk-plugin/` and `no-travis/`, not `gradle-build/`; confirm intended coverage before changing it.

## Modularization observations

- The current plugin has a single flat Scala compilation unit/source root; responsibilities are organized by file rather than by subproject or package boundary.
- Nearly every file imports SBT, Android APIs, or both. `SignAndroidJar.scala` is JDK-only at the import level; `UpdateChecker.scala` is free of direct SBT imports but requires Argonaut. Bytecode scanners have algorithmic behavior that could potentially be separated from their SBT file/IO wrappers.
- The strongest current integration boundary is between plugin settings/task wiring and reusable work performed by helpers. Potential candidates for future isolation include command argument construction, layout/path calculations, bytecode scanning algorithms, and process argument construction. These require symbol-level dependency review before extraction.
- SBT internals and Zinc are not just implementation details in the current design: several source files depend on them, making those areas likely compatibility hotspots during an SBT upgrade.
