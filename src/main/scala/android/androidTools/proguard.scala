package android

import textRes.Proguard._

import java.io.{
  ByteArrayOutputStream,
  File,
  FileInputStream,
  FileOutputStream,
  PrintStream
}
import java.lang.reflect.{Constructor, Method}
import java.util.jar.{JarInputStream, JarOutputStream}

import scala.util.Try

import com.android.builder.core.AndroidBuilder

import sbt._
import sbt.io.Using
import sbt.internal.inc.classpath.ClasspathUtilities

case class ProguardInputs(
    injars: Seq[Attributed[File]],
    libraryjars: Seq[File],
    proguardCache: Option[File] = None
)

object ProguardUtil {
  // write to output jar directly, no intermediate unpacking
  def createCacheJar(
      proguardJar: File,
      outJar: File,
      rules: Seq[String],
      log: Logger
  ): Unit = {
    AndroidLog.info(log, "Creating proguard cache: " + outJar.getName)
    // not going to use 'Using' here because it's so ugly!
    val jin = new JarInputStream(new FileInputStream(proguardJar))
    val jout = new JarOutputStream(new FileOutputStream(outJar))
    try {
      val buf = Array.ofDim[Byte](32768)
      Iterator.continually(jin.getNextJarEntry) takeWhile (_ != null) filter {
        entry =>
          inPackages(entry.getName, rules) && !entry.getName.matches(
            ".*/R\\W+.*class"
          )
      } foreach { entry =>
        jout.putNextEntry(entry)
        Iterator.continually(
          jin.read(buf, 0, 32768)
        ) takeWhile (_ != -1) foreach { r =>
          jout.write(buf, 0, r)
        }
      }
    } finally {
      jin.close()
      jout.close()
    }
  }

  def startsWithAny(s: String, ss: Seq[String]): Boolean =
    ss exists s.startsWith
  def inPackages(s: String, pkgs: Seq[String]): Boolean =
    startsWithAny(s.replace('/', '.'), pkgs map (_ + "."))

  def listjar(jarfile: Attributed[File]): List[String] = {
    if (!jarfile.data.isFile) Nil
    else {
      Using.fileInputStream(jarfile.data)(Using.jarInputStream(_) { jin =>
        val classes =
          Iterator
            .continually(jin.getNextJarEntry)
            .takeWhile(_ != null)
            .map(_.getName)
            .filter(n =>
              n.endsWith(".class") && !n.matches(".*/R\\W+.*class")
            ) // R.class (and variants) are irrelevant
            .toList

        classes
      })
    }
  }
}

object Proguard {
  import ProguardUtil._

  private val outputCaptureLock = new AnyRef

