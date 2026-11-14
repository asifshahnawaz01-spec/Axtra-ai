package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.core.AppPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun appPreferences(): AppPreferences
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, BootEntryPoint::class.java)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = ep.appPreferences().settingsFlow.first()
                if (settings.startOnBoot && settings.alwaysOnEnabled) {
                    val svcIntent = Intent(context, AxtraForegroundService::class.java)
                    ContextCompat.startForegroundService(context, svcIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
