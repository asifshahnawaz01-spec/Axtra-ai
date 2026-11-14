package com.example.utils

import com.example.core.PrayerCalculationMethod
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

data class DailyPrayerTimes(
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

@Singleton
class PrayerTimeCalculator @Inject constructor() {

    fun calculateForToday(
        latitude: Double = 30.1575,  // Default: Multan / Pakistan center if location permission skipped
        longitude: Double = 71.5249,
        method: PrayerCalculationMethod = PrayerCalculationMethod.KARACHI
    ): DailyPrayerTimes {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val tzOffsetHours = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3600000.0

        val jd = julianDate(year, month, day) - longitude / (15.0 * 24.0)
        val (decl, eqt) = sunPosition(jd)

        val dhuhr = 12.0 + tzOffsetHours - longitude / 15.0 - eqt
        val sunrise = dhuhr - hourAngle(latitude, decl, 0.833)
        val sunset = dhuhr + hourAngle(latitude, decl, 0.833)
        val fajr = dhuhr - hourAngle(latitude, decl, method.fajrAngle)
        val asr = dhuhr + asrHourAngle(latitude, decl, factor = 1.0)
        val isha = if (method == PrayerCalculationMethod.UMM_AL_QURA) {
            sunset + 1.5
        } else {
            dhuhr + hourAngle(latitude, decl, method.ishaAngle)
        }

        return DailyPrayerTimes(
            fajr = formatHours(fajr),
            sunrise = formatHours(sunrise),
            dhuhr = formatHours(dhuhr + 0.02),
            asr = formatHours(asr),
            maghrib = formatHours(sunset + 0.02),
            isha = formatHours(isha)
        )
    }

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = atan2(cosDeg(e) * sinDeg(l), cosDeg(l)) * 180.0 / Math.PI / 15.0
        val decl = asin(sinDeg(e) * sinDeg(l)) * 180.0 / Math.PI
        val eqt = q / 15.0 - fixHour(ra)
        return decl to eqt
    }

    private fun hourAngle(lat: Double, decl: Double, angle: Double): Double {
        val num = -sinDeg(angle) - sinDeg(lat) * sinDeg(decl)
        val den = cosDeg(lat) * cosDeg(decl)
        val ratio = (num / den).coerceIn(-1.0, 1.0)
        return acos(ratio) * 180.0 / Math.PI / 15.0
    }

    private fun asrHourAngle(lat: Double, decl: Double, factor: Double): Double {
        val angle = -atan(1.0 / (factor + tan(Math.toRadians(kotlin.math.abs(lat - decl))))) * 180.0 / Math.PI
        return hourAngle(lat, decl, angle)
    }

    private fun formatHours(hoursInput: Double): String {
        val hFixed = fixHour(hoursInput)
        val totalMinutes = (hFixed * 60.0).toInt()
        val h24 = (totalMinutes / 60) % 24
        val min = totalMinutes % 60
        val amPm = if (h24 >= 12) "PM" else "AM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return "%02d:%02d %s".format(h12, min, amPm)
    }

    private fun fixAngle(a: Double): Double {
        var r = a % 360.0
        if (r < 0) r += 360.0
        return r
    }

    private fun fixHour(h: Double): Double {
        var r = h % 24.0
        if (r < 0) r += 24.0
        return r
    }

    private fun sinDeg(d: Double) = sin(Math.toRadians(d))
    private fun cosDeg(d: Double) = cos(Math.toRadians(d))
}
