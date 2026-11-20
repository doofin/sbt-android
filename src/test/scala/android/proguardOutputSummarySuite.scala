package android

class ProguardOutputSummarySuite extends munit.FunSuite {
  test("summarizes ProGuard jar output using basenames only") {
    val summary = ProguardOutputSummary.summarize(
      Seq(
        "Reading program jar [/workspace/app/classes.jar] (filtered)",
        "Reading program jar [/cache/lib/util.jar] (filtered)",
        "Reading library jar [/sdk/platforms/android-28/android.jar]",
        "Copying resources from program jar [/cache/lib/util.jar] (filtered)",
        "Preparing output jar [/workspace/target/classes.proguard.jar]",
        "Initializing..."
      )
    )

    assertEquals(
      summary.fileMessages,
      Seq(
        "ProGuard files: classes.jar, util.jar, android.jar, classes.proguard.jar"
      )
    )
  }

  test("limits long file summaries and preserves unrelated output") {
    val fileLines = (1 to 10).map(i =>
      s"Reading program jar [/cache/lib-$i.jar] (filtered)"
    )

    assertEquals(
      ProguardOutputSummary.summarize(fileLines).fileMessages,
      Seq(
        "ProGuard files: lib-1.jar, lib-2.jar, lib-3.jar, lib-4.jar, " +
          "lib-5.jar, lib-6.jar, lib-7.jar, lib-8.jar, +2 more"
      )
    )
    assert(!ProguardOutputSummary.isFileDetail("Initializing..."))
  }
}
