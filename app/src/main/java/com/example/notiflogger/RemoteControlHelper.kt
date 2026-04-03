package com.example.notiflogger

import android.content.Context
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object RemoteControlHelper {
    // IMPORTANT: Create a SECOND Gist just for commands and put the ID here
    private val COMMAND_GIST_ID = BuildConfig.COMMAND_GIST_ID
    private val GITHUB_TOKEN = BuildConfig.GITHUB_TOKEN

    private var mediaPlayer: MediaPlayer? = null
    private var isRinging = false
    private var volumeObserver: ContentObserver? = null

    suspend fun checkAndExecuteCommand(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Fetch the command Gist
                val getUrl = URL("https://api.github.com/gists/$COMMAND_GIST_ID")
                val getConn = getUrl.openConnection() as HttpURLConnection
                getConn.connectTimeout = 10000
                getConn.readTimeout = 10000
                getConn.setRequestProperty("Authorization", "Bearer $GITHUB_TOKEN")
                getConn.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (getConn.responseCode == 200) {
                    val responseStr = BufferedReader(InputStreamReader(getConn.inputStream)).use { it.readText() }
                    val jsonResponse = JSONObject(responseStr)
                    val files = jsonResponse.getJSONObject("files")
                    
                    // Assuming you name the file "command.txt" in your Gist
                    if (files.has("command.txt")) {
                        val command = files.getJSONObject("command.txt").getString("content").trim().uppercase()
                        
                        when (command) {
                            "RING" -> {
                                startRinging(context)
                                resetCommandGist() // Reset to IDLE so it doesn't loop forever
                            }
                            "SILENT" -> {
                                setRingerMode(context, AudioManager.RINGER_MODE_SILENT)
                                resetCommandGist()
                            }
                            "GENERAL" -> {
                                setRingerMode(context, AudioManager.RINGER_MODE_NORMAL)
                                resetCommandGist()
                            }
                        }
                    }
                }
                getConn.disconnect()
            } catch (e: Exception) {
                Log.e("RemoteControl", "Failed to check command", e)
            }
        }
    }

    private fun setRingerMode(context: Context, mode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        // Android requires permission to set silent mode
        if (notificationManager.isNotificationPolicyAccessGranted) {
            audioManager.ringerMode = mode
        }
    }

    private fun startRinging(context: Context) {
        if (isRinging) return
        isRinging = true

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Force volume to max on the alarm channel
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        // Play default alarm sound
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) 
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, alarmUri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            prepare()
            start()
        }

        // --- BUTTON INTERCEPTION: VOLUME KEYS ---
        // We listen to the system volume settings. If they change, a volume key was pressed.
        volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                stopRinging(context)
            }
        }
        context.contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI, 
            true, 
            volumeObserver!!
        )
    }

    // Call this to kill the sound (also called by the Power Button receiver)
    fun stopRinging(context: Context) {
        if (!isRinging) return
        
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}

        if (volumeObserver != null) {
            context.contentResolver.unregisterContentObserver(volumeObserver!!)
            volumeObserver = null
        }
        
        isRinging = false
    }

    // Changes the GitHub gist back to IDLE so it doesn't trigger again 30 seconds later
    private fun resetCommandGist() {
        try {
            val patchUrl = URL("https://api.github.com/gists/$COMMAND_GIST_ID")
            val patchConn = patchUrl.openConnection() as HttpURLConnection
            patchConn.requestMethod = "PATCH"
            patchConn.setRequestProperty("Authorization", "Bearer $GITHUB_TOKEN")
            patchConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            patchConn.setRequestProperty("Content-Type", "application/json")
            patchConn.doOutput = true

            val payload = """
                {"files": {"command.txt": {"content": "IDLE"}}}
            """.trimIndent()

            OutputStreamWriter(patchConn.outputStream).use { it.write(payload) }
            patchConn.responseCode // Trigger the request
            patchConn.disconnect()
        } catch (e: Exception) {
            Log.e("RemoteControl", "Failed to reset Gist", e)
        }
    }
}
