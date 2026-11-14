package com.example.core

object AxtraConstants {
    const val OWNER_LABEL = "🅜🅡 🅐🅢🅘🅕"
    const val APP_NAME = "Axtra AI"
    const val APP_TAGLINE = "Your Personal AI Companion"
    const val APP_VERSION = "1.0.0"

    const val CHANNEL_ALWAYS_ON = "axtra_always_on_channel"
    const val CHANNEL_ALERTS = "axtra_alerts_channel"
    const val CHANNEL_BRIEFING = "axtra_briefing_channel"
    const val NOTIFICATION_ID_FOREGROUND = 1001
    const val NOTIFICATION_ID_PERMISSION_ALERT = 1002
    const val NOTIFICATION_ID_CALL_SMS = 1003

    const val ACTION_START_LISTENING = "com.example.ACTION_START_LISTENING"
    const val ACTION_PAUSE_SERVICE = "com.example.ACTION_PAUSE_SERVICE"
    const val ACTION_STOP_SERVICE = "com.example.ACTION_STOP_SERVICE"
    const val ACTION_STOP_EVERYTHING = "com.example.ACTION_STOP_EVERYTHING"
    const val ACTION_TOGGLE_ALWAYS_ON = "com.example.ACTION_TOGGLE_ALWAYS_ON"
    const val ACTION_TALK_TO_AXTRA = "com.example.ACTION_TALK_TO_AXTRA"
    const val ACTION_OVERLAY_START_LISTENING = "com.example.ACTION_OVERLAY_START_LISTENING"
    const val EXTRA_INITIAL_COMMAND = "com.example.EXTRA_INITIAL_COMMAND"
    const val DEFAULT_WAKE_WORDS_CSV = "hey astra, hello astra, astra"

    const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
    val AVAILABLE_GEMINI_MODELS = listOf(
        "gemini-3.8-flash" to "Gemini 3.8 Flash (Fast & Balanced)",
        "gemini-3.5-flash-lite" to "Gemini 3.5 Flash-Lite (Ultra Fast / High Quota)",
        "gemini-4-argon" to "Gemini 4 Argon (Deep Reasoning)"
    )

    const val DEFAULT_SYSTEM_PROMPT = """You are Axtra AI, a personal assistant owned by MR ASIF. Reply mainly in natural Urdu. If the user writes Roman Urdu, reply in Roman Urdu. Understand Urdu, Saraiki and English, including mixed sentences. Be short, polite and accurate. If a request is unclear, ask one short question instead of guessing. Never perform a risky action (message, call, payment, delete, install) without confirmation.

Always return ONLY valid JSON matching this exact schema (no markdown fences):
{
  "intent": "ONE OF: OPEN_APP, CLOSE_APP, CALL, SMS, WHATSAPP_MESSAGE, SEARCH_WEB, PLAY_MEDIA, SYSTEM_TOGGLE, ALARM, TIMER, REMINDER, CALENDAR, CAMERA, READ_SCREEN, READ_NOTIFICATIONS, NAVIGATE, TRANSLATE, NOTE, ROUTINE, CHAT, UNKNOWN",
  "app": "target app name or package if applicable, else empty string",
  "target": "contact name, query, URL, place, alarm time, note text, or target value",
  "parameters": { "key": "value" },
  "needs_confirmation": true or false,
  "spoken_reply": "short polite reply in Urdu / Roman Urdu / English matching user language"
}"""

    val SARAIKI_HINTS: Map<String, String> = mapOf(
        "kithe" to "kahan / کہاں (where)",
        "kia haal" to "kya haal hai / کیا حال ہے (how are you)",
        "karo" to "karein / کریں (do it)",
        "tusan" to "aap / آپ (you)",
        "asan" to "hum / ہم (we)",
        "wadda" to "bara / بڑا (big)",
        "nikka" to "chhota / چھوٹا (small)",
        "aaho" to "ji haan / جی ہاں (yes)",
        "koi na" to "koi nahi / کوئی نہیں (no/nothing)",
        "ajj" to "aaj / آج (today)",
        "sveray" to "subah / صبح (morning)",
        "ratin" to "raat ko / رات کو (at night)",
        "changa" to "achha / اچھا (good)",
        "manda" to "bura / برا (bad)",
        "dasso" to "bataein / بتائیں (tell me)",
        "sunao" to "sunayein / سنائیں (tell/share)",
        "vanjo" to "jao / جاؤ (go)",
        "ghino" to "lo / لو (take)",
        "devo" to "do / دو (give)",
        "kiven" to "kaise / کیسے (how)",
        "kithan" to "kahan se / کہاں سے (from where)",
        "meda" to "mera / میرا (my)",
        "teda" to "tumhara / تمہارا (your)"
    )

    val DEFAULT_BLOCKED_PACKAGES: Set<String> = setOf(
        "com.easypaisa.app",
        "pk.com.telenor.phoenix",
        "com.techlogix.mobilinkcustomer",
        "com.hbl.android.hblmobilebanking",
        "com.meezanbank.mb",
        "com.ubldigital.android",
        "com.alfalah.mobile",
        "com.paypal.android.p2pmobile",
        "com.google.android.apps.walletnfcrel",
        "com.samsung.android.spay",
        "com.lastpass.lpandroid",
        "com.x8bit.bitwarden",
        "com.agilebits.onepassword",
        "keepass2android.keepass2android",
        "com.binance.dev",
        "com.coinbase.android"
    )
}

enum class AxtraThemeStyle(val titleEn: String, val titleUr: String) {
    CYBER_BLUE("Cyber Blue", "سائبر بلیو"),
    ROYAL_GOLD("Royal Gold", "شاہی گولڈ"),
    MATRIX_GREEN("Matrix Green", "میٹرکس گرین"),
    PURE_AMOLED("Pure AMOLED Black", "خالص امولیڈ سیاہ")
}

enum class OverlayAnimationStyle(val titleEn: String, val titleUr: String) {
    ORB("Floating Orb", "فلوٹنگ آرب"),
    EDGE_GLOW("Edge Glow", "ایجز گلو"),
    WAVEFORM_BAR("Waveform Bar", "ویو فارم بار"),
    MINIMAL_DOT("Minimal Dot", "مختصر نقطہ"),
    OFF("Off", "بند")
}

enum class ConfirmationLevel(val titleEn: String, val titleUr: String) {
    LOW("Low (Risky only)", "کم (صرف خطرناک)"),
    MEDIUM("Medium (Recommended)", "درمیانہ (تجویز کردہ)"),
    HIGH("High (Confirm all)", "زیادہ (ہر کام کی تصدیق)")
}

enum class LanguageMode(val titleEn: String, val titleUr: String) {
    AUTO("Auto (Urdu/Saraiki/English)", "خودکار (اردو/سرائیکی/انگلش)"),
    URDU_ONLY("Urdu Only", "صرف اردو"),
    ENGLISH_ONLY("English Only", "صرف انگلش")
}

enum class UiLanguage(val code: String, val title: String) {
    ENGLISH("en", "English"),
    URDU("ur", "اردو")
}

enum class OrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

enum class PrayerCalculationMethod(val titleEn: String, val fajrAngle: Double, val ishaAngle: Double) {
    KARACHI("University of Islamic Sciences, Karachi", 18.0, 18.0),
    MWL("Muslim World League", 18.0, 17.0),
    ISNA("Islamic Society of North America", 15.0, 15.0),
    UMM_AL_QURA("Umm al-Qura University, Makkah", 18.5, 0.0),
    EGYPT("Egyptian General Authority", 19.5, 17.5)
}
