package com.android.mycalculator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// An empty receiver used exclusively to "shock" the Android Package Manager
class GhostReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}
