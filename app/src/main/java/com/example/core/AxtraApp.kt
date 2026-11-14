package com.example.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AxtraApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val alwaysOnChannel = NotificationChannel(
            AxtraConstants.CHANNEL_ALWAYS_ON,
            "Axtra Always-On Voice",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent status for Axtra AI background listening"
            setShowBadge(false)
        }
        val alertsChannel = NotificationChannel(
            AxtraConstants.CHANNEL_ALERTS,
            "Axtra Alerts & Permissions",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Important permission alerts, caller announcements, and task updates"
        }
        val briefingChannel = NotificationChannel(
            AxtraConstants.CHANNEL_BRIEFING,
            "Daily Briefing & Prayer Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Scheduled daily briefing and prayer reminders"
        }
        manager.createNotificationChannels(listOf(alwaysOnChannel, alertsChannel, briefingChannel))
    }
}
