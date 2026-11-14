package com.example

import com.example.actions.FuzzyAppMatcher
import com.example.ai.AxtraIntent
import com.example.ai.JsonValidationResult
import com.example.ai.JsonValidator
import com.example.ai.OfflineCommandParser
import com.example.core.PrayerCalculationMethod
import com.example.data.repository.InstalledAppInfo
import com.example.utils.PrayerTimeCalculator
import com.example.voice.WakeWordDetector
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AxtraUnitTests {

    private val offlineParser = OfflineCommandParser()
    private val jsonValidator = JsonValidator(Json { ignoreUnknownKeys = true; isLenient = true })
    private val fuzzyMatcher = FuzzyAppMatcher()

    @Test
    fun `offline parser handles open app, call, torch, alarm, stop, and expense`() {
        val openApp = offlineParser.parse("YouTube kholo")
        assertEquals(AxtraIntent.OPEN_APP, openApp.intent)
        assertTrue(openApp.app.contains("YouTube", ignoreCase = true))

        val callCmd = offlineParser.parse("Ali ko phone karo")
        assertEquals(AxtraIntent.CALL, callCmd.intent)
        assertTrue(callCmd.needsConfirmation)

        val torchOn = offlineParser.parse("Torch on karo")
        assertEquals(AxtraIntent.SYSTEM_TOGGLE, torchOn.intent)
        assertEquals("on", torchOn.parameters["state"])

        val alarm = offlineParser.parse("Subah 6 30 baje alarm lagao")
        assertEquals(AxtraIntent.ALARM, alarm.intent)
        assertEquals("6", alarm.parameters["hour"])
        assertEquals("30", alarm.parameters["minute"])

        val stop = offlineParser.parse("Axtra ruk jao")
        assertEquals("STOP_ALL", stop.target)

        val expense = offlineParser.parse("500 rupay khane par kharch hue")
        assertEquals(AxtraIntent.NOTE, expense.intent)
        assertEquals("500", expense.parameters["amount"])
    }

    @Test
    fun `json validator parses markdown wrapped JSON and rejects invalid schema`() {
        val validRaw = """
            ```json
            {
              "intent": "WHATSAPP_MESSAGE",
              "app": "WhatsApp",
              "target": "Ahmed",
              "parameters": { "message": "Main late hun" },
              "needs_confirmation": true,
              "spoken_reply": "کیا میں احمد کو واٹس ایپ پیغام بھیجوں؟"
            }
            ```
        """.trimIndent()

        val validRes = jsonValidator.parseAndValidate(validRaw)
        assertTrue(validRes is JsonValidationResult.Valid)
        val parsed = (validRes as JsonValidationResult.Valid).response
        assertEquals(AxtraIntent.WHATSAPP_MESSAGE, parsed.intent)
        assertEquals("Ahmed", parsed.target)
        assertEquals("Main late hun", parsed.parameters["message"])
        assertTrue(parsed.needsConfirmation)

        val invalidRaw = """{ "intent": "FAKE_INTENT", "spoken_reply": "ok" }"""
        val invalidRes = jsonValidator.parseAndValidate(invalidRaw)
        assertTrue(invalidRes is JsonValidationResult.Invalid)
    }

    @Test
    fun `fuzzy app matcher matches typos and Urdu Roman-Urdu aliases`() {
        val apps = listOf(
            InstalledAppInfo("com.whatsapp", "WhatsApp"),
            InstalledAppInfo("com.google.android.youtube", "YouTube"),
            InstalledAppInfo("com.android.settings", "Settings"),
            InstalledAppInfo("com.google.android.apps.maps", "Maps")
        )

        assertEquals("com.whatsapp", fuzzyMatcher.findBestMatch("waatsaap", apps)?.packageName)
        assertEquals("com.google.android.youtube", fuzzyMatcher.findBestMatch("yootoob", apps)?.packageName)
        assertEquals("com.google.android.apps.maps", fuzzyMatcher.findBestMatch("Google Maps", apps)?.packageName)
    }

    @Test
    fun `prayer time calculator produces formatted times for all 6 prayers`() {
        val calc = PrayerTimeCalculator()
        val times = calc.calculateForToday(30.1575, 71.5249, PrayerCalculationMethod.KARACHI)
        assertNotNull(times.fajr)
        assertTrue(times.fajr.endsWith("AM"))
        assertTrue(times.maghrib.endsWith("PM"))
        assertTrue(times.isha.endsWith("PM"))
    }

    @Test
    fun `wake word detector matches hey astra, hello astra, astra and extracts trailing command`() {
        val exactHey = WakeWordDetector.matchWakePhraseAndExtractCommand("Hey Astra")
        assertTrue(exactHey.matched)
        assertEquals("hey astra", exactHey.matchedPhrase)
        assertEquals("", exactHey.trailingCommand)

        val exactHello = WakeWordDetector.matchWakePhraseAndExtractCommand("Hello Astra!")
        assertTrue(exactHello.matched)
        assertEquals("hello astra", exactHello.matchedPhrase)

        val inlineCmd = WakeWordDetector.matchWakePhraseAndExtractCommand("Astra, YouTube kholo")
        assertTrue(inlineCmd.matched)
        assertEquals("astra", inlineCmd.matchedPhrase)
        assertEquals("youtube kholo", inlineCmd.trailingCommand)

        val customPhrase = WakeWordDetector.matchWakePhraseAndExtractCommand("Ok Buddy turn on torch", "ok buddy, astra")
        assertTrue(customPhrase.matched)
        assertEquals("ok buddy", customPhrase.matchedPhrase)
        assertEquals("turn on torch", customPhrase.trailingCommand)

        val falseSubstring = WakeWordDetector.matchWakePhraseAndExtractCommand("Tell me about astronomy")
        assertFalse(falseSubstring.matched)

        val unrelated = WakeWordDetector.matchWakePhraseAndExtractCommand("Open settings please")
        assertFalse(unrelated.matched)
    }
}
