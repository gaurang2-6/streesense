package com.example.stressdetection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("StressBoot", "Boot completed, starting DataCollectionService")
            val serviceIntent = Intent(context, DataCollectionService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
