package android

/** Formats device-list output without depending on ddmlib or SBT logging. */
private[android] object DeviceListFormatter {
  val header = " %-22s %-16s %8s Android Version" format (
    "Serial",
    "Model",
    "Battery %"
  )
  val separator = " %s %s %s ---------------" format (
    "-" * 22,
    "-" * 16,
    "-" * 9
  )

  def row(
      serial: String,
      model: String,
      battery: Option[String],
      androidVersion: String,
      apiLevel: String,
      selected: Boolean
  ): String = {
    val marker = if (selected) "*" else " "
    val batteryText = battery.getOrElse("--")
    f"$marker$serial%-22s $model%-16s $batteryText%8s%% $androidVersion%-6s API $apiLevel"
  }
}