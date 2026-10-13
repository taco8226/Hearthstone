package com.tacorian.fireplace.ui

/*
 * The module reports tenths of a degree in its own unit (temperature_unit 0 = °C, 1 = °F).
 * That applies to both room_temperature and temperature_set.
 */

/** Degrees in the display unit, or null when there is no reading. */
fun displayDegrees(raw: Int?, deviceUnit: Int, celsius: Boolean): Float? {
  if (raw == null || raw <= 0) return null
  val deviceValue = raw / 10f
  val c = if (deviceUnit == 1) (deviceValue - 32f) * 5f / 9f else deviceValue
  return if (celsius) c else c * 9f / 5f + 32f
}

/** Returns null when there is no reading. Whole degrees: the module never reports tenths. */
fun formatTemperature(raw: Int?, deviceUnit: Int, celsius: Boolean): String? =
  displayDegrees(raw, deviceUnit, celsius)?.let { if (celsius) "%.0f°C".format(it) else "%.0f°F".format(it) }

/** How long ago [then] was, e.g. "just now", "4 min ago", "2 h ago". */
fun formatAge(then: Long, now: Long): String {
  val minutes = (now - then).coerceAtLeast(0) / 60_000
  return when {
    minutes < 1 -> "just now"
    minutes < 60 -> "$minutes min ago"
    else -> "${minutes / 60} h ago"
  }
}

/** A room reading older than this is shown as stale. */
const val ROOM_STALE_MS = 15 * 60_000L

/**
 * Thermostat set points, in tenths of a degree in the module's unit. The module only keeps whole
 * degrees (it turns 222 into 220), so the −/+ buttons step one whole module degree.
 */
object SetPoint {
  const val STEP = 10

  fun range(deviceUnit: Int) = if (deviceUnit == 1) 500..900 else 100..320
  fun default(deviceUnit: Int) = if (deviceUnit == 1) 700 else 210

  /** Rounds to a whole module degree within range. */
  fun snap(tenths: Int, deviceUnit: Int) = ((tenths + STEP / 2) / STEP * STEP).coerceIn(range(deviceUnit))
}
