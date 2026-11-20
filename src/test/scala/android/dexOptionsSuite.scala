package android

import java.io.File
import java.util.zip.{ZipEntry, ZipOutputStream}

import scala.collection.JavaConverters._

class DexOptionsSuite extends munit.FunSuite {
  private def aggregateDex(
      minimizeMain: Boolean,
      additionalParams: Seq[String]
  ): Aggregate.Dex =
    Aggregate.Dex(
      inputs = false -> Nil,
      maxHeap = "1g",
      maxProcessCount = 1,
      multi = true,
      mainClassesConfig = new File("main-dex-list.txt"),
      minimizeMain = minimizeMain,
      dexInProcess = false,
      buildTools = null,
      additionalParams = additionalParams
    )

  test("minimal main-dex flag reaches Android DexOptions") {
    val actual = Dex
      .dexOptionsWithMainParams(aggregateDex(true, Seq("--verbose")))
      .getAdditionalParameters
      .asScala
      .toSeq

    assertEquals(actual, Seq("--verbose", "--minimal-main-dex"))
  }

  test("minimal main-dex flag is omitted when disabled") {
    val actual = Dex
      .dexOptionsWithMainParams(aggregateDex(false, Seq("--verbose")))
      .getAdditionalParameters
      .asScala
      .toSeq

    assertEquals(actual, Seq("--verbose"))
  }

  test("minimal main-dex list contains only classes from the roots jar") {
    val jar = File.createTempFile("main-dex-roots", ".jar")
    val output = new ZipOutputStream(new java.io.FileOutputStream(jar))
    try {
      Seq(
        "com/example/App.class",
        "scala/Function1.class",
        "META-INF/MANIFEST.MF",
        "com/example/resources/"
      ).foreach { name =>
        output.putNextEntry(new ZipEntry(name))
        output.closeEntry()
      }
    } finally output.close()

    try {
      assertEquals(
        Dex.mainDexRootsFromJar(jar),
        List("com/example/App.class", "scala/Function1.class")
      )
    } finally jar.delete()
  }
}