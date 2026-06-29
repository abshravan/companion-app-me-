package com.relayme.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Persistent foreground service that hosts the Bluetooth server for the whole
 * app lifetime. A foreground service is required so Android does not kill the
 * connection while the screen is off — the companion link must stay alive like
 * a smartwatch link.
 *
 * The service owns the [RelayLink]; the UI only sends START/STOP intents and
 * observes [RelayLinkState].
 */
class RelayService : Service() {

    private var link: RelayLink? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopLink(); stopSelf(); return START_NOT_STICKY }
            else -> startLink()
        }
        return START_STICKY
    }

    private fun startLink() {
        if (link != null) return
        startForeground(NOTIFICATION_ID, buildNotification())

        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            RelayLinkState.update(RelayLinkState.Phase.STOPPED, "Bluetooth is off")
            stopSelf()
            return
        }

        link = RelayLink(adapter, deviceName = Build.MODEL ?: "Android").also { it.start() }
    }

    private fun stopLink() {
        link?.stop()
        link = null
    }

    override fun onDestroy() {
        stopLink()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_link_status),
                NotificationManager.IMPORTANCE_LOW,
            )
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_running))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "relayme_link"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.relayme.app.action.START"
        const val ACTION_STOP = "com.relayme.app.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RelayService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RelayService::class.java).setAction(ACTION_STOP))
        }
    }
}
