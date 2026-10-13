package com.tacorian.fireplace.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tacorian.fireplace.data.Command
import com.tacorian.fireplace.data.FireplaceHost
import com.tacorian.fireplace.data.FireplaceState
import com.tacorian.fireplace.data.ProflameOneShot
import com.tacorian.fireplace.notify.FireplaceService
import com.tacorian.fireplace.ui.formatTemperature

/** Home screen widget: power, split flow, and −/+ steppers for flame, fan and light. */
class HearthstoneWidget : GlanceAppWidget() {
  override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val appPrefs = context.getSharedPreferences("fireplace", Context.MODE_PRIVATE)
    provideContent {
      val prefs = currentState<Preferences>()
      WidgetContent(
        prefs = prefs,
        showFan = appPrefs.getBoolean("show_fan", true),
        showLight = appPrefs.getBoolean("show_light", true),
        showSplitFlow = appPrefs.getBoolean("show_split_flow", true),
        celsius = appPrefs.getBoolean("celsius", false),
      )
    }
  }
}

class HearthstoneWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = HearthstoneWidget()
}

/* ---------------- State ---------------- */

private object Keys {
  val known = booleanPreferencesKey("known")
  val mainMode = intPreferencesKey("main_mode")
  val flame = intPreferencesKey("flame")
  val fan = intPreferencesKey("fan")
  val lamp = intPreferencesKey("lamp")
  val split = booleanPreferencesKey("split")
  val room = intPreferencesKey("room")
  val unit = intPreferencesKey("unit")
  val busy = booleanPreferencesKey("busy")
  val error = booleanPreferencesKey("error")
  /** Bumped to force a redraw when only app settings changed. */
  val rev = intPreferencesKey("rev")
}

private fun MutablePreferences.store(s: FireplaceState) {
  this[Keys.known] = true
  this[Keys.mainMode] = s.mainMode
  this[Keys.flame] = s.flame
  this[Keys.fan] = s.fan
  this[Keys.lamp] = s.lamp
  this[Keys.split] = s.splitFlow
  this[Keys.room] = s.roomTemperature ?: 0
  this[Keys.unit] = s.temperatureUnit
}

/** Applies [edit] to every placed widget's state and redraws them. */
private suspend fun updateAllWidgets(context: Context, edit: (MutablePreferences) -> Unit) {
  val widget = HearthstoneWidget()
  GlanceAppWidgetManager(context).getGlanceIds(HearthstoneWidget::class.java).forEach { id ->
    updateAppWidgetState(context, id) { edit(it) }
    widget.update(context, id)
  }
}

/** Lets the app push live state so the widget matches what the app last saw. */
object WidgetSync {
  suspend fun push(context: Context, state: FireplaceState) =
    updateAllWidgets(context) { it.store(state); it[Keys.error] = false }

  /** Redraws without changing state, e.g. after display settings change. */
  suspend fun redraw(context: Context) = updateAllWidgets(context) { it[Keys.rev] = (it[Keys.rev] ?: 0) + 1 }
}

/* ---------------- Actions ---------------- */

private val OpKey = ActionParameters.Key<String>("op")
private val FieldKey = ActionParameters.Key<String>("field")
private val DeltaKey = ActionParameters.Key<Int>("delta")

private fun toggle(field: String) = actionRunCallback<FireplaceAction>(actionParametersOf(OpKey to "toggle", FieldKey to field))
private fun step(field: String, delta: Int) =
  actionRunCallback<FireplaceAction>(actionParametersOf(OpKey to "step", FieldKey to field, DeltaKey to delta))
private val refresh = actionRunCallback<FireplaceAction>(actionParametersOf(OpKey to "refresh"))

class FireplaceAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val host = FireplaceHost.get(context)
    val field = parameters[FieldKey]
    val delta = parameters[DeltaKey] ?: 0

    // Commands are computed from freshly read state so a stale widget never flips the wrong way.
    val command: ((FireplaceState) -> Command?)? = when (parameters[OpKey]) {
      "toggle" -> { s ->
        when (field) {
          "main_mode" -> Command("main_mode", if (s.isOn) 0 else 1)
          "split_flow" -> Command("split_flow", if (s.splitFlow) 0 else 1)
          else -> null
        }
      }
      "step" -> { s ->
        val current = when (field) {
          "flame_control" -> s.flame
          "fan_control" -> s.fan
          "lamp_control" -> s.lamp
          else -> null
        }
        current?.let { Command(field!!, (it + delta).coerceIn(0, 6)) }?.takeIf { it.value != current }
      }
      else -> null
    }

    updateAllWidgets(context) { it[Keys.busy] = true }
    val result = runCatching { ProflameOneShot.exchange(host, command) }
    updateAllWidgets(context) { prefs ->
      prefs[Keys.busy] = false
      prefs[Keys.error] = result.isFailure
      result.getOrNull()?.let { prefs.store(it) }
    }
    if (result.getOrNull()?.isOn == true) FireplaceService.ensureRunning(context)
  }
}

/* ---------------- UI ---------------- */

private val Bg = ColorProvider(Color(0xF21A1410))
private val Surface = ColorProvider(Color(0xFF2A211B))
private val EmberC = ColorProvider(Color(0xFFE8590C))
private val Gold = ColorProvider(Color(0xFFE0B040))
private val TextMain = ColorProvider(Color(0xFFF5EDE6))
private val TextDim = ColorProvider(Color(0xFFB3A398))
private val ErrorC = ColorProvider(Color(0xFFFF8A80))

@Composable
private fun WidgetContent(prefs: Preferences, showFan: Boolean, showLight: Boolean, showSplitFlow: Boolean, celsius: Boolean) {
  val known = prefs[Keys.known] ?: false
  val isOn = (prefs[Keys.mainMode] ?: 0) != 0
  val busy = prefs[Keys.busy] ?: false
  val error = prefs[Keys.error] ?: false

  Column(
    modifier = GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp).padding(12.dp),
  ) {
    // Header: power button + status
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = GlanceModifier
          .width(88.dp).height(44.dp)
          .background(if (isOn) EmberC else Surface)
          .cornerRadius(22.dp)
          .clickable(toggle("main_mode")),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          if (isOn) "ON" else "OFF",
          style = TextStyle(color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
      }
      Spacer(GlanceModifier.width(10.dp))
      Column(modifier = GlanceModifier.defaultWeight().clickable(refresh)) {
        Text("Hearthstone", style = TextStyle(color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        val status = when {
          busy -> "Working…"
          error -> "Can't reach fireplace · tap to retry"
          !known -> "Tap to load"
          else -> formatTemperature(prefs[Keys.room], prefs[Keys.unit] ?: 0, celsius)?.let { "Room $it" } ?: "Tap to refresh"
        }
        Text(status, style = TextStyle(color = if (error) ErrorC else TextDim, fontSize = 12.sp), maxLines = 1)
      }
      if (showSplitFlow) {
        val split = prefs[Keys.split] ?: false
        Box(
          modifier = GlanceModifier
            .height(32.dp).width(64.dp)
            .background(if (split) EmberC else Surface)
            .cornerRadius(16.dp)
            .clickable(toggle("split_flow")),
          contentAlignment = Alignment.Center,
        ) {
          Text("Split", style = TextStyle(color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
      }
    }

    Spacer(GlanceModifier.height(8.dp))
    StepperRow("Flame", "flame_control", prefs[Keys.flame] ?: 0)
    if (showFan) StepperRow("Fan", "fan_control", prefs[Keys.fan] ?: 0)
    if (showLight) StepperRow("Light", "lamp_control", prefs[Keys.lamp] ?: 0)
  }
}

@Composable
private fun StepperRow(label: String, field: String, level: Int) {
  Row(
    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = TextStyle(color = TextMain, fontSize = 14.sp), modifier = GlanceModifier.width(52.dp))
    StepButton("−", step(field, -1))
    Text(
      "●".repeat(level) + "○".repeat(6 - level),
      style = TextStyle(color = if (level > 0) Gold else TextDim, fontSize = 14.sp, textAlign = TextAlign.Center),
      modifier = GlanceModifier.defaultWeight(),
    )
    StepButton("+", step(field, +1))
  }
}

@Composable
private fun StepButton(symbol: String, action: androidx.glance.action.Action) {
  Box(
    modifier = GlanceModifier.size(36.dp).background(Surface).cornerRadius(18.dp).clickable(action),
    contentAlignment = Alignment.Center,
  ) {
    Text(symbol, style = TextStyle(color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold))
  }
}
