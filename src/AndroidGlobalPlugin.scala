package android

import Keys._
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.AndroidSdkHandler
import sbt._
import sbt.Keys.{onLoad, onUnload}

case object AndroidGlobalPlugin extends AutoPlugin {
  val autoImport: Keys.type = android.Keys

  override def globalSettings =
    (onLoad := onLoad.value andThen onLoadOnce(this) { s =>
      val e = Project.extract(s)

      def isAndroid(ref: ProjectRef) = e.getOpt(projectLayout in ref).isDefined
      def isAndroidJar(ref: ProjectRef) = e.structure.allProjects.exists(p =>
        p.id == ref.project && p.autoPlugins.contains(AndroidJar)
      )

      val androids = e.structure.allProjects map (p =>
        ProjectRef(e.structure.root, p.id)
      ) filter isAndroid
      val androidSet = androids.toSet

      def isAndroidDep(ref: ProjectRef) = androidSet(ref) || isAndroid(ref)

      def checkAndroidDependencies(
          p: ProjectRef
      ): (ProjectRef, Seq[ProjectRef]) = {
        (
          p,
          Project.getProject(p, e.structure).toSeq flatMap { prj =>
            val deps = prj.dependencies map (_.project)
            val locals = Project
              .extract(s)
              .get(localProjects in p)
              .map(_.path.getCanonicalPath)
              .toSet
            val depandroids = deps filter isAndroidDep
            depandroids filterNot isAndroidJar filterNot (a =>
              Project
                .getProject(a, e.structure)
                .exists(d => locals(d.base.getCanonicalPath))
            )
          }
        )
      }
      def checkForExport(p: ProjectRef): Seq[ProjectRef] = {
        Project.getProject(p, e.structure).toSeq flatMap { prj =>
          val deps = prj.dependencies map (_.project)
          val nonAndroid = deps filterNot isAndroidDep

          (deps flatMap checkForExport) ++ (nonAndroid filterNot (d =>
            e.getOpt(sbt.Keys.exportJars in d) exists (_ == true)
          ))
        }
      }
      val addDeps = androids map checkAndroidDependencies map { case (p, dep) =>
        p -> android.buildWith(dep).map(VariantSettings.fixProjectScope(p))
      }
      androids flatMap checkForExport foreach { unexported =>
        // TODO automatically append `exportJars := true` ?
        s.log.warn(
          s"${unexported.project} is an Android dependency but does not specify `exportJars := true`"
        )
      }

      val s2 = androids.headOption.fold(s) { a =>
        val s3 = e.runTask(updateCheck in a, s)._1
        e.runTask(updateCheckSdk in a, s3)._1
      }

      val end = androids.foldLeft(s2) { (s, ref) =>
        e.runTask(antLayoutDetector in ref, s)._1
      }

      s.log.success("AndroidGlobalPlugin: sbt android load finished")

      if (addDeps.flatMap(_._2).nonEmpty) {
        s.log.info(s"Adding android subproject dependency rules for: ${addDeps
            .collect { case (p, ds) if ds.nonEmpty => p.project }
            .mkString(", ")}")
        VariantSettings.append(end, addDeps.flatMap(_._2))
      } else end

    }) :: (onUnload := onUnload.value andThen onReload(this) { s =>
      s.remove(VariantSettings.originalSettings)
    }) :: Nil

  def platformTarget(
      targetHash: String,
      sdkHandler: AndroidSdkHandler,
      showProgress: Boolean,
      slog: Logger
  ): IAndroidTarget = {
    SdkInstaller.retryWhileFailed("determine platform target", slog) {
      val manager =
        sdkHandler.getAndroidTargetManager(SbtAndroidProgressIndicator(slog))
      val ptarget = manager.getTargetFromHashString(
        targetHash,
        SbtAndroidProgressIndicator(slog)
      )
      SdkInstaller.autoInstallPackage(
        sdkHandler,
        "platforms;",
        targetHash,
        targetHash,
        showProgress,
        slog,
        _ => ptarget == null
      )
      manager.getTargetFromHashString(
        targetHash,
        SbtAndroidProgressIndicator(slog)
      )
    }
  }

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  override def buildSettings = Commands.androidCommands

  override def projectConfigurations =
    AndroidTest :: Internal.AndroidInternal :: Nil

}
