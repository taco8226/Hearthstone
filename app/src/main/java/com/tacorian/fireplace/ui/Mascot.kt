package com.tacorian.fireplace.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.tacorian.fireplace.R
import com.tacorian.fireplace.data.ConnectionStatus
import com.tacorian.fireplace.data.FireplaceState

/** Toasty, the flame spirit: changes expression with the fireplace's state and bobs while it burns. */
@Composable
internal fun MascotCard(state: FireplaceState, status: ConnectionStatus, remoteInControl: Boolean, celsius: Boolean) {
  val lit = state.isOn && status == ConnectionStatus.Connected
  val (greeting, detail) =
    if (remoteInControl && status == ConnectionStatus.Connected) "Who's driving?" to "The remote has taken over."
    else mascotLines(state, status, celsius)

  val glow by animateFloatAsState(if (lit) 1f else 0f, tween(600), label = "glow")
  val bob by rememberInfiniteTransition(label = "bob").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
    label = "bob",
  )

  Card(
    Modifier
      .fillMaxWidth()
      .clearAndSetSemantics { contentDescription = "Toasty says: $greeting $detail" },
    colors = CardDefaults.cardColors(
      containerColor = if (lit) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ),
  ) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Crossfade(mascotExpression(state, status, remoteInControl), label = "expression") { expression ->
        Image(
          painterResource(expression),
          contentDescription = null,
          modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
              translationY = -6.dp.toPx() * bob * glow
              val breathe = 1f + 0.03f * bob * glow
              scaleX = breathe
              scaleY = breathe
            },
        )
      }
      Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
        Text(greeting, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@DrawableRes
private fun mascotExpression(state: FireplaceState, status: ConnectionStatus, remoteInControl: Boolean): Int = when {
  status != ConnectionStatus.Connected || remoteInControl -> R.drawable.toasty_offline
  state.isThermostat -> R.drawable.toasty_thermostat
  !state.isOn -> R.drawable.toasty_sleeps
  state.flame >= 5 -> R.drawable.toasty_bright
  else -> R.drawable.toasty_main
}

private fun mascotLines(state: FireplaceState, status: ConnectionStatus, celsius: Boolean): Pair<String, String> {
  val room = formatTemperature(state.roomTemperature, state.temperatureUnit, celsius)
  return when {
    status != ConnectionStatus.Connected -> "Where'd the hearth go?" to "Looking for the fireplace…"
    state.isThermostat -> {
      val target = formatTemperature(state.temperatureSet, state.temperatureUnit, celsius)
      "On watch!" to listOfNotNull(target?.let { "Keeping it at $it" }, room?.let { "now $it" }).joinToString(", ")
    }
    state.isOn -> when {
      state.flame >= 5 -> "Roaring!"
      state.flame >= 3 -> "Nice and warm!"
      else -> "Cozy glow"
    } to listOfNotNull("Flame ${state.flame} of 6", room?.let { "room $it" }).joinToString(" · ")
    else -> "Zzz…" to (room?.let { "Room is $it. Tap On to wake me." } ?: "Tap On to wake me.")
  }
}
