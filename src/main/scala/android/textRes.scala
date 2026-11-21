package android

object textRes {
  object Proguard {
    val filePrefixes = Seq(
      "Reading program jar [",
      "Reading library jar [",
      "Copying resources from program jar [",
      "Preparing output jar ["
    )
    val maxNames = 8
    val licenseNoticePrefixes = Seq(
      "ProGuard is released under the GNU General Public License",
      "must ensure that programs that link to it",
      "carry the GNU General Public License as well",
      "apply for an exception with the author of ProGuard"
    )

  }
}
