package com.example.notiflogger

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

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
                // This constraint ensures the worker ONLY runs if there is internet
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .build()

                // Queue the work. If it's already running, it just queues it up next.
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "GistSyncWork",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    syncWorkRequest
                )
            }
        }
    }
}
