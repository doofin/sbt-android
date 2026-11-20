# Project structure and import map

Basics of test and building:
- build the project: `sbt -java-home /usr/lib/jvm/java-8-openjdk/ compile`
- publish the plugin locally: `sbt -java-home /usr/lib/jvm/java-8-openjdk/ publishLocal`


## Project at a glance

This repository is a single SBT plugin project integrating Android SDK/build tooling with SBT projects. It now uses conventional SBT roots: production Scala sources under `src/main/scala/`, MUnit sources under `src/test/scala/`, and plugin resources under `src/main/resources/`. Within the production source root, files are organized into responsibility folders (`android/coreLogic`, `android/androidTools`, `android/sbtPlugin`, and `android/misc`); most retain the `android` Scala package, so these folders do not create package or compilation boundaries.

The source set is predominantly SBT- and Android-API-dependent. Some algorithms and helpers may be separable in a future modularization, but this document describes the current layout and imports; it does not imply that these parts are already independent modules.

## current pros and cons
The design is a pragmatic, feature-rich SBT plugin: plugin entry points and domain helpers give it recognizable boundaries, but most code still belongs to one source set and shares SBT/Android build context. These are tradeoffs of the current structure, not claims that the helpers are independently buildable modules.

### Pros

- **Clear plugin entry points.** App, project, library, and JAR plugins are separate, with explicit `requires` relationships. This makes the supported project types and their layering relatively easy to discover.
- **A centralized configuration contract.** `keys.scala` collects public settings/tasks alongside internal wiring keys, while `package.scala` and `dsl.scala` provide a user-facing configuration surface. This avoids scattering key definitions across task implementations.
- **Responsibilities are identifiable.** The source folders distinguish reusable logic, Android operations, and SBT integration, while files separate layout detection, resources, packaging, dexing, signing, SDK operations, and command handling. These folders improve navigation but do not enforce dependency boundaries.
- **Practical layout and workflow coverage.** The plugin accounts for both Ant- and Gradle-style Android layouts and coordinates end-to-end Android build steps that would otherwise need to be assembled by each SBT project.
- **Real-project integration fixtures.** Scripted examples exercise a range of project shapes and features, including resources, JNI, multidex, ProGuard, Retrolambda, and tests. These fixtures are useful for checking interactions that isolated unit tests would miss.
- **Some seams for focused logic.** Helpers such as `signAndroidJar.scala` keep argument construction separate from process execution, and bytecode scanning responsibilities are distinct from the main settings assembly, even though these are not separate modules.

### Cons

- **Single source set and mostly flat package.** The plugin is compiled as one source set and most implementation files remain in package `android`, despite nested responsibility folders. File-level boundaries help navigation, but do not enforce architectural boundaries or prevent unrelated features from depending on one another.
- **Large orchestration surfaces.** Central task and settings files wire many workflows together. This makes cross-feature behavior harder to follow and raises the chance that a change to shared configuration has effects beyond the feature being edited.
- **Tight coupling to SBT and Android APIs.** Most files depend directly on SBT, Android build/lint APIs, or both. Reusing or testing domain logic outside the plugin host is therefore difficult without extracting and defining stable interfaces around those dependencies.
- **SBT internals are compatibility hotspots.** The use of internal SBT loading/structure APIs and Zinc/xsbti compiler interfaces means some behavior depends on implementation details rather than only stable public APIs. The pinned SBT version and recorded newer-version difficulties reinforce this upgrade risk.
- **Limited unit-level isolation.** Four MUnit suites now cover selected scanners, manifest activity discovery, signing argument construction, and resource lookup (10 tests total). Scripted fixtures still provide the main consumer-build integration coverage; focused tests remain limited relative to the plugin surface.
- **Build lifecycle has side effects.** Global hooks perform update checks, and project configuration can generate files. Such behavior can make loading less predictable and can complicate offline, repeatable, or narrowly scoped builds.
- **Dependency ownership is not always obvious.** Some imported APIs are supplied transitively rather than declared as clear direct dependencies, so dependency upgrades or exclusions can cause surprising compile/runtime changes. The import catalog above is a source-level map, not a resolved dependency graph.

Overall, the design favors broad compatibility with existing Android/SBT workflows and centralized coordination over modularity and isolation. The most significant maintenance risks are the SBT-internal integration points, the large orchestration files, and the lack of enforced module boundaries; the clearest strengths are the explicit plugin layers, centralized keys, and realistic integration fixtures.


## Repository layout

