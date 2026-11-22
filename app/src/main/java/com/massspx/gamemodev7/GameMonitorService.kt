package com.massspx.gamemodev7

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import java.util.*

class GameMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private val tick = object : Runnable {
        override fun run() {
            val chosen = getSharedPreferences("v7", MODE_PRIVATE).getStringSet("selected", emptySet()) ?: emptySet()
            val current = foregroundPackage()
            if (current in chosen && Settings.canDrawOverlays(this@GameMonitorService)) showBubble(current) else hideBubble()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val channel = "game_mode_v7"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(channel, "Game Mode V7", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channel).setContentTitle("Game Mode V7").setContentText("ရွေးထားသောဂိမ်းများကို စောင့်ကြည့်နေသည်").setSmallIcon(android.R.drawable.ic_media_play).build()
        else Notification.Builder(this).setContentTitle("Game Mode V7").setContentText("စောင့်ကြည့်နေသည်").setSmallIcon(android.R.drawable.ic_media_play).build()
        startForeground(7, notification)
        handler.post(tick)
    }

    private fun foregroundPackage(): String {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 15000, end)
        val event = UsageEvents.Event()
        var top = ""
        var latest = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.timeStamp >= latest) {
                latest = event.timeStamp
                top = event.packageName ?: ""
            }
        }
        return top
    }

    private fun showBubble(pkg: String) {
        val appName = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg,0)).toString() } catch (_: Exception) { "Game" }
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem / (1024*1024)
        val free = mi.availMem / (1024*1024)
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempText = if (temp > 0) String.format(Locale.US,"%.1f°C",temp/10f) else "N/A"
        val label = "🎮 " + appName + "  •  RAM " + free + "MB/" + total + "MB  •  " + tempText
        if (bubble == null) {
            val view = TextView(this).apply {
                setTextColor(Color.WHITE); textSize=11f; setPadding(13,10,13,10)
                setBackgroundColor(0xEE202630.toInt()); elevation=8f
                setOnTouchListener(object : View.OnTouchListener {
                    var sx=0f; var sy=0f; var ox=0; var oy=0
                    override fun onTouch(v: View, e: MotionEvent): Boolean {
                        val lp = v.layoutParams as WindowManager.LayoutParams
                        when(e.action) {
                            MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=lp.x; oy=lp.y; return true }
                            MotionEvent.ACTION_MOVE -> { lp.x=ox+(e.rawX-sx).toInt(); lp.y=oy+(e.rawY-sy).toInt(); wm.updateViewLayout(v,lp); return true }
                        }
                        return true
                    }
                })
            }
            val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
            lp.gravity = Gravity.TOP or Gravity.END
            lp.x=10; lp.y=110
            try { wm.addView(view,lp); bubble=view } catch (_: Exception) { bubble=null }
        }
        bubble?.text = label
    }

    private fun hideBubble() {
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { handler.removeCallbacks(tick); hideBubble(); super.onDestroy() }
    override fun onBind(intent: Intent?) = null
}