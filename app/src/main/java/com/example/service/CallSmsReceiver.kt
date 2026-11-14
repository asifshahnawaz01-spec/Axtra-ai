package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.TelephonyManager
import com.example.actions.DeviceDataHelper
import com.example.core.AppPreferences
import com.example.voice.AxtraTtsEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CallSmsReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun appPreferences(): AppPreferences
        fun ttsEngine(): AxtraTtsEngine
        fun deviceDataHelper(): DeviceDataHelper
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val settings = ep.appPreferences().settingsFlow.first()
                if (!settings.callSmsAnnounceEnabled || settings.silentModeEnabled) return@launch

                when (intent?.action) {
                    TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                            @Suppress("DEPRECATION")
                            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty()
                            val label = if (number.isNotBlank()) number else "نامعلوم نمبر"
                            ep.ttsEngine().speak("$label کی کال آ رہی ہے۔")
                        }
                    }
                    Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                        val sender = msgs.firstOrNull()?.displayOriginatingAddress.orEmpty()
                        val body = msgs.joinToString(" ") { it.displayMessageBody.orEmpty() }
                        if (body.isNotBlank()) {
                            ep.ttsEngine().speak("$sender کا نیا پیغام آیا ہے: ${body.take(120)}")
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
