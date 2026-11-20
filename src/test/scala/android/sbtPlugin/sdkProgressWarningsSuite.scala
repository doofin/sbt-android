package android

class SdkProgressWarningsSuite extends munit.FunSuite {
  test("namespace compatibility notices are suppressed") {
    val messages = scala.collection.mutable.ListBuffer.empty[String]
    val reporter = new SdkWarningReporter(verbose = false, messages += _)
    val namespaceWarning =
      "Mapping new ns http://schemas.android.com/repository/android/common/02 to old ns http://schemas.android.com/repository/android/common/01"

    assert(!reporter.warning(namespaceWarning))
    assert(!reporter.warning("  " + namespaceWarning))
    assert(messages.isEmpty)
  }

  test("metadata warnings are summarized once and actionable warnings stay visible") {
    val messages = scala.collection.mutable.ListBuffer.empty[String]
    val summaryKey = new Object
    val reporter = new SdkWarningReporter(
      verbose = false,
      emit = messages += _,
      summaryKey = summaryKey
    )
    val secondReporter = new SdkWarningReporter(
      verbose = false,
      emit = messages += _,
      summaryKey = summaryKey
    )

    assert(
      !reporter.warning(
        "cvc-complex-type.2.4.a: Invalid content in SDK metadata"
      )
    )
    assert(
      !secondReporter.warning("unexpected element (uri:\"\", local:\"tag\")")
    )
    assert(reporter.warning("Observed package id in an inconsistent location"))
    assert(
      !secondReporter.warning("Observed package id in an inconsistent location")
    )
    assert(messages.toList == List(
      SdkProgressWarnings.metadataSummary,
      "Observed package id in an inconsistent location"
    ))
  }

  test("verbose mode preserves full SDK metadata and namespace warnings") {
    val messages = scala.collection.mutable.ListBuffer.empty[String]
    val reporter = new SdkWarningReporter(verbose = true, messages += _)
    val namespaceWarning = "Mapping new ns http://example.test/02 to old ns /01"
    val metadataWarning = "cvc-complex-type.2.4.a: Invalid SDK metadata"

    assert(reporter.warning(namespaceWarning))
    assert(reporter.warning(metadataWarning))
    assert(messages.toList == List(namespaceWarning, metadataWarning))
  }

  test("logger-level verbose setting applies to progress reporters too") {
    val messages = scala.collection.mutable.ListBuffer.empty[String]
    val loggerKey = new Object
    SdkProgressWarnings.configureVerbose(loggerKey, enabled = true)
    val reporter = new SdkWarningReporter(
      verbose = false,
      emit = messages += _,
      summaryKey = loggerKey
    )
    val namespaceWarning = "Mapping new ns http://example.test/02 to old ns /01"

    assert(reporter.warning(namespaceWarning))
    assert(messages.toList == List(namespaceWarning))
  }

  test("other SDK warnings remain visible") {
    assert(!SdkProgressWarnings.shouldSuppress("Invalid SDK metadata"))
    assert(!SdkProgressWarnings.shouldSuppress(null))
  }
}
