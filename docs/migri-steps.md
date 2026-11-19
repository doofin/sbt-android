# Refactoring steps

This summarizes the source reorganization performed before the planned sbt migration. See [the structure proposal](new-project-structure.md) for the full file mapping and [the migration plan](migri.md) for the sbt upgrade.

## Changes made

1. Kept a single sbt plugin project and organized the Scala sources under `src/main/scala/android/` by responsibility: `coreLogic`, `androidTools`, and `sbtPlugin`.
2. Preserved existing package declarations and plugin coordinates; the directories are organizational, not separate Scala packages, sbt projects, or enforced dependency boundaries.
3. Moved plugin templates and configuration to `src/main/resources/`. Updated `build.sbt` to use that resource directory and sbt's conventional compile and test source roots. Scripted fixtures remain under `sbt-test/`.
4. Removed direct sbt file/IO dependencies from `MethodCounter` and `ReferenceFinder` by using JDK jar streams, and from `NativeFinder` by using JDK streams and recursive file traversal. Their bytecode libraries remain dependencies. The new traversal may behave differently for symlinks or unreadable directories and needs focused tests.
5. Placed Android/SBT bridge code, including `sdkInterfaces.scala`, in `sbtPlugin`. Android-focused files in `androidTools` still contain sbt logging, caching, task, and file APIs; further separation remains work for a later step.

## Verification and remaining work

- A Java 8 `clean test` build succeeded, compiling 36 Scala sources (including generated build information) with 10 warnings. No unit test sources were found in the old or new test roots.
- The default Java 21 failed during startup of the pinned sbt 1.3.13; Java 8 was used for the successful build.
- Dedicated scanner tests, resource lookup and packaged-artifact checks, `publishLocal`, Scripted fixtures, and a consumer build have not been verified after the reorganization.
- Add focused tests, including traversal edge cases; separate mixed SBT adapters where practical; and review non-obvious imports for useful comments. No import comments were added in this pass.