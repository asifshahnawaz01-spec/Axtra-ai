package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessibility.AxtraAccessibilityService
import com.example.actions.ActionExecutor
import com.example.actions.ExecutionUiState
import com.example.ai.AiBrainRepository
import com.example.ai.AxtraIntent
import com.example.core.AppPreferences
import com.example.core.AxtraSettingsState
import com.example.core.OrbState
import com.example.data.repository.BlockedAppsRepository
import com.example.data.repository.RoutineRepository
import com.example.service.AxtraForegroundService
import com.example.voice.AxtraSpeechRecognizer
import com.example.voice.AxtraTtsEngine
import com.example.voice.RecognitionSource
import com.example.voice.SpeechResultEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeErrorState(
    val messageUr: String,
    val messageEn: String,
    val retryCommand: String? = null,
    val isTtsMissing: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val aiBrainRepository: AiBrainRepository,
    private val speechRecognizer: AxtraSpeechRecognizer,
    private val ttsEngine: AxtraTtsEngine,
    private val actionExecutor: ActionExecutor,
    private val routineRepository: RoutineRepository,
    private val blockedAppsRepository: BlockedAppsRepository
) : ViewModel() {

    val settings: StateFlow<AxtraSettingsState> = appPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AxtraSettingsState())

    val executionState: StateFlow<ExecutionUiState> = actionExecutor.executionState
    val partialTranscript: StateFlow<String> = speechRecognizer.partialText
    val rmsLevel: StateFlow<Float> = speechRecognizer.rmsLevel
    val noisyWarning: StateFlow<Boolean> = speechRecognizer.noisyWarning
    val isUrduVoiceMissing: StateFlow<Boolean> = ttsEngine.isUrduVoiceMissing
    val isServiceRunning: StateFlow<Boolean> = AxtraForegroundService.isServiceRunning

    private val _orbState = MutableStateFlow(OrbState.IDLE)
    val orbState: StateFlow<OrbState> = _orbState.asStateFlow()

    private val _lastAnswer = MutableStateFlow("السلام علیکم! میں ایکسٹرا ہوں، آپ کیسے مدد کر سکتی ہوں؟")
    val lastAnswer: StateFlow<String> = _lastAnswer.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _errorState = MutableStateFlow<HomeErrorState?>(null)
    val errorState: StateFlow<HomeErrorState?> = _errorState.asStateFlow()

    private val _batteryPercent = MutableStateFlow(100)
    val batteryPercent: StateFlow<Int> = _batteryPercent.asStateFlow()

    private var followUpJob: Job? = null

    init {
        viewModelScope.launch {
            routineRepository.ensureBuiltInRoutines()
            blockedAppsRepository.ensureDefaults()
        }
        refreshStatusMetrics()
        observeVoiceEvents()
    }

    fun refreshStatusMetrics() {
        _isOfflineMode.value = !aiBrainRepository.isNetworkAvailable()
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level >= 0 && scale > 0) {
            _batteryPercent.value = (level * 100) / scale
        }
    }

    private fun observeVoiceEvents() {
        viewModelScope.launch {
            speechRecognizer.isListening.collectLatest { listening ->
                if (listening) _orbState.value = OrbState.LISTENING
                else if (_orbState.value == OrbState.LISTENING) _orbState.value = OrbState.IDLE
            }
        }
        viewModelScope.launch {
            ttsEngine.isSpeaking.collectLatest { speaking ->
                if (speaking) _orbState.value = OrbState.SPEAKING
                else if (_orbState.value == OrbState.SPEAKING) _orbState.value = OrbState.IDLE
            }
        }
        viewModelScope.launch {
            speechRecognizer.events.collectLatest { event ->
                if (event.source != RecognitionSource.APP) return@collectLatest
                when (event) {
                    is SpeechResultEvent.Success -> {
                        val spoken = event.text
                        // Check if waiting for confirmation
                        if (executionState.value.confirmation != null) {
                            val lower = spoken.lowercase()
                            if (lower.contains("haan") || lower.contains("yes") || lower.contains("ji") || lower.contains("ہاں")) {
                                actionExecutor.resolveConfirmation(true)
                                return@collectLatest
                            } else if (lower.contains("nahi") || lower.contains("no") || lower.contains("cancel") || lower.contains("نہیں")) {
                                actionExecutor.resolveConfirmation(false)
                                return@collectLatest
                            }
                        }
                        submitCommand(spoken)
                    }
                    is SpeechResultEvent.LowConfidence -> {
                        _orbState.value = OrbState.IDLE
                        val prompt = "دوبارہ بولیے، آپ کی آواز واضح نہیں آئی۔"
                        _lastAnswer.value = prompt
                        ttsEngine.speak(prompt)
                    }
                    is SpeechResultEvent.Error -> {
                        _orbState.value = OrbState.ERROR
                        _errorState.value = HomeErrorState(
                            messageUr = event.messageUr,
                            messageEn = event.messageEn
                        )
                    }
                }
            }
        }
    }

    fun toggleMicListening() {
        followUpJob?.cancel()
        _errorState.value = null

        // Barge-in: stop speaking immediately and listen
        if (ttsEngine.isSpeaking.value) {
            ttsEngine.stopSpeaking()
        }

        if (speechRecognizer.isListening.value) {
            speechRecognizer.stopListening()
            _orbState.value = OrbState.IDLE
        } else {
            viewModelScope.launch {
                val cur = appPreferences.settingsFlow.first()
                speechRecognizer.startListening(
                    languageTag = cur.sttLanguagePrimary,
                    silenceTimeoutMs = cur.silenceTimeoutMs,
                    source = RecognitionSource.APP
                )
            }
        }
    }

    fun submitCommand(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        followUpJob?.cancel()
        _errorState.value = null
        refreshStatusMetrics()

        viewModelScope.launch {
            _orbState.value = OrbState.THINKING

            // Optional screen context if user asks about screen
            val wantsScreen = clean.lowercase().let {
                it.contains("screen") || it.contains("اسکرین") || it.contains("isko translate")
            }
            val screenText = if (wantsScreen && settings.value.screenAssistantEnabled) {
                AxtraAccessibilityService.instance?.captureScreenText()?.getOrNull()
            } else null

            val response = aiBrainRepository.processCommand(clean, screenText)
            _isOfflineMode.value = response.isOffline || !aiBrainRepository.isNetworkAvailable()

            if (response.rawError == "QUOTA_429") {
                _errorState.value = HomeErrorState(
                    messageUr = "جیمنائی مفت کوٹہ پورا ہو گیا ہے، آف لائن موڈ یا دوبارہ کوشش کریں۔",
                    messageEn = "Gemini free quota exceeded. Retrying or using offline parser.",
                    retryCommand = clean
                )
            } else if (response.rawError == "APP_CHECK_403") {
                _errorState.value = HomeErrorState(
                    messageUr = "فائر بیس ایپ چیک تصدیق ناکام ہوئی۔ Settings > AI میں Gemini ٹیسٹ کریں۔",
                    messageEn = "Firebase App Check attestation error. Test Gemini AI in Settings.",
                    retryCommand = clean
                )
            }

            actionExecutor.executeAction(clean, response) { finalReply, success ->
                _lastAnswer.value = finalReply
                _orbState.value = if (success) OrbState.SPEAKING else OrbState.ERROR
                ttsEngine.speak(finalReply) {
                    viewModelScope.launch {
                        _orbState.value = OrbState.IDLE
                        val cur = appPreferences.settingsFlow.first()
                        if (cur.conversationModeEnabled && success && response.intent != AxtraIntent.UNKNOWN) {
                            startFollowUpWindow(cur.sttLanguagePrimary, cur.silenceTimeoutMs)
                        }
                    }
                }
            }
        }
    }

    private fun startFollowUpWindow(lang: String, silenceTimeoutMs: Int) {
        followUpJob?.cancel()
        followUpJob = viewModelScope.launch {
            delay(400L)
            speechRecognizer.startListening(lang, silenceTimeoutMs)
            delay(8000L)
            if (speechRecognizer.isListening.value && speechRecognizer.partialText.value.isBlank()) {
                speechRecognizer.stopListening()
            }
        }
    }

    fun resolveConfirmation(confirmed: Boolean) {
        actionExecutor.resolveConfirmation(confirmed)
    }

    fun stopEverything() {
        followUpJob?.cancel()
        speechRecognizer.cancelAndRelease()
        actionExecutor.stopEverything()
        _orbState.value = OrbState.IDLE
        _lastAnswer.value = "تمام کارروائیاں روک دی گئی ہیں۔"
    }

    fun toggleAlwaysOn(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.updateAlwaysOnEnabled(enabled)
            val svc = Intent(context, AxtraForegroundService::class.java)
            if (enabled) {
                ContextCompat.startForegroundService(context, svc)
            } else {
                context.stopService(svc)
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            appPreferences.updateOnboardingCompleted(true)
        }
    }

    fun installUrduTtsData() {
        runCatching { context.startActivity(ttsEngine.buildInstallTtsDataIntent()) }
    }

    fun dismissError() {
        _errorState.value = null
        if (_orbState.value == OrbState.ERROR) _orbState.value = OrbState.IDLE
    }
}
