package com.novacut.editor

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NovaCutApp : Application() {

    companion object {
        const val CHANNEL_EXPORT = "novacut_export"
        const val VERSION = "v2.3.0"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val exportChannel = NotificationChannel(
            CHANNEL_EXPORT,
            "Export Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows video export progress"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(exportChannel)
    }
}
