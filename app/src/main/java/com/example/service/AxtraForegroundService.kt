package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R
import com.example.actions.ActionExecutor
import com.example.core.AppPreferences
import com.example.core.AxtraConstants
import com.example.core.AxtraLogger
import com.example.overlay.OverlayService
import com.example.voice.HotwordCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class AxtraForegroundService : Service() {

    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var hotwordCoordinator: HotwordCoordinator
    @Inject lateinit var actionExecutor: ActionExecutor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isPaused = false
    private var currentStatus = "Listening"

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AxtraConstants.ACTION_PAUSE_SERVICE -> {
                    isPaused = !isPaused
                    if (isPaused) {
                        hotwordCoordinator.setSystemPaused(true)
                        updateNotification("Paused")
                    } else {
                        hotwordCoordinator.setSystemPaused(false)
                        startWakeWordIfAllowed()
                        updateNotification("Listening")
                    }
                }
                AxtraConstants.ACTION_STOP_EVERYTHING -> {
                    actionExecutor.stopEverything()
                    updateNotification("Stopped Tasks")
                }
                AxtraConstants.ACTION_STOP_SERVICE -> {
                    scope.launch { appPreferences.updateAlwaysOnEnabled(false) }
                    stopSelf()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isServiceRunning.value = true
        val filter = IntentFilter().apply {
            addAction(AxtraConstants.ACTION_PAUSE_SERVICE)
            addAction(AxtraConstants.ACTION_STOP_EVERYTHING)
            addAction(AxtraConstants.ACTION_STOP_SERVICE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(controlReceiver, filter)
        }
        scheduleWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AxtraConstants.ACTION_PAUSE_SERVICE -> {
                isPaused = !isPaused
                hotwordCoordinator.setSystemPaused(isPaused)
                if (!isPaused) startWakeWordIfAllowed()
                startForegroundNotification(if (isPaused) "Paused" else "Listening")
                return START_STICKY
            }
            AxtraConstants.ACTION_STOP_EVERYTHING -> {
                actionExecutor.stopEverything()
                startForegroundNotification("Paused")
                return START_STICKY
            }
            AxtraConstants.ACTION_STOP_SERVICE -> {
                scope.launch { appPreferences.updateAlwaysOnEnabled(false) }
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForegroundNotification("Listening")
        startBatteryAndConditionLoop()
        startOverlayIfPermitted()
        startWakeWordIfAllowed()
        return START_STICKY
    }

    private fun startForegroundNotification(status: String) {
        currentStatus = status
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(
                this,
                AxtraConstants.NOTIFICATION_ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(AxtraConstants.NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    private fun updateNotification(status: String) {
        currentStatus = status
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm?.notify(AxtraConstants.NOTIFICATION_ID_FOREGROUND, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AxtraForegroundService::class.java).apply { action = AxtraConstants.ACTION_PAUSE_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, AxtraForegroundService::class.java).apply { action = AxtraConstants.ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val killIntent = PendingIntent.getService(
            this, 3,
            Intent(this, AxtraForegroundService::class.java).apply { action = AxtraConstants.ACTION_STOP_EVERYTHING },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, AxtraConstants.CHANNEL_ALWAYS_ON)
            .setSmallIcon(R.drawable.ic_mic_shortcut)
            .setContentTitle("${AxtraConstants.APP_NAME} • ${AxtraConstants.OWNER_LABEL}")
            .setContentText("Status: $status")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, if (isPaused) "Resume" else "Pause", pauseIntent)
            .addAction(0, "Stop", stopIntent)
            .addAction(0, "Stop Everything", killIntent)
            .build()
    }

    private fun startWakeWordIfAllowed() {
        scope.launch {
            val settings = appPreferences.settingsFlow.first()
            if (!settings.wakeWordEnabled || isPaused) {
                hotwordCoordinator.stop(OWNER_TAG)
                return@launch
            }
            hotwordCoordinator.setSystemPaused(false)
            hotwordCoordinator.start(OWNER_TAG)
        }
    }

    private fun startOverlayIfPermitted() {
        if (Settings.canDrawOverlays(this)) {
            runCatching { startService(Intent(this, OverlayService::class.java)) }
        }
    }

    private fun startBatteryAndConditionLoop() {
        scope.launch {
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            while (isActive) {
                delay(15_000L)
                val settings = appPreferences.settingsFlow.first()
                if (!settings.alwaysOnEnabled) {
                    stopSelf()
                    break
                }

                // Pause on low battery (<10%), active phone call, or screen-off if configured
                val bm = getSystemService(BatteryManager::class.java)
                val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                val am = getSystemService(AudioManager::class.java)
                val inCall = am?.mode == AudioManager.MODE_IN_CALL || am?.mode == AudioManager.MODE_IN_COMMUNICATION
                val pm = getSystemService(PowerManager::class.java)
                val screenOff = pm?.isInteractive == false

                val shouldAutoPause = (settings.pauseOnLowBattery && pct in 1..9) ||
                    inCall ||
                    (settings.screenOnOnly && screenOff)

                if (shouldAutoPause && !isPaused) {
                    hotwordCoordinator.setSystemPaused(true)
                    updateNotification("Paused (Battery/Call/Screen)")
                } else if (!shouldAutoPause && !isPaused) {
                    hotwordCoordinator.setSystemPaused(false)
                    if (settings.wakeWordEnabled) {
                        hotwordCoordinator.start(OWNER_TAG)
                    } else {
                        hotwordCoordinator.stop(OWNER_TAG)
                    }
                    if (currentStatus != "Listening") {
                        updateNotification("Listening")
                    }
                    startOverlayIfPermitted()
                    appPreferences.addListeningTime(15_000L, dateFmt.format(Date()))
                }
            }
        }
    }

    private fun scheduleWatchdog() {
        val req = PeriodicWorkRequestBuilder<AlwaysOnWatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "axtra_always_on_watchdog",
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        hotwordCoordinator.stop(OWNER_TAG)
        runCatching { unregisterReceiver(controlReceiver) }
        runCatching { stopService(Intent(this, OverlayService::class.java)) }
        AxtraLogger.d("ForegroundService", "AxtraForegroundService destroyed")
        scope.cancel()
    }

    companion object {
        private const val OWNER_TAG = "ForegroundService"
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }
}
