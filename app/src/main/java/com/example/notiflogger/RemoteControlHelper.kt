package com.example.notiflogger

import android.content.Context
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
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
    private val COMMAND_GIST_ID = BuildConfig.COMMAND_GIST_ID
    private val GITHUB_TOKEN = BuildConfig.GITHUB_TOKEN

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isRinging = false
    private var volumeObserver: ContentObserver? = null

    suspend fun checkAndExecuteCommand(context: Context) {
        withContext(Dispatchers.IO) {
            try {
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
                    
                    if (files.has("command.txt")) {
                        val command = files.getJSONObject("command.txt").getString("content").trim().uppercase()
                        
                        when (command) {
                            "RING" -> {
                                startRinging(context)
                                resetCommandGist()
                            }
                            "SILENT" -> {
                                forceSilentMode(context)
                                resetCommandGist()
                            }
                            "GENERAL" -> {
                                forceGeneralMode(context)
                                resetCommandGist()
                            }
                            // IDLE is ignored so it doesn't constantly overwrite user volume
                        }
                    }
                }
                getConn.disconnect()
            } catch (e: Exception) {
                Log.e("RemoteControl", "Failed to check command", e)
            }
        }
    }

    private fun forceSilentMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        // RINGER_MODE_SILENT completely disables sounds AND vibrations
        if (notificationManager.isNotificationPolicyAccessGranted) {
            try {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            } catch (e: Exception) {
                Log.e("RemoteControl", "Failed to set standard silent mode", e)
            }
        }
        
        try {
            val streamsToMute = intArrayOf(
                AudioManager.STREAM_RING,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_SYSTEM,
                AudioManager.STREAM_MUSIC
            )
            
            for (stream in streamsToMute) {
                audioManager.setStreamVolume(stream, 0, 0)
            }
        } catch (e: Exception) {
            Log.e("RemoteControl", "Failed to brute-force volume down", e)
        }
    }

    private fun forceGeneralMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (notificationManager.isNotificationPolicyAccessGranted) {
            try {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            } catch (e: Exception) {
                Log.e("RemoteControl", "Failed to restore standard mode", e)
            }
        }
        
        try {
            val streamsToRestore = intArrayOf(
                AudioManager.STREAM_RING,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_SYSTEM,
                AudioManager.STREAM_MUSIC // Added Media stream to be restored as well
            )
            
            for (stream in streamsToRestore) {
                val maxVol = audioManager.getStreamMaxVolume(stream)
                // Restoring to 100% volume
                audioManager.setStreamVolume(stream, maxVol, 0)
            }
        } catch (e: Exception) {
            Log.e("RemoteControl", "Failed to brute-force volume up", e)
        }
    }

    private fun startRinging(context: Context) {
        if (isRinging) return
        isRinging = true

        // 1. Force the phone into General Mode at 100% volume first
        forceGeneralMode(context)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Double-check alarm stream is maxed out
        val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0)

        // 2. Start intense vibration
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 1000, 1000) // Wait 0ms, Vibrate 1000ms, Sleep 1000ms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 means loop indefinitely
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        // 3. Play default alarm sound
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

    fun stopRinging(context: Context) {
        if (!isRinging) return
        
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {}

        if (volumeObserver != null) {
            context.contentResolver.unregisterContentObserver(volumeObserver!!)
            volumeObserver = null
        }
        
        isRinging = false
    }

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
            patchConn.responseCode 
            patchConn.disconnect()
        } catch (e: Exception) {
            Log.e("RemoteControl", "Failed to reset Gist", e)
        }
    }
}
