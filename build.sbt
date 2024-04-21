val pluginVersion = "2.0.0-SNAPSHOT"
val gradleBuildVersion = "2.0.0-SNAPSHOT"

scalaVersion := "2.12.19"

val androidToolsVersion = "2.3.0"
val gradleToolingApi = "2.6"

resolvers ++= Seq(
  "Google's Maven repository" at "https://dl.google.com/dl/android/maven2/"
)

name := "sbt-android"

organization := "org.scala-android"

version := pluginVersion

scalacOptions ++= Seq("-deprecation", "-Xlint", "-feature")

sourceDirectories in Compile := baseDirectory(b => Seq(b / "src")).value

scalaSource in Compile := baseDirectory(_ / "src").value

scalaSource in Test := baseDirectory(_ / "test").value

unmanagedBase := baseDirectory(_ / "libs").value

resourceDirectory in Compile := baseDirectory(_ / "resources").value

// gradle-plugin and gradle-model projects
val model = project
  .in(file("gradle-model"))
  .settings(
    name := "gradle-discovery-model",
    organization := "com.hanhuy.gradle",
    resolvers ++= Seq(
      "Google's Maven repository" at "https://dl.google.com/dl/android/maven2/",
      "Gradle Releases Repository" at "https://repo.gradle.org/gradle/libs-releases-local/"
    ),
    javacOptions ++= "-source" :: "1.6" :: "-target" :: "1.6" :: Nil,
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++=
      "com.android.tools.build" % "builder-model" % androidToolsVersion ::
        "org.gradle" % "gradle-tooling-api" % gradleToolingApi % "provided" :: Nil
  )

val gradle = project
  .in(file("gradle-plugin"))
  .settings(
    name := "gradle-discovery-plugin",
    mappings in (Compile, packageBin) ++= (mappings in (Compile, packageBin) in model).value,
    bintrayRepository in bintray := "maven",
    organization := "com.hanhuy.gradle",
    bintrayOrganization in bintray := None,
    resolvers ++= Seq(
      "Google's Maven repository" at "https://dl.google.com/dl/android/maven2/",
      "Gradle Releases Repository" at "https://repo.gradle.org/gradle/libs-releases-local/"
    ),
    publishMavenStyle := true,
    autoScalaLibrary := false,
    crossPaths := false,
    sbtPlugin := false,
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    version := "0.5",
    javacOptions ++= "-source" :: "1.6" :: "-target" :: "1.6" :: Nil,
    javacOptions in doc := {
      (javacOptions in doc).value.foldRight(List.empty[String]) { (x, a) =>
        if (x != "-target") x :: a else a.drop(1)
      }
    },
    libraryDependencies ++=
      "org.codehaus.groovy" % "groovy" % "2.4.4" % "provided" ::
        "com.android.tools.build" % "gradle" % androidToolsVersion ::
        "com.android.tools.build" % "builder-model" % androidToolsVersion ::
        "org.gradle" % "gradle-tooling-api" % gradleToolingApi % "provided" ::
        "javax.inject" % "javax.inject" % "1" % "provided" ::
        Nil
  )
  .dependsOn(model % "compile-internal")

val gradlebuild = project
  .in(file("gradle-build"))
  .enablePlugins(BuildInfoPlugin, SbtPlugin)
  .settings(
    version := gradleBuildVersion,
    resolvers ++= Seq(
      "Gradle Releases Repository" at "https://repo.gradle.org/gradle/libs-releases-local/"
    ),
    mappings in (Compile, packageBin) ++=
      (mappings in (Compile, packageBin) in model).value,
    name := "sbt-android-gradle",
    organization := "org.scala-android",
    scalacOptions ++= Seq("-deprecation", "-Xlint", "-feature"),
    libraryDependencies ++= Seq(
      "com.google.code.findbugs" % "jsr305" % "3.0.2" % "compile-internal",
      "org.gradle" % "gradle-tooling-api" % gradleToolingApi % "provided",
      "org.slf4j" % "slf4j-api" % "1.7.10" // required by gradle-tooling-api
    ),
    // embed gradle-tooling-api jar in plugin since they don't publish on central
    products in Compile := {
      val p = (products in Compile).value
      val t = crossTarget.value
      val m = (managedClasspath in Compile).value
      val g = t / "gradle-tooling-api"
      val apiJar = m.collectFirst {
        case j
            if j.get(moduleID.key).exists(_.organization == "org.gradle") &&
              j.get(moduleID.key).exists(_.name == "gradle-tooling-api") =>
          j.data
      }
      FileFunction.cached(
        streams.value.cacheDirectory / "gradle-tooling-api",
        FilesInfo.lastModified
      ) { in =>
        in foreach (IO.unzip(_, g, { n: String => !n.startsWith("META-INF") }))
        (g ** "*.class").get.toSet
      }(apiJar.toSet)
      g +: p
    },
    sbtPlugin := true,
    bintrayRepository in bintray := "sbt-plugins",
    publishMavenStyle := false,
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    bintrayOrganization in bintray := None,
    buildInfoKeys := Seq(name, version),
    buildInfoPackage := "android.gradle"
  )
  .settings(
    addSbtPlugin("org.scala-android" % "sbt-android" % pluginVersion)
  )
  .dependsOn(model % "compile-internal")

