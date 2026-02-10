package com.memorylink.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device admin receiver for kiosk mode (LockTask).
 * Must be set as device owner via ADB for full kiosk functionality.
 * 
 * Per plan.md Phase 7:
 * - Configure AndroidManifest for LockTaskMode
 * - Create device_admin_receiver.xml
 * 
 * Device owner provisioning command:
 * adb shell dpm set-device-owner com.memorylink/.service.DeviceAdminReceiver
 */
class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // Device admin enabled
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // Device admin disabled
    }
}
