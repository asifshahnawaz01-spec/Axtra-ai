package com.example.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import com.example.core.AppPreferences
import com.example.core.AxtraConstants
import com.example.core.AxtraLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android VoiceInteractionService for Axtra AI.
 * When selected as the device's Default Digital Assistant, Android binds this service
 * and keeps the "hey astra" / "hello astra" / "astra" hotword pipeline active.
 */
class AxtraVoiceInteractionService : VoiceInteractionService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface VoiceServiceEntryPoint {
        fun appPreferences(): AppPreferences
        fun hotwordCoordinator(): HotwordCoordinator
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var entryPoint: VoiceServiceEntryPoint? = null

    override fun onReady() {
        super.onReady()
        instance = this
        _isServiceReady.value = true
        AxtraLogger.d(TAG, "AxtraVoiceInteractionService ready")

        val ep = EntryPointAccessors.fromApplication(applicationContext, VoiceServiceEntryPoint::class.java)
        entryPoint = ep

        scope.launch {
            ep.appPreferences().settingsFlow.collectLatest { settings ->
                if (settings.wakeWordEnabled) {
                    ep.hotwordCoordinator().start(OWNER_TAG)
                } else {
                    ep.hotwordCoordinator().stop(OWNER_TAG)
                }
            }
        }
    }

    fun showHotwordSession(trailingCommand: String? = null) {
        val args = Bundle().apply {
            if (!trailingCommand.isNullOrBlank()) {
                putString(AxtraConstants.EXTRA_INITIAL_COMMAND, trailingCommand)
            }
            putBoolean(EXTRA_FROM_HOTWORD, true)
        }
        runCatching {
            showSession(args, VoiceInteractionSession.SHOW_WITH_ASSIST)
        }.onFailure { err ->
            AxtraLogger.w(TAG, "showSession failed", err)
            entryPoint?.hotwordCoordinator()?.setSessionActive(false)
        }
    }

    override fun onShutdown() {
        super.onShutdown()
        AxtraLogger.d(TAG, "AxtraVoiceInteractionService shutdown")
        entryPoint?.hotwordCoordinator()?.stop(OWNER_TAG)
        instance = null
        _isServiceReady.value = false
        scope.cancel()
    }

    companion object {
        private const val TAG = "AxtraVoiceInteraction"
        private const val OWNER_TAG = "VoiceInteractionService"
        const val EXTRA_FROM_HOTWORD = "extra_from_hotword"

        @Volatile
        var instance: AxtraVoiceInteractionService? = null
            private set

        private val _isServiceReady = MutableStateFlow(false)
        val isServiceReady: StateFlow<Boolean> = _isServiceReady.asStateFlow()
    }
}
