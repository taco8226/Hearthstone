package com.tacorian.fireplace.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tacorian.fireplace.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  host: String,
  settings: DisplaySettings,
  onChange: ((DisplaySettings) -> DisplaySettings) -> Unit,
  onDone: (String) -> Unit,
) {
  var hostText by rememberSaveable { mutableStateOf(host) }
  BackHandler { onDone(hostText) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = { onDone(hostText) }) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
          }
        },
      )
    },
  ) { padding ->
    Column(
      Modifier
        .fillMaxSize()
        .padding(padding)
        .consumeWindowInsets(padding)
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp),
    ) {
      SectionTitle("Theme")
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { i, mode ->
          SegmentedButton(
            selected = settings.theme == mode,
            onClick = { onChange { it.copy(theme = mode) } },
            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
          ) { Text(mode.label) }
        }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchRow(
          title = "Wallpaper colors",
          subtitle = "Use Material You colors instead of Ember",
          checked = settings.dynamicColor,
          enabled = true,
          onChange = { v -> onChange { it.copy(dynamicColor = v) } },
          modifier = Modifier.padding(vertical = 12.dp),
        )
      }

      SectionTitle("Temperature")
      SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
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

      SectionTitle("Tiles")
      Text(
        "Drag the handle to reorder. Uncheck to hide.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TileList(settings, onChange)

      SectionTitle("Connection")
      OutlinedTextField(
        value = hostText,
        onValueChange = { hostText = it },
        label = { Text("Fireplace IP address") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
  )
}

/** Tiles in their main-screen order, reordered by dragging a row's handle. */
@Composable
private fun TileList(settings: DisplaySettings, onChange: ((DisplaySettings) -> DisplaySettings) -> Unit) {
  val rowHeight = 56.dp
  val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
  var dragged by remember { mutableStateOf<Tile?>(null) }
  var offset by remember { mutableFloatStateOf(0f) }
  val order by rememberUpdatedState(settings.order)

  // Moves by one step relative to the latest order, so fast drags can't act on a stale index.
  fun move(tile: Tile, by: Int) = onChange { s ->
    val from = s.order.indexOf(tile)
    s.moved(from, (from + by).coerceIn(0, s.order.lastIndex))
  }

  Column(Modifier.padding(vertical = 4.dp)) {
    settings.order.forEachIndexed { index, tile ->
      key(tile) {
        val isDragged = dragged == tile
        val elevation by animateDpAsState(if (isDragged) 6.dp else 0.dp, label = "lift")
        Surface(
          color = if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
          shadowElevation = elevation,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer { translationY = if (isDragged) offset else 0f }
            .semantics {
              customActions = buildList {
                if (index > 0) add(CustomAccessibilityAction("Move up") { move(tile, -1); true })
                if (index < settings.order.lastIndex) add(CustomAccessibilityAction("Move down") { move(tile, 1); true })
              }
            },
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            val visible = settings.isVisible(tile)
            // Power and Flame can't be hidden.
            Checkbox(
              checked = visible,
              onCheckedChange = { v -> onChange { it.withVisible(tile, v) } },
              enabled = tile.key != null,
            )
            Text(
              tile.label,
              style = MaterialTheme.typography.bodyLarge,
              color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            Icon(
              painterResource(R.drawable.ic_drag_indicator),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .pointerInput(tile) {
                  detectDragGestures(
                    onDragStart = { dragged = tile; offset = 0f },
                    onDragEnd = { dragged = null; offset = 0f },
                    onDragCancel = { dragged = null; offset = 0f },
                  ) { change, amount ->
                    change.consume()
                    offset += amount.y
                    val from = order.indexOf(tile)
                    if (offset > rowPx / 2 && from < order.lastIndex) {
                      offset -= rowPx
                      move(tile, 1)
                    } else if (offset < -rowPx / 2 && from > 0) {
                      offset += rowPx
                      move(tile, -1)
                    }
                  }
                }
                .padding(16.dp),
            )
          }
        }
      }
    }
  }
}
