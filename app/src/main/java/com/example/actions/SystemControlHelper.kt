package com.example.actions

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SystemActionOutcome(
    val verified: Boolean,
    val messageUr: String,
    val messageEn: String
)

@Singleton
class SystemControlHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var isTorchCurrentlyOn = false

    fun toggleFlashlight(turnOn: Boolean? = null): SystemActionOutcome {
        return try {
            val cm = context.getSystemService(CameraManager::class.java)
            val cameraId = cm?.cameraIdList?.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return SystemActionOutcome(
                verified = false,
                messageUr = "اس ڈیوائس میں فلیش لائٹ دستیاب نہیں ہے۔",
                messageEn = "Flashlight hardware not available on this device."
            )
            val targetState = turnOn ?: !isTorchCurrentlyOn
            cm.setTorchMode(cameraId, targetState)
            isTorchCurrentlyOn = targetState
            SystemActionOutcome(
                verified = true,
                messageUr = if (targetState) "ٹارچ آن کر دی گئی ہے۔" else "ٹارچ بند کر دی گئی ہے۔",
                messageEn = if (targetState) "Flashlight turned on." else "Flashlight turned off."
            )
        } catch (e: Exception) {
            SystemActionOutcome(
                verified = false,
                messageUr = "ٹارچ کنٹرول کرنے میں خرابی: ${e.message}",
                messageEn = "Failed to toggle flashlight: ${e.message}"
            )
        }
    }

    fun adjustVolume(action: String, streamType: Int = AudioManager.STREAM_MUSIC): SystemActionOutcome {
        val am = context.getSystemService(AudioManager::class.java)
            ?: return SystemActionOutcome(false, "آڈیو سروس دستیاب نہیں۔", "Audio service unavailable.")
        return when (action.lowercase()) {
            "up", "increase", "zyada" -> {
                am.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                SystemActionOutcome(true, "آواز بڑھا دی گئی ہے۔", "Volume increased.")
            }
            "down", "decrease", "kam" -> {
                am.adjustStreamVolume(streamType, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                SystemActionOutcome(true, "آواز کم کر دی گئی ہے۔", "Volume decreased.")
            }
            "mute", "silent", "band" -> {
                am.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                SystemActionOutcome(true, "آواز میوٹ کر دی گئی ہے۔", "Audio muted.")
            }
            "unmute" -> {
                am.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                SystemActionOutcome(true, "آواز بحال کر دی گئی ہے۔", "Audio unmuted.")
            }
            else -> {
                val pct = Regex("\\d+").find(action)?.value?.toIntOrNull()?.coerceIn(0, 100)
                if (pct != null) {
                    val max = am.getStreamMaxVolume(streamType)
                    am.setStreamVolume(streamType, (max * pct / 100), AudioManager.FLAG_SHOW_UI)
                    SystemActionOutcome(true, "والیوم $pct فیصد پر سیٹ کر دیا گیا ہے۔", "Volume set to $pct%.")
                } else {
                    SystemActionOutcome(false, "والیوم کی واضح کمانڈ نہیں ملی۔", "Unknown volume command.")
                }
            }
        }
    }

    fun adjustBrightness(levelOrAction: String): SystemActionOutcome {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return SystemActionOutcome(
                verified = false,
                messageUr = "سسٹم سیٹنگز بدلنے کی اجازت نہیں ہے، اس لیے ڈسپلے سیٹنگز کھول دی گئی ہیں۔",
                messageEn = "Modify system settings not granted; opened Display Settings."
            )
        }
        val target = when (levelOrAction.lowercase()) {
            "low", "kam", "dim" -> 40
            "medium", "normal" -> 128
            "high", "full", "zyada" -> 240
            else -> {
                val pct = Regex("\\d+").find(levelOrAction)?.value?.toIntOrNull()?.coerceIn(5, 100)
                if (pct != null) (pct * 255 / 100) else 140
            }
        }
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
        return SystemActionOutcome(true, "اسکرین کی روشنی تبدیل کر دی گئی ہے۔", "Screen brightness updated.")
    }

    fun toggleDoNotDisturb(enable: Boolean): SystemActionOutcome {
        val nm = context.getSystemService(NotificationManager::class.java)
            ?: return SystemActionOutcome(false, "نوٹیفکیشن سروس دستیاب نہیں۔", "NotificationManager unavailable.")
        if (!nm.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return SystemActionOutcome(
                verified = false,
                messageUr = "Do Not Disturb اجازت درکار ہے، سیٹنگز اسکرین کھول دی گئی ہے۔",
                messageEn = "DND access required; opened DND settings."
            )
        }
        nm.setInterruptionFilter(
            if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return SystemActionOutcome(
            verified = true,
            messageUr = if (enable) "ڈو ناٹ ڈسٹرب آن کر دیا گیا ہے۔" else "ڈو ناٹ ڈسٹرب بند کر دیا گیا ہے۔",
            messageEn = if (enable) "Do Not Disturb enabled." else "Do Not Disturb disabled."
        )
    }

    fun openConnectivityPanel(setting: String): SystemActionOutcome {
        val intent = when (setting.lowercase()) {
            "wifi", "wi-fi" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "mobile_data", "data", "internet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            } else Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            "location", "gps" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "hotspot" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
            "battery", "battery_saver" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            SystemActionOutcome(
                verified = true,
                messageUr = "$setting سیٹنگز پینل کھول دیا گیا ہے۔ (اینڈرائیڈ پابندی کی وجہ سے صارف خود ٹوگل کرے گا)",
                messageEn = "Opened $setting panel."
            )
        } catch (e: Exception) {
            SystemActionOutcome(false, "پینل کھولنے میں ناکامی۔", "Failed to open settings panel.")
        }
    }

    fun dispatchMediaKey(command: String): SystemActionOutcome {
        val am = context.getSystemService(AudioManager::class.java)
            ?: return SystemActionOutcome(false, "آڈیو سروس دستیاب نہیں۔", "Audio service unavailable.")
        val keyCode = when (command.lowercase()) {
            "pause", "stop" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play", "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "next", "skip" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val eventTime = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
        return SystemActionOutcome(true, "میڈیا کمانڈ ($command) بھیج دی گئی ہے۔", "Media command ($command) sent.")
    }
}
