package com.example.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.core.AppPreferences
import com.example.core.AxtraLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AlwaysOnWatchdogWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val appPreferences: AppPreferences
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = appPreferences.settingsFlow.first()
        if (settings.alwaysOnEnabled && !AxtraForegroundService.isServiceRunning.value) {
            AxtraLogger.d("Watchdog", "Restarting AxtraForegroundService")
            runCatching {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, AxtraForegroundService::class.java)
                )
            }
        }
        return Result.success()
    }
}
