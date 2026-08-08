package com.sharepark.platform.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sharepark.platform.notification.NotificationHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Re-initialize notification channels
            NotificationHelper.createNotificationChannels(context)
        }
    }
}
