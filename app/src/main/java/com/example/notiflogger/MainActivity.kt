package com.example.notiflogger

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
        if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
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
