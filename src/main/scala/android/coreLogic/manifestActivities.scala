package android

import scala.xml.{Elem, Node, NodeSeq}

/** XML-only Android manifest activity discovery, independent of SBT state. */
private[android] object ManifestActivities {
  val ANDROID_NS = "http://schemas.android.com/apk/res/android"
  val ACTION_MAIN = "android.intent.action.MAIN"

  def activityName(node: Node): String =
    node.attribute(ANDROID_NS, "name").head.text

  def findMainActivities(element: Elem): NodeSeq = {
    for {
      activity <- element \\ "activity" ++ element \\ "activity-alias"
      intentAction <- activity \ "intent-filter" \ "action"
      actionName <- intentAction.attribute(ANDROID_NS, "name").toSeq.flatten
      mainAction <- actionName if mainAction.text == ACTION_MAIN
    } yield activity
  }
}