| Path                                                                              | Purpose                                                                                              |
| --------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| [build.sbt](../build.sbt)                                                         | Plugin build definition, dependencies, publishing settings, MUnit setup, and Scripted configuration. |
| [project/](../project/)                                                           | SBT meta-build, launcher version, plugins, and build tooling configuration.                          |
| [src/main/scala/android/](../src/main/scala/android/)                             | Production plugin sources, grouped by responsibility; most files remain in package `android`.        |
| [src/main/resources/](../src/main/resources/)                                     | Android project templates, generated Scala templates, and ProGuard configuration.                    |
| [src/test/scala/android/](../src/test/scala/android/)                             | MUnit unit tests for scanners, signing arguments, and plugin resource lookup.                        |
| [sbt-test/](../sbt-test/)                                                         | Scripted consumer fixtures under `android-sdk-plugin/`, `gradle-build/`, and `no-travis/`.           |
| [docs/](.)                                                                        | Current structure, proposed organization, and migration notes.                                       |
| [README.md](../README.md), [GRADLE.md](../GRADLE.md), [CHANGES.md](../CHANGES.md) | User, Gradle-layout, and release/change documentation.                                               |
| `.github/`, `.travis.yml`, `.gitignore`, `.scalafmt.conf`, `.vscode/`             | CI, repository/editor configuration, and formatting settings.                                        |
| `target/`, `project/target/`, `project/project/`, `.bloop/`, `.metals/`           | Generated SBT or local IDE state; not primary plugin source.                                         |

SBT's conventional source roots are active; no custom Compile/Test Scala source roots are configured. `unmanagedBase` points at `libs/` for unmanaged JARs. The Compile resource root is explicitly set to `src/main/resources/`.

### Build configuration snapshot

- The plugin build uses Scala 2.12.19 and sets `sbtPlugin := true`.
- `project/build.properties` pins the launcher to SBT 1.3.13; comments there record unsuccessful attempts with 1.4.9 and 1.9.9.
- `project/plugins.sbt` adds sbt-buildinfo and the scripted plugin. `project/bintray.sbt` configures the legacy sbt-bintray plugin.
- `build.sbt` declares Android build/lint libraries and other dependencies, configures MUnit and Scripted fixtures, and makes scripted dependency setup depend on `publishLocal`.
- `project/metals.sbt` and nested project/target directories are meta-build or IDE state, not runtime plugin modules.

## Main source catalog and imports

The import tags summarize direct source imports, not a fully resolved dependency graph:

- **SBT** — SBT public or internal APIs; **Zinc** is called out separately where compile internals are involved.
- **Android** — Android SDK, Android Gradle/build, lint, or repository APIs.
- **JDK/Scala** — Java/Scala standard library APIs, including XML, concurrency, and process/file utilities.
- **External** — other imported third-party APIs. Project-local imports are not repeated in the tag column.

| Source folder                                            | Files and responsibilities                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Import/dependency notes                                                                                                                                                                                                                                              |
| -------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [coreLogic/](../src/main/scala/android/coreLogic/)       | `methodCounter.scala`, `nativeFinder.scala`, `referenceFinder.scala` scan class/JAR bytecode; `manifestActivities.scala` discovers launcher activities from manifest XML; `signAndroidJar.scala` builds jarsigner arguments; `updateChecker.scala` performs asynchronous version lookup and JSON decoding. | Scanner code uses JDK file/stream APIs plus ASM/Javassist; manifest discovery uses Scala XML; `SignAndroidJar` uses JDK APIs; `UpdateChecker` uses Scala concurrency and Argonaut. No direct SBT or Android SDK/build imports in these six production files. |
| [androidTools/](../src/main/scala/android/androidTools/) | `androidLint.scala`, `dex.scala`, `packaging.scala`, `projectLayout.scala`, `resources.scala`, `androidRepo.scala`, `proguard.scala`, `retrolambda.scala`, `signing.scala`. Covers Android lint, layout, resource, DEX, packaging, SDK, ProGuard, Retrolambda, and signing operations. | Android APIs throughout; several files still directly depend on SBT for task streams, logging, caching, and file/path utilities. This is not yet an SBT-independent layer. |
| [sbtPlugin/](../src/main/scala/android/sbtPlugin/)       | `androidAppPlugins.scala`, `androidAppSettingsPlugin.scala`, `androidGlobalPlugin.scala`, `androidJarSettings.scala`, `androidLibSettings.scala`, `androidProjectSettings.scala`, `androidTestSettings.scala`; `keys.scala`, `pluginFail.scala`, `taskBase.scala`, `variantSettings.scala`, `aggregates.scala`, `commands.scala`, `dependencies.scala`, `dsl.scala`, `package.scala`, `parsers.scala`, `tasks.scala`; `sdkInterfaces.scala`, `compat.scala`, `sbtInternals.scala`. | SBT plugin entry points, public key/DSL surfaces, task/command wiring, adapters, and compatibility code. `androidTestSettings.scala`, `sbtInternals.scala`, `variantSettings.scala`, `dependencies.scala`, and `compat.scala` include internal SBT/Zinc touchpoints. |
| [misc/](../src/main/scala/android/misc/)                 | `methodCounter.scala`, `nativeFinder.scala`, `referenceFinder.scala` are retained legacy implementations, renamed `MethodCounterOld`, `NativeFinderOld`, and `ReferenceFinderOld`.                                                                                                                                                                                                                                                                                                   | Still compiled into the production source set; currently used as parity references by the MUnit scanner suite. Consider moving them under test sources or removing after parity confidence.                                                                          |

