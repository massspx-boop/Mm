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
import java.util.Locale

class GameMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var panel: TextView? = null
    private var currentPackage = ""
    private var lastEventQuery = 0L
    private var expanded = true
    private var cachedPackage = ""
    private var cachedAppName = "Game"

    // Polling ကို ၂.၅ စက္ကန့်ခြားထားပြီး မလိုအပ်သော အလုပ်လုပ်မှုကို လျှော့ထားသည်။
    private val monitorTask = object : Runnable {
        override fun run() {
            try {
                updateForegroundState()
                val chosen = getSharedPreferences("v7", MODE_PRIVATE)
                    .getStringSet("selected", emptySet()) ?: emptySet()
                if (currentPackage.isNotBlank() && chosen.contains(currentPackage) &&
                    Settings.canDrawOverlays(this@GameMonitorService)) {
                    showPanel(currentPackage)
                } else {
                    hidePanel()
                }
            } catch (_: Exception) {
                hidePanel()
            }
            handler.postDelayed(this, 2500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotification()
        lastEventQuery = System.currentTimeMillis() - 60000L
        handler.post(monitorTask)
    }

    private fun createNotification() {
        val channelId = "game_mode_v7"
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Game Mode V7", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }.setContentTitle("Game Mode V7")
            .setContentText("ရွေးထားသော Game များကို အလိုအလျောက် စောင့်ကြည့်နေသည်")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(7, notification)
    }

    // နောက်ဆုံး foreground/background event ကို မှတ်ထား၍ event window ကုန်သွားလို့
    // လက်ရှိဂိမ်းအမည် ပျောက်မသွားစေရန် ထိန်းထားသည်။
    private fun updateForegroundState() {
        val now = System.currentTimeMillis()
        val from = (lastEventQuery - 1000L).coerceAtLeast(now - 120000L)
        val manager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(from, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> currentPackage = pkg
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (currentPackage == pkg) currentPackage = ""
                // Android 10+ activity event များ
                UsageEvents.Event.ACTIVITY_RESUMED -> currentPackage = pkg
                UsageEvents.Event.ACTIVITY_STOPPED -> if (currentPackage == pkg) currentPackage = ""
            }
        }
        lastEventQuery = now
    }

    private fun showPanel(pkg: String) {
        val name = if (cachedPackage == pkg) cachedAppName else {
            cachedPackage = pkg
            cachedAppName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { "Game" }
            cachedAppName
        }

        val memory = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        val totalMb = (memory.totalMem / 1048576L).coerceAtLeast(1L)
        val availableMb = memory.availMem / 1048576L
        val ramPercent = (availableMb * 100L / totalMb).toInt()

        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryTempRaw = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val batteryTemp = if (batteryTempRaw > 0) batteryTempRaw / 10f else -1f

        val risk = when {
            availableMb < 300L || ramPercent < 10 || batteryTemp >= 43f -> "မြင့်"
            availableMb < 500L || ramPercent < 18 || batteryTemp >= 40f -> "သတိထား"
            else -> "ပုံမှန်"
        }
        val advice = when (risk) {
            "မြင့်" -> "နောက်ခံ App ပိတ်ပါ • ခဏနား/အေးအောင်ထားပါ"
            "သတိထား" -> "နောက်ခံ App လျှော့ပါ • အပူချိန်စောင့်ကြည့်ပါ"
            else -> "လက်ရှိဖတ်ရှုချက်အရ အခြေအနေကောင်း"
        }

        val detail = if (expanded) {
            "\nRAM လွတ်: ${availableMb} / ${totalMb} MB (${ramPercent}%)" +
                "\nBattery အပူချိန်: " + (if (batteryTemp >= 0f) String.format(Locale.US, "%.1f°C", batteryTemp) else "မရနိုင်") +
                "\nLag အန္တရာယ်: $risk" +
                "\nအကြံပြုချက်: $advice" +
                "\nCPU / FPS: မရနိုင် (Root မရှိ)"
        } else {
            "\nRAM ${availableMb}MB • Battery ${if (batteryTemp >= 0f) String.format(Locale.US, "%.1f°C", batteryTemp) else "N/A"} • Lag $risk"
        }
        val text = "🎮 $name  •  V7" + detail

        if (panel == null) {
            val view = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 11f
                setPadding(13, 10, 13, 10)
                setBackgroundColor(0xEE202630.toInt())
                elevation = 8f
                maxWidth = (resources.displayMetrics.widthPixels * 0.88f).toInt()
                setOnTouchListener(object : View.OnTouchListener {
                    private var downX = 0f
                    private var downY = 0f
                    private var startX = 0
                    private var startY = 0
                    private var dragged = false
                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        val params = v.layoutParams as WindowManager.LayoutParams
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                downX = event.rawX
                                downY = event.rawY
                                startX = params.x
                                startY = params.y
                                dragged = false
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = (event.rawX - downX).toInt()
                                val dy = (event.rawY - downY).toInt()
                                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 8) dragged = true
                                params.x = startX - dx
                                params.y = startY + dy
                                try { windowManager.updateViewLayout(v, params) } catch (_: Exception) {}
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                if (!dragged) expanded = !expanded
                                return true
                            }
                        }
                        return true
                    }
                })
            }
            val type = if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.END
            params.x = 12
            params.y = 120
            try {
                windowManager.addView(view, params)
                panel = view
            } catch (_: Exception) {
                panel = null
            }
        }
        panel?.text = text
    }

    private fun hidePanel() {
        panel?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        panel = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(monitorTask)
        hidePanel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}