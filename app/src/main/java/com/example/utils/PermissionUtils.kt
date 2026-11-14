package com.example.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.accessibility.AxtraAccessibilityService
import com.example.service.AxtraNotificationListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PermissionItemInfo(
    val id: String,
    val titleEn: String,
    val titleUr: String,
    val whyNeededEn: String,
    val whyNeededUr: String,
    val willNotDoEn: String,
    val willNotDoUr: String,
    val runtimePermissions: List<String> = emptyList(),
    val isSpecialAccess: Boolean = false,
    val isZeroPermissionFeature: Boolean = false
)

@Singleton
class PermissionUtils @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getAllPermissionItems(): List<PermissionItemInfo> = listOf(
        PermissionItemInfo(
            id = "microphone",
            titleEn = "Microphone",
            titleUr = "مائیکروفون",
            whyNeededEn = "Required to hear your Urdu, Saraiki, and English voice commands.",
            whyNeededUr = "آپ کی اردو، سرائیکی اور انگلش آواز سننے کے لیے ضروری ہے۔",
            willNotDoEn = "Axtra will NOT record or upload raw audio in the background.",
            willNotDoUr = "ایکسٹرا پس منظر میں آپ کی آڈیو ریکارڈ یا اپ لوڈ نہیں کرے گی۔",
            runtimePermissions = listOf(Manifest.permission.RECORD_AUDIO)
        ),
        PermissionItemInfo(
            id = "accessibility",
            titleEn = "Accessibility Service",
            titleUr = "ایکسیسبیلٹی سروس",
            whyNeededEn = "Enables multi-step phone control, typing, scrolling, and Screen Assistant.",
            whyNeededUr = "ملٹی اسٹیپ فون کنٹرول، ٹائپنگ اور اسکرین پڑھنے کے لیے ضروری ہے۔",
            willNotDoEn = "Axtra will NEVER read banking, wallet, or password manager apps.",
            willNotDoUr = "ایکسٹرا بینکنگ یا پاسورڈ ایپس کی اسکرین ہرگز نہیں پڑھے گی۔",
            isSpecialAccess = true
        ),
        PermissionItemInfo(
            id = "overlay",
            titleEn = "Display Over Other Apps",
            titleUr = "دیگر ایپس کے اوپر دکھائیں",
            whyNeededEn = "Shows the floating AI orb and soft edge glow while using other apps.",
            whyNeededUr = "دوسری ایپس کے دوران فلوٹنگ اے آئی آرب دکھانے کے لیے۔",
            willNotDoEn = "Axtra will NOT block your screen touches or change screen resolution.",
            willNotDoUr = "ایکسٹرا آپ کے ٹچ یا اسکرین ریزولوشن میں رکاوٹ نہیں بنے گی۔",
            isSpecialAccess = true
        ),
        PermissionItemInfo(
            id = "notification_access",
            titleEn = "Notification Access",
            titleUr = "نوٹیفکیشن رسائی",
            whyNeededEn = "Reads and summarizes your unread WhatsApp/SMS notifications on request.",
            whyNeededUr = "آپ کے کہنے پر نئے نوٹیفکیشنز پڑھنے اور خلاصہ بتانے کے لیے۔",
            willNotDoEn = "Axtra will NOT store notifications permanently or share them externally.",
            willNotDoUr = "ایکسٹرا نوٹیفکیشنز کو مستقل محفوظ یا شیئر نہیں کرے گی۔",
            isSpecialAccess = true
        ),
        PermissionItemInfo(
            id = "post_notifications",
            titleEn = "Post Notifications",
            titleUr = "نوٹیفکیشن بھیجنے کی اجازت",
            whyNeededEn = "Shows the Always-On status bar control with Pause and Stop buttons.",
            whyNeededUr = "آل ویز آن اسٹیٹس بار میں Pause اور Stop بٹن دکھانے کے لیے۔",
            willNotDoEn = "Axtra will NOT send spam or marketing notifications.",
            willNotDoUr = "ایکسٹرا کوئی غیر ضروری یا اشتہاری نوٹیفکیشن نہیں بھیجے گی۔",
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else emptyList()
        ),
        PermissionItemInfo(
            id = "contacts",
            titleEn = "Contacts",
            titleUr = "کانٹیکٹس",
            whyNeededEn = "Looks up the exact contact number when you say 'Call Ahmed' or send a message.",
            whyNeededUr = "جب آپ کسی کو کال یا میسج کہیں تو درست نمبر تلاش کرنے کے لیے۔",
            willNotDoEn = "Axtra will NOT upload your contact list to any server.",
            willNotDoUr = "ایکسٹرا آپ کی کانٹیکٹ لسٹ کسی سرور پر اپ لوڈ نہیں کرے گی۔",
            runtimePermissions = listOf(Manifest.permission.READ_CONTACTS)
        ),
        PermissionItemInfo(
            id = "phone_call_log",
            titleEn = "Phone & Call Log",
            titleUr = "فون اور کال لاگ",
            whyNeededEn = "Places voice-confirmed calls and reads your last missed call.",
            whyNeededUr = "تصدیق کے بعد کال ملانے اور آخری مسڈ کال بتانے کے لیے۔",
            willNotDoEn = "Axtra will NEVER place a call without your confirmation or record calls.",
            willNotDoUr = "ایکسٹرا آپ کی تصدیق کے بغیر کال نہیں ملائے گی اور کال ریکارڈ نہیں کرے گی۔",
            runtimePermissions = listOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_PHONE_STATE
            )
        ),
        PermissionItemInfo(
            id = "sms",
            titleEn = "SMS",
            titleUr = "ایس ایم ایس",
            whyNeededEn = "Reads incoming SMS aloud and sends SMS after your confirmation.",
            whyNeededUr = "آنے والے ایس ایم ایس پڑھ کر سنانے اور تصدیق کے بعد بھیجنے کے لیے۔",
            willNotDoEn = "Axtra will NEVER send an SMS without your confirmation.",
            willNotDoUr = "ایکسٹرا آپ کی تصدیق کے بغیر کوئی ایس ایم ایس نہیں بھیجے گی۔",
            runtimePermissions = listOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS
            )
        ),
        PermissionItemInfo(
            id = "calendar",
            titleEn = "Calendar",
            titleUr = "کیلنڈر",
            whyNeededEn = "Reads today's schedule for your Daily Briefing and creates event reminders.",
            whyNeededUr = "ڈیلی بریفنگ میں آج کا شیڈول بتانے اور ریمائنڈر بنانے کے لیے۔",
            willNotDoEn = "Axtra will NOT delete existing calendar events on its own.",
            willNotDoUr = "ایکسٹرا خود سے آپ کے کیلنڈر ایونٹس حذف نہیں کرے گی۔",
            runtimePermissions = listOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            )
        ),
        PermissionItemInfo(
            id = "camera",
            titleEn = "Camera",
            titleUr = "کیمرہ",
            whyNeededEn = "Powers Photo & Document OCR Reader, QR Scanner, and Flashlight control.",
            whyNeededUr = "دستاویز پڑھنے (OCR)، کیو آر اسکینر اور فلیش لائٹ کے لیے۔",
            willNotDoEn = "Axtra will NOT activate the camera without you opening a camera tool.",
            willNotDoUr = "ایکسٹرا آپ کے کھولے بغیر کیمرہ آن نہیں کرے گی۔",
            runtimePermissions = listOf(Manifest.permission.CAMERA)
        ),
        PermissionItemInfo(
            id = "media_files",
            titleEn = "Photos & Files (Zero-Permission Picker)",
            titleUr = "تصاویر اور فائلیں (محفوظ پکر)",
            whyNeededEn = "Uses Android Photo Picker & Document Picker only for files you choose.",
            whyNeededUr = "صرف آپ کی منتخب کردہ تصاویر اور فائلیں کھولنے کے لیے محفوظ سسٹم پکر۔",
            willNotDoEn = "Axtra does NOT request broad storage access to scan your personal gallery.",
            willNotDoUr = "ایکسٹرا آپ کی تمام گیلری اسکین کرنے کی اجازت نہیں مانگتی۔",
            isZeroPermissionFeature = true
        ),
        PermissionItemInfo(
            id = "location",
            titleEn = "Location",
            titleUr = "لوکیشن",
            whyNeededEn = "Calculates accurate local Prayer Times and Weather for your Daily Briefing.",
            whyNeededUr = "نماز کے درست اوقات اور موسم کی معلومات کے لیے۔",
            willNotDoEn = "Axtra will NOT track your location history in the background.",
            willNotDoUr = "ایکسٹرا پس منظر میں آپ کی لوکیشن ہسٹری ٹریک نہیں کرے گی۔",
            runtimePermissions = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        ),
        PermissionItemInfo(
            id = "bluetooth",
            titleEn = "Bluetooth",
            titleUr = "بلوٹوتھ",
            whyNeededEn = "Connects or checks Bluetooth audio status for hands-free listening.",
            whyNeededUr = "ہینڈز فری بلوٹوتھ ڈیوائسز سے رابطے کے لیے۔",
            willNotDoEn = "Axtra will NOT scan nearby devices for tracking.",
            willNotDoUr = "ایکسٹرا قریبی ڈیوائسز کو ٹریکنگ کے لیے اسکین نہیں کرے گی۔",
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Manifest.permission.BLUETOOTH_CONNECT)
            } else emptyList()
        ),
        PermissionItemInfo(
            id = "usage_access",
            titleEn = "Usage Access",
            titleUr = "استعمال کی تفصیل (Usage Access)",
            whyNeededEn = "Powers Screen Time statistics and Focus Mode app blocking.",
            whyNeededUr = "اسکرین ٹائم اسٹیٹس اور فوکس موڈ کے لیے ضروری ہے۔",
            willNotDoEn = "Axtra will NOT upload your app usage data off your device.",
            willNotDoUr = "ایکسٹرا آپ کے ایپ استعمال کا ڈیٹا ڈیوائس سے باہر نہیں بھیجے گی۔",
            isSpecialAccess = true
        ),
        PermissionItemInfo(
            id = "battery_opt",
            titleEn = "Battery Optimization Exemption",
            titleUr = "بیٹری آپٹیمائزیشن استثنیٰ",
            whyNeededEn = "Prevents Android from killing Always-On voice mode in the background.",
            whyNeededUr = "آل ویز آن سروس کو پس منظر میں بند ہونے سے بچانے کے لیے۔",
            willNotDoEn = "Axtra automatically pauses when battery drops below 10%.",
            willNotDoUr = "بیٹری 10% سے کم ہونے پر ایکسٹرا خودکار رک جاتی ہے۔",
            isSpecialAccess = true
        ),
        PermissionItemInfo(
            id = "exact_alarms",
            titleEn = "Exact Alarms",
            titleUr = "درست وقت کے الارم",
            whyNeededEn = "Triggers Daily Briefing and Prayer/Azan reminders on exact time.",
            whyNeededUr = "ڈیلی بریفنگ اور نماز ریمائنڈر عین وقت پر چلانے کے لیے۔",
            willNotDoEn = "Axtra will NOT schedule hidden wakeups.",
            willNotDoUr = "ایکسٹرا کوئی خفیہ الرٹ شیڈول نہیں کرے گی۔",
            isSpecialAccess = true
        ),
        PermissionItemInfo(
            id = "dnd_access",
            titleEn = "Do Not Disturb Access",
            titleUr = "ڈو ناٹ ڈسٹرب رسائی",
            whyNeededEn = "Lets Axtra toggle DND during Study Mode, Good Night, or Prayer time.",
            whyNeededUr = "نماز یا اسٹڈی موڈ کے دوران فون کو خاموش کرنے کے لیے۔",
            willNotDoEn = "Axtra only changes DND when you ask or enable a routine.",
            willNotDoUr = "ایکسٹرا صرف آپ کے کہنے پر DND تبدیل کرے گی۔",
            isSpecialAccess = true
        ),
        PermissionItemInfo(
            id = "write_settings",
            titleEn = "Modify System Settings",
            titleUr = "سسٹم سیٹنگز کی تبدیلی",
            whyNeededEn = "Allows voice adjustment of screen brightness.",
            whyNeededUr = "آواز کے ذریعے اسکرین کی روشنی کم یا زیادہ کرنے کے لیے۔",
            willNotDoEn = "Axtra will NOT modify network or security settings silently.",
            willNotDoUr = "ایکسٹرا خاموشی سے سیکیورٹی سیٹنگز تبدیل نہیں کرے گی۔",
            isSpecialAccess = true
        )
    )

    fun isGranted(id: String): Boolean {
        return when (id) {
            "accessibility" -> AxtraAccessibilityService.isServiceConnected.value || isAccessibilityEnabledInSettings()
            "overlay" -> Settings.canDrawOverlays(context)
            "notification_access" -> AxtraNotificationListenerService.isConnected.value ||
                NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            "usage_access" -> hasUsageStatsAccess()
            "battery_opt" -> {
                val pm = context.getSystemService(PowerManager::class.java)
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
            "exact_alarms" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val am = context.getSystemService(AlarmManager::class.java)
                    am?.canScheduleExactAlarms() == true
                } else true
            }
            "dnd_access" -> {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.isNotificationPolicyAccessGranted == true
            }
            "write_settings" -> Settings.System.canWrite(context)
            "media_files" -> true
            else -> {
                val item = getAllPermissionItems().firstOrNull { it.id == id } ?: return false
                if (item.runtimePermissions.isEmpty()) true
                else item.runtimePermissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
    }

    @SuppressLint("BatteryLife")
    fun buildSpecialAccessIntent(id: String): Intent {
        val pkgUri = Uri.parse("package:${context.packageName}")
        val intent = when (id) {
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            "notification_access" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            "usage_access" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            "battery_opt" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
            "exact_alarms" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri)
            } else Intent(Settings.ACTION_SETTINGS)
            "dnd_access" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "write_settings" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun isAccessibilityEnabledInSettings(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return enabled.contains(context.packageName)
    }

    private fun hasUsageStatsAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
