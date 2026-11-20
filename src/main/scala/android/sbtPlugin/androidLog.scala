package android

import sbt.Logger
import sbt.internal.util.ConsoleAppender

/** Plugin-owned informational output with a stable, branded console label. */
private[android] object AndroidLog {
  private val label = "[sbt-android]"
  private val darkGreen = "\u001b[38;5;22m"
  private val reset = "\u001b[0m"
  private val outputLock = new AnyRef

  private[android] def render(
      message: String,
      ansiSupported: Boolean
  ): String = {
    val prefix = if (ansiSupported) s"$darkGreen$label$reset" else label
    message
      .split("\\r?\\n", -1)
      .map(line => s"$prefix $line")
      .mkString(System.lineSeparator())
  }

  def info(logger: Logger, message: String): Unit =
    outputLock.synchronized {
      System.out.println(render(message, ConsoleAppender.formatEnabledInEnv))
    }

  def success(logger: Logger, message: String): Unit = info(logger, message)
}
