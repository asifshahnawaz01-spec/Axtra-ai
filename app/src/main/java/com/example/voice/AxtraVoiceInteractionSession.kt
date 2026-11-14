package com.example.voice

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.accessibility.AxtraAccessibilityService
import com.example.actions.ActionExecutor
import com.example.ai.AiBrainRepository
import com.example.core.AppPreferences
import com.example.core.AxtraConstants
import com.example.data.repository.BlockedAppsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Assistant overlay session displayed by Android's VoiceInteractionService
 * when the user says "Hey Astra" / "Hello Astra" / "Astra" or presses the Assist shortcut.
 */
class AxtraVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SessionEntryPoint {
        fun appPreferences(): AppPreferences
        fun aiBrainRepository(): AiBrainRepository
        fun actionExecutor(): ActionExecutor
        fun speechRecognizer(): AxtraSpeechRecognizer
        fun ttsEngine(): AxtraTtsEngine
        fun hotwordCoordinator(): HotwordCoordinator
        fun blockedAppsRepository(): BlockedAppsRepository
    }

    private val entryPoint: SessionEntryPoint by lazy {
        EntryPointAccessors.fromApplication(context.applicationContext, SessionEntryPoint::class.java)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var rootFrame: FrameLayout? = null
    private var titleText: TextView? = null
    private var statusText: TextView? = null
    private var bodyText: TextView? = null
    private var confirmRow: LinearLayout? = null
    private var speakBtn: Button? = null

    private var observerJob: Job? = null
    private var commandJob: Job? = null
    private var autoHideJob: Job? = null
    private var assistContextText: String? = null
    private var isSessionOpen = false

    override fun onCreateContentView(): View {
        val density = context.resources.displayMetrics.density
        val pad = (density * 18).toInt()

        val root = FrameLayout(context).apply {
            setPadding((density * 14).toInt(), (density * 14).toInt(), (density * 14).toInt(), (density * 24).toInt())
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F50B1224"))
                cornerRadius = density * 22f
                setStroke((density * 1.8f).toInt(), Color.parseColor("#00E5FF"))
            }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(context).apply {
            text = "${AxtraConstants.APP_NAME}  •  ${AxtraConstants.OWNER_LABEL}"
            setTextColor(Color.parseColor("#FFD54F"))
            textSize = 14f
        }
        val stateTv = TextView(context).apply {
            text = "Ready"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 13f
            gravity = Gravity.END
        }
        headerRow.addView(titleTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(stateTv)

        val bodyTv = TextView(context).apply {
            text = "سن رہی ہوں... اپنا حکم بولیں"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, (density * 10).toInt(), 0, (density * 12).toInt())
        }

        val confirmButtons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, 0, 0, (density * 10).toInt())
        }
        val yesBtn = Button(context).apply {
            text = "ہاں (Yes)"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setOnClickListener {
                entryPoint.actionExecutor().resolveConfirmation(true)
                confirmButtons.visibility = View.GONE
            }
        }
        val noBtn = Button(context).apply {
            text = "نہیں (No)"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#EF4444"))
            setOnClickListener {
                entryPoint.actionExecutor().resolveConfirmation(false)
                confirmButtons.visibility = View.GONE
            }
        }
        confirmButtons.addView(
            yesBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (density * 8).toInt()
            }
        )
        confirmButtons.addView(noBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val listenBtn = Button(context).apply {
            text = "Speak"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setOnClickListener { startVoiceListening() }
        }
        val openAppBtn = Button(context).apply {
            text = "Open App"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#253256"))
            setOnClickListener {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                runCatching { context.startActivity(intent) }
                hide()
            }
        }
        val closeBtn = Button(context).apply {
            text = "Close"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3B1C2E"))
            setOnClickListener {
                entryPoint.speechRecognizer().stopListening()
                entryPoint.ttsEngine().stopSpeaking()
                hide()
            }
        }
        val btnMargin = (density * 6).toInt()
        actionsRow.addView(
            listenBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = btnMargin }
        )
        actionsRow.addView(
            openAppBtn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = btnMargin }
        )
        actionsRow.addView(closeBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        card.addView(headerRow)
        card.addView(bodyTv)
        card.addView(confirmButtons)
        card.addView(actionsRow)

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        root.addView(card, cardParams)

        rootFrame = root
        titleText = titleTv
        statusText = stateTv
        bodyText = bodyTv
        confirmRow = confirmButtons
        speakBtn = listenBtn
        return root
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        isSessionOpen = true
        autoHideJob?.cancel()
        assistContextText = null
        entryPoint.hotwordCoordinator().setSessionActive(true)
        startObservingVoicePipeline()

        val initialCommand = args?.getString(AxtraConstants.EXTRA_INITIAL_COMMAND)?.trim().orEmpty()
        if (initialCommand.isNotBlank()) {
            statusText?.text = "Thinking..."
            bodyText?.text = initialCommand
            executeSpokenCommand(initialCommand)
        } else {
            startVoiceListening()
        }
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        val pkg = state.assistStructure?.activityComponent?.packageName.orEmpty()
        if (pkg.isBlank()) return
        scope.launch {
            val settings = entryPoint.appPreferences().settingsFlow.first()
            if (!settings.screenAssistantEnabled) return@launch
            if (entryPoint.blockedAppsRepository().isPackageBlocked(pkg)) return@launch
            val title = state.assistStructure?.getWindowNodeAt(0)?.title?.toString().orEmpty()
            if (title.isNotBlank()) {
                assistContextText = "Foreground App: $pkg\nWindow: $title"
            }
        }
    }

    private fun startObservingVoicePipeline() {
        observerJob?.cancel()
        val recognizer = entryPoint.speechRecognizer()
        val executor = entryPoint.actionExecutor()
        val tts = entryPoint.ttsEngine()

        observerJob = scope.launch {
            launch {
                recognizer.partialText.collectLatest { partial ->
                    if (isSessionOpen && recognizer.isListening.value && partial.isNotBlank()) {
                        bodyText?.text = partial
                    }
                }
            }
            launch {
                executor.executionState.collectLatest { execState ->
                    val conf = execState.confirmation
                    if (isSessionOpen && conf != null) {
                        statusText?.text = "Confirm (${conf.secondsRemaining}s)"
                        bodyText?.text = "${conf.title}\n${conf.description}"
                        confirmRow?.visibility = View.VISIBLE
                    } else {
                        confirmRow?.visibility = View.GONE
                    }
                }
            }
            launch {
                recognizer.events.collectLatest { event ->
                    if (!isSessionOpen || event.source != RecognitionSource.VOICE_INTERACTION) return@collectLatest
                    when (event) {
                        is SpeechResultEvent.Success -> {
                            val spoken = event.text
                            if (executor.executionState.value.confirmation != null) {
                                val lower = spoken.lowercase()
                                if (lower.contains("haan") || lower.contains("yes") || lower.contains("ji") || lower.contains("ہاں")) {
                                    executor.resolveConfirmation(true)
                                    return@collectLatest
                                } else if (lower.contains("nahi") || lower.contains("no") || lower.contains("cancel") || lower.contains("نہیں")) {
                                    executor.resolveConfirmation(false)
                                    return@collectLatest
                                }
                            }
                            executeSpokenCommand(spoken)
                        }
                        is SpeechResultEvent.LowConfidence -> {
                            statusText?.text = "Tap Speak"
                            bodyText?.text = "دوبارہ بولیے، آپ کی آواز واضح نہیں آئی۔"
                            tts.speak("دوبارہ بولیے، آپ کی آواز واضح نہیں آئی۔")
                        }
                        is SpeechResultEvent.Error -> {
                            statusText?.text = "Notice"
                            bodyText?.text = event.messageUr
                        }
                    }
                }
            }
        }
    }

    private fun startVoiceListening() {
        val recognizer = entryPoint.speechRecognizer()
        val tts = entryPoint.ttsEngine()
        autoHideJob?.cancel()
        if (tts.isSpeaking.value) tts.stopSpeaking()

        if (!recognizer.hasRecordPermission()) {
            statusText?.text = "Permission Needed"
            bodyText?.text = "مائیکروفون کی اجازت درکار ہے۔ براہ کرم ایپ میں اجازت دیں۔"
            return
        }

        scope.launch {
            val settings = entryPoint.appPreferences().settingsFlow.first()
            statusText?.text = "Listening..."
            bodyText?.text = "سن رہی ہوں... اپنا حکم بولیں"
            recognizer.startListening(
                languageTag = settings.sttLanguagePrimary,
                silenceTimeoutMs = settings.silenceTimeoutMs,
                source = RecognitionSource.VOICE_INTERACTION
            )
        }
    }

    private fun executeSpokenCommand(spokenText: String) {
        val clean = spokenText.trim()
        if (clean.isBlank()) return

        commandJob?.cancel()
        commandJob = scope.launch {
            statusText?.text = "Thinking..."
            bodyText?.text = clean

            val settings = entryPoint.appPreferences().settingsFlow.first()
            val wantsScreen = clean.lowercase().let {
                it.contains("screen") || it.contains("اسکرین") || it.contains("isko translate")
            }
            val screenText = if (wantsScreen && settings.screenAssistantEnabled) {
                AxtraAccessibilityService.instance?.captureScreenText()?.getOrNull() ?: assistContextText
            } else null

            val response = entryPoint.aiBrainRepository().processCommand(clean, screenText)
            entryPoint.actionExecutor().executeAction(clean, response) { finalReply, success ->
                statusText?.text = if (success) "Executed" else "Notice"
                bodyText?.text = finalReply
                entryPoint.ttsEngine().speak(finalReply) {
                    scheduleAutoHide(3200L)
                }
            }
        }
    }

    private fun scheduleAutoHide(delayMs: Long) {
        autoHideJob?.cancel()
        autoHideJob = scope.launch {
            delay(delayMs)
            if (isSessionOpen && entryPoint.actionExecutor().executionState.value.confirmation == null) {
                hide()
            }
        }
    }

    override fun onHide() {
        super.onHide()
        isSessionOpen = false
        autoHideJob?.cancel()
        commandJob?.cancel()
        observerJob?.cancel()
        entryPoint.speechRecognizer().stopListening()
        entryPoint.hotwordCoordinator().setSessionActive(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        isSessionOpen = false
        entryPoint.hotwordCoordinator().setSessionActive(false)
        scope.cancel()
    }
}
