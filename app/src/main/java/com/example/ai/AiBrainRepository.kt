package com.example.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.core.AppPreferences
import com.example.core.AxtraConstants
import com.example.core.AxtraLogger
import com.example.core.AxtraSettingsState
import com.example.core.LanguageMode
import com.example.data.repository.MemoryRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApiTestResult {
    data class Success(val message: String, val latencyMs: Long) : ApiTestResult()
    data class Failure(val errorEn: String, val errorUr: String) : ApiTestResult()
}

@Singleton
class AiBrainRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val memoryRepository: MemoryRepository,
    private val jsonValidator: JsonValidator,
    private val offlineParser: OfflineCommandParser
) {
    private val shortTermHistory = ArrayDeque<ChatTurn>(20)

    // Lazily initialized default Gemini model for quick health checks / default plain queries
    private val defaultModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(modelName = AxtraConstants.DEFAULT_GEMINI_MODEL)
    }

    private val axtraActionSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "intent" to Schema.enumeration(
                    values = AxtraIntent.entries.map { it.name },
                    description = "Classified Axtra user command intent"
                ),
                "app" to Schema.string(description = "Target app name or package, or empty string"),
                "target" to Schema.string(description = "Target contact, query, place, time, note, or STOP_ALL"),
                "parameters" to Schema.obj(
                    properties = mapOf(
                        "message" to Schema.string(),
                        "text" to Schema.string(),
                        "query" to Schema.string(),
                        "setting" to Schema.string(),
                        "state" to Schema.string(),
                        "control" to Schema.string(),
                        "hour" to Schema.string(),
                        "minute" to Schema.string(),
                        "seconds" to Schema.string(),
                        "mode" to Schema.string(),
                        "type" to Schema.string(),
                        "amount" to Schema.string(),
                        "category" to Schema.string(),
                        "action" to Schema.string()
                    ),
                    optionalProperties = listOf(
                        "message", "text", "query", "setting", "state", "control",
                        "hour", "minute", "seconds", "mode", "type", "amount", "category", "action"
                    ),
                    description = "Optional key-value action parameters"
                ),
                "needs_confirmation" to Schema.boolean(description = "Whether risky action needs confirmation"),
                "spoken_reply" to Schema.string(description = "Short polite spoken reply matching user language")
            )
        )
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun clearShortTermHistory() {
        shortTermHistory.clear()
    }

    private fun normalizeModelName(model: String): String = when (model) {
        "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon" -> model
        "gemini-2.5-flash-lite" -> "gemini-3.5-flash-lite"
        else -> AxtraConstants.DEFAULT_GEMINI_MODEL
    }

    private fun buildStructuredModel(
        modelName: String,
        temperatureValue: Float,
        maxTokensValue: Int,
        systemInstructionText: String
    ): GenerativeModel {
        val config = generationConfig {
            temperature = temperatureValue
            maxOutputTokens = maxTokensValue
            responseMimeType = "application/json"
            responseSchema = axtraActionSchema
        }
        return Firebase.ai.generativeModel(
            modelName = normalizeModelName(modelName),
            generationConfig = config,
            systemInstruction = content { text(systemInstructionText) }
        )
    }

    private fun buildPlainModel(
        modelName: String,
        temperatureValue: Float,
        maxTokensValue: Int,
        systemInstructionText: String? = null
    ): GenerativeModel {
        val config = generationConfig {
            temperature = temperatureValue
            maxOutputTokens = maxTokensValue
        }
        return Firebase.ai.generativeModel(
            modelName = normalizeModelName(modelName),
            generationConfig = config,
            systemInstruction = systemInstructionText?.let { text -> content { text(text) } }
        )
    }

    suspend fun processCommand(userText: String, screenContext: String? = null): AiActionResponse {
        val settings = appPreferences.settingsFlow.first()

        if (!isNetworkAvailable()) {
            val offline = offlineParser.parse(userText)
            return offline.copy(rawError = "NO_INTERNET")
        }

        val systemInstruction = buildSystemPrompt(settings)
        val promptInput = if (!screenContext.isNullOrBlank()) {
            "User command: $userText\n\n[Active Screen Text (user requested)]:\n${screenContext.take(3500)}"
        } else {
            userText
        }

        val historyContents = shortTermHistory.map { turn ->
            content(role = turn.role) { text(turn.text) }
        }

        val structuredModel = buildStructuredModel(
            modelName = settings.geminiModel,
            temperatureValue = settings.temperature,
            maxTokensValue = settings.maxTokens,
            systemInstructionText = systemInstruction
        )

        val responseResult = callFirebaseAiWithRetry(
            model = structuredModel,
            history = historyContents,
            prompt = promptInput
        )

        return responseResult.fold(
            onSuccess = { rawText ->
                when (val validated = jsonValidator.parseAndValidate(rawText)) {
                    is JsonValidationResult.Valid -> {
                        recordTurn(userText, validated.response.spokenReply)
                        validated.response
                    }
                    is JsonValidationResult.Invalid -> {
                        // One repair retry asking Gemini to fix invalid JSON
                        val repairModel = buildStructuredModel(
                            modelName = settings.geminiModel,
                            temperatureValue = 0.1f,
                            maxTokensValue = settings.maxTokens,
                            systemInstructionText = systemInstruction
                        )
                        val repairPrompt = "Fix this output into valid JSON strictly matching the Axtra schema " +
                            "(intent, app, target, parameters, needs_confirmation, spoken_reply). " +
                            "Reason: ${validated.reason}\n\nBroken output:\n$rawText"
                        val repaired = callFirebaseAiWithRetry(repairModel, emptyList(), repairPrompt).getOrNull()
                        val secondCheck = repaired?.let { jsonValidator.parseAndValidate(it) }
                        if (secondCheck is JsonValidationResult.Valid) {
                            recordTurn(userText, secondCheck.response.spokenReply)
                            secondCheck.response
                        } else {
                            val cleanReply = rawText.replace(Regex("[{}\"`]"), "").trim()
                                .ifBlank { "معذرت، میں سمجھ نہیں سکی۔ براہ کرم دوبارہ کہیں۔" }
                            recordTurn(userText, cleanReply)
                            AiActionResponse(
                                intent = AxtraIntent.CHAT,
                                spokenReply = cleanReply
                            )
                        }
                    }
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Firebase AI processCommand failed", error)
                val msg = error.message.orEmpty()
                if (msg.contains("429") || msg.contains("QUOTA", ignoreCase = true) || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                    val offline = offlineParser.parse(userText)
                    if (offline.intent != AxtraIntent.UNKNOWN) {
                        offline.copy(rawError = "QUOTA_429")
                    } else {
                        AiActionResponse(
                            intent = AxtraIntent.UNKNOWN,
                            spokenReply = "جیمنائی کا کوٹہ عارضی طور پر پورا ہو گیا ہے۔ تھوڑی دیر بعد کوشش کریں یا آف لائن کمانڈ استعمال کریں۔",
                            isOffline = true,
                            rawError = "QUOTA_429"
                        )
                    }
                } else if (msg.contains("403") || msg.contains("APP_CHECK", ignoreCase = true)) {
                    val offline = offlineParser.parse(userText)
                    if (offline.intent != AxtraIntent.UNKNOWN) {
                        offline.copy(rawError = "APP_CHECK_403")
                    } else {
                        AiActionResponse(
                            intent = AxtraIntent.UNKNOWN,
                            spokenReply = "فائر بیس ایپ چیک کی تصدیق میں مسئلہ ہے (${msg.take(90)})۔",
                            isOffline = true,
                            rawError = "APP_CHECK_403"
                        )
                    }
                } else {
                    val offline = offlineParser.parse(userText)
                    offline.copy(rawError = msg.ifBlank { "NETWORK_ERROR" })
                }
            }
        )
    }

    suspend fun askPlainGemini(prompt: String, systemPromptOverride: String? = null): Result<String> {
        if (!isNetworkAvailable()) {
            return Result.failure(IllegalStateException("انٹرنیٹ دستیاب نہیں ہے۔"))
        }
        val settings = appPreferences.settingsFlow.first()
        val model = buildPlainModel(
            modelName = settings.geminiModel,
            temperatureValue = settings.temperature,
            maxTokensValue = settings.maxTokens,
            systemInstructionText = systemPromptOverride
        )
        return callFirebaseAiWithRetry(model, emptyList(), prompt)
    }

    fun streamPlainGemini(prompt: String, systemPromptOverride: String? = null): Flow<String> = flow {
        if (!isNetworkAvailable()) {
            throw IllegalStateException("انٹرنیٹ دستیاب نہیں ہے۔")
        }
        val settings = appPreferences.settingsFlow.first()
        val model = buildPlainModel(
            modelName = settings.geminiModel,
            temperatureValue = settings.temperature,
            maxTokensValue = settings.maxTokens,
            systemInstructionText = systemPromptOverride
        )
        withTimeout(60_000L) {
            model.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) emit(text)
            }
        }
    }

    suspend fun testGeminiConnection(modelOverride: String? = null): ApiTestResult {
        if (!isNetworkAvailable()) {
            return ApiTestResult.Failure(
                errorEn = "No internet connection.",
                errorUr = "انٹرنیٹ کنکشن موجود نہیں ہے۔"
            )
        }
        val settings = appPreferences.settingsFlow.first()
        val modelName = normalizeModelName(modelOverride ?: settings.geminiModel)
        val start = System.currentTimeMillis()
        return try {
            val model = if (modelName == AxtraConstants.DEFAULT_GEMINI_MODEL) {
                defaultModel
            } else {
                Firebase.ai.generativeModel(modelName = modelName)
            }
            val response = withTimeout(30_000L) {
                model.generateContent("Reply with only: OK")
            }
            val latency = System.currentTimeMillis() - start
            val reply = response.text.orEmpty().trim()
            ApiTestResult.Success("Connected via Firebase AI Logic ($modelName): ${reply.ifBlank { "OK" }}", latency)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI test timed out", e)
            ApiTestResult.Failure(
                errorEn = "Timed out after 30s.",
                errorUr = "رابطہ وقت ختم ہو گیا۔ دوبارہ کوشش کریں۔"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI test failed", e)
            ApiTestResult.Failure(
                errorEn = e.localizedMessage ?: "Firebase AI connection failed",
                errorUr = "فائر بیس اے آئی رابطہ ناکام: ${e.localizedMessage ?: "نیٹ ورک یا ایپ چیک مسئلہ"}"
            )
        }
    }

    private suspend fun callFirebaseAiWithRetry(
        model: GenerativeModel,
        history: List<com.google.firebase.ai.type.Content>,
        prompt: String
    ): Result<String> {
        var attempt = 0
        var backoffMs = 800L
        while (attempt < 3) {
            try {
                val response = withTimeout(60_000L) {
                    if (history.isEmpty()) {
                        model.generateContent(prompt)
                    } else {
                        model.startChat(history = history).sendMessage(prompt)
                    }
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    return Result.success(text)
                }
                return Result.failure(IllegalStateException("Empty Gemini response"))
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Firebase AI call timed out", e)
                return Result.failure(IllegalStateException("AI request timed out after 60 seconds."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                val isRateLimit = msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
                if (isRateLimit && attempt < 2) {
                    AxtraLogger.w(TAG, "Rate limit hit, backing off ${backoffMs}ms")
                    delay(backoffMs)
                    backoffMs *= 2
                    attempt++
                    continue
                }
                Log.e(TAG, "Firebase AI inference failed", e)
                return Result.failure(e)
            }
        }
        return Result.failure(IllegalStateException("HTTP 429 quota exceeded"))
    }

    private suspend fun buildSystemPrompt(settings: AxtraSettingsState): String {
        val base = if (settings.advancedPromptMode && settings.customSystemPrompt.isNotBlank()) {
            settings.customSystemPrompt
        } else {
            AxtraConstants.DEFAULT_SYSTEM_PROMPT
        }
        val langInstruction = when (settings.languageMode) {
            LanguageMode.AUTO -> "Language rule: Auto-detect Urdu, Roman Urdu, Saraiki, or English and reply naturally in the user's language (default Urdu)."
            LanguageMode.URDU_ONLY -> "Language rule: Always write spoken_reply in natural Urdu script (اردو)."
            LanguageMode.ENGLISH_ONLY -> "Language rule: Always write spoken_reply in clear English."
        }
        val saraikiHints = AxtraConstants.SARAIKI_HINTS.entries.joinToString(", ") { "${it.key}=${it.value}" }
        val memoriesText = if (settings.longTermMemoryEnabled) {
            val list = memoryRepository.getAllOnce().take(25)
            if (list.isEmpty()) "None saved yet."
            else list.joinToString("\n") { "- ${it.memoryKey}: ${it.memoryValue}" }
        } else {
            "Long-term memory disabled by user."
        }

        return """$base

$langInstruction

Saraiki-to-Urdu vocabulary hints:
$saraikiHints

Owner Long-Term Memories (use when relevant):
$memoriesText"""
    }

    private fun recordTurn(userText: String, modelReply: String) {
        while (shortTermHistory.size >= 20) {
            shortTermHistory.removeFirst()
        }
        shortTermHistory.addLast(ChatTurn("user", userText))
        if (shortTermHistory.size >= 20) {
            shortTermHistory.removeFirst()
        }
        shortTermHistory.addLast(ChatTurn("model", modelReply))
    }

    private companion object {
        const val TAG = "AiBrainRepository"
    }
}
