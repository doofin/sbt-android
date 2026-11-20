package android

import java.io.File

import scala.sys.process.Process

class apkPackagingSuite extends munit.FunSuite {
  private def repositoryRoot: File = {
    val start = new File(sys.props("user.dir")).getCanonicalFile
    Iterator
      .iterate(start)(_.getParentFile)
      .takeWhile(_ != null)
      .find(root => new File(root, "test/scala4Android/build.sbt").isFile)
      .getOrElse(
        fail(
          "Could not locate test/scala4Android from the test working directory"
        )
      )
  }

  private def runSbt(directory: File, tasks: String*): Unit = {
    val javaHome = new File(sys.props("java.home")).getCanonicalPath
    val sbtExecutable = sys.env.getOrElse("SBT", "sbt")
    val exitCode = Process(
      Seq(sbtExecutable, "-java-home", javaHome) ++ tasks,
      directory
    ).!
    assertEquals(exitCode, 0, s"SBT failed in ${directory.getAbsolutePath}")
  }

  test("packageDebug produces a non-empty debug APK") {
    val root = repositoryRoot
    val fixture = new File(root, "test/scala4Android")

    runSbt(root, "publishLocal")
    runSbt(fixture, "clean", "test")

    val outputDirectory = new File(fixture, "target/android/output")
    val debugApks = Option(outputDirectory.listFiles())
      .getOrElse(Array.empty[File])
      .filter(file =>
        file.isFile && file.getName.endsWith("-debug-unaligned.apk")
      )

    assert(
      debugApks.nonEmpty,
      s"No debug APK found in ${outputDirectory.getPath}"
    )
    debugApks.foreach { apk =>
      assert(apk.length() > 0, s"Debug APK is empty: ${apk.getPath}")
    }
  }
}
