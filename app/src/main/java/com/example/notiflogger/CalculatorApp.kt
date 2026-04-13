package com.android.mycalculator

import android.app.Application
import kotlin.system.exitProcess

class CalculatorApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // INTERCEPT ALL FATAL CRASHES
        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            // 1. We do NOT pass the error to the Android OS.
            // 2. We silently kill our own process immediately.
            // 3. The system UI never shows a popup.
            // 4. The AlarmManager will wake us back up seamlessly.
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(0)
        }
    }
}
