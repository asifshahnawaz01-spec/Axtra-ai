package com.example.core

import android.util.Log

object AxtraLogger {
    private const val TAG = "AxtraAI"

    fun d(tag: String, message: String) {
        Log.d("$TAG/$tag", redact(message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("$TAG/$tag", redact(message), throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$TAG/$tag", redact(message), throwable)
    }

    private fun redact(input: String): String {
        return input.replace(Regex("AIza[0-9A-Za-z\\-_]{20,}"), "[REDACTED_KEY]")
            .replace(Regex("key=[0-9A-Za-z\\-_]+"), "key=[REDACTED]")
    }
}
