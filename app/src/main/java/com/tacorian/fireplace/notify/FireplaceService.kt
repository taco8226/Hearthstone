package com.tacorian.fireplace.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tacorian.fireplace.MainActivity
import com.tacorian.fireplace.R
import com.tacorian.fireplace.data.ConnectionStatus
import com.tacorian.fireplace.data.FireplaceHost
import com.tacorian.fireplace.data.FireplaceState
import com.tacorian.fireplace.data.ProflameClient
import com.tacorian.fireplace.ui.formatTemperature
import com.tacorian.fireplace.widget.WidgetSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Foreground service that runs while the fireplace is on: keeps a live connection,
 * shows an ongoing notification with Off / Flame − / Flame + actions, and stops itself
 * once the fireplace is turned off.
 */
class FireplaceService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val client = ProflameClient(scope)
  private var started = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!started) {
      started = true
      ensureChannel(this)
      ServiceCompat.startForeground(
        this, NOTIFICATION_ID, buildNotification(FireplaceState(), ConnectionStatus.Connecting),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
      )
      client.start(FireplaceHost.get(this))
      observe()
    }

    when (intent?.action) {
      ACTION_OFF -> client.setPower(false)
      ACTION_FLAME_DOWN -> client.state.value.let { if (it.flame > 0) client.setFlame(it.flame - 1) }
      ACTION_FLAME_UP -> client.state.value.let { if (it.flame < 6) client.setFlame(it.flame + 1) }
    }
    return START_NOT_STICKY
  }

  private fun observe() {
    val manager = getSystemService(NotificationManager::class.java)
    scope.launch {
      combine(client.state, client.status) { s, c -> s to c }.distinctUntilChanged().collect { (state, status) ->
        if (state.synced && status == ConnectionStatus.Connected && !state.isOn) {
          stopSelf()
          return@collect
        }
        manager.notify(NOTIFICATION_ID, buildNotification(state, status))
      }
    }
    // Keep the home screen widget in step with what the notification sees.
    scope.launch {
      @OptIn(kotlinx.coroutines.FlowPreview::class)
      client.state.debounce(500).collect { if (it.synced) WidgetSync.push(applicationContext, it) }
    }
  }

  private fun buildNotification(state: FireplaceState, status: ConnectionStatus): Notification {
    val celsius = getSharedPreferences("fireplace", Context.MODE_PRIVATE).getBoolean("celsius", false)
    val title = when {
      status != ConnectionStatus.Connected -> "Fireplace · reconnecting…"
      else -> "Fireplace is on"
    }
    val text = buildList {
      add("Flame ${state.flame}/6")
      if (state.fan > 0) add("Fan ${state.fan}/6")
      formatTemperature(state.roomTemperature, state.temperatureUnit, celsius)?.let { add("Room $it") }
    }.joinToString(" · ")

    val open = PendingIntent.getActivity(
      this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_flame)
      .setContentTitle(title)
      .setContentText(if (state.synced) text else "Connecting…")
      .setContentIntent(open)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .addAction(0, "Turn off", action(ACTION_OFF, 1))
      .addAction(0, "Flame −", action(ACTION_FLAME_DOWN, 2))
      .addAction(0, "Flame +", action(ACTION_FLAME_UP, 3))
      .build()
  }

  private fun action(name: String, requestCode: Int) = PendingIntent.getService(
    this, requestCode, Intent(this, FireplaceService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE,
  )

  override fun onDestroy() {
    client.stop()
    scope.cancel()
  }

  companion object {
    private const val CHANNEL_ID = "fireplace_active"
    private const val NOTIFICATION_ID = 1
    private const val ACTION_OFF = "com.tacorian.fireplace.OFF"
    private const val ACTION_FLAME_DOWN = "com.tacorian.fireplace.FLAME_DOWN"
    private const val ACTION_FLAME_UP = "com.tacorian.fireplace.FLAME_UP"

    /** Shows the notification if it isn't already up. Safe to call repeatedly. */
    fun ensureRunning(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
      ) return
      // Starting from the background can be refused by the OS; the app/widget will retry next time.
      runCatching { ContextCompat.startForegroundService(context, Intent(context, FireplaceService::class.java)) }
    }

    private fun ensureChannel(context: Context) {
      val manager = context.getSystemService(NotificationManager::class.java)
      if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(
          NotificationChannel(CHANNEL_ID, "Fireplace on", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while the fireplace is lit, with quick controls"
          },
        )
      }
    }
  }
}
