package com.example.notiflogger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Upgraded to Version 3!
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "Notifs.db", null, 3) {
    
    override fun onCreate(db: SQLiteDatabase) {
        // NEW: Added is_synced column (0 means offline/pending, 1 means uploaded)
        db.execSQL("CREATE TABLE logs (id INTEGER PRIMARY KEY AUTOINCREMENT, app TEXT, title TEXT, content TEXT, logTime TEXT, timestampMs INTEGER, is_synced INTEGER DEFAULT 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS logs")
        onCreate(db)
    }

    fun insertLog(app: String, title: String, content: String): Boolean {
        val db = this.writableDatabase
        val currentTimeMs = System.currentTimeMillis()

        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM logs WHERE app = ? AND title = ? AND content = ? AND (? - timestampMs) <= 2000",
            arrayOf(app, title, content, currentTimeMs.toString())
        )
        
        var isDuplicate = false
        if (cursor.moveToFirst()) {
            if (cursor.getInt(0) > 0) isDuplicate = true
        }
        cursor.close()

        if (isDuplicate) {
            db.close()
            return false 
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault())
        val formattedLocalTime = sdf.format(Date(currentTimeMs))

        val values = ContentValues().apply {
            put("app", app)
            put("title", title)
            put("content", content)
            put("logTime", formattedLocalTime)
            put("timestampMs", currentTimeMs)
            put("is_synced", 0) // Always starts as unsynced
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
        cursor.close()
        db.close()
        return result
    }

    // NEW: Grabs ONLY the logs that haven't been uploaded yet
    fun getUnsyncedLogs(): List<Pair<Int, String>> {
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM logs WHERE is_synced = 0 ORDER BY id ASC", null)
        val unsyncedList = mutableListOf<Pair<Int, String>>()
        val deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val app = cursor.getString(cursor.getColumnIndexOrThrow("app"))?.replace("\"", "\"\"") ?: ""
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))?.replace("\"", "\"\"") ?: ""
                val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))?.replace("\"", "\"\"") ?: ""
                val time = cursor.getString(cursor.getColumnIndexOrThrow("logTime")) ?: ""
                
                val csvRow = "\"$deviceName\",\"$app\",\"$title\",\"$content\",\"$time\"\n"
                unsyncedList.add(Pair(id, csvRow))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return unsyncedList
    }

    // NEW: Marks specific logs as successfully uploaded
    fun markAsSynced(ids: List<Int>) {
        val db = this.writableDatabase
        for (id in ids) {
            val values = ContentValues().apply { put("is_synced", 1) }
            db.update("logs", values, "id=?", arrayOf(id.toString()))
        }
        db.close()
    }
}
