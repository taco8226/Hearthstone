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
  /** Comes from the handheld remote's thermometer, relayed by the module. Whole degrees, in tenths. */
  val roomTemperature: Int? = null,
  /**
   * When the module last pushed a room reading (epoch ms). The remote only reports now and then,
   * and the connect snapshot carries no age, so this stays null until a fresh reading arrives.
   */
  val roomUpdatedAt: Long? = null,
  val temperatureUnit: Int = 0,
  /** Thermostat set point, in tenths of a degree in the module's unit. 0 when never set. */
  val temperatureSet: Int = 0,
  /** Undocumented; reads [BURNER_OUT] whenever the burner is out, other values while it lights or burns. */
  val shMemory1: Int? = null,
  /** False until the module has sent its first state snapshot. */
  val synced: Boolean = false,
) {
  val isOn: Boolean get() = mainMode != MODE_OFF
  val isThermostat: Boolean get() = mainMode == MODE_THERMOSTAT
  /** Null when the module hasn't reported burner state. Unfamiliar values count as burning. */
  val burnerLit: Boolean? get() = shMemory1?.let { it != BURNER_OUT }
}

/** sh_memory_1 while the burner is out; seen when off and while the thermostat idles. */
const val BURNER_OUT = 32672

const val MODE_OFF = 0
const val MODE_MANUAL = 1
/** The module lights and idles the fire itself to hold [FireplaceState.temperatureSet]. */
const val MODE_THERMOSTAT = 2

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

  private val _remoteInControl = MutableStateFlow(false)
  /**
   * True while the handheld remote keeps re-sending its full state. It does that after it turns the
   * fireplace on, about twice a second, until it turns it off again, and undoes every app command.
   */
  val remoteInControl: StateFlow<Boolean> = _remoteInControl.asStateFlow()

  /** Arrival times of full-state messages after the connect snapshot, within [REPEAT_WINDOW_MS]. */
  private val repeats = ArrayDeque<Long>()

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
        noteRepeat(null)
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
    synchronized(repeats) { repeats.clear() }
    _remoteInControl.value = false
    loop?.cancel()
    loop = null
    socket?.close(1000, null)
    socket = null
    _status.value = ConnectionStatus.Disconnected
  }

  fun setPower(on: Boolean) = send("main_mode", if (on) MODE_MANUAL else MODE_OFF)

  /** Thermostat on hands control to the module; off turns the fireplace off rather than leaving it burning. */
  fun setThermostat(on: Boolean, setPoint: Int? = null) {
    if (on && setPoint != null) send("temperature_set", setPoint)
    send("main_mode", if (on) MODE_THERMOSTAT else MODE_OFF)
  }

  /** [tenths] is in the module's own unit, like room_temperature. */
  fun setTargetTemperature(tenths: Int) = send("temperature_set", tenths)
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
      /** The first JSON message is the full snapshot; later ones are pushed changes. */
      private var snapshotSeen = false

      override fun onOpen(webSocket: WebSocket, response: Response) {
        _status.value = ConnectionStatus.Connected
        webSocket.send(PING)
        webSocket.send(HANDSHAKE)
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        if (!text.startsWith("{")) return // PROFLAMEPONG, PROFLAMECONNECTIONOPEN
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        val isSnapshot = !snapshotSeen
        snapshotSeen = true
        if (!isSnapshot && json.length() >= FULL_STATE_KEYS) noteRepeat(System.currentTimeMillis())
        _state.update { current ->
          val next = json.keys().asSequence().fold(current) { s, key -> s.withField(key, json.optInt(key)) }
          val roomUpdatedAt = when {
            !json.has("room_temperature") -> current.roomUpdatedAt
            !isSnapshot -> System.currentTimeMillis()
            // A snapshot after a reconnect: the old time still holds only if the reading didn't change meanwhile.
            next.roomTemperature == current.roomTemperature -> current.roomUpdatedAt
            else -> null
          }
          next.copy(synced = true, roomUpdatedAt = roomUpdatedAt)
        }
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket)
      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = dropped(webSocket)
    })
  }

  /** Records a repeated full-state message at [at] (null just expires old ones) and updates [remoteInControl]. */
  private fun noteRepeat(at: Long?) = synchronized(repeats) {
    val now = System.currentTimeMillis()
    at?.let(repeats::addLast)
    while (repeats.isNotEmpty() && now - repeats.first() > REPEAT_WINDOW_MS) repeats.removeFirst()
    _remoteInControl.value = repeats.size >= REPEATS_FOR_REMOTE
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
    // Another client connecting triggers one full-state message; the remote sends ~10 per window.
    private const val FULL_STATE_KEYS = 8
    private const val REPEAT_WINDOW_MS = 5_000L
    private const val REPEATS_FOR_REMOTE = 3
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
  "temperature_set" -> copy(temperatureSet = value)
  "sh_memory_1" -> copy(shMemory1 = value)
  else -> this
}
