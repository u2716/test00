package app.screenlookup.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

fun Context.notificationBuilder(
    channelId: String,
    block: NotificationCompat.Builder.() -> Unit,
): NotificationCompat.Builder {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
    return NotificationCompat.Builder(this, channelId).apply(block)
}

fun Context.notify(id: Int, channelId: String, block: NotificationCompat.Builder.() -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
    }
    NotificationManagerCompat.from(this).notify(id, notificationBuilder(channelId, block).build())
}

fun Context.cancelNotification(id: Int) {
    NotificationManagerCompat.from(this).cancel(id)
}
