package com.android.mycalculator

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService

class AlarmReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        // Acquire wake lock with try-finally to prevent leaks
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotifyLog:AlarmWakeLock"
        )
        
        try {
            wakeLock.acquire(30000) // 30 seconds (increased from 10)
            
            // Check and restart services
            val keepAliveIntent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(keepAliveIntent)
            } else {
                context.startService(keepAliveIntent)
            }
            
            // Also restart notification service to be safe
            val notifIntent = Intent(context, NotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(notifIntent)
            } else {
                context.startService(notifIntent)
            }
            
            // Rebind notification listener
            val componentName = ComponentName(context, NotificationService::class.java)
            NotificationListenerService.requestRebind(componentName)
            
            // Force a sync
            val syncIntent = Intent(context, SyncWorker::class.java)
            context.startService(syncIntent)
            
            // Set next alarm
            AlarmScheduler.scheduleNextAlarm(context)
            
        } finally {
            // Always release, even if there's an error
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}
