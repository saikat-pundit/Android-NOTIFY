package com.example.notiflogger

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.service.notification.NotificationListenerService

class AlarmReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        // Acquire wake lock
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotifyLog:AlarmWakeLock"
        )
        wakeLock.acquire(10000) // 10 seconds
        
        try {
            // Check and restart services
            val keepAliveIntent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(keepAliveIntent)
            } else {
                context.startService(keepAliveIntent)
            }
            
            // Rebind notification listener
            val componentName = ComponentName(context, NotificationService::class.java)
            NotificationListenerService.requestRebind(componentName)
            
            // Set next alarm
            AlarmScheduler.scheduleNextAlarm(context)
            
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}
