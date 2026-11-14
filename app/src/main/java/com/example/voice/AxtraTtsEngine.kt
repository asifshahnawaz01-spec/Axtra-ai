package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.core.AppPreferences
import com.example.core.AxtraLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class TtsVoiceOption(
    val name: String,
    val localeDisplay: String
)

@Singleton
class AxtraTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isUrduVoiceMissing = MutableStateFlow(false)
    val isUrduVoiceMissing: StateFlow<Boolean> = _isUrduVoiceMissing.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private var onSpeechFinishedCallback: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    configureLanguageAndVoices()
                    attachProgressListener()
                } else {
                    AxtraLogger.e("TtsEngine", "TTS initialization failed: $status")
                }
            }
        } catch (e: Exception) {
            AxtraLogger.e("TtsEngine", "TTS init exception", e)
        }
    }

    private fun configureLanguageAndVoices() {
        val engine = tts ?: return
        val urLocale = Locale.forLanguageTag("ur-PK")
        val availability = engine.isLanguageAvailable(urLocale)
        val missingUrdu = availability == TextToSpeech.LANG_MISSING_DATA ||
            availability == TextToSpeech.LANG_NOT_SUPPORTED
        _isUrduVoiceMissing.value = missingUrdu

        if (!missingUrdu) {
            engine.language = urLocale
        } else {
            engine.language = Locale.US
        }

        val voicesList = runCatching {
            engine.voices?.filter {
                it.locale.language == "ur" || it.locale.language == "en"
            }?.sortedBy { it.locale.language }?.map { v ->
                TtsVoiceOption(
                    name = v.name,
                    localeDisplay = "${v.locale.displayLanguage} (${v.locale.country})"
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())
        _availableVoices.value = voicesList
    }

    private fun attachProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("FINAL_") == true) {
                    _isSpeaking.value = false
                    onSpeechFinishedCallback?.invoke()
                    onSpeechFinishedCallback = null
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onSpeechFinishedCallback = null
            }
        })
    }

    fun speak(
        text: String,
        forceSpeak: Boolean = false,
        onFinished: (() -> Unit)? = null
    ) {
        scope.launch {
            val settings = appPreferences.settingsFlow.first()
            if (settings.silentModeEnabled && !forceSpeak) {
                onFinished?.invoke()
                return@launch
            }
            val engine = tts
            if (!isInitialized || engine == null) {
                onFinished?.invoke()
                return@launch
            }

            engine.setSpeechRate(settings.ttsSpeed.coerceIn(0.5f, 1.5f))
            engine.setPitch(settings.ttsPitch.coerceIn(0.6f, 1.5f))

            if (settings.ttsVoiceName.isNotBlank()) {
                val chosen: Voice? = runCatching {
                    engine.voices?.firstOrNull { it.name == settings.ttsVoiceName }
                }.getOrNull()
                if (chosen != null) {
                    engine.voice = chosen
                }
            } else {
                val containsUrduScript = text.any { it in '\u0600'..'\u06FF' }
                val targetLocale = if (containsUrduScript && !_isUrduVoiceMissing.value) {
                    Locale.forLanguageTag("ur-PK")
                } else {
                    Locale.US
                }
                engine.language = targetLocale
            }

            onSpeechFinishedCallback = onFinished
            val prepared = prepareNaturalChunks(text)
            if (prepared.isEmpty()) {
                onFinished?.invoke()
                return@launch
            }

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.ttsVolume.coerceIn(0f, 1f))
            }

            engine.stop()
            prepared.forEachIndexed { index, chunk ->
                val isLast = index == prepared.lastIndex
                val utteranceId = if (isLast) "FINAL_${UUID.randomUUID()}" else "CHUNK_${UUID.randomUUID()}"
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                engine.speak(chunk, queueMode, params, utteranceId)
                if (!isLast) {
                    engine.playSilentUtterance(160L, TextToSpeech.QUEUE_ADD, "PAUSE_$index")
                }
            }
        }
    }

    fun stopSpeaking() {
        onSpeechFinishedCallback = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
    }

    fun buildInstallTtsDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        isInitialized = false
    }

    private fun prepareNaturalChunks(input: String): List<String> {
        val cleaned = input.trim()
        if (cleaned.isEmpty()) return emptyList()
        // Split at commas, Urdu comma '،', periods, Urdu full stop '۔', or question marks
        return cleaned
            .split(Regex("(?<=[,،.۔?!])\\s*"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
