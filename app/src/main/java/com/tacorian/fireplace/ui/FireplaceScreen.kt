package com.tacorian.fireplace.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tacorian.fireplace.R
import com.tacorian.fireplace.data.ConnectionStatus
import com.tacorian.fireplace.data.FireplaceState
import com.tacorian.fireplace.data.ProflameClient
import com.tacorian.fireplace.theme.Ember
import com.tacorian.fireplace.theme.Ok
import com.tacorian.fireplace.theme.Warn
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The main screen, or Settings on top of it. */
@Composable
fun HearthstoneApp(vm: FireplaceViewModel) {
  // Hold the socket only while the app is visible.
  LifecycleStartEffect(vm) {
    vm.start()
    onStopOrDispose { vm.stop() }
  }

  val host by vm.host.collectAsStateWithLifecycle()
  val settings by vm.settings.collectAsStateWithLifecycle()
  var showSettings by rememberSaveable { mutableStateOf(false) }

  AnimatedContent(showSettings, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { settingsOpen ->
    if (settingsOpen) {
      SettingsScreen(
        host = host,
        settings = settings,
        onChange = vm::updateSettings,
        onDone = { newHost -> vm.setHost(newHost); showSettings = false },
      )
    } else {
      FireplaceScreen(vm, host, settings, onOpenSettings = { showSettings = true })
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FireplaceScreen(vm: FireplaceViewModel, host: String, settings: DisplaySettings, onOpenSettings: () -> Unit) {
  val state by vm.client.state.collectAsStateWithLifecycle()
  val status by vm.client.status.collectAsStateWithLifecycle()
  val remoteInControl by vm.client.remoteInControl.collectAsStateWithLifecycle()
  val connected = status == ConnectionStatus.Connected

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Title(status, host) },
        actions = {
          RoomReading(state, settings.celsius, connected)
          IconButton(onClick = onOpenSettings) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (remoteInControl && connected) RemoteInControlBanner()
      settings.order.filter(settings::isVisible).forEach { tile ->
        key(tile) { TileCard(tile, state, status, remoteInControl, settings.celsius, vm.client) }
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun TileCard(
  tile: Tile,
  state: FireplaceState,
  status: ConnectionStatus,
  remoteInControl: Boolean,
  celsius: Boolean,
  c: ProflameClient,
) {
  val connected = status == ConnectionStatus.Connected
  when (tile) {
    Tile.Mascot -> MascotCard(state, status, remoteInControl, celsius)
    Tile.Power -> PowerButton(state, enabled = connected, onClick = { c.setPower(!state.isOn) })
    Tile.Thermostat -> ThermostatCard(state, connected, celsius, c)
    Tile.Flame -> LevelCard("Flame", state.flame, enabled = connected && state.isOn, onChange = c::setFlame)
    Tile.Fan -> LevelCard("Fan", state.fan, enabled = connected, onChange = c::setFan)
    Tile.Light -> LevelCard("Light", state.lamp, enabled = connected, onChange = c::setLamp)
    Tile.SplitFlow -> ToggleCard("Split flow", "Second burner", state.splitFlow, connected, c::setSplitFlow)
    Tile.Aux -> ToggleCard("Auxiliary", "Aux output", state.aux, connected, c::setAux)
    Tile.Pilot -> ToggleCard("Continuous pilot", "Keep pilot lit", state.pilot, connected, c::setPilot)
  }
}

@Composable
private fun Title(status: ConnectionStatus, host: String) {
  Column {
    Text("Hearthstone", fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
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
}

/** Shown while the remote keeps re-sending its settings, which undoes anything changed here. */
@Composable
private fun RemoteInControlBanner() {
  Card(
    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer,
      contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("The remote is in control", style = MaterialTheme.typography.titleMedium)
      Text(
        "It turned the fireplace on and keeps re-sending its settings, so changes here are undone. " +
          "Turn the fireplace off with the remote to hand control back to the app.",
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

/** Room temperature in both units, where it comes from, and how fresh it is. */
@Composable
private fun RoomReading(state: FireplaceState, celsius: Boolean, connected: Boolean) {
  val primary = formatTemperature(state.roomTemperature, state.temperatureUnit, celsius) ?: return
  val secondary = formatTemperature(state.roomTemperature, state.temperatureUnit, !celsius)
  // Re-read the clock every 30 s so the age label keeps moving.
  val now by produceState(System.currentTimeMillis()) {
    while (true) {
      delay(30_000)
      value = System.currentTimeMillis()
    }
  }
  val updatedAt = state.roomUpdatedAt
  val old = updatedAt != null && now - updatedAt > ROOM_STALE_MS
  val stale = !connected || updatedAt == null || old
  val age = when {
    !connected -> "last known"
    updatedAt == null -> "awaiting update"
    else -> formatAge(updatedAt, now)
  }
  val faded = MaterialTheme.colorScheme.onSurfaceVariant

  Column(
    horizontalAlignment = Alignment.End,
    modifier = Modifier
      .padding(end = 4.dp)
      .semantics(mergeDescendants = true) {
        contentDescription = "Room temperature at the remote, $primary, $secondary, $age"
      },
  ) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text(primary, style = MaterialTheme.typography.titleLarge, color = if (stale) faded else Color.Unspecified)
      Spacer(Modifier.width(6.dp))
      Text(secondary.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = faded)
    }
    Text(
      "At remote · $age",
      style = MaterialTheme.typography.labelSmall,
      color = if (old && connected) Warn else faded,
    )
  }
}

@Composable
private fun PowerButton(state: FireplaceState, enabled: Boolean, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.fillMaxWidth().height(88.dp),
    shape = MaterialTheme.shapes.extraLarge,
    colors = if (state.isOn) {
      ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Color.White)
    } else {
      ButtonDefaults.filledTonalButtonColors()
    },
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        when {
          state.isThermostat -> "On · Thermostat"
          state.isOn -> "On"
          else -> "Off"
        },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
      )
      Text(if (state.isOn) "Tap to turn off" else "Tap to ignite", style = MaterialTheme.typography.labelMedium)
    }
  }
}

@Composable
private fun ThermostatCard(state: FireplaceState, connected: Boolean, celsius: Boolean, c: ProflameClient) {
  val unit = state.temperatureUnit
  // −/+ taps adjust a local draft; it's sent once the taps stop, or when the thermostat is switched on.
  var draft by remember(unit) { mutableStateOf<Int?>(null) }
  val target = draft ?: state.temperatureSet.takeIf { it > 0 }?.let { SetPoint.snap(it, unit) } ?: SetPoint.default(unit)

  LaunchedEffect(draft, state.isThermostat, connected) {
    val d = draft ?: return@LaunchedEffect
    if (!state.isThermostat || !connected) return@LaunchedEffect
    delay(SET_POINT_DEBOUNCE_MS)
    c.setTargetTemperature(d)
    draft = null
  }

  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SwitchRow(
        title = "Thermostat",
        subtitle = if (state.isThermostat) {
          "Turns off if it burns ${ROOM_STALE_MS / 60_000} min with no new reading from the remote"
        } else {
          "Let the fireplace hold a target temperature"
        },
        checked = state.isThermostat,
        enabled = connected,
        onChange = { on ->
          if (on) c.setThermostat(true, target) else c.setThermostat(false)
          draft = null
        },
      )
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val range = SetPoint.range(unit)
        FilledTonalIconButton(
          onClick = { draft = SetPoint.snap(target - SetPoint.STEP, unit) },
          enabled = connected && target > range.first,
          modifier = Modifier.size(56.dp),
        ) { Icon(painterResource(R.drawable.ic_remove), contentDescription = "Lower target") }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 120.dp)) {
          Text(
            formatTemperature(target, unit, celsius).orEmpty(),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            color = if (state.isThermostat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
          )
          Text("Target", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(
          onClick = { draft = SetPoint.snap(target + SetPoint.STEP, unit) },
          enabled = connected && target < range.last,
          modifier = Modifier.size(56.dp),
        ) { Icon(painterResource(R.drawable.ic_add), contentDescription = "Raise target") }
      }
    }
  }
}

private const val SET_POINT_DEBOUNCE_MS = 800L

@Composable
private fun LevelCard(label: String, level: Int, enabled: Boolean, onChange: (Int) -> Unit) {
  // Track the thumb locally while dragging; send only when released.
  var dragging by remember { mutableStateOf(false) }
  var draft by remember { mutableFloatStateOf(level.toFloat()) }
  val shown = if (dragging) draft else level.toFloat()
  // Slider snaps can land a hair under the step (5 → 4.9999995), so round rather than truncate.
  val shownLevel = shown.roundToInt()

  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
          if (shownLevel == 0) "Off" else "$shownLevel / 6",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Slider(
        value = shown,
        onValueChange = { dragging = true; draft = it },
        onValueChangeFinished = { dragging = false; onChange(draft.roundToInt()) },
        valueRange = 0f..6f,
        steps = 5,
        enabled = enabled,
      )
    }
  }
}

@Composable
private fun ToggleCard(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    SwitchRow(title, subtitle, checked, enabled, onChange, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
  }
}

/** A title/subtitle with a switch; the whole row toggles. */
@Composable
internal fun SwitchRow(
  title: String,
  subtitle: String?,
  checked: Boolean,
  enabled: Boolean,
  onChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
      .then(modifier),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Spacer(Modifier.width(12.dp))
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
  }
}
