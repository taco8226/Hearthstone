package com.tacorian.fireplace.ui

import android.content.SharedPreferences
import androidx.core.content.edit

/** One card on the main screen. [key] names its visibility pref; null means it can't be hidden. */
enum class Tile(val label: String, val key: String?) {
  Mascot("Toasty", "show_mascot"),
  Power("Power", null),
  Thermostat("Thermostat", "show_thermostat"),
  Flame("Flame", null),
  Fan("Fan", "show_fan"),
  Light("Light", "show_light"),
  SplitFlow("Split flow", "show_split_flow"),
  Aux("Auxiliary", "show_aux"),
  Pilot("Continuous pilot", "show_pilot"),
}

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

/** Which controls are visible and in what order, plus appearance and units. */
data class DisplaySettings(
  val order: List<Tile> = Tile.entries,
  val hidden: Set<Tile> = emptySet(),
  val celsius: Boolean = false,
  val theme: ThemeMode = ThemeMode.System,
  val dynamicColor: Boolean = false,
) {
  fun isVisible(tile: Tile) = tile !in hidden

  fun withVisible(tile: Tile, visible: Boolean) = copy(hidden = if (visible) hidden - tile else hidden + tile)

  fun moved(from: Int, to: Int) = copy(order = order.toMutableList().apply { add(to, removeAt(from)) })

  // Keys are shared with the widget and notification, which read show_* and celsius directly.
  fun save(prefs: SharedPreferences) = prefs.edit {
    Tile.entries.forEach { t -> t.key?.let { putBoolean(it, isVisible(t)) } }
    putString("tile_order", order.joinToString(",") { it.name })
    putBoolean("celsius", celsius)
    putString("theme", theme.name)
    putBoolean("dynamic_color", dynamicColor)
  }

  companion object {
    fun load(prefs: SharedPreferences): DisplaySettings {
      val saved = prefs.getString("tile_order", null).orEmpty().split(",")
        .mapNotNull { name -> Tile.entries.find { it.name == name } }
      return DisplaySettings(
        // Tiles added in later versions go at the end.
        order = saved + (Tile.entries - saved.toSet()),
        hidden = Tile.entries.filter { t -> t.key != null && !prefs.getBoolean(t.key, true) }.toSet(),
        celsius = prefs.getBoolean("celsius", false),
        theme = ThemeMode.entries.find { it.name == prefs.getString("theme", null) } ?: ThemeMode.System,
        dynamicColor = prefs.getBoolean("dynamic_color", false),
      )
    }
  }
}