libraryDependencies ++= Seq(
  "org.ow2.asm" % "asm-all" % "5.2",
  "com.google.code.findbugs" % "jsr305" % "3.0.2" % "compile-internal",
  "org.javassist" % "javassist" % "3.22.0-GA",
  "com.android.tools.build" % "builder" % androidToolsVersion,
  "com.android.tools.build" % "manifest-merger" % "25.3.0",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.59",
  "com.android.tools.build" % "gradle-core" % androidToolsVersion excludeAll
    ExclusionRule(organization = "net.sf.proguard"),
  "com.android.tools.lint" % "lint" % "25.3.0",
//  "com.android.tools.external.com-intellij" % "uast" % "145.597.4", // because google didn't sync the correct version...
  "io.argonaut" %% "argonaut" % "6.2.3",
  "net.orfjackal.retrolambda" % "retrolambda" % "2.5.3",
  "org.scalaz" %% "scalaz-core" % "7.2.20"
)

aggregate := false

sbtPlugin := true

enablePlugins(BuildInfoPlugin, SbtPlugin)

// build info plugin

buildInfoKeys := Seq(name, version, scalaVersion, sbtVersion)

buildInfoPackage := "android"

// bintray

bintrayRepository in bintray := "sbt-plugins"

publishMavenStyle := false

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

bintrayOrganization in bintray := None

pomExtra :=
  <scm>
    <url>git@github.com:scala-android/sbt-android.git</url>
    <connection>scm:git:git@github.com:scala-android/sbt-android.git</connection>
  </scm>
    <developers>
      <developer>
        <id>pfnguyen</id>
        <name>Perry Nguyen</name>
        <url>https://github.com/pfn</url>
      </developer>
    </developers>

scriptedLaunchOpts ++= Seq(
  "-Xmx1024m",
  "-Dplugin.version=" + version.value,
  "-DgradlePlugin.version=" + gradleBuildVersion
)

sbtTestDirectory := baseDirectory(_ / "sbt-test").value

// TODO reorganize tests better, ditch android-sdk-plugin prefix
// group by test config type

// TODO these scriptedDependencies blocks can be removed since sbt scripted uses the current
//  sbt version in scripted tests and you can use `sys.props("plugin.version")` to fetch
//  the current plugin version, rather than adding a task to automatically create these files.
//  Leaving a modified version for now to clean up old versions of the files left behind in local workspaces.
scriptedDependencies := {
  val dir = sbtTestDirectory.value
  val s = streams.value
  val org = organization.value
  val n = name.value
  val v = version.value
  val sbtv = sbtVersion.value

  val testBases = List(dir / "android-sdk-plugin", dir / "no-travis")
  val tests = testBases.flatMap(_.listFiles(DirectoryFilter)) filter { d =>
    (d ** "*.sbt").get.nonEmpty || (d / "project").isDirectory
  }
  tests foreach { test =>
    val project = test / "project"
    project.mkdirs()
    val pluginsFile = project / "auto_plugins.sbt"
    val propertiesFile = project / "build.properties"
    pluginsFile.delete()
    propertiesFile.delete()
  }
}
scriptedDependencies := (scriptedDependencies dependsOn publishLocal).value
