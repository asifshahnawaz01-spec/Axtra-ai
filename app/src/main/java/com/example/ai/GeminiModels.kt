package com.example.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class AxtraIntent {
    OPEN_APP,
    CLOSE_APP,
    CALL,
    SMS,
    WHATSAPP_MESSAGE,
    SEARCH_WEB,
    PLAY_MEDIA,
    SYSTEM_TOGGLE,
    ALARM,
    TIMER,
    REMINDER,
    CALENDAR,
    CAMERA,
    READ_SCREEN,
    READ_NOTIFICATIONS,
    NAVIGATE,
    TRANSLATE,
    NOTE,
    ROUTINE,
    CHAT,
    UNKNOWN
}

@Serializable
data class AiActionResponse(
    val intent: AxtraIntent = AxtraIntent.UNKNOWN,
    val app: String = "",
    val target: String = "",
    val parameters: Map<String, String> = emptyMap(),
    @SerialName("needs_confirmation")
    val needsConfirmation: Boolean = false,
    @SerialName("spoken_reply")
    val spokenReply: String = "",
    val isOffline: Boolean = false,
    val rawError: String? = null
)

@Serializable
data class ChatTurn(
    val role: String, // "user" or "model"
    val text: String
)
