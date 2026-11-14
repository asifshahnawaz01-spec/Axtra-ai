package com.example.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "axtra_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            AxtraLogger.w("EncryptedKeyStore", "Falling back to private SharedPreferences", e)
            context.getSharedPreferences("axtra_secure_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API, key.trim()).apply()
    }

    fun getGeminiApiKey(): String {
        return prefs.getString(KEY_GEMINI_API, "")?.trim().orEmpty()
    }

    fun saveAppPin(pin: String) {
        prefs.edit().putString(KEY_APP_PIN, pin.trim()).apply()
    }

    fun getAppPin(): String {
        return prefs.getString(KEY_APP_PIN, "")?.trim().orEmpty()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_APP_PIN = "app_lock_pin"
    }
}
