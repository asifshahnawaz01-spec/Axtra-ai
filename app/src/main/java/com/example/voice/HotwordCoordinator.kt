package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.core.AppPreferences
import com.example.core.AxtraConstants
import com.example.core.AxtraLogger
import com.example.overlay.OverlayService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class HotwordStatus(val labelEn: String, val labelUr: String) {
    INACTIVE("Inactive", "بند"),
    MONITORING_ACOUSTIC("Listening for 'Hey Astra' / 'Astra'", "'Hey Astra' یا 'Astra' سن رہی ہے"),
    VERIFYING_HOTWORD("Checking wake word...", "ویک ورڈ کی تصدیق ہو رہی ہے..."),
    TRIGGERED("Wake word detected!", "ویک ورڈ مل گیا!"),
    SESSION_ACTIVE("Assistant session active", "اسسٹنٹ سیشن فعال ہے"),
    PAUSED("Paused (Call / Low Battery / Screen)", "عارضی طور پر رکا ہوا"),
    PERMISSION_MISSING("Microphone permission needed", "مائیکروفون اجازت درکار ہے")
}

@Singleton
class HotwordCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val wakeWordDetector: WakeWordDetector,
    private val speechRecognizer: AxtraSpeechRecognizer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val activeOwners = mutableSetOf<String>()

    private val _status = MutableStateFlow(HotwordStatus.INACTIVE)
    val status: StateFlow<HotwordStatus> = _status.asStateFlow()

    private val _lastDetectedPhrase = MutableStateFlow<String?>(null)
    val lastDetectedPhrase: StateFlow<String?> = _lastDetectedPhrase.asStateFlow()

    private val _lastHeardCandidate = MutableStateFlow<String?>(null)
    val lastHeardCandidate: StateFlow<String?> = _lastHeardCandidate.asStateFlow()

    private var verifyTimeoutJob: Job? = null
    private var restartJob: Job? = null

    @Volatile
    private var isPausedBySystem = false

    @Volatile
    private var isSessionBusy = false

    init {
        observeHotwordVerificationEvents()
    }

    fun start(owner: String) {
        synchronized(activeOwners) {
            activeOwners.add(owner)
        }
        ensureMonitoringLoop()
    }

    fun stop(owner: String) {
        val empty = synchronized(activeOwners) {
            activeOwners.remove(owner)
            activeOwners.isEmpty()
        }
        if (empty) {
            verifyTimeoutJob?.cancel()
            restartJob?.cancel()
            wakeWordDetector.stopMonitoring()
            _status.value = HotwordStatus.INACTIVE
        }
    }

    fun setSystemPaused(paused: Boolean) {
        isPausedBySystem = paused
        if (paused) {
            verifyTimeoutJob?.cancel()
            restartJob?.cancel()
            wakeWordDetector.stopMonitoring()
            if (hasActiveOwners()) {
                _status.value = HotwordStatus.PAUSED
            }
        } else if (hasActiveOwners() && !isSessionBusy) {
            ensureMonitoringLoop()
        }
    }

    fun setSessionActive(active: Boolean) {
        isSessionBusy = active
        if (active) {
            verifyTimeoutJob?.cancel()
            restartJob?.cancel()
            wakeWordDetector.stopMonitoring()
            _status.value = HotwordStatus.SESSION_ACTIVE
        } else if (hasActiveOwners() && !isPausedBySystem) {
            scheduleRestart(500L)
        }
    }

    fun ensureMonitoringLoop() {
        scope.launch {
            if (!hasActiveOwners()) {
                _status.value = HotwordStatus.INACTIVE
                return@launch
            }
            val settings = appPreferences.settingsFlow.first()
            if (!settings.wakeWordEnabled) {
                wakeWordDetector.stopMonitoring()
                _status.value = HotwordStatus.INACTIVE
                return@launch
            }
            if (isSessionBusy || speechRecognizer.isListening.value) {
                _status.value = HotwordStatus.SESSION_ACTIVE
                return@launch
            }
            if (isPausedBySystem || shouldAutoPause(settings.pauseOnLowBattery, settings.screenOnOnly)) {
                wakeWordDetector.stopMonitoring()
                _status.value = HotwordStatus.PAUSED
                return@launch
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                _status.value = HotwordStatus.PERMISSION_MISSING
                return@launch
            }

            if (wakeWordDetector.isMonitoring.value) return@launch

            wakeWordDetector.updateSensitivity(settings.wakeWordSensitivity)
            _status.value = HotwordStatus.MONITORING_ACOUSTIC

            wakeWordDetector.startMonitoring {
                scope.launch {
                    beginWakePhraseVerification()
                }
            }
        }
    }

    private suspend fun beginWakePhraseVerification() {
        if (!hasActiveOwners() || isSessionBusy || isPausedBySystem) return
        val settings = appPreferences.settingsFlow.first()
        if (!settings.wakeWordEnabled) {
            _status.value = HotwordStatus.INACTIVE
            return
        }
        if (speechRecognizer.isListening.value) {
            scheduleRestart(1200L)
            return
        }

        _status.value = HotwordStatus.VERIFYING_HOTWORD
        speechRecognizer.startListening(
            languageTag = "en-US",
            silenceTimeoutMs = 1300,
            source = RecognitionSource.HOTWORD
        )

        verifyTimeoutJob?.cancel()
        verifyTimeoutJob = scope.launch {
            delay(3600L)
            if (_status.value == HotwordStatus.VERIFYING_HOTWORD) {
                speechRecognizer.stopListening()
                delay(250L)
                if (_status.value == HotwordStatus.VERIFYING_HOTWORD) {
                    scheduleRestart(350L)
                }
            }
        }
    }

    private fun observeHotwordVerificationEvents() {
        scope.launch {
            speechRecognizer.events.collectLatest { event ->
                if (event.source != RecognitionSource.HOTWORD) return@collectLatest
                verifyTimeoutJob?.cancel()

                when (event) {
                    is SpeechResultEvent.Success -> {
                        val transcript = event.text
                        _lastHeardCandidate.value = transcript
                        val settings = appPreferences.settingsFlow.first()
                        val match = wakeWordDetector.matchWakePhraseAndExtractCommand(
                            spokenText = transcript,
                            customCsv = settings.customWakeWordsCsv
                        )
                        if (match.matched) {
                            AxtraLogger.d(TAG, "Hotword matched '${match.matchedPhrase}', trailing='${match.trailingCommand}'")
                            dispatchVerifiedHotword(match.matchedPhrase, match.trailingCommand)
                        } else {
                            AxtraLogger.d(TAG, "Speech ignored (not wake word): $transcript")
                            scheduleRestart(350L)
                        }
                    }
                    is SpeechResultEvent.LowConfidence -> {
                        val settings = appPreferences.settingsFlow.first()
                        val match = wakeWordDetector.matchWakePhraseAndExtractCommand(
                            spokenText = event.rawText,
                            customCsv = settings.customWakeWordsCsv
                        )
                        if (match.matched) {
                            dispatchVerifiedHotword(match.matchedPhrase, match.trailingCommand)
                        } else {
                            scheduleRestart(400L)
                        }
                    }
                    is SpeechResultEvent.Error -> {
                        val backoff = if (event.code == android.speech.SpeechRecognizer.ERROR_CLIENT) 3500L else 450L
                        if (event.code == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            _status.value = HotwordStatus.PERMISSION_MISSING
                        } else {
                            scheduleRestart(backoff)
                        }
                    }
                }
            }
        }
    }

    fun triggerManualTestSession() {
        dispatchVerifiedHotword("astra", "")
    }

    private fun dispatchVerifiedHotword(matchedPhrase: String, trailingCommand: String) {
        _lastDetectedPhrase.value = matchedPhrase
        _status.value = HotwordStatus.TRIGGERED
        isSessionBusy = true
        wakeWordDetector.playDingSound()

        val voiceService = AxtraVoiceInteractionService.instance
        if (voiceService != null) {
            voiceService.showHotwordSession(trailingCommand.ifBlank { null })
            return
        }

        if (Settings.canDrawOverlays(context)) {
            val overlayIntent = Intent(context, OverlayService::class.java).apply {
                action = AxtraConstants.ACTION_OVERLAY_START_LISTENING
                if (trailingCommand.isNotBlank()) {
                    putExtra(AxtraConstants.EXTRA_INITIAL_COMMAND, trailingCommand)
                }
            }
            runCatching { context.startService(overlayIntent) }
                .onFailure {
                    isSessionBusy = false
                    scheduleRestart(1000L)
                }
        } else {
            val talkIntent = Intent(context, MainActivity::class.java).apply {
                action = AxtraConstants.ACTION_TALK_TO_AXTRA
                if (trailingCommand.isNotBlank()) {
                    putExtra(AxtraConstants.EXTRA_INITIAL_COMMAND, trailingCommand)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            runCatching { context.startActivity(talkIntent) }
            // Resume hotword monitoring after foreground app handles the session
            scheduleRestart(4500L)
            isSessionBusy = false
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMs)
            isSessionBusy = false
            ensureMonitoringLoop()
        }
    }

    private fun hasActiveOwners(): Boolean = synchronized(activeOwners) { activeOwners.isNotEmpty() }

    private fun shouldAutoPause(pauseOnLowBattery: Boolean, screenOnOnly: Boolean): Boolean {
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val am = context.getSystemService(AudioManager::class.java)
        val inCall = am?.mode == AudioManager.MODE_IN_CALL || am?.mode == AudioManager.MODE_IN_COMMUNICATION
        val pm = context.getSystemService(PowerManager::class.java)
        val screenOff = pm?.isInteractive == false
        return (pauseOnLowBattery && pct in 1..9) || inCall || (screenOnOnly && screenOff)
    }

    private companion object {
        const val TAG = "HotwordCoordinator"
    }
}
