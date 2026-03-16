package com.novacut.editor.engine

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.novacut.editor.NovaCutApp

class ExportService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.novacut.editor.CANCEL_EXPORT"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    fun updateProgress(progress: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(progress))
    }

    fun notifyComplete() {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, NovaCutApp.CHANNEL_EXPORT)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Export Complete")
            .setContentText("Your video has been exported successfully")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID + 1, notification)
        stopSelf()
    }

    fun notifyError(message: String) {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, NovaCutApp.CHANNEL_EXPORT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Export Failed")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID + 1, notification)
        stopSelf()
    }

    private fun buildNotification(progress: Int): Notification {
        val cancelIntent = Intent(this, ExportService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPi = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NovaCutApp.CHANNEL_EXPORT)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Exporting Video")
            .setContentText("${progress}% complete")
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPi)
            .build()
    }
}
