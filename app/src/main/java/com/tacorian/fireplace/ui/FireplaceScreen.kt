package com.tacorian.fireplace.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tacorian.fireplace.data.ConnectionStatus
import com.tacorian.fireplace.theme.Ember
import com.tacorian.fireplace.theme.Ok
import com.tacorian.fireplace.theme.Warn

@Composable
fun FireplaceScreen(vm: FireplaceViewModel = viewModel()) {
  // Hold the socket only while the app is visible.
  LifecycleStartEffect(vm) {
    vm.start()
    onStopOrDispose { vm.stop() }
  }

  val state by vm.client.state.collectAsStateWithLifecycle()
  val status by vm.client.status.collectAsStateWithLifecycle()
  val host by vm.host.collectAsStateWithLifecycle()
  val settings by vm.settings.collectAsStateWithLifecycle()
  var showSettings by remember { mutableStateOf(false) }

  val connected = status == ConnectionStatus.Connected
  val c = vm.client

  Column(
    modifier = Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    val roomTemp = formatTemperature(state.roomTemperature, state.temperatureUnit, settings.celsius)
    Header(status, host, roomTemp, onOpenSettings = { showSettings = true })

    PowerButton(isOn = state.isOn, enabled = connected, onClick = { c.setPower(!state.isOn) })

    Card(Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LevelControl("Flame", state.flame, enabled = connected && state.isOn, onChange = c::setFlame)
        if (settings.showFan) LevelControl("Fan", state.fan, enabled = connected, onChange = c::setFan)
        if (settings.showLight) LevelControl("Light", state.lamp, enabled = connected, onChange = c::setLamp)
      }
    }

    val toggles = buildList<@Composable () -> Unit> {
      if (settings.showSplitFlow) add { ToggleRow("Split flow", "Second burner", state.splitFlow, connected, c::setSplitFlow) }
      if (settings.showAux) add { ToggleRow("Auxiliary", "Aux output", state.aux, connected, c::setAux) }
      if (settings.showPilot) add { ToggleRow("Continuous pilot", "Keep pilot lit", state.pilot, connected, c::setPilot) }
    }
    if (toggles.isNotEmpty()) {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
          toggles.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider()
            row()
          }
        }
      }
    }
  }

  if (showSettings) {
    SettingsDialog(
      host = host,
      settings = settings,
      onChange = vm::updateSettings,
      onDone = { newHost -> vm.setHost(newHost); showSettings = false },
    )
  }
}

@Composable
private fun Header(status: ConnectionStatus, host: String, roomTemp: String?, onOpenSettings: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text("Hearthstone", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        val (dot, label) = when (status) {
          ConnectionStatus.Connected -> Ok to "Connected"
          ConnectionStatus.Connecting -> Warn to "Connecting…"
          ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.error to "Offline"
        }
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
          if (host.isBlank()) "No address · open Settings" else "$label · $host",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Column(horizontalAlignment = Alignment.End) {
      if (roomTemp != null) {
        Text(roomTemp, style = MaterialTheme.typography.headlineSmall)
        Text("Room", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      TextButton(onClick = onOpenSettings) { Text("Settings") }
    }
  }
}

@Composable
private fun PowerButton(isOn: Boolean, enabled: Boolean, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().height(88.dp),
    colors = if (isOn) {
      ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White)
    } else {
      ButtonDefaults.filledTonalButtonColors()
    },
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(if (isOn) "On" else "Off", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
      Text(if (isOn) "Tap to turn off" else "Tap to ignite", style = MaterialTheme.typography.labelMedium)
    }
  }
}

@Composable
private fun LevelControl(label: String, level: Int, enabled: Boolean, onChange: (Int) -> Unit) {
  // Track the thumb locally while dragging; send only when released.
  var dragging by remember { mutableStateOf(false) }
  var draft by remember { mutableFloatStateOf(level.toFloat()) }
  val shown = if (dragging) draft else level.toFloat()

  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
      Text(
        if (shown.toInt() == 0) "Off" else "${shown.toInt()} / 6",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Slider(
      value = shown,
      onValueChange = { dragging = true; draft = it },
      onValueChangeFinished = { dragging = false; onChange(draft.toInt()) },
      valueRange = 0f..6f,
      steps = 5,
      enabled = enabled,
    )
  }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
  }
}

@Composable
private fun SettingsDialog(
  host: String,
  settings: DisplaySettings,
  onChange: ((DisplaySettings) -> DisplaySettings) -> Unit,
  onDone: (String) -> Unit,
) {
  var hostText by remember { mutableStateOf(host) }
  AlertDialog(
    onDismissRequest = { onDone(hostText) },
    title = { Text("Settings") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Temperature", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
          SegmentedButton(
            selected = !settings.celsius,
            onClick = { onChange { it.copy(celsius = false) } },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
          ) { Text("°F") }
          SegmentedButton(
            selected = settings.celsius,
            onClick = { onChange { it.copy(celsius = true) } },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
          ) { Text("°C") }
        }

        Spacer(Modifier.height(8.dp))
        Text("Show controls", style = MaterialTheme.typography.titleSmall)
        CheckRow("Fan", settings.showFan) { v -> onChange { it.copy(showFan = v) } }
        CheckRow("Light", settings.showLight) { v -> onChange { it.copy(showLight = v) } }
        CheckRow("Split flow", settings.showSplitFlow) { v -> onChange { it.copy(showSplitFlow = v) } }
        CheckRow("Auxiliary", settings.showAux) { v -> onChange { it.copy(showAux = v) } }
        CheckRow("Continuous pilot", settings.showPilot) { v -> onChange { it.copy(showPilot = v) } }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = hostText,
          onValueChange = { hostText = it },
          label = { Text("Fireplace IP address") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = { TextButton(onClick = { onDone(hostText) }) { Text("Done") } },
  )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable { onChange(!checked) },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = onChange)
    Text(label, style = MaterialTheme.typography.bodyLarge)
  }
}
