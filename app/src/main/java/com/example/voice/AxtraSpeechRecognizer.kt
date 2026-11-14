package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.core.AxtraLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class RecognitionSource {
    APP,
    OVERLAY,
    HOTWORD,
    VOICE_INTERACTION
}

sealed class SpeechResultEvent {
    abstract val source: RecognitionSource

    data class Success(
        val text: String,
        val confidence: Float,
        override val source: RecognitionSource = RecognitionSource.APP
    ) : SpeechResultEvent()

    data class LowConfidence(
        val rawText: String,
        override val source: RecognitionSource = RecognitionSource.APP
    ) : SpeechResultEvent()

    data class Error(
        val code: Int,
        val messageUr: String,
        val messageEn: String,
        override val source: RecognitionSource = RecognitionSource.APP
    ) : SpeechResultEvent()
}

@Singleton
class AxtraSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    @Volatile
    private var activeSource: RecognitionSource = RecognitionSource.APP

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _noisyWarning = MutableStateFlow(false)
    val noisyWarning: StateFlow<Boolean> = _noisyWarning.asStateFlow()

    private val _events = MutableSharedFlow<SpeechResultEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SpeechResultEvent> = _events.asSharedFlow()

    private var highNoiseCounter = 0

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening(
        languageTag: String = "ur-PK",
        silenceTimeoutMs: Int = 1500,
        source: RecognitionSource = RecognitionSource.APP
    ) {
        activeSource = source
        if (!hasRecordPermission()) {
            _events.tryEmit(
                SpeechResultEvent.Error(
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                    "مائیکروفون کی اجازت نہیں دی گئی۔",
                    "Microphone permission not granted.",
                    source
                )
            )
            return
        }

        mainHandler.post {
            try {
                destroyInternal()
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _events.tryEmit(
                        SpeechResultEvent.Error(
                            SpeechRecognizer.ERROR_CLIENT,
                            "اس ڈیوائس پر اسپیچ ریکگنیشن دستیاب نہیں ہے۔",
                            "Speech recognition is unavailable on this device.",
                            source
                        )
                    )
                    return@post
                }

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer
                highNoiseCounter = 0
                _noisyWarning.value = false
                _partialText.value = ""

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _rmsLevel.value = normalized
                        if (rmsdB > 8.5f && _partialText.value.isBlank()) {
                            highNoiseCounter++
                            if (highNoiseCounter > 12) {
                                _noisyWarning.value = true
                            }
                        } else {
                            highNoiseCounter = 0
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        val (ur, en) = mapError(error)
                        _events.tryEmit(SpeechResultEvent.Error(error, ur, en, source))
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        val best = matches?.firstOrNull()?.trim().orEmpty()
                        val confidence = scores?.firstOrNull() ?: 0.85f

                        if (best.isBlank() || (confidence in 0.01f..0.32f && best.length < 3)) {
                            _events.tryEmit(SpeechResultEvent.LowConfidence(best, source))
                        } else {
                            _partialText.value = best
                            _events.tryEmit(SpeechResultEvent.Success(best, confidence, source))
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (text.isNotBlank()) {
                            _partialText.value = text
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        silenceTimeoutMs.toLong()
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        silenceTimeoutMs.toLong()
                    )
                }
                recognizer.startListening(intent)
            } catch (e: Exception) {
                AxtraLogger.e("SpeechRecognizer", "Failed starting recognizer", e)
                _isListening.value = false
                _events.tryEmit(
                    SpeechResultEvent.Error(
                        SpeechRecognizer.ERROR_CLIENT,
                        "آواز سننے میں خرابی آئی۔ دوبارہ کوشش کریں۔",
                        "Failed to start speech recognizer.",
                        source
                    )
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            _isListening.value = false
            _rmsLevel.value = 0f
        }
    }

    fun cancelAndRelease() {
        mainHandler.post {
            destroyInternal()
        }
    }

    private fun destroyInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        _isListening.value = false
        _rmsLevel.value = 0f
    }

    private fun mapError(error: Int): Pair<String, String> = when (error) {
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "مائیکروفون کسی اور ایپ میں مصروف ہے۔ براہ کرم دوبارہ کوشش کریں۔" to
                "Microphone is busy in another app."
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "دوبارہ بولیے، آپ کی آواز واضح نہیں آئی۔" to
                "Didn't catch that. Please speak again."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "اسپیچ ریکگنیشن کے لیے نیٹ ورک مسئلہ ہے۔" to
                "Network error during speech recognition."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "مائیکروفون کی اجازت درکار ہے۔" to
                "Microphone permission is required."
        SpeechRecognizer.ERROR_AUDIO ->
            "آڈیو ریکارڈنگ میں خرابی آئی۔" to
                "Audio recording error."
        else ->
            "دوبارہ بولیے، آواز سمجھنے میں مسئلہ آیا۔" to
                "Speech recognition error ($error)."
    }
}
