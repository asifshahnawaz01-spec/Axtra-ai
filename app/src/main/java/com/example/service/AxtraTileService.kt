package com.example.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
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

class AxtraTileService : TileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TileEntryPoint {
        fun appPreferences(): AppPreferences
    }

    override fun onStartListening() {
        super.onStartListening()
        val ep = EntryPointAccessors.fromApplication(applicationContext, TileEntryPoint::class.java)
        CoroutineScope(Dispatchers.Main).launch {
            val enabled = ep.appPreferences().settingsFlow.first().alwaysOnEnabled
            qsTile?.apply {
                state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                subtitle = if (enabled) "Always On" else "Off"
                updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val ep = EntryPointAccessors.fromApplication(applicationContext, TileEntryPoint::class.java)
        CoroutineScope(Dispatchers.Main).launch {
            val current = ep.appPreferences().settingsFlow.first().alwaysOnEnabled
            val next = !current
            ep.appPreferences().updateAlwaysOnEnabled(next)
            if (next) {
                ContextCompat.startForegroundService(
                    applicationContext,
                    Intent(applicationContext, AxtraForegroundService::class.java)
                )
            } else {
                stopService(Intent(applicationContext, AxtraForegroundService::class.java))
            }
            qsTile?.apply {
                state = if (next) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                subtitle = if (next) "Always On" else "Off"
                updateTile()
            }
        }
    }
}
