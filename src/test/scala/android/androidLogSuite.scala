package android

class AndroidLogSuite extends munit.FunSuite {
  test("renders plugin messages with the branded label and dark-green ANSI") {
    assertEquals(
      AndroidLog.render("ready", ansiSupported = true),
      "\u001b[38;5;22m[sbt-android]\u001b[0m ready"
    )
  }

  test("omits ANSI escapes when the logger does not support color") {
    assertEquals(
      AndroidLog.render("ready", ansiSupported = false),
      "[sbt-android] ready"
    )
  }

  test("labels every line in a multiline message") {
    assertEquals(
      AndroidLog.render("first\nsecond", ansiSupported = false),
      "[sbt-android] first" + System.lineSeparator() + "[sbt-android] second"
    )
  }
}

