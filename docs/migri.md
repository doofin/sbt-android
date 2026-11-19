# Migration plan: sbt 1.3.13 to 1.13.1

This document is a plan to migrate to higher sbt versions.

Principles:
- first, analyze the current codebase, and find out possible issues and potential improvements
- make the project modular, split sbt independent code from sbt dependent code(like pure Scala code from code that depends on specific apis).
- before the migration, think other aspects of improvements that may help the migration.

Steps:
- analyze the current codebase, make it modular, split sbt independent code from sbt dependent code(like pure Scala code from code that depends on other apis), and add import comments to suitable code locations. 
- document the current project structure and import things in project-structure.md after careful analysis of the current codebase.
- in more detail, write down the main features of each code file at the end of project-structure.md.
- deeply think a new structure for the project, and write down the new structure in new-project-structure.md. remember that make it modular, split sbt independent code from sbt dependent code(like pure Scala code from code that depends on other apis).

## Current state

- The launcher is pinned to sbt 1.3.13 in `project/build.properties`. Comments report that 1.4.9 and 1.9.9 did not work, but do not include the failure details.
- The plugin build uses Scala 2.12.19 and sbt-buildinfo 0.8.0.
- Publishing configuration depends on sbt-bintray 0.5.4 and a Bintray resolver.
- `src/AndroidTestSettings.scala` customizes incremental compilation using sbt/Zinc internals and documents an `xsbti.compile.Setup.of` overload issue.

## Steps

1. **Establish a baseline.** On the current toolchain, record build load, compile, test, scripted-test, and publishLocal results. Preserve exact errors. Determine what failed with sbt 1.4.9/1.9.9, if possible.
2. **Try the launcher bump in a branch.** Change the sbt launcher to 1.13.1 and first check whether the build loads. Do not combine this with unrelated cleanup.
3. **Resolve compilation/API breakages.** Prioritize `AndroidTestSettings.scala`: verify the `Defaults` compiler helpers, Zinc types, and `Setup.of`/`Inputs.of` construction against sbt 1.13.1. Update the implementation to supported APIs if necessary, preserving Android test compilation behavior.
4. **Verify meta-build plugins and resolvers.** Test whether sbt-buildinfo and scripted-plugin resolve and work. Assess the obsolete Bintray publishing setup; replace or remove it if unavailable, updating its settings in `build.sbt` as needed. Check generated Metals/bloop settings as well.
5. **Review build-definition compatibility.** Confirm the plugin uses an appropriate Scala version and sbt plugin cross-build configuration. Modernize legacy `in` scoping syntax to slash syntax where appropriate, after the build compiles.
6. **Run regression tests.** Run plugin compile and unit tests, then `publishLocal` and scripted fixtures with sbt 1.13.1. The current scripted setup selects `android-sdk-plugin` and `no-travis`; verify whether the `gradle-build` fixtures also need inclusion. Note any Android SDK/device prerequisites.
7. **Verify a consumer build.** Test the published-local plugin from a representative Android project using sbt 1.13.1, then update README/version support notes and CI once the result is stable.

## Main risks

- The project already records failures on sbt versions newer than 1.3.13; diagnosing those failures is essential before assuming a direct version bump will work.
- Custom incremental compilation code relies on sbt/Zinc internals and is the most likely source-level compatibility issue.
- The old Bintray resolver/plugin may prevent build loading or publishing independently of compiler compatibility.
- Scripted fixture builds may bring their own legacy plugins and repository dependencies.

## References

- [sbt 1.x reference manual](https://www.scala-sbt.org/1.x/docs/)
- [sbt migration from 0.13.x](https://www.scala-sbt.org/1.x/docs/Migrating-from-sbt-013x.html)
- [sbt 1.13.1 release notes](https://github.com/sbt/sbt/releases/tag/v1.13.1)
