package com.massspx.gamemodev7

import android.app.*
import android.os.*
import android.content.*
import android.graphics.Color
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.view.*
import android.widget.*
import android.content.pm.PackageManager

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("v7", MODE_PRIVATE) }
    private val selected = mutableSetOf<String>()
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(prefs.getStringSet("selected", emptySet()) ?: emptySet())
        val root = LinearLayout(this).apply { orientation = 1; setPadding(22, 24, 22, 18); setBackgroundColor(Color.rgb(17,19,25)) }
        root.addView(TextView(this).apply { text = "GAME MODE V7"; textSize = 25f; setTextColor(Color.WHITE); setPadding(0,0,0,8) })
        root.addView(TextView(this).apply { text = "realme C30 • Android 11 • Root မလို"; textSize = 13f; setTextColor(0xFFB7C0D0.toInt()) })
        root.addView(TextView(this).apply { text = "၁။ အောက်ကစာရင်းမှ ဂိမ်းကိုရွေးပါ။\n၂။ Overlay နဲ့ Usage Access ခွင့်ပြုပါ။\n၃။ စောင့်ကြည့်မှုစတင်ပါ။"; setTextColor(Color.WHITE); textSize=14f; setPadding(0,18,0,14) })
        root.addView(Button(this).apply { text = "ခွင့်ပြုချက်များ ဖွင့်ရန်"; setOnClickListener { requestPermissionsFlow() } })
        root.addView(TextView(this).apply { text = "ဖုန်းထဲက ဖွင့်လို့ရတဲ့ App / Game များ"; setTextColor(Color.WHITE); textSize=17f; setPadding(0,18,0,8) })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = 1 }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(Button(this).apply { text = "V7 စတင်မည်"; setOnClickListener { saveAndStart() } })
        root.addView(Button(this).apply { text = "ရပ်မည်"; setOnClickListener { stopService(Intent(this@MainActivity, GameMonitorService::class.java)); Toast.makeText(this@MainActivity,"စောင့်ကြည့်မှု ရပ်လိုက်ပြီ",Toast.LENGTH_SHORT).show() } })
        setContentView(root)
        loadApps()
    }

    private fun requestPermissionsFlow() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            return
        }
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val granted = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now-86400000, now)?.isNotEmpty() == true
        if (!granted) startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        else Toast.makeText(this,"လိုအပ်တဲ့ခွင့်ပြုချက် ရရှိပြီးပါပြီ",Toast.LENGTH_SHORT).show()
    }

    private fun loadApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        list.removeAllViews()
        for (info in apps) {
            val pkg = info.activityInfo.packageName
            val cb = CheckBox(this).apply {
                text = info.loadLabel(pm).toString()
                setTextColor(Color.WHITE)
                isChecked = selected.contains(pkg)
                setOnCheckedChangeListener { _, checked -> if (checked) selected.add(pkg) else selected.remove(pkg) }
            }
            list.addView(cb)
        }
        if (apps.isEmpty()) list.addView(TextView(this).apply { text="App စာရင်း မရရှိပါ"; setTextColor(Color.WHITE) })
    }

    private fun saveAndStart() {
        prefs.edit().putStringSet("selected", selected.toSet()).apply()
        if (selected.isEmpty()) { Toast.makeText(this,"ဂိမ်းတစ်ခု အနည်းဆုံးရွေးပါ",Toast.LENGTH_LONG).show(); return }
        if (!Settings.canDrawOverlays(this)) { requestPermissionsFlow(); return }
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        if (usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now-86400000, now)?.isEmpty() != false) {
            requestPermissionsFlow(); return
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, GameMonitorService::class.java))
        else startService(Intent(this, GameMonitorService::class.java))
        Toast.makeText(this,"V7 စောင့်ကြည့်မှု စတင်ပါပြီ",Toast.LENGTH_LONG).show()
    }
}