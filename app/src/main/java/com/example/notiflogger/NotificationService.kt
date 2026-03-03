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
