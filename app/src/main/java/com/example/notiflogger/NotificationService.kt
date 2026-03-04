override fun onListenerConnected() {
    super.onListenerConnected()
    
    try {
        // Wrap everything in try-catch to prevent crashes
        captureAllExistingNotifications()
    } catch (e: Exception) {
        // Silent fail - don't crash the app
        android.util.Log.d("NotificationService", "Listener connected but capture failed: ${e.message}")
    }
}
package com.example.notiflogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.*

class NotificationService : NotificationListenerService() {
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate() {
    super.onCreate()
    dbHelper = DatabaseHelper(this)
    
    // Start as foreground service
    startForegroundService()
    
    // Start keep alive services
    startKeepAliveServices()
    
    // ADD THIS DELAY - gives system time to settle
    android.os.Handler(mainLooper).postDelayed({
        // This will run 2 seconds after service starts
        if (checkNotificationPermission()) {
            captureAllExistingNotifications()
        }
    }, 2000) // 2 second delay
}

// ADD THIS HELPER METHOD
private fun checkNotificationPermission(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    return true
}

    override fun onListenerConnected() {
        super.onListenerConnected()
        // This is called when service connects to system
        // PERFECT TIME to grab all existing notifications!
        captureAllExistingNotifications()
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "notification_service",
                "Notification Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Main notification logging service"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            
            val notification = Notification.Builder(this, "notification_service")
                .setContentTitle("Notify Log Active")
                .setContentText("Monitoring notifications")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
            
            startForeground(1001, notification)
        }
    }

    private fun startKeepAliveServices() {
        // Start keep alive service
        val keepAliveIntent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(keepAliveIntent)
        } else {
            startService(keepAliveIntent)
        }
        
        // Start heartbeat service
        val heartbeatIntent = Intent(this, HeartbeatService::class.java)
        startService(heartbeatIntent)
    }

    // ========== NEW METHOD 1: CAPTURE ALL EXISTING NOTIFICATIONS ==========
    private fun captureAllExistingNotifications() {
        try {
            // Get ALL notifications currently in the status bar/drawer
            val activeNotifications = getActiveNotifications()
            
            if (activeNotifications.isNotEmpty()) {
                var savedCount = 0
                
                for (sbn in activeNotifications) {
                    val packageName = sbn.packageName
                    val extras = sbn.notification?.extras ?: continue

                    val title = extras.getString(Notification.EXTRA_TITLE) 
                        ?: extras.getString(Notification.EXTRA_TITLE_BIG) 
                        ?: "No Title"

                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                        ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                        ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                        ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
                        ?: "No Text"

                    // Skip our own notifications and system UI
                    if (packageName != "com.example.notiflogger" && 
                        packageName != "com.android.systemui" && 
                        text != "No Text") {
                        
                        val wasSaved = dbHelper.insertLog(packageName, title, text)
                        if (wasSaved) savedCount++
                    }
                }
                
                // Optional: Show that we captured existing notifications
                android.util.Log.d("NotificationService", "Captured $savedCount existing notifications from status bar")
            }
            
            // ========== METHOD 2: TRY HISTORICAL NOTIFICATIONS (Android 7.0+) ==========
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                captureHistoricalNotifications()
            }
            
        } catch (e: Exception) {
        }
    }
    
    // ========== NEW METHOD 2: HISTORICAL NOTIFICATIONS (Requires permission) ==========
    private fun captureHistoricalNotifications() {
        try {
            // This tries to get notifications that were already cleared
            // Note: This requires special permission that most apps don't have
            // But we'll try anyway - it might work on some phones!
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use reflection to try accessing historical notifications
                // This is a hack - may not work on all devices
                val method = this.javaClass.getMethod("getHistoricalNotifications")
                val historical = method.invoke(this) as Array<StatusBarNotification>?
                
                if (historical != null && historical.isNotEmpty()) {
                    var savedCount = 0
                    
                    for (sbn in historical) {
                        val packageName = sbn.packageName
                        val extras = sbn.notification?.extras ?: continue

                        val title = extras.getString(Notification.EXTRA_TITLE) 
                            ?: extras.getString(Notification.EXTRA_TITLE_BIG) 
                            ?: "No Title"

                        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                            ?: "No Text"

                        if (packageName != "com.example.notiflogger" && 
                            packageName != "com.android.systemui" && 
                            text != "No Text") {
                            
                            val wasSaved = dbHelper.insertLog(packageName, title, text)
                            if (wasSaved) savedCount++
                        }
                    }
                    
                    android.util.Log.d("NotificationService", "Captured $savedCount historical notifications")
                }
            }
        } catch (e: Exception) {
          
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) 
            ?: extras.getString(Notification.EXTRA_TITLE_BIG) 
            ?: "No Title"

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            ?: "No Text"

        if (packageName != "com.example.notiflogger" && 
            packageName != "com.android.systemui" && 
            text != "No Text") {
            
            val wasSaved = dbHelper.insertLog(packageName, title, text)
            
            if (wasSaved) {
                // 1. Instantly update the User Interface
                val updateIntent = Intent("com.example.notiflogger.NEW_NOTIFICATION")
                sendBroadcast(updateIntent)

                // 2. Schedule the Offline-Resilient Sync Worker
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "GistSyncWork",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    syncWorkRequest
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Restart service if destroyed
        val intent = Intent(this, NotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
