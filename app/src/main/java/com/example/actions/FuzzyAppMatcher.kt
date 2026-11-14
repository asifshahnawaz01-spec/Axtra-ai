package com.example.actions

import com.example.data.repository.InstalledAppInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class FuzzyAppMatcher @Inject constructor() {

    private val aliasMap: Map<String, List<String>> = mapOf(
        "whatsapp" to listOf("waatsaap", "watsapp", "whatsap", "واٹس ایپ", "وٹس ایپ"),
        "youtube" to listOf("yootoob", "utube", "یوٹیوب"),
        "chrome" to listOf("browser", "google", "کروم", "براؤزر"),
        "camera" to listOf("cam", "selfie", "photo", "کیمرہ"),
        "settings" to listOf("setting", "سیٹنگز", "ترتیبات"),
        "maps" to listOf("google maps", "rasta", "navigation", "میپس", "نقشہ"),
        "messages" to listOf("sms", "message", "پیغامات", "میسجز"),
        "phone" to listOf("dialer", "call", "فون"),
        "clock" to listOf("alarm", "timer", "گھڑی", "الارم"),
        "calendar" to listOf("کیلنڈر", "tariq"),
        "gallery" to listOf("photos", "pictures", "گیلری", "تصاویر"),
        "calculator" to listOf("calc", "کیلکولیٹر"),
        "play store" to listOf("store", "پلے سٹور"),
        "instagram" to listOf("insta", "انسٹاگرام"),
        "telegram" to listOf("ٹیلی گرام"),
        "facebook" to listOf("fb", "فیس بک")
    )

    fun findBestMatch(query: String, installedApps: List<InstalledAppInfo>): InstalledAppInfo? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank() || installedApps.isEmpty()) return null

        // Resolve known Urdu / Roman-Urdu aliases first
        val expandedQueries = mutableListOf(normalizedQuery)
        aliasMap.forEach { (canonical, aliases) ->
            if (normalizedQuery == canonical || aliases.any { normalizedQuery.contains(it) }) {
                expandedQueries.add(canonical)
            }
        }

        var bestApp: InstalledAppInfo? = null
        var bestScore = 0.0

        for (app in installedApps) {
            val label = normalize(app.label)
            val pkgTail = normalize(app.packageName.substringAfterLast('.'))
            for (q in expandedQueries) {
                val score = maxOf(
                    similarity(q, label),
                    if (label.contains(q)) 0.92 else 0.0,
                    if (q.contains(label) && label.length >= 3) 0.88 else 0.0,
                    similarity(q, pkgTail) * 0.9
                )
                if (score > bestScore) {
                    bestScore = score
                    bestApp = app
                }
            }
        }
        return if (bestScore >= 0.55) bestApp else null
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9\u0600-\u06FF ]"), " ").replace(Regex("\\s+"), " ").trim()

    internal fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + cost)
                prev = temp
            }
        }
        return dp[b.length]
    }
}
