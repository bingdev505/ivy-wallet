package com.ivy.wallet

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ivy.base.model.TransactionType
import com.ivy.data.backup.auto.AutoBackupManager
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.model.LoanPayment
import com.ivy.data.model.PaymentSuggestion
import com.ivy.data.repository.LoanPaymentRepository
import com.ivy.data.repository.PaymentSuggestionRepository
import com.ivy.widget.balance.WalletBalanceWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class QuickAddPaymentReceiver : BroadcastReceiver() {

    @Inject
    lateinit var db: IvyRoomDatabase

    @Inject
    lateinit var loanPaymentRepository: LoanPaymentRepository

    @Inject
    lateinit var paymentSuggestions: PaymentSuggestionRepository

    @Inject
    lateinit var autoBackupManager: AutoBackupManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_QUICK_ADD) return
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId == -1L) return

        val accountIdStr = intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: return
        val accountId = runCatching { UUID.fromString(accountIdStr) }.getOrNull() ?: return

        val isLoan = intent.getBooleanExtra(EXTRA_IS_LOAN, false)
        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val currency = intent.getStringExtra(EXTRA_CURRENCY) ?: "INR"
        val isDebit = intent.getBooleanExtra(EXTRA_IS_DEBIT, true)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val desc = intent.getStringExtra(EXTRA_DESC)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (isLoan) {
                    val person = intent.getStringExtra(EXTRA_PERSON) ?: title ?: "Person"
                    val loanIdStr = intent.getStringExtra(EXTRA_LOAN_ID)
                    val loanId = loanIdStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    val actionName = intent.getStringExtra(EXTRA_LOAN_ACTION)
                    val action = actionName?.let { runCatching { LoanPayment.Action.valueOf(it) }.getOrNull() }
                        ?: if (isDebit) LoanPayment.Action.LEND else LoanPayment.Action.BORROW

                    loanPaymentRepository.save(
                        action = action,
                        person = person,
                        loanId = loanId,
                        amount = amount,
                        accountId = accountId,
                        currency = currency,
                        title = title,
                        description = desc,
                        time = Instant.now(),
                        messageId = messageId
                    )
                } else {
                    val categoryIdStr = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: return@launch
                    val categoryId = runCatching { UUID.fromString(categoryIdStr) }.getOrNull() ?: return@launch

                    val transaction = TransactionEntity(
                        accountId = accountId,
                        type = if (isDebit) TransactionType.EXPENSE else TransactionType.INCOME,
                        amount = amount,
                        title = title,
                        description = desc,
                        dateTime = Instant.now(),
                        categoryId = categoryId,
                        isSynced = false,
                        isDeleted = false,
                        id = UUID.randomUUID()
                    )

                    db.recordCapturedPaymentDao.saveCaptured(transaction, messageId)
                }

                // Cancel notification
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.cancel("captured_payment", messageId.toInt())

                // Refresh widgets & trigger instant backup
                WalletBalanceWidgetReceiver.updateBroadcast(context)
                autoBackupManager.triggerInstantBackupIfEnabled()
            } catch (e: Exception) {
                Timber.e(e, "QuickAddPaymentReceiver failed to record payment")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.ivy.wallet.ACTION_QUICK_ADD_PAYMENT"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_CATEGORY_ID = "extra_category_id"
        const val EXTRA_ACCOUNT_ID = "extra_account_id"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_CURRENCY = "extra_currency"
        const val EXTRA_IS_DEBIT = "extra_is_debit"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESC = "extra_desc"
        const val EXTRA_IS_LOAN = "extra_is_loan"
        const val EXTRA_PERSON = "extra_person"
        const val EXTRA_LOAN_ID = "extra_loan_id"
        const val EXTRA_LOAN_ACTION = "extra_loan_action"
    }
}
