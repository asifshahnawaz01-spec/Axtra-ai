package com.example.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineCommandParser @Inject constructor() {

    fun parse(input: String): AiActionResponse {
        val text = input.trim()
        val lower = text.lowercase()

        // 1. Emergency / Kill Switch
        if (lower.contains("ruk jao") || lower.contains("stop everything") ||
            lower == "stop" || lower.contains("رک جاؤ") || lower.contains("بند کرو")
        ) {
            return AiActionResponse(
                intent = AxtraIntent.CHAT,
                target = "STOP_ALL",
                spokenReply = "ٹھیک ہے، تمام کام روک دیے گئے ہیں۔",
                isOffline = true
            )
        }

        // 2. Find my phone
        if (lower.contains("kahan ho") || lower.contains("find my phone") || lower.contains("کہاں ہو")) {
            return AiActionResponse(
                intent = AxtraIntent.SYSTEM_TOGGLE,
                target = "find_phone",
                parameters = mapOf("setting" to "find_phone", "state" to "on"),
                spokenReply = "میں یہاں ہوں! فون کی گھنٹی اور فلیش لائٹ چلا رہی ہوں۔",
                isOffline = true
            )
        }

        // 3. Flashlight
        if (lower.contains("torch") || lower.contains("flashlight") ||
            lower.contains("ٹارچ") || lower.contains("فلیش")
        ) {
            val turnOff = lower.contains("off") || lower.contains("band") || lower.contains("بند")
            val state = if (turnOff) "off" else "on"
            return AiActionResponse(
                intent = AxtraIntent.SYSTEM_TOGGLE,
                target = "flashlight",
                parameters = mapOf("setting" to "flashlight", "state" to state),
                spokenReply = if (turnOff) "ٹارچ بند کر دی گئی ہے۔" else "ٹارچ آن کر دی گئی ہے۔",
                isOffline = true
            )
        }

        // 4. Wi-Fi / Bluetooth / DND / Brightness / Volume
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("وائی فائی")) {
            return AiActionResponse(
                intent = AxtraIntent.SYSTEM_TOGGLE,
                target = "wifi",
                parameters = mapOf("setting" to "wifi", "state" to if (lower.contains("off") || lower.contains("band")) "off" else "on"),
                spokenReply = "وائی فائی پینل کھولا جا رہا ہے۔",
                isOffline = true
            )
        }

        if (lower.contains("bluetooth") || lower.contains("بلوٹوتھ")) {
            return AiActionResponse(
                intent = AxtraIntent.SYSTEM_TOGGLE,
                target = "bluetooth",
                parameters = mapOf("setting" to "bluetooth", "state" to if (lower.contains("off") || lower.contains("band")) "off" else "on"),
                spokenReply = "بلوٹوتھ پینل کھولا جا رہا ہے۔",
                isOffline = true
            )
        }

        if (lower.contains("volume") || lower.contains("awaz") || lower.contains("آواز")) {
            val state = when {
                lower.contains("mute") || lower.contains("band") || lower.contains("بند") -> "mute"
                lower.contains("down") || lower.contains("kam") || lower.contains("کم") -> "down"
                else -> "up"
            }
            return AiActionResponse(
                intent = AxtraIntent.SYSTEM_TOGGLE,
                target = "volume",
                parameters = mapOf("setting" to "volume", "state" to state),
                spokenReply = "والیوم تبدیل کیا جا رہا ہے۔",
                isOffline = true
            )
        }

        if (lower.contains("brightness") || lower.contains("roshni") || lower.contains("روشنی")) {
            val state = if (lower.contains("low") || lower.contains("kam") || lower.contains("کم")) "low" else "high"
            return AiActionResponse(
                intent = AxtraIntent.SYSTEM_TOGGLE,
                target = "brightness",
                parameters = mapOf("setting" to "brightness", "state" to state),
                spokenReply = "اسکرین کی روشنی ایڈجسٹ کی جا رہی ہے۔",
                isOffline = true
            )
        }

        // 5. Navigation Home / Back
        if (lower == "home" || lower.contains("go home") || lower.contains("ہوم پر جاؤ")) {
            return AiActionResponse(
                intent = AxtraIntent.SYSTEM_TOGGLE,
                target = "nav_home",
                parameters = mapOf("setting" to "nav_home"),
                spokenReply = "ہوم اسکرین پر جا رہے ہیں۔",
                isOffline = true
            )
        }
        if (lower == "back" || lower.contains("go back") || lower.contains("wapas") || lower.contains("واپس")) {
            return AiActionResponse(
                intent = AxtraIntent.SYSTEM_TOGGLE,
                target = "nav_back",
                parameters = mapOf("setting" to "nav_back"),
                spokenReply = "پیچھے جا رہے ہیں۔",
                isOffline = true
            )
        }

        // 6. Alarm & Timer
        if (lower.contains("alarm") || lower.contains("الارم")) {
            val numbers = Regex("\\d+").findAll(lower).map { it.value.toInt() }.toList()
            val hour = numbers.getOrNull(0)?.coerceIn(0, 23) ?: 7
            val minute = numbers.getOrNull(1)?.coerceIn(0, 59) ?: 0
            return AiActionResponse(
                intent = AxtraIntent.ALARM,
                target = "$hour:$minute",
                parameters = mapOf("hour" to hour.toString(), "minute" to minute.toString()),
                spokenReply = "$hour بج کر $minute منٹ کا الارم سیٹ کیا جا رہا ہے۔",
                isOffline = true
            )
        }

        if (lower.contains("timer") || lower.contains("ٹائمر")) {
            val mins = Regex("\\d+").find(lower)?.value?.toIntOrNull() ?: 5
            val seconds = if (lower.contains("second") || lower.contains("سیکنڈ")) mins else mins * 60
            return AiActionResponse(
                intent = AxtraIntent.TIMER,
                target = "$seconds",
                parameters = mapOf("seconds" to seconds.toString()),
                spokenReply = "ٹائمر سیٹ کیا جا رہا ہے۔",
                isOffline = true
            )
        }

        // 7. Call
        if (lower.startsWith("call ") || lower.contains("phone karo") || lower.contains("فون کرو") || lower.contains("کال کرو")) {
            val target = text
                .replace(Regex("(?i)(call|phone karo|ko call karo|فون کرو|کال کرو)"), "")
                .trim()
            return AiActionResponse(
                intent = AxtraIntent.CALL,
                target = target,
                needsConfirmation = true,
                spokenReply = if (target.isNotBlank()) "کیا میں $target کو کال کروں؟" else "کس کو کال کرنی ہے؟ نام بتائیں۔",
                isOffline = true
            )
        }

        // 8. Open App
        if (lower.startsWith("open ") || lower.endsWith(" kholo") || lower.contains("کھولو") || lower.contains("chala do")) {
            val appName = text
                .replace(Regex("(?i)(open|kholo|chala do|کھولو)"), "")
                .trim()
            return AiActionResponse(
                intent = AxtraIntent.OPEN_APP,
                app = appName,
                target = appName,
                spokenReply = "$appName کھولا جا رہا ہے۔",
                isOffline = true
            )
        }

        // 9. Expense offline log ("500 rupay khane par kharch")
        if (lower.contains("rupay") || lower.contains("rupees") || lower.contains("kharch") || lower.contains("خرچ")) {
            val amount = Regex("\\d+(\\.\\d+)?").find(lower)?.value ?: "0"
            return AiActionResponse(
                intent = AxtraIntent.NOTE,
                target = text,
                parameters = mapOf("type" to "expense", "amount" to amount, "category" to "General"),
                spokenReply = "$amount روپے کا خرچ محفوظ کر لیا گیا ہے۔",
                isOffline = true
            )
        }

        // 10. Remember / Memory
        if (lower.startsWith("yaad rakho") || lower.startsWith("remember") || lower.contains("یاد رکھو")) {
            val fact = text.replace(Regex("(?i)(yaad rakho|remember|ke|that|یاد رکھو)"), "").trim()
            return AiActionResponse(
                intent = AxtraIntent.NOTE,
                target = fact,
                parameters = mapOf("type" to "memory"),
                spokenReply = "جی، میں نے یہ بات یادداشت میں محفوظ کر لی ہے۔",
                isOffline = true
            )
        }

        return AiActionResponse(
            intent = AxtraIntent.UNKNOWN,
            target = text,
            spokenReply = "آف لائن موڈ میں صرف بنیادی کمانڈز (ایپ کھولنا، کال، ٹارچ، والیوم، الارم، ٹائمر) دستیاب ہیں۔ انٹرنیٹ یا جیمنائی کی چیک کریں۔",
            isOffline = true
        )
    }
}
