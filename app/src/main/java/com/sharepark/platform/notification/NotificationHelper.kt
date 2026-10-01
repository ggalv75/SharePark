package com.sharepark.platform.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sharepark.MainActivity
import com.sharepark.R

object NotificationHelper {

    private const val CHANNEL_SERVICE_ID = "parking_detection_service"
    private const val CHANNEL_EVENTS_ID = "parking_events"

    const val SERVICE_NOTIFICATION_ID = 1001
    private const val EVENT_NOTIFICATION_ID = 2002
    // One slot per shared car, so two cars parked by others don't replace each other.
    private const val SHARED_NOTIFICATION_ID_BASE = 3000

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel for the background foreground service
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Parking Detection Service Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of the background Bluetooth parking detection"
            }
            manager.createNotificationChannel(serviceChannel)

            // Channel for parking event notifications
            val eventsChannel = NotificationChannel(
                CHANNEL_EVENTS_ID,
                "Parking Location Events",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you when your parking location has been successfully detected and saved"
                enableVibration(true)
            }
            manager.createNotificationChannel(eventsChannel)
        }
    }

    fun buildServiceNotification(context: Context, message: String): Notification {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Using standard system icon since we don't have custom drawables yet.
        // We will make sure this compiles fine.
        return NotificationCompat.Builder(context, CHANNEL_SERVICE_ID)
            .setContentTitle("SharePark")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun showParkingDetectedNotification(
        context: Context,
        vehicleId: Long,
        vehicleName: String,
        address: String,
        shareText: String
    ) {
        createNotificationChannels(context)

        // Content tap opens the app; MainActivity switches the map to the parked vehicle.
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_VEHICLE_ID, vehicleId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // The "שתף" action launches our own invisible trampoline activity — the same
        // system-driven activity-launch path as the content tap (which works even when the
        // app is backgrounded on ColorOS) — and the trampoline opens the share chooser.
        val trampolineIntent = Intent(context, ShareTrampolineActivity::class.java).apply {
            putExtra(ShareTrampolineActivity.EXTRA_SHARE_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context,
            1,
            trampolineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS_ID)
            .setContentTitle("חנייה זוהתה בהצלחה! 🅿️")
            .setContentText("רכב: $vehicleName\nמיקום: $address")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(android.R.drawable.ic_menu_send, "שתף", sharePendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(EVENT_NOTIFICATION_ID, notification)
    }

    /** Another member of a shared car just parked it. */
    fun showSharedParkingNotification(
        context: Context,
        vehicleId: Long,
        vehicleName: String,
        parkedByName: String,
        address: String
    ) {
        createNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_VEHICLE_ID, vehicleId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            vehicleId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS_ID)
            .setContentTitle("$parkedByName החנה את $vehicleName 🅿️")
            .setContentText(address)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(SHARED_NOTIFICATION_ID_BASE + vehicleId.toInt(), notification)
    }
}
