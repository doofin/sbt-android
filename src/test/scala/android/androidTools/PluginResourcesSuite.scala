package android

class PluginResourcesSuite extends munit.FunSuite {
  test("plugin templates are available by their existing classpath names") {
    val resources = Seq(
      "android-proguard.config",
      "tr.scala.template",
      "viewHolders.scala.template",
      "android-project-template/AndroidManifest.xml",
      "android-project-template/sample.scala",
      "android-project-template/strings.xml"
    )
    resources.foreach { name =>
      assert(Resources.resourceUrl(name) != null, s"Missing classpath resource: $name")
    }
  }
}