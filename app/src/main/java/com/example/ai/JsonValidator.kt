package com.example.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

sealed class JsonValidationResult {
    data class Valid(val response: AiActionResponse) : JsonValidationResult()
    data class Invalid(val reason: String, val rawText: String) : JsonValidationResult()
}

@Singleton
class JsonValidator @Inject constructor(
    private val json: Json
) {
    fun parseAndValidate(rawText: String): JsonValidationResult {
        val cleaned = extractJsonObject(rawText)
            ?: return JsonValidationResult.Invalid("No JSON object found", rawText)

        return try {
            val obj = json.parseToJsonElement(cleaned).jsonObject
            validateJsonObject(obj, rawText)
        } catch (e: Exception) {
            JsonValidationResult.Invalid(e.message ?: "Malformed JSON", rawText)
        }
    }

    private fun validateJsonObject(obj: JsonObject, rawText: String): JsonValidationResult {
        val intentStr = obj["intent"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
            ?: return JsonValidationResult.Invalid("Missing 'intent' field", rawText)

        val intent = runCatching { AxtraIntent.valueOf(intentStr) }.getOrNull()
            ?: return JsonValidationResult.Invalid("Invalid intent: $intentStr", rawText)

        val spokenReply = obj["spoken_reply"]?.jsonPrimitive?.contentOrNull?.trim()
        if (spokenReply.isNullOrBlank()) {
            return JsonValidationResult.Invalid("Missing 'spoken_reply' field", rawText)
        }

        val app = obj["app"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val target = obj["target"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val needsConfirmation = obj["needs_confirmation"]?.jsonPrimitive?.booleanOrNull ?: false

        val paramsMap = mutableMapOf<String, String>()
        val paramsElement = obj["parameters"]
        if (paramsElement is JsonObject) {
            paramsElement.entries.forEach { (k, v) ->
                val strVal = if (v is JsonPrimitive) v.contentOrNull ?: v.toString() else v.toString()
                paramsMap[k] = strVal
            }
        }

        return JsonValidationResult.Valid(
            AiActionResponse(
                intent = intent,
                app = app,
                target = target,
                parameters = paramsMap,
                needsConfirmation = needsConfirmation,
                spokenReply = spokenReply
            )
        )
    }

    internal fun extractJsonObject(raw: String): String? {
        val stripped = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
            .trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return stripped.substring(start, end + 1)
    }
}
