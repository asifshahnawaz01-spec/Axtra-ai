package com.example.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.accessibility.AxtraAccessibilityService
import com.example.actions.ActionExecutor
import com.example.actions.PendingConfirmation
import com.example.ai.AiBrainRepository
import com.example.ai.AxtraIntent
import com.example.core.AppPreferences
import com.example.core.AxtraConstants
import com.example.core.AxtraLogger
import com.example.core.OrbState
import com.example.core.OverlayAnimationStyle
import com.example.voice.AxtraSpeechRecognizer
import com.example.voice.AxtraTtsEngine
import com.example.voice.HotwordCoordinator
import com.example.voice.RecognitionSource
import com.example.voice.SpeechResultEvent
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
import kotlin.math.abs
import kotlin.math.sin

class OverlayService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface OverlayEntryPoint {
        fun appPreferences(): AppPreferences
        fun actionExecutor(): ActionExecutor
        fun ttsEngine(): AxtraTtsEngine
        fun aiBrainRepository(): AiBrainRepository
        fun speechRecognizer(): AxtraSpeechRecognizer
        fun hotwordCoordinator(): HotwordCoordinator
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private lateinit var entryPoint: OverlayEntryPoint

    private var orbView: FloatingOrbCanvasView? = null
    private var orbParams: WindowManager.LayoutParams? = null
    private var edgeGlowView: EdgeGlowOverlayView? = null
    private var quickMenuView: View? = null
    private var statusBubbleView: LinearLayout? = null
    private var statusTitleText: TextView? = null
    private var statusBodyText: TextView? = null
    private var confirmButtonsRow: LinearLayout? = null

    private var currentStyle: OverlayAnimationStyle = OverlayAnimationStyle.ORB
    private var currentIntensity: Float = 0.8f
    private var lowEndMode: Boolean = false

