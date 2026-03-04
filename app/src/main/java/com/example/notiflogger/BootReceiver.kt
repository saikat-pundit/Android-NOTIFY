package com.example.notiflogger
import com.example.notiflogger.NotificationService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received: $action")
        
        // Handle all boot-related actions
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                
                // Acquire wake lock to ensure we can start services
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "NotifyLog:BootWakeLock"
                )
                wakeLock.acquire(30000) // 30 seconds
                
                try {
                    // Wait a bit for system to settle
                    Thread.sleep(5000)
                    
                    // Start all services
                    startAllServices(context)
                    
                    // Schedule alarms
                    AlarmScheduler.scheduleAlarms(context)
                    
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error starting services", e)
                } finally {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                }
            }
        }
    }
    
    private fun startAllServices(context: Context) {
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
        
        // Start heartbeat service
        val heartbeatIntent = Intent(context, HeartbeatService::class.java)
        context.startService(heartbeatIntent)
        
        // Rebind notification listener
        val componentName = ComponentName(context, NotificationService::class.java)
        NotificationListenerService.requestRebind(componentName)
    }
}
