package com.tacorian.fireplace.ui

import android.app.Application
import android.content.Context
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

@OptIn(FlowPreview::class)
class FireplaceViewModel(app: Application) : AndroidViewModel(app) {
  private val prefs = app.getSharedPreferences("fireplace", Context.MODE_PRIVATE)

  val client = ProflameClient(viewModelScope)

  private val _host = MutableStateFlow(FireplaceHost.get(app))
  val host: StateFlow<String> = _host.asStateFlow()

  private val _settings = MutableStateFlow(DisplaySettings.load(prefs))
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
    val old = _settings.value
    val s = transform(old)
    _settings.value = s
    s.save(prefs)
    // The widget only shows some tiles and the unit; skip redraws for reordering and theming.
    if (s.celsius != old.celsius || s.hidden != old.hidden) viewModelScope.launch { WidgetSync.redraw(getApplication()) }
  }

  override fun onCleared() = client.stop()
}
