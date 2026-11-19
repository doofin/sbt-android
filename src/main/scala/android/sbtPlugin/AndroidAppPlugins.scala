package android

import Keys._
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.AndroidSdkHandler
import sbt._
import sbt.Keys.{onLoad, onUnload}

object AndroidApp
    extends AutoPlugin
    with AndroidAppSettingsPlugin
    with AndroidTestSettings {
  override def requires = AndroidProject
}

// AndroidProject should not have `android:package`, `android:run`
// etc.
// consider keeping common `android:test` in AndroidProject
object AndroidProject extends AutoPlugin with AndroidProjectSettings {
  override def requires = plugins.JvmPlugin
}

// AndroidLib should support `android:test` as well. no install, run, etc.
object AndroidLib extends AutoPlugin with AndroidLibSettings {
  override def requires = AndroidProject
}
// AndroidJar should support `android:test` as well. no install, run, etc.
object AndroidJar extends AutoPlugin with AndroidJarSettings {
  override def requires = AndroidProject
}
