package android

import sbt.MessageOnlyException

private[android] trait PluginFail {
  def fail[A](msg: => String): A = {
    throw new MessageOnlyException("sbt-android-plugin: " + msg)
  }
  def fail[A](msg: => String, ex: Throwable): A = {
    val e = new MessageOnlyException(msg)
    e.initCause(ex)
    throw e
  }
}

private[android] object PluginFail extends PluginFail {
  def apply[A](msg: => String, filenm: String = ""): A = fail(s"$filenm : $msg")
  def apply[A](msg: => String, ex: Throwable): A = fail(msg, ex)
}
