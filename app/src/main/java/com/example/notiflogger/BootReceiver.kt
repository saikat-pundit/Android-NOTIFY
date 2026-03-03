package com.example.notiflogger

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted! Forcing NotifLogger to wake up...")
            
            // This is the official Android command to wake up a Notification Listener 
            // from a dead stop and force it to start running in the background again.
            val componentName = ComponentName(context, NotificationService::class.java)
            NotificationListenerService.requestRebind(componentName)
        }
    }
}
