package com.example.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.accessibility.AxtraAccessibilityService
import com.example.ai.AiActionResponse
import com.example.ai.AiBrainRepository
import com.example.ai.AxtraIntent
import com.example.core.AppPreferences
import com.example.core.ConfirmationLevel
import com.example.data.repository.ActionLogRepository
import com.example.data.repository.InstalledAppsRepository
import com.example.data.repository.MemoryRepository
import com.example.data.repository.ToolsRepository
import com.example.service.AxtraNotificationListenerService
import com.example.voice.AxtraTtsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class StepStatus { PENDING, RUNNING, DONE, FAILED }

data class TaskStepUi(
    val label: String,
    val status: StepStatus = StepStatus.PENDING
)

data class PendingConfirmation(
    val title: String,
    val description: String,
    val secondsRemaining: Int = 10
)

data class ExecutionUiState(
    val isRunning: Boolean = false,
    val steps: List<TaskStepUi> = emptyList(),
    val confirmation: PendingConfirmation? = null,
    val statusMessage: String = ""
)

@Singleton
class ActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val installedAppsRepository: InstalledAppsRepository,
    private val fuzzyAppMatcher: FuzzyAppMatcher,
    private val systemControlHelper: SystemControlHelper,
    private val deviceDataHelper: DeviceDataHelper,
    private val memoryRepository: MemoryRepository,
    private val toolsRepository: ToolsRepository,
    private val actionLogRepository: ActionLogRepository,
    private val aiBrainRepository: AiBrainRepository,
    private val ttsEngine: AxtraTtsEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentTaskJob: Job? = null
    private var confirmationDeferred: CompletableDeferred<Boolean>? = null

    private val _executionState = MutableStateFlow(ExecutionUiState())
    val executionState: StateFlow<ExecutionUiState> = _executionState.asStateFlow()

    fun resolveConfirmation(confirmed: Boolean) {
        confirmationDeferred?.complete(confirmed)
        confirmationDeferred = null
        _executionState.value = _executionState.value.copy(confirmation = null)
    }

    fun stopEverything() {
        currentTaskJob?.cancel()
        currentTaskJob = null
        confirmationDeferred?.complete(false)
        confirmationDeferred = null
        ttsEngine.stopSpeaking()
        deviceDataHelper.stopFindMyPhone()
        _executionState.value = ExecutionUiState(
            isRunning = false,
            statusMessage = "تمام کام روک دیے گئے ہیں۔"
        )
    }

    fun executeAction(
        originalCommand: String,
        action: AiActionResponse,
        onFinished: (finalReply: String, success: Boolean) -> Unit
    ) {
        // Kill switch voice trigger
        if (action.target == "STOP_ALL") {
            stopEverything()
            onFinished("تمام کام فوری روک دیے گئے ہیں۔", true)
            return
        }

        currentTaskJob?.cancel()
        currentTaskJob = scope.launch {
            val settings = appPreferences.settingsFlow.first()
            val needsConfirm = shouldRequireConfirmation(action, settings.confirmationLevel)
            if (needsConfirm) {
                val approved = awaitUserConfirmation(
                    title = "Confirm ${action.intent.name.replace('_', ' ')}",
                    description = "${action.spokenReply}\nTarget: ${action.target.ifBlank { action.app }}"
                )
                if (!approved) {
                    val cancelMsg = "کارروائی منسوخ کر دی گئی ہے۔"
                    actionLogRepository.logAction(originalCommand, action.intent.name, "Cancelled by user/timeout", cancelMsg, false)
                    _executionState.value = ExecutionUiState(isRunning = false, statusMessage = cancelMsg)
                    onFinished(cancelMsg, false)
                    return@launch
                }
            }

            val resultPair = runIntentSteps(originalCommand, action)
            actionLogRepository.logAction(
                command = originalCommand,
                intent = action.intent.name,
                stepsSummary = _executionState.value.steps.joinToString(" -> ") { "${it.label} [${it.status}]" },
                resultText = resultPair.first,
                success = resultPair.second
            )
            onFinished(resultPair.first, resultPair.second)
        }
    }

    private fun shouldRequireConfirmation(action: AiActionResponse, level: ConfirmationLevel): Boolean {
        val isRisky = action.intent in setOf(
            AxtraIntent.CALL,
            AxtraIntent.SMS,
            AxtraIntent.WHATSAPP_MESSAGE
        ) || action.parameters["action"]?.lowercase() in setOf("delete", "uninstall", "pay")

        return when (level) {
            ConfirmationLevel.LOW -> isRisky && action.needsConfirmation
            ConfirmationLevel.MEDIUM -> isRisky || action.needsConfirmation
            ConfirmationLevel.HIGH -> action.intent !in setOf(AxtraIntent.CHAT, AxtraIntent.UNKNOWN)
        }
    }

    private suspend fun awaitUserConfirmation(title: String, description: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmationDeferred = deferred
        ttsEngine.speak("تصدیق کریں: $description")

        val countdownJob = scope.launch {
            for (sec in 10 downTo 1) {
                if (deferred.isCompleted) break
                _executionState.value = _executionState.value.copy(
                    isRunning = true,
                    confirmation = PendingConfirmation(title, description, sec)
                )
                delay(1000L)
            }
            if (!deferred.isCompleted) {
                deferred.complete(false)
            }
        }

        val result = withTimeoutOrNull(10_500L) { deferred.await() } ?: false
        countdownJob.cancel()
        _executionState.value = _executionState.value.copy(confirmation = null)
        return result
    }

    private suspend fun runIntentSteps(originalCommand: String, action: AiActionResponse): Pair<String, Boolean> {
        return when (action.intent) {
            AxtraIntent.OPEN_APP -> handleOpenApp(action)
            AxtraIntent.CLOSE_APP -> handleCloseApp()
            AxtraIntent.CALL -> handleCall(action)
            AxtraIntent.SMS -> handleSms(action)
            AxtraIntent.WHATSAPP_MESSAGE -> handleWhatsAppMessage(action)
            AxtraIntent.SEARCH_WEB -> handleSearchWeb(action)
            AxtraIntent.PLAY_MEDIA -> handlePlayMedia(action)
            AxtraIntent.SYSTEM_TOGGLE -> handleSystemToggle(action)
            AxtraIntent.ALARM -> handleAlarm(action)
            AxtraIntent.TIMER -> handleTimer(action)
            AxtraIntent.REMINDER, AxtraIntent.CALENDAR -> handleCalendar(action)
            AxtraIntent.CAMERA -> handleCamera(action)
            AxtraIntent.READ_SCREEN -> handleReadScreen(action)
            AxtraIntent.READ_NOTIFICATIONS -> handleReadNotifications(action)
            AxtraIntent.NAVIGATE -> handleNavigate(action)
            AxtraIntent.TRANSLATE -> handleTranslate(action)
            AxtraIntent.NOTE -> handleNoteOrMemory(originalCommand, action)
            AxtraIntent.CHAT, AxtraIntent.UNKNOWN, AxtraIntent.ROUTINE -> {
                _executionState.value = ExecutionUiState(isRunning = false, statusMessage = action.spokenReply)
                action.spokenReply to true
            }
        }
    }

    private suspend fun handleOpenApp(action: AiActionResponse): Pair<String, Boolean> {
        val query = action.app.ifBlank { action.target }
        val steps = mutableListOf(
            TaskStepUi("Match installed app: $query", StepStatus.RUNNING),
            TaskStepUi("Launch application", StepStatus.PENDING)
        )
        _executionState.value = ExecutionUiState(isRunning = true, steps = steps)

        val apps = installedAppsRepository.getInstalledApps()
        val match = fuzzyAppMatcher.findBestMatch(query, apps)
        if (match == null) {
            steps[0] = steps[0].copy(status = StepStatus.FAILED)
            _executionState.value = ExecutionUiState(isRunning = false, steps = steps)
            return "معذرت، \"$query\" ایپ آپ کے فون میں انسٹال نہیں ملی۔" to false
        }

        steps[0] = steps[0].copy(label = "Matched: ${match.label}", status = StepStatus.DONE)
        steps[1] = steps[1].copy(status = StepStatus.RUNNING)
        _executionState.value = ExecutionUiState(isRunning = true, steps = steps)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (launchIntent != null) {
            context.startActivity(launchIntent)
            steps[1] = steps[1].copy(status = StepStatus.DONE)
            _executionState.value = ExecutionUiState(isRunning = false, steps = steps)
            "${match.label} کھول دیا گیا ہے۔" to true
        } else {
            steps[1] = steps[1].copy(status = StepStatus.FAILED)
            _executionState.value = ExecutionUiState(isRunning = false, steps = steps)
            "${match.label} لانچ نہیں ہو سکی۔" to false
        }
    }

    private suspend fun handleCloseApp(): Pair<String, Boolean> {
        val acc = AxtraAccessibilityService.instance
            ?: return "ایپ بند کرنے کے لیے Accessibility Service آن کریں۔" to false
        acc.performRecentsAction()
        delay(500L)
        acc.dispatchSwipe(540f, 1200f, 540f, 250f, 250L)
        delay(350L)
        acc.performHomeAction()
        return "موجودہ ایپ بند کرنے کی کوشش کی گئی ہے۔" to true
    }

    private fun handleCall(action: AiActionResponse): Pair<String, Boolean> {
        val target = action.target.trim()
        val digitsOnly = target.filter { it.isDigit() || it == '+' }
        val numberToCall = if (digitsOnly.length >= 6) {
            digitsOnly
        } else {
            val matches = deviceDataHelper.findContactsByName(target)
            if (matches.isEmpty()) {
                return "کانٹیکٹ \"$target\" نہیں ملا یا Contacts اجازت درکار ہے۔" to false
            }
            if (matches.size > 1) {
                val names = matches.take(3).joinToString("، ") { "${it.name} (${it.number})" }
                return "ایک سے زیادہ نام ملے ہیں: $names — واضح نام بتائیں۔" to false
            }
            matches.first().number
        }

        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (hasCallPerm) Intent.ACTION_CALL else Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$numberToCall")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return if (hasCallPerm) "$target کو کال ملائی جا رہی ہے۔" to true
        else "$target کا نمبر ڈائلر میں کھول دیا گیا ہے۔" to true
    }

    private fun handleSms(action: AiActionResponse): Pair<String, Boolean> {
        val message = action.parameters["message"] ?: action.parameters["text"].orEmpty()
        val target = action.target.trim()
        if (target.isBlank() || message.isBlank()) {
            return "پیغام بھیجنے کے لیے وصول کنندہ اور پیغام کا متن درکار ہے۔" to false
        }
        val matches = deviceDataHelper.findContactsByName(target)
        val phone = target.filter { it.isDigit() || it == '+' }.takeIf { it.length >= 6 }
            ?: matches.firstOrNull()?.number
            ?: return "کانٹیکٹ \"$target\" کا نمبر نہیں ملا۔" to false

        val ok = deviceDataHelper.sendSmsDirect(phone, message)
        return if (ok) "ایس ایم ایس بھیج دیا گیا ہے۔" to true
        else "ایس ایم ایس بھیجنے میں ناکامی ہوئی۔" to false
    }

    private suspend fun handleWhatsAppMessage(action: AiActionResponse): Pair<String, Boolean> {
        val contactQuery = action.target.trim()
        val message = action.parameters["message"] ?: action.parameters["text"].orEmpty()
        val steps = mutableListOf(
            TaskStepUi("Find contact: $contactQuery", StepStatus.RUNNING),
            TaskStepUi("Open WhatsApp", StepStatus.PENDING),
            TaskStepUi("Search & open chat", StepStatus.PENDING),
            TaskStepUi("Type message", StepStatus.PENDING),
            TaskStepUi("Tap Send", StepStatus.PENDING)
        )
        _executionState.value = ExecutionUiState(isRunning = true, steps = steps)

        val contacts = deviceDataHelper.findContactsByName(contactQuery)
        val firstMatch = contacts.firstOrNull()
        steps[0] = steps[0].copy(
            label = if (firstMatch != null) "Found: ${firstMatch.name}" else "Using query: $contactQuery",
            status = StepStatus.DONE
        )
        steps[1] = steps[1].copy(status = StepStatus.RUNNING)
        _executionState.value = ExecutionUiState(isRunning = true, steps = steps)

        val waIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (waIntent == null) {
            steps[1] = steps[1].copy(status = StepStatus.FAILED)
            _executionState.value = ExecutionUiState(isRunning = false, steps = steps)
            return "واٹس ایپ فون میں انسٹال نہیں ہے۔" to false
        }

        // If phone number exists, deep-link directly to chat with prefilled message
        if (firstMatch != null && message.isNotBlank()) {
            val cleanDigits = firstMatch.number.replace("+", "").replace(" ", "").replace("-", "")
            val deepLink = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanDigits&text=${Uri.encode(message)}")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(deepLink)
            steps[1] = steps[1].copy(status = StepStatus.DONE)
            steps[2] = steps[2].copy(status = StepStatus.DONE)
            steps[3] = steps[3].copy(status = StepStatus.DONE)
            steps[4] = steps[4].copy(status = StepStatus.RUNNING)
            _executionState.value = ExecutionUiState(isRunning = true, steps = steps)

            val acc = AxtraAccessibilityService.instance
            if (acc != null) {
                delay(1800L)
                val clicked = acc.clickNodeByText("Send") || acc.clickNodeByViewId("com.whatsapp:id/send")
                steps[4] = steps[4].copy(status = if (clicked) StepStatus.DONE else StepStatus.FAILED)
                _executionState.value = ExecutionUiState(isRunning = false, steps = steps)
                return if (clicked) "${firstMatch.name} کو واٹس ایپ پیغام بھیج دیا گیا ہے۔" to true
                else "واٹس ایپ چیٹ کھل گئی ہے، براہ کرم Send بٹن خود دبائیں۔" to false
            } else {
                steps[4] = steps[4].copy(status = StepStatus.FAILED)
                _executionState.value = ExecutionUiState(isRunning = false, steps = steps)
                return "واٹس ایپ چیٹ کھل گئی ہے۔ خودکار Send کے لیے Accessibility آن کریں۔" to false
            }
        }

        context.startActivity(waIntent)
        steps[1] = steps[1].copy(status = StepStatus.DONE)
        _executionState.value = ExecutionUiState(isRunning = false, steps = steps)
        return "واٹس ایپ کھول دی گئی ہے۔" to true
    }

    private fun handleSearchWeb(action: AiActionResponse): Pair<String, Boolean> {
        val query = action.target.ifBlank { action.parameters["query"].orEmpty() }
        val url = if (query.startsWith("http://") || query.startsWith("https://")) {
            query
        } else {
            "https://www.google.com/search?q=${Uri.encode(query)}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "ویب تلاش کھولی جا رہی ہے۔" to true
    }

    private fun handlePlayMedia(action: AiActionResponse): Pair<String, Boolean> {
        val cmd = action.parameters["control"] ?: action.target
        if (cmd.lowercase() in setOf("pause", "play", "next", "previous", "prev", "stop", "resume")) {
            val outcome = systemControlHelper.dispatchMediaKey(cmd)
            return outcome.messageUr to outcome.verified
        }
        val query = action.target
        val ytIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(ytIntent)
            "یوٹیوب پر \"$query\" چلایا جا رہا ہے۔" to true
        } catch (_: Exception) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(web)
            "یوٹیوب براؤزر میں کھولا جا رہا ہے۔" to true
        }
    }

    private fun handleSystemToggle(action: AiActionResponse): Pair<String, Boolean> {
        val setting = (action.parameters["setting"] ?: action.target).lowercase()
        val state = action.parameters["state"]?.lowercase()
        return when {
            setting.contains("find_phone") -> {
                deviceDataHelper.startFindMyPhone()
                "فون کی گھنٹی اور فلیش لائٹ آن کر دی گئی ہے۔ روکنے کے لیے Stop دبائیں۔" to true
            }
            setting.contains("flash") || setting.contains("torch") -> {
                val turnOn = when (state) {
                    "on" -> true
                    "off" -> false
                    else -> null
                }
                val res = systemControlHelper.toggleFlashlight(turnOn)
                res.messageUr to res.verified
            }
            setting.contains("volume") -> {
                val res = systemControlHelper.adjustVolume(state ?: "up")
                res.messageUr to res.verified
            }
            setting.contains("bright") -> {
                val res = systemControlHelper.adjustBrightness(state ?: "medium")
                res.messageUr to res.verified
            }
            setting.contains("dnd") || setting.contains("disturb") -> {
                val res = systemControlHelper.toggleDoNotDisturb(state != "off")
                res.messageUr to res.verified
            }
            setting == "nav_home" -> {
                val ok = AxtraAccessibilityService.instance?.performHomeAction() == true
                (if (ok) "ہوم اسکرین پر آ گئے ہیں۔" else "Accessibility Service آن کریں۔") to ok
            }
            setting == "nav_back" -> {
                val ok = AxtraAccessibilityService.instance?.performBackAction() == true
                (if (ok) "پیچھے چلے گئے ہیں۔" else "Accessibility Service آن کریں۔") to ok
            }
            else -> {
                val res = systemControlHelper.openConnectivityPanel(setting)
                res.messageUr to res.verified
            }
        }
    }

    private fun handleAlarm(action: AiActionResponse): Pair<String, Boolean> {
        val hour = action.parameters["hour"]?.toIntOrNull()?.coerceIn(0, 23) ?: 7
        val minute = action.parameters["minute"]?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, action.target.ifBlank { "Axtra Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "$hour بج کر $minute منٹ کا الارم سیٹ کر دیا گیا ہے۔" to true
        } catch (_: Exception) {
            "الارم ایپ کھولنے میں مسئلہ پیش آیا۔" to false
        }
    }

    private fun handleTimer(action: AiActionResponse): Pair<String, Boolean> {
        val seconds = action.parameters["seconds"]?.toIntOrNull()?.coerceAtLeast(1) ?: 60
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Axtra Timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "$seconds سیکنڈ کا ٹائمر لگا دیا گیا ہے۔" to true
        } catch (_: Exception) {
            "ٹائمر لگانے میں مسئلہ پیش آیا۔" to false
        }
    }

    private fun handleCalendar(action: AiActionResponse): Pair<String, Boolean> {
        val title = action.target.ifBlank { "Axtra Reminder" }
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "کیلنڈر ریمائنڈر فارم کھول دیا گیا ہے۔" to true
    }

    private fun handleCamera(action: AiActionResponse): Pair<String, Boolean> {
        val mode = action.parameters["mode"]?.lowercase().orEmpty()
        val intentAction = if (mode.contains("video")) MediaStore.ACTION_VIDEO_CAPTURE
        else MediaStore.ACTION_IMAGE_CAPTURE
        val intent = Intent(intentAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "کیمرہ کھول دیا گیا ہے۔" to true
        } catch (_: Exception) {
            "کیمرہ کھولنے میں ناکامی ہوئی۔" to false
        }
    }

    private suspend fun handleReadScreen(action: AiActionResponse): Pair<String, Boolean> {
        val settings = appPreferences.settingsFlow.first()
        if (!settings.screenAssistantEnabled) {
            return "سیٹنگز میں Screen Assistant بند ہے۔" to false
        }
        val acc = AxtraAccessibilityService.instance
            ?: return "اسکرین پڑھنے کے لیے Accessibility Service آن کریں۔" to false

        val captured = acc.captureScreenText()
        return captured.fold(
            onSuccess = { screenText ->
                val summary = aiBrainRepository.askPlainGemini(
                    "Explain or summarize this screen text clearly in short Urdu:\n\n$screenText"
                )
                summary.fold(
                    onSuccess = { it to true },
                    onFailure = { "اسکرین متن: ${screenText.take(280)}" to true }
                )
            },
            onFailure = { err ->
                (err.message ?: "اسکرین نہیں پڑھی جا سکی۔") to false
            }
        )
    }

    private suspend fun handleReadNotifications(action: AiActionResponse): Pair<String, Boolean> {
        val items = AxtraNotificationListenerService.activeNotifications.value
        if (items.isEmpty()) {
            return "کوئی نیا نوٹیفکیشن موجود نہیں ہے۔" to true
        }
        val formatted = items.take(10).joinToString("\n") { "${it.appName}: ${it.title} - ${it.text}" }
        val summary = aiBrainRepository.askPlainGemini(
            "Summarize these phone notifications concisely in natural Urdu:\n\n$formatted"
        )
        return summary.fold(
            onSuccess = { it to true },
            onFailure = { "تازہ نوٹیفکیشنز:\n$formatted" to true }
        )
    }

    private fun handleNavigate(action: AiActionResponse): Pair<String, Boolean> {
        val place = action.target
        if (place.isBlank()) return "منزل کا نام بتائیں۔" to false
        val uri = Uri.parse("google.navigation:q=${Uri.encode(place)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "$place کا راستہ گوگل میپس پر کھول دیا گیا ہے۔" to true
        } catch (_: Exception) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(place)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(web)
            "$place کا نقشہ براؤزر میں کھول دیا گیا ہے۔" to true
        }
    }

    private suspend fun handleTranslate(action: AiActionResponse): Pair<String, Boolean> {
        val text = action.target
        if (text.isBlank()) return action.spokenReply to true
        val res = aiBrainRepository.askPlainGemini("Translate clearly into Urdu and English:\n$text")
        return res.fold(onSuccess = { it to true }, onFailure = { action.spokenReply to true })
    }

    private suspend fun handleNoteOrMemory(original: String, action: AiActionResponse): Pair<String, Boolean> {
        val type = action.parameters["type"]?.lowercase().orEmpty()
        return when (type) {
            "expense" -> {
                val amount = action.parameters["amount"]?.toDoubleOrNull() ?: 0.0
                val category = action.parameters["category"] ?: "General"
                toolsRepository.addExpense(amount, category, action.target.ifBlank { original })
                "${amount.toInt()} روپے ($category) اخراجات میں محفوظ کر لیے گئے ہیں۔" to true
            }
            "memory" -> {
                memoryRepository.saveFact("Saved Fact", action.target.ifBlank { original })
                "یہ بات طویل مدتی یادداشت میں محفوظ کر لی گئی ہے۔" to true
            }
            "todo", "shopping" -> {
                toolsRepository.addTodo(action.target.ifBlank { original }, if (type == "shopping") "SHOPPING" else "TODO")
                "فہرست میں شامل کر دیا گیا ہے۔" to true
            }
            else -> {
                toolsRepository.saveNote("Voice Note", action.target.ifBlank { original })
                "نوٹ محفوظ کر لیا گیا ہے۔" to true
            }
        }
    }
}
