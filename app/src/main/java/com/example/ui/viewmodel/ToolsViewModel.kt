package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.actions.AppUsageStat
import com.example.actions.DeviceDataHelper
import com.example.actions.PhoneHealthReport
import com.example.ai.AiBrainRepository
import com.example.core.AppPreferences
import com.example.core.AxtraSettingsState
import com.example.data.local.ExpenseEntity
import com.example.data.local.NoteEntity
import com.example.data.local.TodoItemEntity
import com.example.data.repository.ToolsRepository
import com.example.service.AxtraNotificationListenerService
import com.example.service.CapturedNotification
import com.example.utils.DailyPrayerTimes
import com.example.utils.PrayerTimeCalculator
import com.example.utils.WeatherRepository
import com.example.voice.AxtraTtsEngine
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val aiBrainRepository: AiBrainRepository,
    private val toolsRepository: ToolsRepository,
    private val deviceDataHelper: DeviceDataHelper,
    private val prayerTimeCalculator: PrayerTimeCalculator,
    private val weatherRepository: WeatherRepository,
    private val ttsEngine: AxtraTtsEngine
) : ViewModel() {

    val settings: StateFlow<AxtraSettingsState> = appPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AxtraSettingsState())

    val notes: StateFlow<List<NoteEntity>> = toolsRepository.notes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = toolsRepository.expenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlyExpenseTotal: StateFlow<Double> = toolsRepository.observeCurrentMonthTotal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todos: StateFlow<List<TodoItemEntity>> = toolsRepository.todos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeNotifications: StateFlow<List<CapturedNotification>> =
        AxtraNotificationListenerService.activeNotifications

    private val _briefingResult = MutableStateFlow("")
    val briefingResult: StateFlow<String> = _briefingResult.asStateFlow()

    private val _translationResult = MutableStateFlow("")
    val translationResult: StateFlow<String> = _translationResult.asStateFlow()

    private val _studyResult = MutableStateFlow("")
    val studyResult: StateFlow<String> = _studyResult.asStateFlow()

    private val _ocrResult = MutableStateFlow("")
    val ocrResult: StateFlow<String> = _ocrResult.asStateFlow()

    private val _qrResult = MutableStateFlow("")
    val qrResult: StateFlow<String> = _qrResult.asStateFlow()

    private val _notificationSummary = MutableStateFlow("")
    val notificationSummary: StateFlow<String> = _notificationSummary.asStateFlow()

    private val _smartReplies = MutableStateFlow<List<String>>(emptyList())
    val smartReplies: StateFlow<List<String>> = _smartReplies.asStateFlow()

    private val _prayerTimes = MutableStateFlow(prayerTimeCalculator.calculateForToday())
    val prayerTimes: StateFlow<DailyPrayerTimes> = _prayerTimes.asStateFlow()

    private val _phoneHealth = MutableStateFlow(deviceDataHelper.getPhoneHealthReport())
    val phoneHealth: StateFlow<PhoneHealthReport> = _phoneHealth.asStateFlow()

    private val _screenTime = MutableStateFlow<List<AppUsageStat>>(emptyList())
    val screenTime: StateFlow<List<AppUsageStat>> = _screenTime.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun refreshPrayerAndHealth() {
        _prayerTimes.value = prayerTimeCalculator.calculateForToday(method = settings.value.prayerMethod)
        _phoneHealth.value = deviceDataHelper.getPhoneHealthReport()
        _screenTime.value = deviceDataHelper.getScreenTimeStatsToday()
    }

    fun generateDailyBriefing(speakAloud: Boolean = true) {
        viewModelScope.launch {
            _isBusy.value = true
            val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
            val weather = weatherRepository.fetchCurrentWeather()
            val prayers = prayerTimeCalculator.calculateForToday(method = settings.value.prayerMethod)
            val events = deviceDataHelper.getTodayCalendarEvents()
            val eventsLine = if (events.isEmpty()) "No calendar events today." else events.joinToString(", ")

            val rawInfo = """
                Date: $dateStr
                Weather: $weather
                Prayer Times: Fajr ${prayers.fajr}, Dhuhr ${prayers.dhuhr}, Asr ${prayers.asr}, Maghrib ${prayers.maghrib}, Isha ${prayers.isha}
                Calendar: $eventsLine
            """.trimIndent()

            val aiSummary = aiBrainRepository.askPlainGemini(
                "Create a warm, concise Urdu daily briefing from this data:\n$rawInfo"
            ).getOrElse { rawInfo }

            _briefingResult.value = aiSummary
            _isBusy.value = false
            if (speakAloud) ttsEngine.speak(aiSummary)
        }
    }

    fun translateText(input: String, targetLang: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = aiBrainRepository.askPlainGemini(
                "Translate the following text accurately into $targetLang. Keep it natural and concise:\n$input"
            ).getOrElse { "Translation unavailable offline. Connect to internet to use Gemini AI." }
            _translationResult.value = res
            _isBusy.value = false
            ttsEngine.speak(res)
        }
    }

    fun askStudyHelper(question: String, makeQuiz: Boolean = false) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            val prompt = if (makeQuiz) {
                "Create a short 3-question multiple-choice quiz with answers on: $question (use simple Urdu/English)."
            } else {
                "Explain or solve step-by-step in clear, easy Urdu and English: $question"
            }
            _studyResult.value = aiBrainRepository.askPlainGemini(prompt)
                .getOrElse { "Study helper requires an active internet connection for Firebase Gemini AI." }
            _isBusy.value = false
        }
    }

    fun summarizeNotifications() {
        val list = activeNotifications.value
        if (list.isEmpty()) {
            _notificationSummary.value = "کوئی نیا نوٹیفکیشن موجود نہیں ہے۔"
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val joined = list.take(12).joinToString("\n") { "${it.appName}: ${it.title} - ${it.text}" }
            val res = aiBrainRepository.askPlainGemini(
                "Summarize these unread notifications in short Urdu sentences:\n$joined"
            ).getOrElse { joined }
            _notificationSummary.value = res
            _isBusy.value = false
            ttsEngine.speak(res)
        }
    }

    fun generateSmartReplies(notification: CapturedNotification) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = aiBrainRepository.askPlainGemini(
                "Suggest exactly 3 short polite replies (one per line, no numbering) in Urdu/Roman Urdu for this message from ${notification.title}: ${notification.text}"
            ).getOrElse { "جی ٹھیک ہے\nمیں تھوڑی دیر میں جواب دیتا ہوں\nشکریہ" }
            _smartReplies.value = res.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(3)
            _isBusy.value = false
        }
    }

    fun sendQuickReply(notification: CapturedNotification, reply: String) {
        val ok = AxtraNotificationListenerService.instance?.replyToNotification(notification.key, reply) == true
        _notificationSummary.value = if (ok) "جواب بھیج دیا گیا: $reply" else "اس ایپ پر براہ راست جواب دستیاب نہیں۔"
    }

    fun runOcrOnImageUri(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val extracted = visionText.text.trim()
                        if (extracted.isBlank()) {
                            _ocrResult.value = "تصویر میں کوئی متن نہیں ملا۔"
                            _isBusy.value = false
                        } else {
                            viewModelScope.launch {
                                val explained = aiBrainRepository.askPlainGemini(
                                    "Extracted text from photo/document:\n$extracted\n\nExplain and translate its key meaning into simple Urdu:"
                                ).getOrElse { "Extracted text:\n$extracted" }
                                _ocrResult.value = "Detected Text:\n$extracted\n\nExplanation:\n$explained"
                                _isBusy.value = false
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        _ocrResult.value = "OCR error: ${e.message}"
                        _isBusy.value = false
                    }
            } catch (e: Exception) {
                _ocrResult.value = "Failed loading image: ${e.message}"
                _isBusy.value = false
            }
        }
    }

    fun scanQrFromImageUri(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val image = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val values = barcodes.mapNotNull { it.rawValue }
                        _qrResult.value = if (values.isEmpty()) "No QR or barcode found."
                        else values.joinToString("\n")
                        _isBusy.value = false
                    }
                    .addOnFailureListener { e ->
                        _qrResult.value = "Scan error: ${e.message}"
                        _isBusy.value = false
                    }
            } catch (e: Exception) {
                _qrResult.value = "Failed loading image: ${e.message}"
                _isBusy.value = false
            }
        }
    }

    fun saveNote(title: String, content: String) = viewModelScope.launch {
        if (content.isNotBlank()) toolsRepository.saveNote(title, content)
    }

    fun deleteNote(id: Long) = viewModelScope.launch { toolsRepository.deleteNote(id) }

    fun addExpense(amount: Double, category: String, description: String) = viewModelScope.launch {
        if (amount > 0) toolsRepository.addExpense(amount, category, description)
    }

    fun deleteExpense(id: Long) = viewModelScope.launch { toolsRepository.deleteExpense(id) }

    fun addTodo(title: String, listType: String) = viewModelScope.launch {
        if (title.isNotBlank()) toolsRepository.addTodo(title, listType)
    }

    fun toggleTodo(item: TodoItemEntity) = viewModelScope.launch { toolsRepository.toggleTodo(item) }
    fun deleteTodo(id: Long) = viewModelScope.launch { toolsRepository.deleteTodo(id) }

    fun startFocusMode(minutes: Int) = viewModelScope.launch {
        val until = System.currentTimeMillis() + minutes * 60_000L
        appPreferences.updateFocusMode(until, settings.value.focusBlockedPackagesCsv)
    }

    fun stopFocusMode() = viewModelScope.launch {
        appPreferences.updateFocusMode(0L, settings.value.focusBlockedPackagesCsv)
    }

    fun triggerFindMyPhone() = deviceDataHelper.startFindMyPhone()
    fun stopFindMyPhone() = deviceDataHelper.stopFindMyPhone()
}