Test sources currently contain [bytecodeScannerSuite.scala](../src/test/scala/android/coreLogic/bytecodeScannerSuite.scala), [manifestActivitiesSuite.scala](../src/test/scala/android/coreLogic/manifestActivitiesSuite.scala), [signAndroidJarSuite.scala](../src/test/scala/android/coreLogic/signAndroidJarSuite.scala), and [pluginResourcesSuite.scala](../src/test/scala/android/androidTools/pluginResourcesSuite.scala). MUnit is a test-only dependency and is explicitly registered for the pinned SBT version. These tests are complementary to, not replacements for, Scripted consumer fixtures.

### Reading the import map correctly

“No SBT import” identifies a source-level seam, not proof that a file is already a separately buildable library. The current scanners use JDK file/stream APIs, `signAndroidJar.scala` is a small JDK-only argument builder, and `updateChecker.scala` uses standard concurrency and Argonaut. Conversely, many Android-facing files also import SBT because they are task implementations or settings.

The tags are based on imports in source. They must not be interpreted as Maven coordinates. SBT supplies the host plugin API; Android SDK/build/lint packages can arrive through declared or transitive artifacts. `build.sbt` declares ASM, jsr305, Javassist, Android builder/manifest-merger/gradle-core/lint, BouncyCastle, Argonaut, Retrolambda, and Scalaz artifacts. Some directly imported APIs, including Guava, `sjsonnew`, Scala XML, and Zinc interfaces, may be supplied transitively or by SBT. Some declared libraries have no direct imports visible in this source set. Consult the resolved dependency graph before changing or documenting artifact ownership.

## Current architecture and main call paths

1. `androidAppPlugins.scala` assembles plugin variants. `AndroidApp` builds on `AndroidProject` and adds app/test features; library and JAR plugins also reuse the shared project plugin.
2. `androidProjectSettings.scala`, `androidAppSettingsPlugin.scala`, `androidLibSettings.scala`, `androidJarSettings.scala`, and `androidTestSettings.scala` configure the common project and specialized project types.
3. `keys.scala` is the settings/task contract used throughout the plugin. `package.scala` and `dsl.scala` expose a user-facing configuration DSL.
4. `androidGlobalPlugin.scala` connects project loading, commands, and variants. `variantSettings.scala` mutates/reapplies project settings; `compat.scala` supplies a compatibility bridge to an SBT internal API.
5. `projectLayout.scala` detects project layout and maps build outputs. `tasks.scala` orchestrates work across `resources.scala`, `dependencies.scala`, `packaging.scala`, `dex.scala`, `proguard.scala`, `retrolambda.scala`, `signing.scala`, and SDK helpers. `aggregates.scala` provides shared task input records. `manifestActivities.scala` owns XML-only launcher activity discovery used by both the parser and run task.
6. `commands.scala` registers command workflows and uses `parsers.scala` and `androidRepo.scala` for command input and SDK operations. `sdkInterfaces.scala` adapts Android interfaces to SBT logging/process APIs.
7. `androidTestSettings.scala` and `sbtInternals.scala` are the clearest points of direct SBT/Zinc compile-internals coupling; `variantSettings.scala`, `dependencies.scala`, and `compat.scala` also use SBT internals.

This is a conceptual responsibility map, not a proposed completed module split. A future extraction should validate dependencies from actual symbols and build classpaths, not filenames alone.

## Resources and generated content

- `src/main/resources/android-project-template/` contains starter Android project files, including its manifest, sample activity, layouts, strings, and test templates.
- `src/main/resources/android-proguard.config` provides ProGuard configuration.
- `src/main/resources/tr.scala.template` and `src/main/resources/viewHolders.scala.template` are templates for generated Scala resource/view-holder code.
- `build.sbt` sets `src/main/resources/` as the Compile resource directory; the resource names used by plugin code are retained.

## Scripted tests

The conventional `src/test/scala/` tree currently contains four MUnit suites; the latest Java 8 test run passed all 10 tests. Scripted consumer fixtures remain under `sbt-test/`: 29 roots with scripts—24 in `android-sdk-plugin/`, four in `gradle-build/`, and one in `no-travis/`. Fixtures cover Android app/library/JAR layouts, resources, JNI/NDK, multidex, ProGuard caching, RenderScript, Retrolambda, multiproject dependencies, and test integration. Some commands exercise device/instrumentation workflows, so prerequisites vary by fixture. The current `build.sbt` cleanup setup selects `android-sdk-plugin/` and `no-travis/`, not `gradle-build/`; confirm intended coverage before changing it.

## Modularization observations

- The plugin has a single Scala compilation unit with responsibility folders rather than subprojects or package boundaries. Most production sources retain package `android`.
- Most integration and Android-operation files import SBT, Android APIs, or both. `signAndroidJar.scala` has JDK-only argument construction; `updateChecker.scala` has no direct SBT imports but requires Argonaut. The bytecode scanners now use JDK I/O and ASM/Javassist without direct SBT dependencies.
- The strongest current integration boundary is between plugin settings/task wiring and reusable work performed by helpers. Potential candidates for future isolation include command argument construction, layout/path calculations, bytecode scanning algorithms, and process argument construction. These require symbol-level dependency review before extraction.
- SBT internals and Zinc are not just implementation details in the current design: several source files depend on them, making those areas likely compatibility hotspots during an SBT upgrade.
