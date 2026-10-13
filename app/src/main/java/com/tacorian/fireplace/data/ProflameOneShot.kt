package com.tacorian.fireplace.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A single field change, e.g. Command("flame_control", 4). */
data class Command(val key: String, val value: Int)

/**
 * Short-lived connection for the home screen widget: connect, read the current state,
 * optionally send one command computed from that fresh state, wait for the echo, disconnect.
 */
object ProflameOneShot {
  private val http = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).build()

  /**
   * @param command builds the command from the freshly read state, or returns null to only read.
   * @throws IOException if the module can't be reached or doesn't answer.
   */
  suspend fun exchange(host: String, command: ((FireplaceState) -> Command?)? = null): FireplaceState {
    if (host.isBlank()) throw IOException("No fireplace address set")
    val inbox = Channel<String>(Channel.UNLIMITED)
    val request = Request.Builder().url("ws://$host:${ProflameClient.PORT}/").build()
    val ws = http.newWebSocket(request, object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        webSocket.send(ProflameClient.PING)
        webSocket.send(ProflameClient.HANDSHAKE)
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        inbox.trySend(text)
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        inbox.close(IOException("Fireplace unreachable", t))
      }
    })

    try {
      var state = FireplaceState()
      fun absorb(text: String) {
        if (!text.startsWith("{")) return
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        json.keys().forEach { key -> state = state.withField(key, json.optInt(key)) }
        state = state.copy(synced = true)
      }

      // Wait for the first state snapshot, then let any trailing fragments arrive.
      withTimeout(SNAPSHOT_TIMEOUT_MS) {
        while (true) {
          val text = inbox.receive()
          absorb(text)
          if (text.startsWith("{")) break
        }
      }
      drain(inbox, ::absorb)

      val cmd = command?.invoke(state) ?: return state
      ws.send(JSONObject().put(cmd.key, cmd.value).toString())
      state = state.withField(cmd.key, cmd.value)
      drain(inbox, ::absorb)
      return state
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
      throw IOException("Fireplace didn't respond", e)
    } catch (e: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
      throw IOException("Fireplace closed the connection", e)
    } finally {
      ws.close(1000, null)
    }
  }

  /** Reads messages until the line has been quiet for [QUIET_MS]. */
  private suspend fun drain(inbox: Channel<String>, absorb: (String) -> Unit) {
    while (true) {
      val text = withTimeoutOrNull(QUIET_MS) { inbox.receiveCatching().getOrNull() } ?: return
      absorb(text)
    }
  }

  private const val SNAPSHOT_TIMEOUT_MS = 5_000L
  private const val QUIET_MS = 600L
}
