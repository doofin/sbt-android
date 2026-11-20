package android

class DeviceListFormatterSuite extends munit.FunSuite {
  test(
    "device row shows identity and Android version when battery is unknown"
  ) {
    val row = DeviceListFormatter.row(
      serial = "device-123",
      model = "Pixel",
      battery = None,
      androidVersion = "14",
      apiLevel = "34",
      selected = false
    )

    assert(row.contains("device-123"))
    assert(row.contains("Pixel"))
    assert(row.contains("--%"))
    assert(row.contains("14"))
    assert(row.contains("API 34"))
  }

  test("selected device is marked and available battery is printed") {
    val row = DeviceListFormatter.row(
      serial = "device-123",
      model = "Pixel",
      battery = Some("87"),
      androidVersion = "14",
      apiLevel = "34",
      selected = true
    )

    assert(row.startsWith("*device-123"))
    assert(row.contains("87%"))
  }
}
