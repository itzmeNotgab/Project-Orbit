package app.orbit.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import app.orbit.core.EngineState
import app.orbit.network.RelayConfig
import app.orbit.network.RelayStats
import app.orbit.network.UdpRelay

/** Foreground service so Android keeps the relay alive while Minecraft is in front. */
class RelayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            RelayController.relay?.let {
                val st = it.snapshot()
                RelayController.stats.value = st
                RelayController.core.bus.post(app.orbit.core.NetworkSample(
                    System.currentTimeMillis(), st.sessions, st.clientToServerPackets, st.serverToClientPackets,
                    st.clientToServerBytes, st.serverToClientBytes))
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopRelay(); stopSelf(); return START_NOT_STICKY }
        val host = intent?.getStringExtra(EXTRA_HOST)
        val port = intent?.getIntExtra(EXTRA_PORT, 19132) ?: 19132
        if (host.isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
        goForeground()
        RelayController.error.value = null
        RelayController.core.setEngineState(EngineState.Starting)
        Thread {
            try {
                val r = UdpRelay(RelayConfig(host, port, RelayController.LISTEN_PORT))
                r.start()
                RelayController.relay = r
                RelayController.core.setEngineState(EngineState.Running)
                handler.post(poll)
            } catch (e: Exception) {
                RelayController.error.value = e.message ?: e.javaClass.simpleName
                RelayController.core.setEngineState(EngineState.Failed)
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "ORBIT relay", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("ORBIT relay running")
            .setContentText("127.0.0.1:${RelayController.LISTEN_PORT}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun stopRelay() {
        handler.removeCallbacks(poll)
        RelayController.relay?.close()
        RelayController.relay = null
        RelayController.stats.value = RelayStats()
        if (RelayController.core.engine.value != EngineState.Failed)
            RelayController.core.setEngineState(EngineState.Stopped)
    }

    override fun onDestroy() { stopRelay(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "app.orbit.client.STOP"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        private const val CHANNEL = "orbit_relay"

        fun start(ctx: Context, host: String, port: Int) =
            ctx.startForegroundService(Intent(ctx, RelayService::class.java).putExtra(EXTRA_HOST, host).putExtra(EXTRA_PORT, port))

        fun stop(ctx: Context) =
            ctx.startService(Intent(ctx, RelayService::class.java).setAction(ACTION_STOP))
    }
}
