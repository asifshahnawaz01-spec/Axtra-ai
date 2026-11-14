package com.example.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.core.AxtraLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Self-contained on-device Voice Activity + Wake Word acoustic spotter.
 * Uses AudioRecord RMS + Zero-Crossing Rate (ZCR) offline speech-burst detection,
 * then yields the microphone cleanly so SpeechRecognizer can verify "Hey Axtra" / "Axtra".
 */
@Singleton
class WakeWordDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _currentEnergy = MutableStateFlow(0f)
    val currentEnergy: StateFlow<Float> = _currentEnergy.asStateFlow()

    @Volatile
    private var sensitivity: Float = 0.65f

    fun updateSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.1f, 1.0f)
    }

    fun startMonitoring(onWakeTriggered: () -> Unit) {
        if (_isMonitoring.value) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        monitorJob = scope.launch {
            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var audioRecord: AudioRecord? = null
            try {
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer
                )
                audioRecord = recorder
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    AxtraLogger.w("WakeWordDetector", "AudioRecord failed to initialize")
                    return@launch
                }

                recorder.startRecording()
                _isMonitoring.value = true

                val buffer = ShortArray(minBuffer / 2)
                var consecutiveVoiceFrames = 0
                var lastTriggerTime = 0L

                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val (rms, zcr) = analyzeFrame(buffer, read)
                        val normalized = (rms / 6000f).coerceIn(0f, 1f)
                        _currentEnergy.value = normalized

                        // Higher sensitivity lowers the required RMS threshold
                        val rmsThreshold = 3400f - (sensitivity * 2400f)
                        val isSpeechBand = rms > rmsThreshold && zcr in 0.03f..0.38f

                        if (isSpeechBand) {
                            consecutiveVoiceFrames++
                        } else {
                            consecutiveVoiceFrames = (consecutiveVoiceFrames - 1).coerceAtLeast(0)
                        }

                        val now = System.currentTimeMillis()
                        if (consecutiveVoiceFrames >= 3 && now - lastTriggerTime > 2500L) {
                            lastTriggerTime = now
                            consecutiveVoiceFrames = 0
                            // Release AudioRecord immediately before handing off mic to SpeechRecognizer
                            try {
                                recorder.stop()
                                recorder.release()
                            } catch (_: Exception) {
                            }
                            audioRecord = null
                            _isMonitoring.value = false
                            onWakeTriggered()
                            break
                        }
                    } else {
                        delay(40L)
                    }
                }
            } catch (e: Exception) {
                AxtraLogger.e("WakeWordDetector", "Monitor loop error", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {
                }
                _isMonitoring.value = false
                _currentEnergy.value = 0f
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _isMonitoring.value = false
        _currentEnergy.value = 0f
    }

    fun matchesWakePhrase(
        spokenText: String,
        customCsv: String = com.example.core.AxtraConstants.DEFAULT_WAKE_WORDS_CSV
    ): Boolean = matchWakePhraseAndExtractCommand(spokenText, customCsv).matched

    fun matchWakePhraseAndExtractCommand(
        spokenText: String,
        customCsv: String = com.example.core.AxtraConstants.DEFAULT_WAKE_WORDS_CSV
    ): WakeMatchResult = Companion.matchWakePhraseAndExtractCommand(spokenText, customCsv)

    fun playDingSound() {
        scope.launch {
            try {
                val sampleRate = 22050
                val durationMs = 140
                val count = sampleRate * durationMs / 1000
                val samples = ShortArray(count)
                val freq = 880.0
                for (i in 0 until count) {
                    val env = 1.0 - (i.toDouble() / count)
                    samples[i] = (sin(2.0 * PI * i * freq / sampleRate) * Short.MAX_VALUE * 0.25 * env)
                        .toInt()
                        .toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                delay(180L)
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun analyzeFrame(buffer: ShortArray, count: Int): Pair<Float, Float> {
        var sumSq = 0.0
        var zeroCrossings = 0
        for (i in 0 until count) {
            val sample = buffer[i].toDouble()
            sumSq += sample * sample
            if (i > 0 && ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0))) {
                zeroCrossings++
            }
        }
        val rms = sqrt(sumSq / count.coerceAtLeast(1)).toFloat()
        val zcr = zeroCrossings.toFloat() / count.coerceAtLeast(1)
        return rms to zcr
    }

    companion object {
        fun estimatedBatteryImpactPercentPerHour(sensitivity: Float): Int {
            return (2 + (sensitivity * 4)).toInt()
        }

        fun matchWakePhraseAndExtractCommand(
            spokenText: String,
            customCsv: String = com.example.core.AxtraConstants.DEFAULT_WAKE_WORDS_CSV
        ): WakeMatchResult {
            val cleaned = spokenText.trim()
            if (cleaned.isBlank()) return WakeMatchResult(false, "", "")
            val normalized = cleaned.lowercase()
                .replace(Regex("[،,.!?؟:;]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val candidates = buildCandidatePhrases(customCsv)
            for (phrase in candidates) {
                val escaped = Regex.escape(phrase)
                val boundaryRegex = Regex("(^|\\s)$escaped(\\s|$)")
                val match = boundaryRegex.find(normalized)
                if (match != null) {
                    val after = normalized.substring(match.range.last + 1).trim()
                    return WakeMatchResult(
                        matched = true,
                        matchedPhrase = phrase,
                        trailingCommand = after
                    )
                }
            }
            return WakeMatchResult(false, "", "")
        }

        fun buildCandidatePhrases(customCsv: String): List<String> {
            val userPhrases = customCsv.split(',', ';', '\n')
                .map { it.trim().lowercase().replace(Regex("\\s+"), " ") }
                .filter { it.length >= 3 }

            val builtInDefaults = listOf(
                "hey astra",
                "hello astra",
                "astra",
                "hey axtra",
                "hello axtra",
                "axtra",
                "hey extra",
                "hello extra",
                "ایسٹرا",
                "ایکسٹرا",
                "ہیلو ایسٹرا",
                "ہے ایسٹرا"
            )
            return (userPhrases + builtInDefaults)
                .distinct()
                .sortedByDescending { it.length }
        }
    }
}

data class WakeMatchResult(
    val matched: Boolean,
    val matchedPhrase: String,
    val trailingCommand: String
)
