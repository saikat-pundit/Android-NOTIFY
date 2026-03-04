package com.example.notiflogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.work.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class HeartbeatService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    
    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotifyLog:HeartbeatWakeLock"
        )
        
        startHeartbeat()
        scheduleWorkManager()
    }
    
    private fun startHeartbeat() {
        serviceScope.launch {
            while (isActive) {
                if (!wakeLock.isHeld) {
                    wakeLock.acquire(10000)
                }
                
                checkMainProcess()
                
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
                
                delay(60000)
            }
        }
    }
    
    private fun checkMainProcess() {
        val intent = Intent(this, KeepAliveService::class.java)
        startService(intent)
    }
    
    private fun scheduleWorkManager() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .setRequiresCharging(false)
            .build()
        
        val workRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints)
         .setBackoffCriteria(
             BackoffPolicy.EXPONENTIAL,
             1, TimeUnit.MINUTES
         )
         .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "heartbeat_work",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        
        val intent = Intent(this, HeartbeatService::class.java)
        startService(intent)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

// MOVED THIS OUTSIDE - NOW A SEPARATE CLASS
class HeartbeatWorker(context: Context, params: WorkerParameters) : 
    Worker(context, params) {
    
    override fun doWork(): Result {
        return try {
            val intent = Intent(applicationContext, HeartbeatService::class.java)
            applicationContext.startService(intent)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
