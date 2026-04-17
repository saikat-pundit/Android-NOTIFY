package com.android.mycalculator

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import java.util.Random

object AlarmScheduler {
    
    private val random = Random()
    
    fun scheduleAlarms(context: Context) {
        scheduleInexactRepeating(context)
    }
    
    private fun scheduleAlarmWithInterval(context: Context, requestCode: Int, minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        
        // Add random offset to prevent pattern detection
        val randomOffset = random.nextInt(60) * 1000 // 0-60 seconds
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val triggerTime = SystemClock.elapsedRealtime() + (minutes * 60 * 1000) + randomOffset
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Use allow while idle for all to bypass Doze
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
    
    private fun scheduleInexactRepeating(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2001, // Different range to distinguish
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 30 * 60 * 1000, // 30 minutes
            30 * 60 * 1000, // 30 minutes
            pendingIntent
        )
    }
    
    fun scheduleNextAlarm(context: Context) {
        // Reschedule all alarms
        scheduleAlarms(context)
    }
    
    fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        
        val repeatingIntent = PendingIntent.getBroadcast(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(repeatingIntent)
        repeatingIntent.cancel()
    }
}
