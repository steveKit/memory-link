package com.memorylink.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memorylink.MainActivity

/**
 * Broadcast receiver that auto-starts the app on device boot.
 * Per FR-14: Auto-launch on device boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
        }
    }
}
