package com.ivy.wallet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var capture: PaymentCapture

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                // Multipart SMS parts must be joined before extracting amounts and payees.
                val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
                val sender = parts.first().displayOriginatingAddress ?: "SMS"
                capture.capture("SMS", sender, null, body, parts.first().timestampMillis)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
