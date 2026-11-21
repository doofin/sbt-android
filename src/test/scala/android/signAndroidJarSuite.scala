package android

import java.io.File

class SignAndroidJarSuite extends munit.FunSuite {
  import SignAndroidJar._

  test("sign passes options, jar and alias to jarsigner") {
    val jar = new File("app.jar")
    var invocation = Option.empty[(String, List[String])]
    sign(
      jar,
      "release",
      Seq(storeType("jks"), storePassword("secret"), verbose)
    ) { (command, args) =>
      invocation = Some(command -> args)
      0
    }
    assertEquals(
      invocation,
      Some(
        "jarsigner" -> List(
          "-storetype",
          "jks",
          "-storepass",
          "secret",
          "-verbose",
          jar.getAbsolutePath,
          "release"
        )
      )
    )
  }

  test("verify removes sign-only options") {
    val jar = new File("app.jar")
    var invocation = Option.empty[List[String]]
    verify(jar, Seq(storePassword("secret"), verbose, storeType("jks"))) {
      (_, args) =>
        invocation = Some(args)
        0
    }
    assertEquals(
      invocation,
      Some(
        List("-verbose", "-storetype", "jks", "-verify", jar.getAbsolutePath)
      )
    )
  }

  test("sign rejects blank aliases and reports failed commands") {
    intercept[IllegalArgumentException] {
      sign(new File("app.jar"), "  ", Nil)((_, _) => fail("must not run"))
    }
    val error = intercept[RuntimeException] {
      sign(new File("app.jar"), "release", Nil)((_, _) => 7)
    }
    assert(error.getMessage.contains("exit code was 7"))
  }
}