    private var currentOrbState: OrbState = OrbState.IDLE
    private var hideBubbleJob: Job? = null
    private var followUpJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WindowManager::class.java)
        entryPoint = EntryPointAccessors.fromApplication(applicationContext, OverlayEntryPoint::class.java)

        scope.launch {
            entryPoint.appPreferences().settingsFlow.collectLatest { settings ->
                currentStyle = settings.overlayAnimationStyle
                currentIntensity = settings.overlayIntensity
                lowEndMode = settings.lowEndPhoneMode
                refreshOverlayViews()
            }
        }

        observeOverlayVoicePipeline()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == AxtraConstants.ACTION_OVERLAY_START_LISTENING) {
            val initialCommand = intent.getStringExtra(AxtraConstants.EXTRA_INITIAL_COMMAND)
            if (!initialCommand.isNullOrBlank()) {
                executeOverlayCommand(initialCommand)
            } else {
                triggerOverlayAutoCommand()
            }
        }
        return START_STICKY
    }

    private fun observeOverlayVoicePipeline() {
        val recognizer = entryPoint.speechRecognizer()
        val executor = entryPoint.actionExecutor()
        val tts = entryPoint.ttsEngine()

        scope.launch {
            recognizer.rmsLevel.collectLatest { rms ->
                orbView?.updateRms(rms)
            }
        }

        scope.launch {
            recognizer.partialText.collectLatest { partial ->
                if (currentOrbState == OrbState.LISTENING && partial.isNotBlank()) {
                    showStatusBubble(
                        title = "سن رہی ہوں... (Listening)",
                        body = partial,
                        confirmation = null
                    )
                }
            }
        }

        scope.launch {
            executor.executionState.collectLatest { execState ->
                val confirmation = execState.confirmation
                if (confirmation != null && (currentOrbState == OrbState.THINKING || currentOrbState == OrbState.SPEAKING || statusBubbleView != null)) {
                    showStatusBubble(
                        title = confirmation.title,
                        body = confirmation.description,
                        confirmation = confirmation
                    )
                }
            }
        }

        scope.launch {
            recognizer.events.collectLatest { event ->
                if (event.source != RecognitionSource.OVERLAY) return@collectLatest
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
                        executeOverlayCommand(spoken)
                    }
                    is SpeechResultEvent.LowConfidence -> {
                        val prompt = "دوبارہ بولیے، آپ کی آواز واضح نہیں آئی۔"
                        updateOrbState(OrbState.ERROR)
                        showStatusBubble("Axtra AI", prompt, autoDismissMs = 3500L)
                        tts.speak(prompt) {
                            scope.launch {
                                updateOrbState(OrbState.IDLE)
                                entryPoint.hotwordCoordinator().setSessionActive(false)
                            }
                        }
                    }
                    is SpeechResultEvent.Error -> {
                        updateOrbState(OrbState.ERROR)
                        showStatusBubble("Axtra AI", event.messageUr, autoDismissMs = 3800L)
                        if (event.code == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            tts.speak(event.messageUr)
                        }
                        scope.launch {
                            delay(2500L)
                            if (currentOrbState == OrbState.ERROR) updateOrbState(OrbState.IDLE)
                            entryPoint.hotwordCoordinator().setSessionActive(false)
                        }
                    }
                }
            }
        }
    }

    /**
     * Tapping the always-on-screen orb directly listens, processes via Gemini/Offline,
     * auto-executes the action, and speaks the reply WITHOUT opening MainActivity.
     */
    private fun triggerOverlayAutoCommand() {
        val recognizer = entryPoint.speechRecognizer()
        val tts = entryPoint.ttsEngine()
        followUpJob?.cancel()
        hideBubbleJob?.cancel()
        dismissQuickMenu()

        // Barge-in: interrupt speaking immediately
        if (tts.isSpeaking.value) {
            tts.stopSpeaking()
        }

        if (recognizer.isListening.value) {
            recognizer.stopListening()
            updateOrbState(OrbState.IDLE)
            dismissStatusBubble()
            return
        }

        if (!recognizer.hasRecordPermission()) {
            val msg = "مائیکروفون کی اجازت درکار ہے۔ براہ کرم ایپ میں اجازت دیں۔"
            updateOrbState(OrbState.ERROR)
            showStatusBubble("Permission Needed", msg, autoDismissMs = 4000L)
            tts.speak(msg) {
                scope.launch { updateOrbState(OrbState.IDLE) }
            }
            return
        }

        scope.launch {
            val settings = entryPoint.appPreferences().settingsFlow.first()
            updateOrbState(OrbState.LISTENING)
            showStatusBubble(
                title = "سن رہی ہوں... (Listening)",
                body = "ابھی بولیں — کمانڈ خودکار طور پر چلے گی",
                confirmation = null
            )
            recognizer.startListening(
                languageTag = settings.sttLanguagePrimary,
                silenceTimeoutMs = settings.silenceTimeoutMs,
                source = RecognitionSource.OVERLAY
            )
        }
    }

    private fun executeOverlayCommand(spokenText: String) {
        val clean = spokenText.trim()
        if (clean.isBlank()) {
            updateOrbState(OrbState.IDLE)
            dismissStatusBubble()
            return
        }
        val aiRepo = entryPoint.aiBrainRepository()
        val executor = entryPoint.actionExecutor()
        val tts = entryPoint.ttsEngine()

        scope.launch {
            updateOrbState(OrbState.THINKING)
            showStatusBubble("سوچ رہی ہوں... (Thinking)", clean)

            val settings = entryPoint.appPreferences().settingsFlow.first()
            val wantsScreen = clean.lowercase().let {
                it.contains("screen") || it.contains("اسکرین") || it.contains("isko translate")
            }
            val screenText = if (wantsScreen && settings.screenAssistantEnabled) {
                AxtraAccessibilityService.instance?.captureScreenText()?.getOrNull()
            } else null

            val response = aiRepo.processCommand(clean, screenText)

            executor.executeAction(clean, response) { finalReply, success ->
                updateOrbState(if (success) OrbState.SPEAKING else OrbState.ERROR)
                showStatusBubble(
                    title = if (success) "Axtra Executed" else "Axtra Notice",
                    body = finalReply,
                    autoDismissMs = 4200L
                )
                tts.speak(finalReply) {
                    scope.launch {
                        updateOrbState(OrbState.IDLE)
                        val latest = entryPoint.appPreferences().settingsFlow.first()
                        if (latest.conversationModeEnabled && success && response.intent != AxtraIntent.UNKNOWN) {
                            startOverlayFollowUp(latest.sttLanguagePrimary, latest.silenceTimeoutMs)
                        } else {
                            entryPoint.hotwordCoordinator().setSessionActive(false)
                        }
                    }
                }
            }
        }
    }

    private fun startOverlayFollowUp(lang: String, silenceTimeoutMs: Int) {
        followUpJob?.cancel()
        followUpJob = scope.launch {
            delay(450L)
            val recognizer = entryPoint.speechRecognizer()
            updateOrbState(OrbState.LISTENING)
            showStatusBubble("مزید حکم؟ (Listening...)", "بولیں...")
            recognizer.startListening(lang, silenceTimeoutMs, RecognitionSource.OVERLAY)
            delay(7500L)
            if (recognizer.isListening.value && recognizer.partialText.value.isBlank()) {
                recognizer.stopListening()
                updateOrbState(OrbState.IDLE)
                dismissStatusBubble()
                entryPoint.hotwordCoordinator().setSessionActive(false)
            }
        }
    }

    private fun updateOrbState(state: OrbState) {
        currentOrbState = state
        orbView?.updateOrbState(state)
    }

    private fun refreshOverlayViews() {
        removeAllViews()
        if (currentStyle == OverlayAnimationStyle.OFF) {
            return
        }
        if (currentStyle == OverlayAnimationStyle.EDGE_GLOW) {
            attachEdgeGlow()
        }
        attachOrb()
    }

    private fun attachEdgeGlow() {
        if (edgeGlowView != null) return
        val view = EdgeGlowOverlayView(this, currentIntensity, lowEndMode)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        runCatching {
            windowManager.addView(view, params)
            edgeGlowView = view
        }.onFailure {
            AxtraLogger.w("OverlayService", "Failed adding edge glow", it)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOrb() {
        if (orbView != null) return
        val sizePx = (resources.displayMetrics.density * if (currentStyle == OverlayAnimationStyle.MINIMAL_DOT) 38 else 64).toInt()
        val view = FloatingOrbCanvasView(this, currentStyle, currentIntensity, lowEndMode).apply {
            updateOrbState(currentOrbState)
        }
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - sizePx - 24
            y = resources.displayMetrics.heightPixels / 3
        }

        val longPressHandler = Handler(Looper.getMainLooper())
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false
        var longPressed = false

        val longPressRunnable = Runnable {
            longPressed = true
            showQuickMenu(params.x, params.y)
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    longPressed = false
                    longPressHandler.postDelayed(longPressRunnable, 550L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        moved = true
                        longPressHandler.removeCallbacks(longPressRunnable)
                        params.x = initialX + dx
                        params.y = initialY + dy
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (!moved && !longPressed) {
                        // Directly auto-execute voice command WITHOUT opening MainActivity
                        triggerOverlayAutoCommand()
                    } else if (moved) {
                        val screenW = resources.displayMetrics.widthPixels
                        params.x = if (params.x + sizePx / 2 < screenW / 2) 16 else screenW - sizePx - 16
                        params.y = params.y.coerceIn(80, resources.displayMetrics.heightPixels - sizePx - 120)
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            windowManager.addView(view, params)
            orbView = view
            orbParams = params
        }.onFailure {
            AxtraLogger.w("OverlayService", "Failed adding floating orb", it)
        }
    }

    private fun showStatusBubble(
        title: String,
        body: String,
        confirmation: PendingConfirmation? = null,
        autoDismissMs: Long? = null
    ) {
        hideBubbleJob?.cancel()
        val density = resources.displayMetrics.density
        if (statusBubbleView == null) {
            val pad = (density * 12).toInt()
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EE0B1224"))
                    cornerRadius = density * 16f
                    setStroke((density * 1.5f).toInt(), Color.parseColor("#00E5FF"))
                }
            }
            val titleTv = TextView(this).apply {
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 13f
            }
            val bodyTv = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(0, (density * 4).toInt(), 0, 0)
            }
            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (density * 8).toInt(), 0, 0)
                visibility = View.GONE
            }
            val yesBtn = Button(this).apply {
                text = "ہاں (Yes)"
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.parseColor("#00E5FF"))
                setOnClickListener {
                    entryPoint.actionExecutor().resolveConfirmation(true)
                    visibility = View.GONE
                }
            }
            val noBtn = Button(this).apply {
                text = "نہیں (No)"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#EF4444"))
                setOnClickListener {
                    entryPoint.actionExecutor().resolveConfirmation(false)
                    dismissStatusBubble()
                }
            }
            val lpBtn = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (density * 6).toInt()
            }
            btnRow.addView(yesBtn, lpBtn)
            btnRow.addView(noBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            container.addView(titleTv)
            container.addView(bodyTv)
            container.addView(btnRow)

            val anchorY = orbParams?.y ?: (resources.displayMetrics.heightPixels / 3)
            val widthPx = (density * 260).toInt()
            val anchorX = orbParams?.x ?: 24
            val bubbleX = if (anchorX > resources.displayMetrics.widthPixels / 2) {
                (anchorX - widthPx - (density * 12).toInt()).coerceAtLeast(16)
            } else {
                (anchorX + (density * 74).toInt()).coerceAtMost(resources.displayMetrics.widthPixels - widthPx - 16)
            }

            val bubbleParams = WindowManager.LayoutParams(
                widthPx,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bubbleX
                y = anchorY.coerceIn(80, (resources.displayMetrics.heightPixels - 320).coerceAtLeast(80))
            }

            runCatching {
                windowManager.addView(container, bubbleParams)
                statusBubbleView = container
                statusTitleText = titleTv
                statusBodyText = bodyTv
                confirmButtonsRow = btnRow
            }
        }

        statusTitleText?.text = title
        statusBodyText?.text = body
        confirmButtonsRow?.visibility = if (confirmation != null) View.VISIBLE else View.GONE

        if (autoDismissMs != null && confirmation == null) {
            hideBubbleJob = scope.launch {
                delay(autoDismissMs)
                dismissStatusBubble()
            }
        }
    }

    private fun dismissStatusBubble() {
        hideBubbleJob?.cancel()
        statusBubbleView?.let { runCatching { windowManager.removeView(it) } }
        statusBubbleView = null
        statusTitleText = null
        statusBodyText = null
        confirmButtonsRow = null
    }

    private fun showQuickMenu(anchorX: Int, anchorY: Int) {
        dismissQuickMenu()
        val pad = (resources.displayMetrics.density * 14).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#EE0B1224"))
        }

        val ownerView = TextView(this).apply {
            text = "${AxtraConstants.APP_NAME}  •  ${AxtraConstants.OWNER_LABEL}"
            setTextColor(Color.parseColor("#FFD54F"))
            textSize = 14f
        }
        container.addView(ownerView)

        fun addActionBtn(label: String, onClick: () -> Unit) {
            val btn = Button(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#3300E5FF"))
                setOnClickListener {
                    onClick()
                    dismissQuickMenu()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10 }
            container.addView(btn, lp)
        }

        addActionBtn("Open Axtra App") {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(openIntent)
        }
        addActionBtn("Stop Everything") {
            followUpJob?.cancel()
            entryPoint.speechRecognizer().cancelAndRelease()
            entryPoint.actionExecutor().stopEverything()
            updateOrbState(OrbState.IDLE)
            dismissStatusBubble()
        }
        addActionBtn("Pause Always-On") {
            scope.launch { entryPoint.appPreferences().updateAlwaysOnEnabled(false) }
            sendBroadcast(Intent(AxtraConstants.ACTION_PAUSE_SERVICE).setPackage(packageName))
        }
        addActionBtn("Mute Voice") {
            entryPoint.ttsEngine().stopSpeaking()
            scope.launch { entryPoint.appPreferences().updateSilentMode(true) }
        }
        addActionBtn("Close Menu") { }

        val menuParams = WindowManager.LayoutParams(
            (resources.displayMetrics.density * 230).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorX.coerceIn(24, (resources.displayMetrics.widthPixels - 500).coerceAtLeast(24))
            y = anchorY.coerceIn(100, (resources.displayMetrics.heightPixels - 560).coerceAtLeast(100))
        }
        runCatching {
            windowManager.addView(container, menuParams)
            quickMenuView = container
        }
    }

    private fun dismissQuickMenu() {
        quickMenuView?.let { runCatching { windowManager.removeView(it) } }
        quickMenuView = null
    }

    private fun removeAllViews() {
        dismissQuickMenu()
        dismissStatusBubble()
        orbView?.let {
            it.stopAnimation()
            runCatching { windowManager.removeView(it) }
        }
        orbView = null
        edgeGlowView?.let {
            it.stopAnimation()
            runCatching { windowManager.removeView(it) }
        }
        edgeGlowView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        followUpJob?.cancel()
        removeAllViews()
        scope.cancel()
    }
}

