package com.tacorian.fireplace.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Snapshot of the fireplace as reported by the Proflame WiFi module. Levels are 0–6. */
data class FireplaceState(
  val mainMode: Int = 0,
  val flame: Int = 0,
  val fan: Int = 0,
  val lamp: Int = 0,
  val splitFlow: Boolean = false,
  val aux: Boolean = false,
  val pilot: Boolean = false,
  val roomTemperature: Int? = null,
  val temperatureUnit: Int = 0,
  /** False until the module has sent its first state snapshot. */
  val synced: Boolean = false,
) {
  val isOn: Boolean get() = mainMode != 0
}

enum class ConnectionStatus { Disconnected, Connecting, Connected }

/**
 * Talks to the Proflame Connect WiFi module over its local WebSocket (ws://<ip>:88/).
 *
 * Protocol: send the text "PROFLAMECONNECTION" after connecting, keep alive with "PROFLAMEPING"
 * (answered by "PROFLAMEPONG"), and exchange JSON objects such as {"main_mode":1} or
 * {"flame_control":4}. The module pushes JSON with the changed fields whenever state changes.
 */
class ProflameClient(private val scope: CoroutineScope) {
  private val http = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()

  private val _state = MutableStateFlow(FireplaceState())
  val state: StateFlow<FireplaceState> = _state.asStateFlow()

  private val _status = MutableStateFlow(ConnectionStatus.Disconnected)
  val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

  @Volatile private var socket: WebSocket? = null
  private var loop: Job? = null

  /** Connects to [host] and keeps reconnecting until [stop] is called. */
  fun start(host: String) {
    stop()
    if (host.isBlank()) return
    loop = scope.launch {
      var sincePing = 0L
      while (isActive) {
        if (_status.value == ConnectionStatus.Disconnected) {
          open(host)
          sincePing = 0
        }
        delay(RETRY_DELAY_MS)
        sincePing += RETRY_DELAY_MS
        // Ping doubles as a liveness check; a dead socket fails and flips status to Disconnected.
        if (_status.value == ConnectionStatus.Connected && sincePing >= PING_INTERVAL_MS) {
          socket?.send(PING)
          sincePing = 0
        }
      }
    }
  }

  fun stop() {
    loop?.cancel()
    loop = null
    socket?.close(1000, null)
    socket = null
    _status.value = ConnectionStatus.Disconnected
  }

  fun setPower(on: Boolean) = send("main_mode", if (on) 1 else 0)
  fun setFlame(level: Int) = send("flame_control", level.coerceIn(0, 6))
  fun setFan(level: Int) = send("fan_control", level.coerceIn(0, 6))
  fun setLamp(level: Int) = send("lamp_control", level.coerceIn(0, 6))
  fun setSplitFlow(on: Boolean) = send("split_flow", if (on) 1 else 0)
  fun setAux(on: Boolean) = send("auxiliary_out", if (on) 1 else 0)
  fun setPilot(on: Boolean) = send("pilot_mode", if (on) 1 else 0)

  private fun send(key: String, value: Int) {
    val ws = socket ?: return
    if (ws.send(JSONObject().put(key, value).toString())) {
      // Optimistic update; the module echoes the authoritative value back.
      _state.update { it.withField(key, value) }
    }
  }

  private fun open(host: String) {
    _status.value = ConnectionStatus.Connecting
    val request = Request.Builder().url("ws://$host:$PORT/").build()
    socket = http.newWebSocket(request, object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        _status.value = ConnectionStatus.Connected
        webSocket.send(PING)
        webSocket.send(HANDSHAKE)
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        if (!text.startsWith("{")) return // PROFLAMEPONG, PROFLAMECONNECTIONOPEN
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        _state.update { current ->
          json.keys().asSequence().fold(current) { s, key -> s.withField(key, json.optInt(key)) }.copy(synced = true)
        }
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket)
      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dropped(webSocket)
    })
  }

  private fun dropped(webSocket: WebSocket) {
    if (socket === webSocket) {
      socket = null
      _status.value = ConnectionStatus.Disconnected
    }
  }

  companion object {
    const val PORT = 88
    internal const val PING = "PROFLAMEPING"
    internal const val HANDSHAKE = "PROFLAMECONNECTION"
    private const val PING_INTERVAL_MS = 30_000L
    private const val RETRY_DELAY_MS = 3_000L
  }
}

/** Applies one protocol field (e.g. "flame_control") to the state. Unknown keys are ignored. */
internal fun FireplaceState.withField(key: String, value: Int): FireplaceState = when (key) {
  "main_mode" -> copy(mainMode = value)
  "flame_control" -> copy(flame = value)
  "fan_control" -> copy(fan = value)
  "lamp_control" -> copy(lamp = value)
  "split_flow" -> copy(splitFlow = value != 0)
  "auxiliary_out" -> copy(aux = value != 0)
  "pilot_mode" -> copy(pilot = value != 0)
  "room_temperature" -> copy(roomTemperature = value)
  "temperature_unit" -> copy(temperatureUnit = value)
  else -> this
}
