package com.tacorian.fireplace.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tacorian.fireplace.data.FireplaceHost
import com.tacorian.fireplace.data.ProflameClient
import com.tacorian.fireplace.notify.FireplaceService
import com.tacorian.fireplace.widget.WidgetSync
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Which optional controls are visible, and how temperatures are displayed. */
data class DisplaySettings(
  val showFan: Boolean = true,
  val showLight: Boolean = true,
  val showSplitFlow: Boolean = true,
  val showAux: Boolean = true,
  val showPilot: Boolean = true,
  val celsius: Boolean = false,
)

@OptIn(FlowPreview::class)
class FireplaceViewModel(app: Application) : AndroidViewModel(app) {
  private val prefs = app.getSharedPreferences("fireplace", Context.MODE_PRIVATE)

  val client = ProflameClient(viewModelScope)

  private val _host = MutableStateFlow(FireplaceHost.get(app))
  val host: StateFlow<String> = _host.asStateFlow()

  private val _settings = MutableStateFlow(loadSettings())
  val settings: StateFlow<DisplaySettings> = _settings.asStateFlow()

  init {
    // Mirror live state to the widget, and raise the notification whenever the fireplace is lit.
    viewModelScope.launch {
      client.state.filter { it.synced }.debounce(500).collect { WidgetSync.push(app, it) }
    }
    viewModelScope.launch {
      client.state.filter { it.synced }.map { it.isOn }.distinctUntilChanged().collect { on ->
        if (on) FireplaceService.ensureRunning(app)
      }
    }
  }

  fun start() = client.start(_host.value)

  fun stop() = client.stop()

  fun setHost(value: String) {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed == _host.value) return
    FireplaceHost.set(getApplication(), trimmed)
    _host.value = trimmed
    client.start(trimmed)
  }

  fun updateSettings(transform: (DisplaySettings) -> DisplaySettings) {
    val s = transform(_settings.value)
    _settings.value = s
    prefs.edit {
      putBoolean("show_fan", s.showFan)
      putBoolean("show_light", s.showLight)
      putBoolean("show_split_flow", s.showSplitFlow)
      putBoolean("show_aux", s.showAux)
      putBoolean("show_pilot", s.showPilot)
      putBoolean("celsius", s.celsius)
    }
    viewModelScope.launch { WidgetSync.redraw(getApplication()) }
  }

  private fun loadSettings() = DisplaySettings(
    showFan = prefs.getBoolean("show_fan", true),
    showLight = prefs.getBoolean("show_light", true),
    showSplitFlow = prefs.getBoolean("show_split_flow", true),
    showAux = prefs.getBoolean("show_aux", true),
    showPilot = prefs.getBoolean("show_pilot", true),
    celsius = prefs.getBoolean("celsius", false),
  )

  override fun onCleared() = client.stop()
}
