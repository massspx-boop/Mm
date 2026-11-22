package com.massspx.gamemodev7

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("v7", Context.MODE_PRIVATE) }
    private val selected = mutableSetOf<String>()
    private lateinit var appList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(prefs.getStringSet("selected", emptySet()) ?: emptySet())

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 22, 20, 18)
            setBackgroundColor(Color.rgb(15, 18, 25))
        }
        root.addView(TextView(this).apply {
            text = "GAME MODE V7"
            textSize = 25f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Lag Watch • Auto Detect • Low Overhead"
            textSize = 13f
            setTextColor(0xFF9EACC0.toInt())
            setPadding(0, 4, 0, 14)
        })
        root.addView(TextView(this).apply {
            text = "အသုံးပြုပုံ\n၁။ ဂိမ်းကိုရွေးပါ။\n၂။ Overlay နှင့် Usage Access ခွင့်ပြုပါ။\n၃။ V7 စတင်ပြီးနောက် ရွေးထားတဲ့ဂိမ်းကိုဖွင့်ပါ။"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 4, 0, 12)
        })
        root.addView(Button(this).apply {
            text = "ခွင့်ပြုချက်များ စစ်ဆေး / ဖွင့်ရန်"
            setOnClickListener { openNextPermission() }
        })
        root.addView(TextView(this).apply {
            text = "ဖုန်းထဲရှိ ဖွင့်လို့ရသော App များ"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 14, 0, 6)
        })
        val scroll = ScrollView(this)
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(appList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "V7 စတင်မည်"
            setOnClickListener { startMonitor() }
        })
        root.addView(Button(this).apply {
            text = "စောင့်ကြည့်မှု ရပ်မည်"
            setOnClickListener {
                stopService(Intent(this@MainActivity, GameMonitorService::class.java))
                Toast.makeText(this@MainActivity, "V7 ရပ်လိုက်ပါပြီ", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(TextView(this).apply {
            text = "မှတ်ချက် - V7 သည် RAM/ဘက်ထရီအပူချိန်ကို စောင့်ကြည့်ပြီး Lag အန္တရာယ်ကို ခန့်မှန်းပေးသည်။ CPU/GPU Boost သို့မဟုတ် FPS တိုင်းတာမှုကို Root မရှိဘဲ အာမမခံပါ။"
            textSize = 12f
            setTextColor(0xFF9EACC0.toInt())
            setPadding(0, 12, 0, 0)
        })
        setContentView(root)
        loadApps()
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return try {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) { false }
    }

    private fun openNextPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            return
        }
        if (!hasUsageAccess()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        Toast.makeText(this, "လိုအပ်သော ခွင့်ပြုချက် ၂ ခု ရရှိပြီးပါပြီ", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (::appList.isInitialized) {
            // Permission screen မှ ပြန်လာလျှင် လက်ရှိအခြေအနေကို ပြန်စစ်နိုင်သည်။
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        appList.removeAllViews()
        for (info in apps) {
            val pkg = info.activityInfo.packageName
            appList.addView(CheckBox(this).apply {
                text = info.loadLabel(pm).toString()
                textSize = 14f
                setTextColor(Color.WHITE)
                isChecked = selected.contains(pkg)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(pkg) else selected.remove(pkg)
                }
            })
        }
        if (apps.isEmpty()) appList.addView(TextView(this).apply {
            text = "ဖွင့်လို့ရသော App မတွေ့ပါ"
            setTextColor(Color.WHITE)
        })
    }

    private fun startMonitor() {
        prefs.edit().putStringSet("selected", selected.toSet()).apply()
        if (selected.isEmpty()) {
            Toast.makeText(this, "စောင့်ကြည့်မည့် Game/App တစ်ခု ရွေးပါ", Toast.LENGTH_LONG).show()
            return
        }
        if (!Settings.canDrawOverlays(this) || !hasUsageAccess()) {
            openNextPermission()
            return
        }
        val intent = Intent(this, GameMonitorService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            Toast.makeText(this, "V7 စတင်ပါပြီ။ ယခု ရွေးထားသောဂိမ်းကို ဖွင့်နိုင်ပါပြီ။", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "စတင်မရပါ: ${e.message ?: "အမှား"}", Toast.LENGTH_LONG).show()
        }
    }
}