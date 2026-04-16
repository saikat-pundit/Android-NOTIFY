package com.android.mycalculator

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UsageTracker {

    fun extractUsageEvents(context: Context, dbHelper: DatabaseHelper) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTimeMs = System.currentTimeMillis()
        
        // Check the last 15 minutes to be safe (DB logic prevents duplicates)
        val startTimeMs = endTimeMs - (15 * 60 * 1000) 

        val events = usageStatsManager.queryEvents(startTimeMs, endTimeMs)
        val event = UsageEvents.Event()
        val openApps = mutableMapOf<String, Long>()

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val time = event.timeStamp

            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                // App moved to Foreground (Start Time)
                openApps[pkg] = time
            } else if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
                // App moved to Background (End Time)
                if (openApps.containsKey(pkg)) {
                    val startMs = openApps[pkg]!!
                    val endMs = time
                    
                    // Only log if they spent at least 1 second in the app
                    if ((endMs - startMs) > 1000) {
                        val dateStr = sdfDate.format(Date(startMs))
                        val startStr = sdfTime.format(Date(startMs))
                        val endStr = sdfTime.format(Date(endMs))

                        dbHelper.insertUsageLog(deviceName, pkg, dateStr, startStr, endStr, startMs)
                    }
                    openApps.remove(pkg)
                }
            }
        }
    }
}
