package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val preferredLanguage: String = "AUTO",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudMemoryItem(
    @DocumentId val id: String = "",
    val memoryKey: String = "",
    val memoryValue: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "memoryKey" to memoryKey.take(120),
        "memoryValue" to memoryValue.take(2000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class CloudNoteItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "title" to title.ifBlank { "Voice Note" }.take(160),
        "content" to content.take(10000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class CloudExpenseItem(
    @DocumentId val id: String = "",
    val amount: Double = 0.0,
    val currency: String = "PKR",
    val category: String = "General",
    val description: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "amount" to amount.coerceAtLeast(0.01),
        "currency" to currency.ifBlank { "PKR" }.take(12),
        "category" to category.ifBlank { "General" }.take(80),
        "description" to description.take(500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class CloudTodoItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val listType: String = "TODO",
    val isDone: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "title" to title.take(240),
        "listType" to if (listType == "SHOPPING") "SHOPPING" else "TODO",
        "isDone" to isDone,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class CloudRoutineItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val triggerType: String = "VOICE",
    val triggerValue: String = "",
    val steps: List<String> = emptyList(),
    val isEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "name" to name.take(140),
        "triggerType" to if (triggerType in setOf("VOICE", "TIME", "CHARGING", "WIFI", "APP_OPENED")) triggerType else "VOICE",
        "triggerValue" to triggerValue.take(140),
        "steps" to steps.take(20).map { it.take(300) },
        "isEnabled" to isEnabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
