package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "axtra_preferences")

data class AxtraSettingsState(
    // Onboarding & General
    val onboardingCompleted: Boolean = false,
    val uiLanguage: UiLanguage = UiLanguage.ENGLISH,
    val themeStyle: AxtraThemeStyle = AxtraThemeStyle.CYBER_BLUE,
    val fontScaleMultiplier: Float = 1.0f,

    // AI Brain
    val geminiModel: String = AxtraConstants.DEFAULT_GEMINI_MODEL,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 1024,
    val languageMode: LanguageMode = LanguageMode.AUTO,
    val longTermMemoryEnabled: Boolean = true,
    val advancedPromptMode: Boolean = false,
    val customSystemPrompt: String = AxtraConstants.DEFAULT_SYSTEM_PROMPT,

    // Voice
    val sttLanguagePrimary: String = "ur-PK",
    val silenceTimeoutMs: Int = 1500,
    val ttsVoiceName: String = "",
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVolume: Float = 1.0f,
    val wakeWordEnabled: Boolean = false,
    val wakeWordSensitivity: Float = 0.65f,
    val customWakeWordsCsv: String = AxtraConstants.DEFAULT_WAKE_WORDS_CSV,
    val conversationModeEnabled: Boolean = true,
    val silentModeEnabled: Boolean = false,
    val volumeDownShortcutEnabled: Boolean = true,

    // Always On
    val alwaysOnEnabled: Boolean = false,
    val startOnBoot: Boolean = false,
    val pauseOnLowBattery: Boolean = true,
    val screenOnOnly: Boolean = false,
    val listeningTimeTodayMs: Long = 0L,
    val listeningDateKey: String = "",

    // Control & Overlay
    val confirmationLevel: ConfirmationLevel = ConfirmationLevel.MEDIUM,
    val screenAssistantEnabled: Boolean = true,
    val overlayAnimationStyle: OverlayAnimationStyle = OverlayAnimationStyle.ORB,
    val overlayIntensity: Float = 0.8f,
    val lowEndPhoneMode: Boolean = false,
    val hideOverlayInFullscreen: Boolean = true,

    // Safety & Privacy
    val appLockEnabled: Boolean = false,
    val sensitiveActionsLockEnabled: Boolean = false,
    val logRetentionDays: Int = 30,

    // Extra Tools Settings
    val callSmsAnnounceEnabled: Boolean = false,
    val dailyBriefingEnabled: Boolean = false,
    val dailyBriefingHour: Int = 8,
    val dailyBriefingMinute: Int = 0,
    val prayerMethod: PrayerCalculationMethod = PrayerCalculationMethod.KARACHI,
    val silenceDuringPrayer: Boolean = false,
    val focusModeActiveUntilMs: Long = 0L,
    val focusBlockedPackagesCsv: String = "com.zhiliaoapp.musically,com.instagram.android,com.google.android.youtube"
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val FONT_SCALE = floatPreferencesKey("font_scale")

        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val ADVANCED_PROMPT = booleanPreferencesKey("advanced_prompt")
        val CUSTOM_PROMPT = stringPreferencesKey("custom_prompt")

        val STT_LANG = stringPreferencesKey("stt_lang")
        val SILENCE_TIMEOUT = intPreferencesKey("silence_timeout")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_VOLUME = floatPreferencesKey("tts_volume")
        val WAKE_WORD = booleanPreferencesKey("wake_word")
        val WAKE_SENSITIVITY = floatPreferencesKey("wake_sensitivity")
        val CUSTOM_WAKE_WORDS = stringPreferencesKey("custom_wake_words")
        val CONVERSATION_MODE = booleanPreferencesKey("conversation_mode")
        val SILENT_MODE = booleanPreferencesKey("silent_mode")
        val VOL_DOWN_SHORTCUT = booleanPreferencesKey("vol_down_shortcut")

        val ALWAYS_ON = booleanPreferencesKey("always_on")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val PAUSE_LOW_BATTERY = booleanPreferencesKey("pause_low_battery")
        val SCREEN_ON_ONLY = booleanPreferencesKey("screen_on_only")
        val LISTENING_TIME_TODAY = longPreferencesKey("listening_time_today")
        val LISTENING_DATE_KEY = stringPreferencesKey("listening_date_key")

        val CONFIRMATION_LEVEL = stringPreferencesKey("confirmation_level")
        val SCREEN_ASSISTANT = booleanPreferencesKey("screen_assistant")
        val OVERLAY_STYLE = stringPreferencesKey("overlay_style")
        val OVERLAY_INTENSITY = floatPreferencesKey("overlay_intensity")
        val LOW_END_MODE = booleanPreferencesKey("low_end_mode")
        val HIDE_IN_FULLSCREEN = booleanPreferencesKey("hide_in_fullscreen")

        val APP_LOCK = booleanPreferencesKey("app_lock")
        val SENSITIVE_LOCK = booleanPreferencesKey("sensitive_lock")
        val LOG_RETENTION = intPreferencesKey("log_retention")

        val CALL_SMS_ANNOUNCE = booleanPreferencesKey("call_sms_announce")
        val DAILY_BRIEFING = booleanPreferencesKey("daily_briefing")
        val BRIEFING_HOUR = intPreferencesKey("briefing_hour")
        val BRIEFING_MINUTE = intPreferencesKey("briefing_minute")
        val PRAYER_METHOD = stringPreferencesKey("prayer_method")
        val SILENCE_PRAYER = booleanPreferencesKey("silence_prayer")
        val FOCUS_UNTIL = longPreferencesKey("focus_until")
        val FOCUS_PACKAGES = stringPreferencesKey("focus_packages")
    }

    val settingsFlow: Flow<AxtraSettingsState> = context.dataStore.data.map { p ->
        AxtraSettingsState(
            onboardingCompleted = p[Keys.ONBOARDING_DONE] ?: false,
            uiLanguage = runCatching { UiLanguage.valueOf(p[Keys.UI_LANGUAGE] ?: UiLanguage.ENGLISH.name) }
                .getOrDefault(UiLanguage.ENGLISH),
            themeStyle = runCatching { AxtraThemeStyle.valueOf(p[Keys.THEME_STYLE] ?: AxtraThemeStyle.CYBER_BLUE.name) }
                .getOrDefault(AxtraThemeStyle.CYBER_BLUE),
            fontScaleMultiplier = p[Keys.FONT_SCALE] ?: 1.0f,

            geminiModel = when (val stored = p[Keys.GEMINI_MODEL]) {
                "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon" -> stored
                "gemini-2.5-flash-lite" -> "gemini-3.5-flash-lite"
                else -> AxtraConstants.DEFAULT_GEMINI_MODEL
            },
            temperature = p[Keys.TEMPERATURE] ?: 0.2f,
            maxTokens = p[Keys.MAX_TOKENS] ?: 1024,
            languageMode = runCatching { LanguageMode.valueOf(p[Keys.LANGUAGE_MODE] ?: LanguageMode.AUTO.name) }
                .getOrDefault(LanguageMode.AUTO),
            longTermMemoryEnabled = p[Keys.MEMORY_ENABLED] ?: true,
            advancedPromptMode = p[Keys.ADVANCED_PROMPT] ?: false,
            customSystemPrompt = p[Keys.CUSTOM_PROMPT] ?: AxtraConstants.DEFAULT_SYSTEM_PROMPT,

            sttLanguagePrimary = p[Keys.STT_LANG] ?: "ur-PK",
            silenceTimeoutMs = p[Keys.SILENCE_TIMEOUT] ?: 1500,
            ttsVoiceName = p[Keys.TTS_VOICE] ?: "",
            ttsSpeed = p[Keys.TTS_SPEED] ?: 1.0f,
            ttsPitch = p[Keys.TTS_PITCH] ?: 1.0f,
            ttsVolume = p[Keys.TTS_VOLUME] ?: 1.0f,
            wakeWordEnabled = p[Keys.WAKE_WORD] ?: false,
            wakeWordSensitivity = p[Keys.WAKE_SENSITIVITY] ?: 0.65f,
            customWakeWordsCsv = p[Keys.CUSTOM_WAKE_WORDS]
                ?.takeIf { it.isNotBlank() }
                ?: AxtraConstants.DEFAULT_WAKE_WORDS_CSV,
            conversationModeEnabled = p[Keys.CONVERSATION_MODE] ?: true,
            silentModeEnabled = p[Keys.SILENT_MODE] ?: false,
            volumeDownShortcutEnabled = p[Keys.VOL_DOWN_SHORTCUT] ?: true,

            alwaysOnEnabled = p[Keys.ALWAYS_ON] ?: false,
            startOnBoot = p[Keys.START_ON_BOOT] ?: false,
            pauseOnLowBattery = p[Keys.PAUSE_LOW_BATTERY] ?: true,
            screenOnOnly = p[Keys.SCREEN_ON_ONLY] ?: false,
            listeningTimeTodayMs = p[Keys.LISTENING_TIME_TODAY] ?: 0L,
            listeningDateKey = p[Keys.LISTENING_DATE_KEY] ?: "",

            confirmationLevel = runCatching {
                ConfirmationLevel.valueOf(p[Keys.CONFIRMATION_LEVEL] ?: ConfirmationLevel.MEDIUM.name)
            }.getOrDefault(ConfirmationLevel.MEDIUM),
            screenAssistantEnabled = p[Keys.SCREEN_ASSISTANT] ?: true,
            overlayAnimationStyle = runCatching {
                OverlayAnimationStyle.valueOf(p[Keys.OVERLAY_STYLE] ?: OverlayAnimationStyle.ORB.name)
            }.getOrDefault(OverlayAnimationStyle.ORB),
            overlayIntensity = p[Keys.OVERLAY_INTENSITY] ?: 0.8f,
            lowEndPhoneMode = p[Keys.LOW_END_MODE] ?: false,
            hideOverlayInFullscreen = p[Keys.HIDE_IN_FULLSCREEN] ?: true,

            appLockEnabled = p[Keys.APP_LOCK] ?: false,
            sensitiveActionsLockEnabled = p[Keys.SENSITIVE_LOCK] ?: false,
            logRetentionDays = p[Keys.LOG_RETENTION] ?: 30,

            callSmsAnnounceEnabled = p[Keys.CALL_SMS_ANNOUNCE] ?: false,
            dailyBriefingEnabled = p[Keys.DAILY_BRIEFING] ?: false,
            dailyBriefingHour = p[Keys.BRIEFING_HOUR] ?: 8,
            dailyBriefingMinute = p[Keys.BRIEFING_MINUTE] ?: 0,
            prayerMethod = runCatching {
                PrayerCalculationMethod.valueOf(p[Keys.PRAYER_METHOD] ?: PrayerCalculationMethod.KARACHI.name)
            }.getOrDefault(PrayerCalculationMethod.KARACHI),
            silenceDuringPrayer = p[Keys.SILENCE_PRAYER] ?: false,
            focusModeActiveUntilMs = p[Keys.FOCUS_UNTIL] ?: 0L,
            focusBlockedPackagesCsv = p[Keys.FOCUS_PACKAGES]
                ?: "com.zhiliaoapp.musically,com.instagram.android,com.google.android.youtube"
        )
    }

    suspend fun updateOnboardingCompleted(done: Boolean) = edit { it[Keys.ONBOARDING_DONE] = done }
    suspend fun updateUiLanguage(lang: UiLanguage) = edit { it[Keys.UI_LANGUAGE] = lang.name }
    suspend fun updateThemeStyle(style: AxtraThemeStyle) = edit { it[Keys.THEME_STYLE] = style.name }
    suspend fun updateFontScale(scale: Float) = edit { it[Keys.FONT_SCALE] = scale }

    suspend fun updateGeminiModel(model: String) = edit { it[Keys.GEMINI_MODEL] = model }
    suspend fun updateTemperature(temp: Float) = edit { it[Keys.TEMPERATURE] = temp }
    suspend fun updateMaxTokens(tokens: Int) = edit { it[Keys.MAX_TOKENS] = tokens }
    suspend fun updateLanguageMode(mode: LanguageMode) = edit { it[Keys.LANGUAGE_MODE] = mode.name }
    suspend fun updateMemoryEnabled(enabled: Boolean) = edit { it[Keys.MEMORY_ENABLED] = enabled }
    suspend fun updateAdvancedPromptMode(enabled: Boolean) = edit { it[Keys.ADVANCED_PROMPT] = enabled }
    suspend fun updateCustomSystemPrompt(prompt: String) = edit { it[Keys.CUSTOM_PROMPT] = prompt }

    suspend fun updateSttLanguage(lang: String) = edit { it[Keys.STT_LANG] = lang }
    suspend fun updateSilenceTimeout(ms: Int) = edit { it[Keys.SILENCE_TIMEOUT] = ms }
    suspend fun updateTtsVoice(name: String) = edit { it[Keys.TTS_VOICE] = name }
    suspend fun updateTtsSpeed(speed: Float) = edit { it[Keys.TTS_SPEED] = speed }
    suspend fun updateTtsPitch(pitch: Float) = edit { it[Keys.TTS_PITCH] = pitch }
    suspend fun updateTtsVolume(volume: Float) = edit { it[Keys.TTS_VOLUME] = volume }
    suspend fun updateWakeWordEnabled(enabled: Boolean) = edit { it[Keys.WAKE_WORD] = enabled }
    suspend fun updateWakeSensitivity(sens: Float) = edit { it[Keys.WAKE_SENSITIVITY] = sens }
    suspend fun updateCustomWakeWords(csv: String) = edit {
        it[Keys.CUSTOM_WAKE_WORDS] = csv.ifBlank { AxtraConstants.DEFAULT_WAKE_WORDS_CSV }
    }
    suspend fun updateConversationMode(enabled: Boolean) = edit { it[Keys.CONVERSATION_MODE] = enabled }
    suspend fun updateSilentMode(enabled: Boolean) = edit { it[Keys.SILENT_MODE] = enabled }
    suspend fun updateVolumeDownShortcut(enabled: Boolean) = edit { it[Keys.VOL_DOWN_SHORTCUT] = enabled }

    suspend fun updateAlwaysOnEnabled(enabled: Boolean) = edit { it[Keys.ALWAYS_ON] = enabled }
    suspend fun updateStartOnBoot(enabled: Boolean) = edit { it[Keys.START_ON_BOOT] = enabled }
    suspend fun updatePauseOnLowBattery(enabled: Boolean) = edit { it[Keys.PAUSE_LOW_BATTERY] = enabled }
    suspend fun updateScreenOnOnly(enabled: Boolean) = edit { it[Keys.SCREEN_ON_ONLY] = enabled }

    suspend fun addListeningTime(deltaMs: Long, dateKey: String) {
        context.dataStore.edit { prefs ->
            val prevDate = prefs[Keys.LISTENING_DATE_KEY] ?: ""
            val current = if (prevDate == dateKey) (prefs[Keys.LISTENING_TIME_TODAY] ?: 0L) else 0L
            prefs[Keys.LISTENING_DATE_KEY] = dateKey
            prefs[Keys.LISTENING_TIME_TODAY] = current + deltaMs
        }
    }

    suspend fun updateConfirmationLevel(level: ConfirmationLevel) = edit { it[Keys.CONFIRMATION_LEVEL] = level.name }
    suspend fun updateScreenAssistant(enabled: Boolean) = edit { it[Keys.SCREEN_ASSISTANT] = enabled }
    suspend fun updateOverlayStyle(style: OverlayAnimationStyle) = edit { it[Keys.OVERLAY_STYLE] = style.name }
    suspend fun updateOverlayIntensity(intensity: Float) = edit { it[Keys.OVERLAY_INTENSITY] = intensity }
    suspend fun updateLowEndMode(enabled: Boolean) = edit { it[Keys.LOW_END_MODE] = enabled }
    suspend fun updateHideOverlayFullscreen(enabled: Boolean) = edit { it[Keys.HIDE_IN_FULLSCREEN] = enabled }

    suspend fun updateAppLock(enabled: Boolean) = edit { it[Keys.APP_LOCK] = enabled }
    suspend fun updateSensitiveLock(enabled: Boolean) = edit { it[Keys.SENSITIVE_LOCK] = enabled }
    suspend fun updateLogRetention(days: Int) = edit { it[Keys.LOG_RETENTION] = days }

    suspend fun updateCallSmsAnnounce(enabled: Boolean) = edit { it[Keys.CALL_SMS_ANNOUNCE] = enabled }
    suspend fun updateDailyBriefing(enabled: Boolean, hour: Int, minute: Int) = edit {
        it[Keys.DAILY_BRIEFING] = enabled
        it[Keys.BRIEFING_HOUR] = hour
        it[Keys.BRIEFING_MINUTE] = minute
    }
    suspend fun updatePrayerMethod(method: PrayerCalculationMethod) = edit { it[Keys.PRAYER_METHOD] = method.name }
    suspend fun updateSilenceDuringPrayer(enabled: Boolean) = edit { it[Keys.SILENCE_PRAYER] = enabled }
    suspend fun updateFocusMode(untilMs: Long, packagesCsv: String) = edit {
        it[Keys.FOCUS_UNTIL] = untilMs
        it[Keys.FOCUS_PACKAGES] = packagesCsv
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
