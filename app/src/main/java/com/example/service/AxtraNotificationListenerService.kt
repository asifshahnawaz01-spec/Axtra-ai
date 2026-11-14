package com.example.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val replyAction: Notification.Action? = null
)

class AxtraNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        _isConnected.value = true
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) instance = null
        _isConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshNotifications()
    }

    fun refreshNotifications() {
        val list = runCatching { activeNotifications?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.packageName != packageName && !it.isOngoing }
            .map { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                val pm = packageManager
                val appLabel = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                }.getOrDefault(sbn.packageName.substringAfterLast('.'))
                val replyAction = sbn.notification.actions?.firstOrNull { action ->
                    action.remoteInputs?.isNotEmpty() == true
                }
                CapturedNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appName = appLabel,
                    title = title,
                    text = text,
                    timestamp = sbn.postTime,
                    replyAction = replyAction
                )
            }
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .sortedByDescending { it.timestamp }

        _activeNotifications.value = list
    }

    fun replyToNotification(key: String, replyText: String): Boolean {
        val item = _activeNotifications.value.firstOrNull { it.key == key } ?: return false
        val action = item.replyAction ?: return false
        val remoteInputs = action.remoteInputs ?: return false
        val intent = Intent()
        val bundle = Bundle()
        remoteInputs.forEach { input ->
            bundle.putCharSequence(input.resultKey, replyText)
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
        return try {
            action.actionIntent.send(this, 0, intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun dismissAllNotifications() {
        runCatching { cancelAllNotifications() }
        _activeNotifications.value = emptyList()
    }

    companion object {
        @Volatile
        var instance: AxtraNotificationListenerService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _activeNotifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
        val activeNotifications: StateFlow<List<CapturedNotification>> = _activeNotifications.asStateFlow()
    }
}
