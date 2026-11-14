package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.AppPreferences
import com.example.core.AxtraConstants
import com.example.core.AxtraLogger
import com.example.data.repository.BlockedAppsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AxtraAccessibilityService : AccessibilityService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AxtraAccessibilityEntryPoint {
        fun blockedAppsRepository(): BlockedAppsRepository
        fun appPreferences(): AppPreferences
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var entryPoint: AxtraAccessibilityEntryPoint? = null
    private var lastEventTimeMs = 0L
    private var volumeDownPressTimeMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            AxtraAccessibilityEntryPoint::class.java
        )
        AxtraLogger.d("Accessibility", "AxtraAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()
        // Throttle accessibility event processing to protect CPU/battery
        if (now - lastEventTimeMs < 200L) return
        lastEventTimeMs = now

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotBlank()) {
            _currentForegroundPackage.value = pkg
            _lastScreenChangeTimestamp.value = now
            checkFocusModeRestriction(pkg)
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                volumeDownPressTimeMs = System.currentTimeMillis()
            } else if (event.action == KeyEvent.ACTION_UP) {
                val heldMs = System.currentTimeMillis() - volumeDownPressTimeMs
                if (heldMs in 650L..3500L) {
                    scope.launch {
                        val enabled = entryPoint?.appPreferences()?.settingsFlow?.first()
                            ?.volumeDownShortcutEnabled ?: false
                        if (enabled) {
                            val launch = Intent(AxtraConstants.ACTION_TALK_TO_AXTRA).apply {
                                setPackage(packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(launch)
                        }
                    }
                    return true
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun checkFocusModeRestriction(pkg: String) {
        if (pkg == packageName || pkg.contains("launcher") || pkg == "com.android.systemui") return
        scope.launch {
            val settings = entryPoint?.appPreferences()?.settingsFlow?.first() ?: return@launch
            if (settings.focusModeActiveUntilMs > System.currentTimeMillis()) {
                val blockedSet = settings.focusBlockedPackagesCsv
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (blockedSet.any { it.equals(pkg, ignoreCase = true) }) {
                    AxtraLogger.d("Accessibility", "Focus mode redirecting from $pkg")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    override fun onInterrupt() {
        AxtraLogger.w("Accessibility", "AxtraAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        _isServiceConnected.value = false
    }

    suspend fun isCurrentAppBlocked(): Boolean {
        val pkg = _currentForegroundPackage.value
        if (pkg.isBlank()) return false
        val fromDb = entryPoint?.blockedAppsRepository()?.isPackageBlocked(pkg) ?: false
        val heuristicBlocked = listOf("bank", "wallet", "pay", "easypaisa", "jazzcash", "password", "vault")
            .any { pkg.lowercase().contains(it) }
        return fromDb || heuristicBlocked
    }

    suspend fun captureScreenText(): Result<String> {
        if (isCurrentAppBlocked()) {
            return Result.failure(
                SecurityException("بینکنگ یا پاسورڈ ایپ محفوظ ہے — اسکرین ریڈنگ بند رکھی گئی ہے۔")
            )
        }
        val root = rootInActiveWindow
            ?: return Result.failure(IllegalStateException("اسکرین کا مواد دستیاب نہیں ہے۔"))

        val sb = StringBuilder()
        collectNodeText(root, sb, depth = 0)
        val text = sb.toString().trim()
        return if (text.isEmpty()) {
            Result.failure(IllegalStateException("اسکرین پر کوئی متن نہیں ملا۔"))
        } else {
            Result.success(text)
        }
    }

    private fun collectNodeText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 25 || sb.length > 4500) return
        val txt = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!txt.isNullOrEmpty()) sb.appendLine(txt)
        else if (!desc.isNullOrEmpty()) sb.appendLine(desc)

        for (i in 0 until node.childCount) {
            collectNodeText(node.getChild(i), sb, depth + 1)
        }
    }

    suspend fun clickNodeByText(query: String): Boolean {
        if (isCurrentAppBlocked()) return false
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(query)
        for (node in matches) {
            if (clickSelfOrParent(node)) return true
        }
        return false
    }

    suspend fun clickNodeByViewId(viewId: String): Boolean {
        if (isCurrentAppBlocked()) return false
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByViewId(viewId)
        for (node in matches) {
            if (clickSelfOrParent(node)) return true
        }
        return false
    }

    suspend fun typeIntoFocusedOrEditableField(text: String): Boolean {
        if (isCurrentAppBlocked()) return false
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = if (focused != null && focused.isEditable) focused else findFirstEditable(root)
        if (target != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun clickSelfOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        var hops = 0
        while (current != null && hops < 5) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            hops++
        }
        return false
    }

    fun performScrollForward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findScrollableAndPerform(root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun performScrollBackward(): Boolean {
        val root = rootInActiveWindow ?: return false
        return findScrollableAndPerform(root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private fun findScrollableAndPerform(node: AccessibilityNodeInfo?, action: Int): Boolean {
        if (node == null) return false
        if (node.isScrollable) {
            return node.performAction(action)
        }
        for (i in 0 until node.childCount) {
            if (findScrollableAndPerform(node.getChild(i), action)) return true
        }
        return false
    }

    fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun performBackAction(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHomeAction(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecentsAction(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotificationsShade(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun takeScreenshotAction(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else false
    }

    fun lockScreenAction(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else false
    }

    companion object {
        @Volatile
        var instance: AxtraAccessibilityService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _currentForegroundPackage = MutableStateFlow("")
        val currentForegroundPackage: StateFlow<String> = _currentForegroundPackage.asStateFlow()

        private val _lastScreenChangeTimestamp = MutableStateFlow(0L)
        val lastScreenChangeTimestamp: StateFlow<Long> = _lastScreenChangeTimestamp.asStateFlow()
    }
}
