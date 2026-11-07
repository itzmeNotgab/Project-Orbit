package app.orbit.client

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Draws the enabled modules' HUD lines in a system overlay window above other apps (including Minecraft).
 * This is a separate Android window, NOT Minecraft's own renderer. Needs the "draw over other apps" permission.
 */
class HudOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private val tick = object : Runnable { override fun run() { refresh(); handler.postDelayed(this, 1000) } }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("orbit_hud", "ORBIT HUD", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "orbit_hud").setContentTitle("ORBIT HUD overlay on")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(2, n)
        if (view == null) {
            val tv = TextView(this).apply { setShadowLayer(4f, 0f, 0f, Color.BLACK); setPadding(12, 8, 12, 8) }
            val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
            getSystemService(WindowManager::class.java).addView(tv, lp)
            view = tv; running = true
            handler.post(tick)
        }
        return START_NOT_STICKY
    }

    private fun refresh() {
        val tv = view ?: return
        val core = RelayController.core; val h = core.hud
        val lines = core.modules.all.filter { it.enabled }.flatMap { it.hudLines() }
        tv.visibility = if (!h.visible || lines.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        tv.text = lines.joinToString("\n"); tv.textSize = 14f * h.scale; tv.alpha = h.opacity
        tv.setTextColor(Color.parseColor(h.color))
        val dm = resources.displayMetrics
        val lp = tv.layoutParams as WindowManager.LayoutParams
        lp.x = (h.x * dm.widthPixels).toInt(); lp.y = (h.y * dm.heightPixels).toInt()
        runCatching { getSystemService(WindowManager::class.java).updateViewLayout(tv, lp) }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        view?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        view = null; running = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "app.orbit.client.HUD_STOP"
        @Volatile var running = false
        fun start(c: Context) = c.startForegroundService(Intent(c, HudOverlayService::class.java))
        fun stop(c: Context) { c.startService(Intent(c, HudOverlayService::class.java).setAction(ACTION_STOP)) }
    }
}
