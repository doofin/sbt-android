# Refactoring steps

This summarizes the source reorganization performed before the planned sbt migration. See: 
- [new structure proposal](new-project-structure.md) for the full file mapping and 
- [the migration plan](migri.md) for the sbt upgrade.

Don't delete the instructions!

## 1st round

1. Kept a single sbt plugin project and organized the Scala sources under `src/main/scala/android/` by responsibility: `coreLogic`, `androidTools`, and `sbtPlugin`.
2. Preserved existing package declarations and plugin coordinates; the directories are organizational, not separate Scala packages, sbt projects, or enforced dependency boundaries.
3. Moved plugin templates and configuration to `src/main/resources/`. Updated `build.sbt` to use that resource directory and sbt's conventional compile and test source roots. Scripted fixtures remain under `sbt-test/`.
4. Removed direct sbt file/IO dependencies from `MethodCounter` and `ReferenceFinder` by using JDK jar streams, and from `NativeFinder` by using JDK streams and recursive file traversal. Their bytecode libraries remain dependencies. The new traversal may behave differently for symlinks or unreadable directories and needs focused tests.
5. Placed Android/SBT bridge code, including `sdkInterfaces.scala`, in `sbtPlugin`. Android-focused files in `androidTools` still contain sbt logging, caching, task, and file APIs; further separation remains work for a later step.


results of 1st round:
- A Java 8 `clean test` build succeeded, compiling 36 Scala sources (including generated build information) with 10 warnings. No unit test sources were found in the old or new test roots.
- The default Java 21 failed during startup of the pinned sbt 1.3.13; Java 8 was used for the successful build.
- At that point, dedicated scanner tests, resource lookup and packaged-artifact checks, `publishLocal`, Scripted fixtures, and a consumer build had not been verified after the reorganization.
- Further work includes separating mixed SBT adapters where practical and reviewing non-obvious imports for useful comments. No import comments were added in this pass.

## 2nd round: MUnit tests

results of 2nd round:
- Added MUnit as a test-only dependency and registered its test framework with sbt 1.3.13. The existing Scripted fixtures remain unchanged.
- Added tests for bytecode scanning, including comparisons with the retained `*Old` implementations, malformed input, nested directories, and empty jars; also tested signing arguments and selected classpath resource names.
- Java 8 `test` discovered and passed all 8 MUnit tests. These do not replace Scripted integration tests. Symlink and unreadable-directory traversal, packaged-artifact resources, `publishLocal`, Scripted fixtures, and a consumer build remain unverified.

## 3rd round
instructions:
- Review the current codebase and the new structure proposal. Identify files that are semantically related and should be merged, and files that are semantically unrelated and should be separated. Consider whether they are named and organized in a way that makes the best sense.
- check what code can be further modularized so it's easier to test with munit and to migrate to newer sbt versions. if possible, add those tests with munit.


### Findings

- **Current organization:** the project now uses `src/main/scala` and `src/main/resources`, with source folders `android/coreLogic`, `android/androidTools`, `android/sbtPlugin`, plus `android/misc`. These are still one Scala package/project in practice: folder placement does not enforce package or dependency boundaries. The test tree has MUnit suites, while consumer-level tests remain in `sbt-test/`.
- **Structure documentation:** `docs/project-structure.md` has been synchronized with the current source, resource, and test roots, responsibility folders, MUnit suites, and remaining SBT/Android coupling. Keep it updated as boundaries change.
- **Retained scanner copies:** the production scanners are under `coreLogic`; three older copies in `android/misc` are referenced by `BytecodeScannerSuite` as parity oracles. Keep them only while useful for regression comparison, then move them to test sources or remove them; `misc` is not a meaningful long-term name and compiling old copies into the plugin artifact is unnecessary.
- **Keep central contracts distinct:** do not merge `keys.scala` into settings or task implementations. It is the plugin's key registry/public configuration surface. Keep plugin entry points, DSL/macro code, and key definitions discoverable and stable during the sbt upgrade.
- **Potential small cohesion groupings:** `compat.scala` and `sbtInternals.scala` are both SBT-internal compatibility shims and could be documented or grouped together, but combining files is low priority and must preserve the special `sbt` package names. `commands.scala` and `parsers.scala` are related by command behavior, but the parser object also performs state/task/SDK lookups; consolidate only if that reduces coupling rather than merely reducing file count.
- **High-value splits:** `tasks.scala` is a large SBT task-orchestration object spanning resources, manifest, native builds, DEX/ProGuard, packaging, and device tests. Extract cohesive task-definition groups incrementally, preserving task keys and behavior. `commands.scala` similarly combines ADB/device/file operations, logcat, project generation, SDK management, and variants; separate these only along tested behavior seams.
- **Boundary debt:** files in `androidTools` still directly use SBT types/utilities for streams, logging, caching, and file handling. Keep their Android-domain operations grouped, but move SBT adapters to `sbtPlugin` only when there is a stable interface and tests; do not treat current directory placement as completed modularization.
- **Naming:** Scala source and test filenames now consistently use lower camelCase (for example, `packaging.scala`, `proguard.scala`, and `signAndroidJar.scala`). Scala declarations and package names are unchanged; `sample.scala` remains the Android project template. Prefer descriptive names over `misc`; avoid package renames because most existing API remains in package `android`.

### Outcome and next actions

No broad task/command orchestration files were merged or split in this analysis round; the bounded manifest XML helper extraction is the one additional modularization step completed, with behavior covered by MUnit. Recommended next actions: retain scanner reference copies only until parity tests are trusted, then move/remove them; split the task/command monoliths in small behavior-preserving steps; address SBT adapters as a distinct boundary-refactoring effort. Keep public plugin entry points, key names/types, DSL, artifact coordinates, and Scripted fixtures stable. The planned SBT launcher migration itself has not been attempted in this round.