  def proguardInputs(
      u: Boolean,
      pgOptions: Seq[String],
      pgConfig: Seq[String],
      l: Seq[File],
      d: sbt.Def.Classpath,
      b: sbt.Def.Classpath,
      c: File,
      s: Boolean,
      pc: Seq[String],
      debug: Boolean,
      st: sbt.Keys.TaskStreams
  ): ProguardInputs = {

    val cacheDir = st.cacheDirectory
    if (u) {
      val injars = d.filter { a =>
        val in = a.data
        (s || !in.getName.startsWith("scala-library")) &&
        !l.exists { i => i.getName == in.getName } &&
        in.isFile
      }.distinct :+ Attributed.blank(c)

      if (debug && pc.nonEmpty) {
        st.log.debug("Proguard cache rules: " + pc)
        val deps = cacheDir / "proguard_deps"
        val out = cacheDir / "proguard_cache"

        deps.mkdirs()
        out.mkdirs()

        // TODO cache resutls of jar listing
        val cacheJars =
          injars.filter(listjar(_).exists(inPackages(_, pc))).toSet
        val filtered = injars.filterNot(cacheJars)

        val indeps = filtered.map(f =>
          deps / (f.data.getName + "-" + Hash.toHex(
            Hash(f.data.getAbsolutePath)
          ))
        )

        AndroidLog.info(st.log, "Finding ProGuard dependencies...")

        indeps
          .zip(filtered)
          .filter { case (dep, j) =>
            !dep.exists || dep.lastModified < j.data.lastModified
          }
          .foreach { case (dep, j) =>
            // st.log.info("Finding dependency for: " + (j.get(sbt.Keys.moduleID.key) getOrElse j.data.getName))
            IO.write(
              dep,
              ReferenceFinder(j.data, pc.map(_.replace('.', '/'))) mkString "\n"
            )
          }

        val alldeps =
          indeps
            .flatMap(dep => IO.readLines(dep))
            .sortWith(_ > _)
            .distinct
            .mkString("\n")

        val allhash = Hash.toHex(
          Hash(
            (pgConfig ++ pgOptions).mkString("\n") + "\n" + pc.mkString(
              ":"
            ) + "\n" + alldeps
          )
        )

        val cacheJar = out / ("proguard-cache-" + allhash + ".jar")
        FileFunction.cached(
          st.cacheDirectory / s"cacheJar-$allhash",
          FilesInfo.hash
        ) { in =>
          cacheJar.delete()
          in
        }(cacheJars map (_.data))

        ProguardInputs(injars, b.map(_.data) ++ l, Some(cacheJar))
      } else ProguardInputs(injars, b.map(_.data) ++ l)
    } else
      ProguardInputs(Seq.empty, Seq.empty)
  }

