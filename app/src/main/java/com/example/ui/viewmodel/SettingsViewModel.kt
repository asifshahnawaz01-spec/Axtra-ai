package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiBrainRepository
import com.example.ai.ApiTestResult
import com.example.auth.GoogleAuthHelper
import com.example.core.AppPreferences
import com.example.core.AxtraConstants
import com.example.core.AxtraSettingsState
import com.example.core.AxtraThemeStyle
import com.example.core.ConfirmationLevel
import com.example.core.EncryptedKeyStore
import com.example.core.LanguageMode
import com.example.core.OverlayAnimationStyle
import com.example.core.PrayerCalculationMethod
import com.example.core.UiLanguage
import com.example.data.local.BlockedAppEntity
import com.example.data.remote.CloudSyncRepository
import com.example.data.repository.BackupRepository
import com.example.data.repository.BlockedAppsRepository
import com.example.data.repository.InstalledAppInfo
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.RoutineRepository
import com.example.data.repository.ToolsRepository
import com.example.service.AxtraForegroundService
import com.example.voice.AxtraTtsEngine
import com.example.voice.AxtraVoiceInteractionService
import com.example.voice.HotwordCoordinator
import com.example.voice.HotwordStatus
import com.example.voice.TtsVoiceOption
import com.example.voice.WakeWordDetector
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val encryptedKeyStore: EncryptedKeyStore,
    private val aiBrainRepository: AiBrainRepository,
    private val ttsEngine: AxtraTtsEngine,
    private val wakeWordDetector: WakeWordDetector,
    private val hotwordCoordinator: HotwordCoordinator,
    private val blockedAppsRepository: BlockedAppsRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val backupRepository: BackupRepository,
    private val googleAuthHelper: GoogleAuthHelper,
    private val cloudSyncRepository: CloudSyncRepository,
    private val memoryRepository: MemoryRepository,
    private val toolsRepository: ToolsRepository,
    private val routineRepository: RoutineRepository,
    private val json: Json
) : ViewModel() {

    val settings: StateFlow<AxtraSettingsState> = appPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AxtraSettingsState())

    val blockedApps: StateFlow<List<BlockedAppEntity>> = blockedAppsRepository.blockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableVoices: StateFlow<List<TtsVoiceOption>> = ttsEngine.availableVoices
    val hotwordStatus: StateFlow<HotwordStatus> = hotwordCoordinator.status
    val lastDetectedHotword: StateFlow<String?> = hotwordCoordinator.lastDetectedPhrase
    val isVoiceInteractionReady: StateFlow<Boolean> = AxtraVoiceInteractionService.isServiceReady

    private val _aiTestStatus = MutableStateFlow<String?>(null)
    val aiTestStatus: StateFlow<String?> = _aiTestStatus.asStateFlow()

    private val _isTestingAi = MutableStateFlow(false)
    val isTestingAi: StateFlow<Boolean> = _isTestingAi.asStateFlow()

    private val _isSyncingCloud = MutableStateFlow(false)
    val isSyncingCloud: StateFlow<Boolean> = _isSyncingCloud.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow<String?>(null)
    val cloudSyncStatus: StateFlow<String?> = _cloudSyncStatus.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch {
            _installedApps.value = installedAppsRepository.getInstalledApps()
        }
    }

    fun getSignedInDisplayName(): String =
        Firebase.auth.currentUser?.displayName ?: "Axtra Owner"

    fun getSignedInEmail(): String =
        Firebase.auth.currentUser?.email ?: "Signed in with Google"

    fun testGeminiAi() {
        viewModelScope.launch {
            _isTestingAi.value = true
            val result = aiBrainRepository.testGeminiConnection(settings.value.geminiModel)
            _aiTestStatus.value = when (result) {
                is ApiTestResult.Success -> "Success (${result.latencyMs} ms): ${result.message}"
                is ApiTestResult.Failure -> "Error: ${result.errorEn} | ${result.errorUr}"
            }
            _isTestingAi.value = false
        }
    }

    fun syncLocalDataToCloud() {
        val user = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            _isSyncingCloud.value = true
            var syncedCount = 0
            cloudSyncRepository.upsertUserProfile(
                userId = user.uid,
                displayName = user.displayName ?: "Axtra Owner",
                email = user.email,
                photoUrl = user.photoUrl?.toString(),
                preferredLanguage = settings.value.languageMode.name
            )
            memoryRepository.getAllOnce().forEach { mem ->
                if (cloudSyncRepository.createMemory(user.uid, mem.memoryKey, mem.memoryValue, "mem_local_${mem.id}").isSuccess) {
                    syncedCount++
                }
            }
            toolsRepository.notes.first().forEach { note ->
                if (cloudSyncRepository.createNote(user.uid, note.title, note.content, "note_local_${note.id}").isSuccess) {
                    syncedCount++
                }
            }
            toolsRepository.expenses.first().forEach { exp ->
                if (exp.amount > 0 && cloudSyncRepository.createExpense(user.uid, exp.amount, exp.category, exp.description, exp.currency, "exp_local_${exp.id}").isSuccess) {
                    syncedCount++
                }
            }
            toolsRepository.todos.first().forEach { todo ->
                if (cloudSyncRepository.createTodo(user.uid, todo.title, todo.listType, "todo_local_${todo.id}").isSuccess) {
                    syncedCount++
                }
            }
            routineRepository.getAllOnce().filter { !it.isBuiltIn }.forEach { r ->
                val steps = runCatching { json.decodeFromString<List<String>>(r.stepsJson) }.getOrDefault(emptyList())
                if (steps.isNotEmpty() && cloudSyncRepository.createRoutine(user.uid, r.name, r.triggerType, r.triggerValue, steps, r.isEnabled, "routine_local_${r.id}").isSuccess) {
                    syncedCount++
                }
            }
            _cloudSyncStatus.value = "Synced $syncedCount items to Cloud Firestore."
            _isSyncingCloud.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            googleAuthHelper.signOut(context)
        }
    }

    fun saveAppPin(pin: String) {
        encryptedKeyStore.saveAppPin(pin)
        _statusBanner.value = "Security PIN updated."
    }

    fun verifyPin(input: String): Boolean {
        val saved = encryptedKeyStore.getAppPin()
        return saved.isNotBlank() && saved == input.trim()
    }

    fun hasPinSet(): Boolean = encryptedKeyStore.getAppPin().isNotBlank()

    fun updateGeminiModel(m: String) = viewModelScope.launch { appPreferences.updateGeminiModel(m) }
    fun updateTemperature(t: Float) = viewModelScope.launch { appPreferences.updateTemperature(t) }
    fun updateMaxTokens(t: Int) = viewModelScope.launch { appPreferences.updateMaxTokens(t) }
    fun updateLanguageMode(m: LanguageMode) = viewModelScope.launch { appPreferences.updateLanguageMode(m) }
    fun updateMemoryEnabled(e: Boolean) = viewModelScope.launch { appPreferences.updateMemoryEnabled(e) }
    fun updateAdvancedPrompt(e: Boolean) = viewModelScope.launch { appPreferences.updateAdvancedPromptMode(e) }
    fun updateCustomPrompt(p: String) = viewModelScope.launch { appPreferences.updateCustomSystemPrompt(p) }

    fun updateSttLanguage(l: String) = viewModelScope.launch { appPreferences.updateSttLanguage(l) }
    fun updateSilenceTimeout(ms: Int) = viewModelScope.launch { appPreferences.updateSilenceTimeout(ms) }
    fun updateTtsVoice(name: String) = viewModelScope.launch { appPreferences.updateTtsVoice(name) }
    fun updateTtsSpeed(s: Float) = viewModelScope.launch { appPreferences.updateTtsSpeed(s) }
    fun updateTtsPitch(p: Float) = viewModelScope.launch { appPreferences.updateTtsPitch(p) }
    fun updateTtsVolume(v: Float) = viewModelScope.launch { appPreferences.updateTtsVolume(v) }
    fun updateWakeWord(e: Boolean) = viewModelScope.launch {
        appPreferences.updateWakeWordEnabled(e)
        hotwordCoordinator.ensureMonitoringLoop()
    }
    fun updateWakeSensitivity(s: Float) = viewModelScope.launch {
        appPreferences.updateWakeSensitivity(s)
        wakeWordDetector.updateSensitivity(s)
    }
    fun updateCustomWakeWords(csv: String) = viewModelScope.launch {
        appPreferences.updateCustomWakeWords(csv)
        _statusBanner.value = "Custom wake words saved: ${csv.ifBlank { AxtraConstants.DEFAULT_WAKE_WORDS_CSV }}"
    }
    fun resetDefaultWakeWords() = viewModelScope.launch {
        appPreferences.updateCustomWakeWords(AxtraConstants.DEFAULT_WAKE_WORDS_CSV)
        _statusBanner.value = "Reset wake words to: ${AxtraConstants.DEFAULT_WAKE_WORDS_CSV}"
    }
    fun updateConversationMode(e: Boolean) = viewModelScope.launch { appPreferences.updateConversationMode(e) }
    fun updateSilentMode(e: Boolean) = viewModelScope.launch { appPreferences.updateSilentMode(e) }
    fun updateVolumeDownShortcut(e: Boolean) = viewModelScope.launch { appPreferences.updateVolumeDownShortcut(e) }

    fun testVoice() {
        ttsEngine.speak("السلام علیکم، میں ایکسٹرا اے آئی ہوں۔ آپ کی آواز کی سیٹنگز بالکل درست کام کر رہی ہیں۔", forceSpeak = true)
    }

    fun testWakeWord() {
        wakeWordDetector.playDingSound()
        hotwordCoordinator.triggerManualTestSession()
        _statusBanner.value = "Hotword session triggered! Say 'Hey Astra', 'Hello Astra', or 'Astra' anytime when enabled."
    }

    fun updateAlwaysOn(e: Boolean) = viewModelScope.launch {
        appPreferences.updateAlwaysOnEnabled(e)
        val svc = Intent(context, AxtraForegroundService::class.java)
        if (e) ContextCompat.startForegroundService(context, svc) else context.stopService(svc)
    }
    fun updateStartOnBoot(e: Boolean) = viewModelScope.launch { appPreferences.updateStartOnBoot(e) }
    fun updatePauseLowBattery(e: Boolean) = viewModelScope.launch { appPreferences.updatePauseOnLowBattery(e) }
    fun updateScreenOnOnly(e: Boolean) = viewModelScope.launch { appPreferences.updateScreenOnOnly(e) }

    fun updateConfirmationLevel(l: ConfirmationLevel) = viewModelScope.launch { appPreferences.updateConfirmationLevel(l) }
    fun updateScreenAssistant(e: Boolean) = viewModelScope.launch { appPreferences.updateScreenAssistant(e) }
    fun updateThemeStyle(s: AxtraThemeStyle) = viewModelScope.launch { appPreferences.updateThemeStyle(s) }
    fun updateOverlayStyle(s: OverlayAnimationStyle) = viewModelScope.launch { appPreferences.updateOverlayStyle(s) }
    fun updateOverlayIntensity(i: Float) = viewModelScope.launch { appPreferences.updateOverlayIntensity(i) }
    fun updateLowEndMode(e: Boolean) = viewModelScope.launch { appPreferences.updateLowEndMode(e) }
    fun updateHideFullscreen(e: Boolean) = viewModelScope.launch { appPreferences.updateHideOverlayFullscreen(e) }
    fun updateFontScale(f: Float) = viewModelScope.launch { appPreferences.updateFontScale(f) }
    fun updateUiLanguage(l: UiLanguage) = viewModelScope.launch { appPreferences.updateUiLanguage(l) }

    fun updateAppLock(e: Boolean) = viewModelScope.launch { appPreferences.updateAppLock(e) }
    fun updateSensitiveLock(e: Boolean) = viewModelScope.launch { appPreferences.updateSensitiveLock(e) }
    fun updateLogRetention(days: Int) = viewModelScope.launch { appPreferences.updateLogRetention(days) }
    fun updateCallSmsAnnounce(e: Boolean) = viewModelScope.launch { appPreferences.updateCallSmsAnnounce(e) }
    fun updateDailyBriefing(e: Boolean, h: Int, m: Int) = viewModelScope.launch { appPreferences.updateDailyBriefing(e, h, m) }
    fun updatePrayerMethod(m: PrayerCalculationMethod) = viewModelScope.launch { appPreferences.updatePrayerMethod(m) }
    fun updateSilencePrayer(e: Boolean) = viewModelScope.launch { appPreferences.updateSilenceDuringPrayer(e) }

    fun addBlockedApp(pkg: String, label: String) = viewModelScope.launch {
        blockedAppsRepository.addBlockedApp(pkg, label)
    }

    fun removeBlockedApp(entity: BlockedAppEntity) = viewModelScope.launch {
        blockedAppsRepository.removeBlockedApp(entity)
    }

    suspend fun getBackupJson(): String = backupRepository.exportBackupJson()

    fun restoreFromJson(raw: String) {
        viewModelScope.launch {
            backupRepository.restoreFromBackupJson(raw).fold(
                onSuccess = { count -> _statusBanner.value = "Restored $count items successfully." },
                onFailure = { e -> _statusBanner.value = "Restore failed: ${e.message}" }
            )
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            backupRepository.deleteAllUserData()
            encryptedKeyStore.clearAll()
            aiBrainRepository.clearShortTermHistory()
            _statusBanner.value = "تمام ذاتی ڈیٹا حذف کر دیا گیا ہے۔"
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }
}
