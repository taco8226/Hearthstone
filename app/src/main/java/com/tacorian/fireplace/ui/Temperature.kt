package com.tacorian.fireplace.ui

/**
 * The module reports tenths of a degree in its own unit (temperature_unit 0 = °C, 1 = °F).
 * Returns null when there is no reading.
 */
fun formatTemperature(raw: Int?, deviceUnit: Int, celsius: Boolean): String? {
  if (raw == null || raw <= 0) return null
  val deviceValue = raw / 10f
  val c = if (deviceUnit == 1) (deviceValue - 32f) * 5f / 9f else deviceValue
  return if (celsius) "%.1f°C".format(c) else "%.0f°F".format(c * 9f / 5f + 32f)
}
