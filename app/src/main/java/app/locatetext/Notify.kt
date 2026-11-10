package app.locatetext

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Notify {
    private const val CH_ONGOING = "sharing"
    private const val CH_RECEIVED = "received"

    private fun manager(c: Context) = c.getSystemService(NotificationManager::class.java)

    private fun ensureChannels(c: Context) {
        val m = manager(c)
        m.createNotificationChannel(
            NotificationChannel(CH_ONGOING, "Location sharing", NotificationManager.IMPORTANCE_LOW)
        )
        m.createNotificationChannel(
            NotificationChannel(CH_RECEIVED, "Received locations", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun ongoing(c: Context, text: String): Notification {
        ensureChannels(c)
        return Notification.Builder(c, CH_ONGOING)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Locate and Text")
            .setContentText(text)
            .setContentIntent(openApp(c))
            .setOngoing(true)
            .build()
    }

    /** Shown on the phone being located, each time it answers a request. */
    fun shared(c: Context, text: String) {
        ensureChannels(c)
        val n = Notification.Builder(c, CH_RECEIVED)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Location shared")
            .setContentText(text)
            .setContentIntent(openApp(c))
            .setAutoCancel(true)
            .build()
        manager(c).notify(3, n)
    }

    fun received(c: Context, text: String) {
        ensureChannels(c)
        val n = Notification.Builder(c, CH_RECEIVED)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Location received")
            .setContentText(text)
            .setContentIntent(openApp(c))
            .setAutoCancel(true)
            .build()
        manager(c).notify(2, n)
    }
}