/**
 * Hardware-layered floating orb Canvas view with zero per-frame allocations and live OrbState feedback.
 */
@SuppressLint("ViewConstructor")
private class FloatingOrbCanvasView(
    context: Context,
    private val style: OverlayAnimationStyle,
    private val intensity: Float,
    private val lowEndMode: Boolean
) : View(context) {

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private var phase = 0f
    private var orbState = OrbState.IDLE
    private var rmsLevel = 0f

    private val animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = if (lowEndMode) 2600L else 1800L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        animator.start()
    }

    fun updateOrbState(state: OrbState) {
        orbState = state
        invalidate()
    }

    fun updateRms(rms: Float) {
        rmsLevel = rms.coerceIn(0f, 1f)
        if (orbState == OrbState.LISTENING) invalidate()
    }

    fun stopAnimation() {
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(cx, cy) - 6f
        val pulse = (sin(phase.toDouble()).toFloat() + 1f) * 0.5f

        val (r, g, b) = when (orbState) {
            OrbState.IDLE -> Triple(0, 229, 255)
            OrbState.LISTENING -> Triple(16, 185, 129)
            OrbState.THINKING -> Triple(139, 92, 246)
            OrbState.SPEAKING -> Triple(255, 213, 79)
            OrbState.ERROR -> Triple(239, 68, 68)
        }
        val audioBoost = if (orbState == OrbState.LISTENING) rmsLevel * 0.18f else 0f

        when (style) {
            OverlayAnimationStyle.MINIMAL_DOT -> {
                corePaint.color = Color.argb((175 + 75 * pulse).toInt().coerceIn(80, 255), r, g, b)
                canvas.drawCircle(cx, cy, maxR * (0.55f + 0.12f * pulse + audioBoost), corePaint)
            }
            OverlayAnimationStyle.WAVEFORM_BAR -> {
                corePaint.color = Color.argb(220, 10, 16, 36)
                canvas.drawCircle(cx, cy, maxR, corePaint)
                ringPaint.color = Color.argb((180 * intensity).toInt().coerceIn(70, 255), r, g, b)
                canvas.drawCircle(cx, cy, maxR - 2f, ringPaint)
                wavePaint.color = Color.rgb(r, g, b)
                for (i in -2..2) {
                    val barH = maxR * (0.25f + (0.35f + audioBoost) * ((sin((phase + i * 0.8f).toDouble()).toFloat() + 1f) * 0.5f))
                    val xPos = cx + i * (maxR * 0.24f)
                    canvas.drawLine(xPos, cy - barH, xPos, cy + barH, wavePaint)
                }
            }
            else -> {
                ringPaint.color = Color.argb(((110 + 130 * pulse) * intensity).toInt().coerceIn(50, 255), r, g, b)
                canvas.drawCircle(cx, cy, maxR * (0.80f + 0.12f * pulse + audioBoost).coerceAtMost(0.98f), ringPaint)
                corePaint.color = Color.argb(230, 13, 25, 52)
                canvas.drawCircle(cx, cy, maxR * 0.72f, corePaint)
                corePaint.color = Color.argb(235, 139, 92, 246)
                canvas.drawCircle(cx, cy, maxR * (0.34f + 0.08f * pulse + audioBoost), corePaint)
                corePaint.color = Color.argb(248, r, g, b)
                canvas.drawCircle(cx, cy, maxR * 0.20f, corePaint)
            }
        }
    }
}

/**
 * Hardware-layered border edge-glow view with pass-through touches and zero per-frame allocations.
 */
@SuppressLint("ViewConstructor")
private class EdgeGlowOverlayView(
    context: Context,
    private val intensity: Float,
    lowEndMode: Boolean
) : View(context) {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    private val borderRect = RectF()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = if (lowEndMode) 3200L else 2000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        animator.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        borderRect.set(4f, 4f, w - 4f, h - 4f)
        borderPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(
                Color.parseColor("#00E5FF"),
                Color.parseColor("#8B5CF6"),
                Color.parseColor("#FFD54F"),
                Color.parseColor("#00E5FF")
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }

    fun stopAnimation() {
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val pulse = (sin(phase.toDouble()).toFloat() + 1f) * 0.5f
        borderPaint.alpha = ((90 + 130 * pulse) * intensity).toInt().coerceIn(30, 255)
        canvas.drawRoundRect(borderRect, 48f, 48f, borderPaint)
    }
}
