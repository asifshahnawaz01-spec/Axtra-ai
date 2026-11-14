package com.example.actions

import android.Manifest
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ContactMatch(val name: String, val number: String)

data class PhoneHealthReport(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val batteryTempCelsius: Float,
    val freeStorageGb: Double,
    val totalStorageGb: Double,
    val availableRamGb: Double,
    val totalRamGb: Double,
    val tips: List<String>
)

data class AppUsageStat(
    val packageName: String,
    val appLabel: String,
    val minutesToday: Long
)

@Singleton
class DeviceDataHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemControlHelper: SystemControlHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var findPhoneJob: Job? = null
    private var activeRingtone: Ringtone? = null

    fun findContactsByName(query: String): List<ContactMatch> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()
        val matches = mutableListOf<ContactMatch>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$cleanQuery%")
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && matches.size < 8) {
                val name = cursor.getString(nameIdx).orEmpty().trim()
                val num = cursor.getString(numIdx).orEmpty().replace("\\s+".toRegex(), "")
                if (name.isNotEmpty() && num.isNotEmpty()) {
                    matches.add(ContactMatch(name, num))
                }
            }
        }
        return matches.distinctBy { it.number }
    }

    fun getLastMissedOrRecentCall(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE
        )
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: cursor.getString(1)
                return name
            }
        }
        return null
    }

    fun readLatestSms(limit: Int = 3): List<Pair<String, String>> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val list = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext() && list.size < limit) {
                list.add(cursor.getString(0).orEmpty() to cursor.getString(1).orEmpty())
            }
        }
        return list
    }

    fun sendSmsDirect(phoneNumber: String, message: String): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val smsManager = context.getSystemService(SmsManager::class.java)
                    ?: return false
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                true
            } else {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getTodayCalendarEvents(): List<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val now = System.currentTimeMillis()
        val end = now + 24L * 60L * 60L * 1000L
        val events = mutableListOf<String>()
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events.TITLE),
            selection,
            arrayOf(now.toString(), end.toString()),
            "${CalendarContract.Events.DTSTART} ASC"
        )?.use { cursor ->
            while (cursor.moveToNext() && events.size < 10) {
                val title = cursor.getString(0).orEmpty().trim()
                if (title.isNotEmpty()) events.add(title)
            }
        }
        return events
    }

    fun getPhoneHealthReport(): PhoneHealthReport {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes.toDouble()
        val freeBytes = stat.availableBytes.toDouble()
        val gb = 1024.0 * 1024.0 * 1024.0

        val am = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val tips = mutableListOf<String>()
        if (freeBytes / totalBytes < 0.15) tips.add("Storage is below 15% free — clear downloads or unused media.")
        if (tempC > 41f) tips.add("Battery is warm (${tempC}°C) — reduce brightness and close heavy apps.")
        if (pct < 20 && !charging) tips.add("Battery is low ($pct%) — enable Battery Saver.")
        if (tips.isEmpty()) tips.add("System health looks optimal.")

        return PhoneHealthReport(
            batteryPercent = pct,
            isCharging = charging,
            batteryTempCelsius = tempC,
            freeStorageGb = freeBytes / gb,
            totalStorageGb = totalBytes / gb,
            availableRamGb = memInfo.availMem.toDouble() / gb,
            totalRamGb = memInfo.totalMem.toDouble() / gb,
            tips = tips
        )
    }

    fun getScreenTimeStatsToday(): List<AppUsageStat> {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val start = now - 24L * 60L * 60L * 1000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now) ?: return emptyList()
        val pm = context.packageManager
        return stats
            .filter { it.totalTimeInForeground > 60_000L }
            .groupBy { it.packageName }
            .map { (pkg, entries) ->
                val totalMs = entries.sumOf { it.totalTimeInForeground }
                val label = runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                }.getOrDefault(pkg.substringAfterLast('.'))
                AppUsageStat(pkg, label, totalMs / 60_000L)
            }
            .sortedByDescending { it.minutesToday }
            .take(15)
    }

    fun startFindMyPhone() {
        stopFindMyPhone()
        findPhoneJob = scope.launch {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                activeRingtone = ringtone
                ringtone?.play()

                var flash = true
                repeat(20) {
                    if (!isActive) return@repeat
                    systemControlHelper.toggleFlashlight(flash)
                    flash = !flash
                    delay(500L)
                }
            } finally {
                stopFindMyPhone()
            }
        }
    }

    fun stopFindMyPhone() {
        findPhoneJob?.cancel()
        findPhoneJob = null
        try {
            activeRingtone?.stop()
        } catch (_: Exception) {
        }
        activeRingtone = null
        systemControlHelper.toggleFlashlight(false)
    }
}
