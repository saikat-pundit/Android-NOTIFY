package com.example.notiflogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class KeepAliveService : Service() {
    
    private val NOTIFICATION_ID = 1002
    private val CHANNEL_ID = "keep_alive_channel"
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground()
        acquireWakeLock()
        
        // Start monitoring main service
        startMonitoring()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Keep Alive Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the main service running"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Notify Log is running")
            .setContentText("Background service active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun acquireWakeLock() {
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotifyLog:KeepAliveWakeLock"
        ).apply {
            acquire(10*60*1000L) // 10 minutes
        }
    }
    
    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                delay(30000) // Check every 30 seconds
                
                // Check if main service is running - FIXED METHOD
                if (!isNotificationServiceRunning()) {
                    // Restart notification service
                    val componentName = ComponentName(
                        this@KeepAliveService,
                        NotificationService::class.java
                    )
                    NotificationListenerService.requestRebind(componentName)
                }
                
                // Re-acquire wake lock if needed
                if (!wakeLock.isHeld) {
                    wakeLock.acquire(10*60*1000L)
                }
            }
        }
    }
    
    private fun isNotificationServiceRunning(): Boolean {
        // FIXED: Correct way to check if notification listener is enabled
        val enabledListeners = android.provider.Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners != null && enabledListeners.contains(packageName)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        
        // Restart service
        val intent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
