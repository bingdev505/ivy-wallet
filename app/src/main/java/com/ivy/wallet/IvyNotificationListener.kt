package com.ivy.wallet

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IvyNotificationListener : NotificationListenerService() {
    @Inject lateinit var capture: PaymentCapture
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch { capture.reconcileDuplicates() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (com.ivy.data.model.PaymentCaptureOrigin.isIvyPackage(posted.packageName) ||
            posted.packageName == packageName || posted.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = posted.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        scope.launch {
            // Updating a notification changes postTime; its original event time can stay the same.
            val eventTime = posted.notification.`when`.takeIf { it > 0 && it <= posted.postTime } ?: posted.postTime
            capture.capture(posted.packageName, title ?: posted.packageName, title, body, eventTime)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
