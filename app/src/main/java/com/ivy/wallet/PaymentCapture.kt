package com.ivy.wallet

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.entity.CapturedMessageEntity
import com.ivy.data.model.PaymentParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: IvyRoomDatabase,
    private val suggestions: com.ivy.data.repository.PaymentSuggestionRepository,
) {
    suspend fun reconcileDuplicates() {
        try {
            val ids = db.writeCapturedMessageDao.duplicateCopyIds()
            val manager = context.getSystemService(NotificationManager::class.java)
            ids.forEach { manager?.cancel("captured_payment", it.toInt()) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Timber.e(e, "Unable to reconcile payment alerts") }
    }
    suspend fun capture(source: String, sender: String, title: String?, body: String, timestamp: Long) {
        if (com.ivy.data.model.PaymentCaptureOrigin.isIvyPackage(source) || com.ivy.data.model.PaymentCaptureOrigin.isGeneratedAlert(body)) return
        if (!PaymentParser.isPaymentMessage(title, body, sender)) return
        try {
            val id = db.writeCapturedMessageDao.insertIfNew(
                CapturedMessageEntity(source = source, sender = sender, title = title, body = body, timestamp = timestamp)
            ) ?: return
            // Capture still works when the user chooses not to enable outgoing alerts.
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            showAlert(id, sender, title, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to capture payment")
        }
    }

    private suspend fun showAlert(id: Long, sender: String, title: String?, body: String) {
        val parsed = PaymentParser.parse(title, body, sender)
        val suggestion = suggestions.suggest(parsed)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channelId = "payment_detected"
        manager.createNotificationChannel(NotificationChannel(channelId, "Payment Alerts", NotificationManager.IMPORTANCE_HIGH))
        val intent = Intent(context, RootActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("ivywallet://captured/$id/review")
            putExtra(RootViewModel.EXTRA_ADD_TRANSACTION_TYPE,
                if (parsed.isDebit) "EXPENSE" else "INCOME")
            putExtra(RootViewModel.EXTRA_ADD_TRANSACTION_AMOUNT, parsed.amount ?: 0.0)
            putExtra(RootViewModel.EXTRA_ADD_TRANSACTION_TITLE, parsed.merchant ?: sender)
            putExtra(RootViewModel.EXTRA_ADD_TRANSACTION_DESC, parsed.cleanDescription ?: body)
            putExtra(RootViewModel.EXTRA_CAPTURED_MESSAGE_ID, id)
            putExtra(RootViewModel.EXTRA_PAYMENT_CURRENCY, parsed.currency)
        }
        val pendingIntent = PendingIntent.getActivity(context, id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val amount = parsed.amount?.let { parsed.currency + " " + "%,.2f".format(it) }.orEmpty()
        val direction = if (parsed.isDebit) "Paid" else "Received"
        val counterparty = parsed.merchant?.let { " ${if (parsed.isDebit) "to" else "from"} $it" }.orEmpty()
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.ivy.ui.R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, com.ivy.ui.R.color.ivy))
            .setSubText("Payment detected")
            .setContentTitle("$direction $amount$counterparty")
            .setContentText("${suggestion.reviewLabel} · Check before saving")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nReview before recording in Ivy Wallet."))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup("ivy_payment_alerts")
            .setContentIntent(pendingIntent)
            .addAction(0, suggestion.reviewLabel, pendingIntent)

        if (suggestion.canQuickAdd && suggestion.accountId != null) {
            val isLoan = suggestion.kind == com.ivy.data.model.PaymentSuggestion.Kind.LOAN
            val quickAddIntent = Intent(context, QuickAddPaymentReceiver::class.java).apply {
                action = QuickAddPaymentReceiver.ACTION_QUICK_ADD
                putExtra(QuickAddPaymentReceiver.EXTRA_MESSAGE_ID, id)
                putExtra(QuickAddPaymentReceiver.EXTRA_ACCOUNT_ID, suggestion.accountId.toString())
                putExtra(QuickAddPaymentReceiver.EXTRA_AMOUNT, parsed.amount ?: 0.0)
                putExtra(QuickAddPaymentReceiver.EXTRA_CURRENCY, parsed.currency)
                putExtra(QuickAddPaymentReceiver.EXTRA_IS_DEBIT, parsed.isDebit)
                putExtra(QuickAddPaymentReceiver.EXTRA_TITLE, suggestion.name ?: parsed.merchant ?: sender)
                putExtra(QuickAddPaymentReceiver.EXTRA_DESC, parsed.cleanDescription ?: body)
                if (isLoan) {
                    putExtra(QuickAddPaymentReceiver.EXTRA_IS_LOAN, true)
                    putExtra(QuickAddPaymentReceiver.EXTRA_LOAN_ID, suggestion.loanId?.toString())
                    putExtra(QuickAddPaymentReceiver.EXTRA_LOAN_ACTION, suggestion.loanAction?.name)
                    putExtra(QuickAddPaymentReceiver.EXTRA_PERSON, suggestion.name)
                } else {
                    putExtra(QuickAddPaymentReceiver.EXTRA_CATEGORY_ID, suggestion.categoryId)
                }
            }
            val quickAddPendingIntent = PendingIntent.getBroadcast(
                context,
                (id + 100000).toInt(),
                quickAddIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val actionTitle = if (isLoan) {
                if (suggestion.repayment) "Quick Repay: ${suggestion.name}"
                else if (parsed.isDebit) "Quick Lend: ${suggestion.name}"
                else "Quick Borrow: ${suggestion.name}"
            } else {
                "Quick Add: ${suggestion.categoryName ?: "Expense"}"
            }
            notificationBuilder.addAction(0, actionTitle, quickAddPendingIntent)
        }

        manager.notify("captured_payment", id.toInt(), notificationBuilder.build())
    }
}