  def proguard(
      a: Aggregate.Proguard,
      bldr: AndroidBuilder,
      l: Boolean,
      inputs: ProguardInputs,
      debug: Boolean,
      b: File,
      ra: Aggregate.Retrolambda,
      s: sbt.Keys.TaskStreams
  ): Option[File] = {
    val cp = a.managedClasspath
    val p = a.useProguard
    val d = a.useProguardInDebug
    val c = a.proguardConfig
    val o = a.proguardOptions
    val pc = a.proguardCache
    val re = ra.enable

    if (inputs.proguardCache exists (_.exists)) {
      AndroidLog.info(s.log, "ProGuard cache hit; skipping ProGuard")
      None
    } else if ((p && !debug && !l) || ((d && debug) && !l)) {
      val libjars = inputs.libraryjars
      val pjars = inputs.injars map (_.data)
      val jars =
        if (re && RetrolambdaSupport.isAvailable)
          RetrolambdaSupport(b, pjars, ra.classpath, ra.bootClasspath, s)
        else pjars
      val t = b / "classes.proguard.jar"

      val libraryjars = for {
        j <- libjars
        a <- Seq(s"""-libraryjars "${j.getAbsolutePath}"""")
      } yield a
      val injars = "-injars " + (jars map { j =>
        s""""${j.getPath}"(!META-INF/**,!rootdoc.txt)"""
      } mkString File.pathSeparator)
      val outjars = s"""-outjars "${t.getAbsolutePath}""""
      val printmappings = Seq(
        s"""-printmapping "${(b / "mappings.txt").getAbsolutePath}""""
      )
      val cfg = c ++ o ++ libraryjars ++ printmappings :+ injars :+ outjars
      val ruleCache = s.cacheDirectory / "proguard-rules.hash"
      val cacheHash = Try(IO.read(ruleCache)).toOption getOrElse ""
      val rulesHash = Hash.toHex(Hash(cfg mkString "\n"))

      if (
        jars.exists(_.lastModified > t.lastModified) || cacheHash != rulesHash
      ) {
        cfg foreach (l => s.log.debug(l))
        IO.write(s.cacheDirectory / "proguard-rules.hash", rulesHash)
        runProguard(cp, cfg, s.log)
      } else {
        AndroidLog.info(s.log, t.getName + " is up-to-date")
      }
      inputs.proguardCache foreach {
        ProguardUtil.createCacheJar(t, _, pc, s.log)
      }
      Option(t)
    } else None
  }

  object ProguardMirror {
    def fromClasspath(cp: Def.Classpath): ProguardMirror = {
      val cl = ClasspathUtilities.toLoader(cp.map(_.data))
      val cfgClass = cl.loadClass("proguard.Configuration")
      val pgClass = cl.loadClass("proguard.ProGuard")
      val cpClass = cl.loadClass("proguard.ConfigurationParser")
      val cpCtor = cpClass.getConstructor(
        classOf[Array[String]],
        classOf[java.util.Properties]
      )
      val cpMethod = cpClass.getDeclaredMethod("parse", cfgClass)
      cpMethod.setAccessible(true)
      val pgCtor = pgClass.getConstructor(cfgClass)
      ProguardMirror(cfgClass, pgClass, cpClass, cpCtor, cpMethod, pgCtor)
    }
  }
  case class ProguardMirror(
      cfgClass: Class[_],
      pgClass: Class[_],
      cpClass: Class[_],
      cpCtor: Constructor[_],
      cpMethod: Method,
      pgCtor: Constructor[_]
  )

  lazy val proguardMemo =
    scalaz.Memo.immutableHashMapMemo[Def.Classpath, ProguardMirror](
      ProguardMirror.fromClasspath
    )

  def runProguard(
      classpath: Def.Classpath,
      cfg: Seq[String],
      log: Logger
  ): Unit = {
    import language.reflectiveCalls
    val mirror = proguardMemo(classpath)
    type ProG = {
      def execute(): Unit
    }
    import mirror._
    val pgcfg = cfgClass.newInstance().asInstanceOf[AnyRef]

    val cparser =
      cpCtor.newInstance(cfg.toArray[String], new java.util.Properties)
    cpMethod.invoke(cparser, pgcfg)
    val pg = pgCtor.newInstance(pgcfg).asInstanceOf[ProG]
    outputCaptureLock.synchronized {
      val originalOut = System.out
      val capturedBytes = new ByteArrayOutputStream
      val capturedOut = new PrintStream(capturedBytes, true, "UTF-8")
      var executionFailure = Option.empty[Throwable]
      try {
        System.setOut(capturedOut)
        pg.execute()
      } catch {
        case error: Throwable => executionFailure = Some(error)
      } finally {
        System.setOut(originalOut)
        capturedOut.close()
      }

      val lines = new String(capturedBytes.toByteArray, "UTF-8")
        .split("\\r?\\n")
      val summary = ProguardOutputSummary.summarize(lines.toSeq)
      summary.fileMessages.foreach(message => AndroidLog.info(log, message))
      lines
        .filterNot(ProguardOutputSummary.isFileDetail)
        .filterNot(ProguardOutputSummary.isLicenseNotice)
        .foreach(originalOut.println)
      executionFailure.foreach(throw _)
    }
  }
}

object ProguardOutputSummary {

  private[android] case class Summary(fileMessages: Seq[String])

  def isFileDetail(line: String): Boolean = entry(line).isDefined

  def isLicenseNotice(line: String): Boolean =
    licenseNoticePrefixes.exists(line.trim.startsWith)

  def summarize(lines: Seq[String]): Summary = {
    val names = lines.flatMap(line => entry(line)).distinct
    if (names.isEmpty) Summary(Nil)
    else {
      val shown = names.take(maxNames)
      val remaining = names.size - shown.size
      val suffix = if (remaining > 0) s", +$remaining more" else ""
      Summary(Seq(s"ProGuard files: ${shown.mkString(", ")}$suffix"))
    }
  }

  private def entry(line: String): Option[String] =
    filePrefixes.iterator
      .flatMap(prefix => entry(line, prefix))
      .take(1)
      .toList
      .headOption

  private def entry(line: String, prefix: String): Option[String] = {
    val trimmed = line.trim
    if (!trimmed.startsWith(prefix)) None
    else {
      val start = prefix.length
      val end = trimmed.indexOf(']', start)
      if (end < 0) None
      else
        Option(new File(trimmed.substring(start, end)).getName)
          .filter(_.nonEmpty)
    }
  }
}
