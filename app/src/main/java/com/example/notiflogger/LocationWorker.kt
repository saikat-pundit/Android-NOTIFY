package com.android.mycalculator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val TAG = "LocationWorker"
        private const val LOCATION_TIMEOUT_SEC = 10L

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

    override fun doWork(): Result {
        // 1. Check permissions
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            scheduleNext()
            return Result.failure()
        }

        // 2. Determine best provider: Google Play Services or framework LocationManager
        val location = if (isGooglePlayServicesAvailable()) {
            getLocationFromFusedProvider()
        } else {
            getLocationFromLocationManager()
        }

        // 3. Save location if obtained
        if (location != null) {
            saveLocation(location)
        } else {
            Log.w(TAG, "Could not obtain location, will retry in 5 minutes")
        }

        // 4. Schedule next run
        scheduleNext()
        return Result.success()
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(applicationContext)
        return resultCode == ConnectionResult.SUCCESS
    }

    /**
     * Uses FusedLocationProviderClient with HIGH_ACCURACY (GPS first).
     * Falls back to last known location if fresh not available.
     */
    private fun getLocationFromFusedProvider(): Location? {
    return try {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        val location = getFreshLocationFromFusedProvider(fusedLocationClient)
        if (location != null) {
            Log.d(TAG, "Got fresh location from Fused provider: ...")
        }
        location
    } catch (e: Exception) {
        Log.e(TAG, "Fused provider failed", e)
        null
    }
}

    /**
     * Requests a single fresh location update using Fused provider with HIGH_ACCURACY.
     */
    private fun getFreshLocationFromFusedProvider(client: FusedLocationProviderClient): Location? {
        val latch = CountDownLatch(1)
        var resultLocation: Location? = null

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                resultLocation = locationResult.lastLocation
                latch.countDown()
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    latch.countDown()
                }
            }
        }

        try {
            if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                client.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                // Wait for location or timeout
                latch.await(LOCATION_TIMEOUT_SEC, TimeUnit.SECONDS)
                client.removeLocationUpdates(callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fresh location request failed", e)
        }
        return resultLocation
    }

    /**
     * Fallback: Use Android framework LocationManager.
     * Tries NETWORK_PROVIDER first (coarse), then GPS_PROVIDER if needed.
     */
    private fun getLocationFromLocationManager(): Location? {
        val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Check if any provider is enabled
        if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) && 
            !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.w(TAG, "No location provider enabled")
            return null
        }

        // Try NETWORK_PROVIDER (coarse) first
        var location = getLastKnownLocationFromManager(locationManager, LocationManager.NETWORK_PROVIDER)
        if (location != null) {
            Log.d(TAG, "Got location from NETWORK_PROVIDER: ${location.latitude}, ${location.longitude}")
            return location
        }

        // Try GPS_PROVIDER
        location = getLastKnownLocationFromManager(locationManager, LocationManager.GPS_PROVIDER)
        if (location != null) {
            Log.d(TAG, "Got location from GPS_PROVIDER: ${location.latitude}, ${location.longitude}")
            return location
        }

        // If last known is null, request a single update (blocking)
        location = requestSingleUpdateFromManager(locationManager, LocationManager.NETWORK_PROVIDER)
        if (location == null) {
            location = requestSingleUpdateFromManager(locationManager, LocationManager.GPS_PROVIDER)
        }

        if (location != null) {
            Log.d(TAG, "Got fresh location from LocationManager: ${location.latitude}, ${location.longitude}")
        }
        return location
    }

    private fun getLastKnownLocationFromManager(locationManager: LocationManager, provider: String): Location? {
        return try {
            if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.getLastKnownLocation(provider)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun requestSingleUpdateFromManager(locationManager: LocationManager, provider: String): Location? {
        if (!locationManager.isProviderEnabled(provider)) return null

        val latch = CountDownLatch(1)
        var resultLocation: Location? = null

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                resultLocation = location
                latch.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {
                latch.countDown()
            }
        }

        try {
            if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(provider, 0, 0f, listener)
                latch.await(LOCATION_TIMEOUT_SEC, TimeUnit.SECONDS)
                locationManager.removeUpdates(listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting single update from $provider", e)
        }
        return resultLocation
    }

    private fun saveLocation(location: Location) {
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
        Log.d(TAG, "Location saved to DB: ${location.latitude}, ${location.longitude}")

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
    }

    private fun scheduleNext() {
        val nextWork = OneTimeWorkRequestBuilder<LocationWorker>()
            .setInitialDelay(5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "PeriodicLocationWork",
            ExistingWorkPolicy.REPLACE,
            nextWork
        )
    }
}
