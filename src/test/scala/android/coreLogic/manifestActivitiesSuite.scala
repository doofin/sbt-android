package android

import scala.xml.XML

class ManifestActivitiesSuite extends munit.FunSuite {
  private val manifest = XML.loadString(
    s"""<manifest xmlns:android="${ManifestActivities.ANDROID_NS}">
       |  <application>
       |    <activity android:name=".Main">
       |      <intent-filter><action android:name="${ManifestActivities.ACTION_MAIN}"/></intent-filter>
       |    </activity>
       |    <activity android:name=".Other">
       |      <intent-filter><action android:name="android.intent.action.VIEW"/></intent-filter>
       |    </activity>
       |    <activity-alias android:name=".Alias" android:targetActivity=".Main">
       |      <intent-filter><action android:name="${ManifestActivities.ACTION_MAIN}"/></intent-filter>
       |    </activity-alias>
       |  </application>
       |</manifest>""".stripMargin
  )

  test("finds MAIN activities and aliases in parser order") {
    val activities = ManifestActivities.findMainActivities(manifest)

    assert(
      activities.map(ManifestActivities.activityName).toList ==
        List(".Main", ".Alias")
    )
  }

  test("does not include activities without a MAIN action") {
    val activities = ManifestActivities.findMainActivities(manifest)

    assert(!activities.exists(ManifestActivities.activityName(_) == ".Other"))
  }
}
