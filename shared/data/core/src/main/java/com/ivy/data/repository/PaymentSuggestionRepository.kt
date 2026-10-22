package com.ivy.data.repository

import android.content.Context
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.model.PaymentParser
import com.ivy.data.model.PaymentSuggestion
import com.ivy.data.model.ParsedPaymentInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentSuggestionRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val db: IvyRoomDatabase,
    private val loans: LoanPaymentRepository,
) {
    private val prefs = context.getSharedPreferences("payment_suggestions", Context.MODE_PRIVATE)
    private fun ruleKey(payment: ParsedPaymentInfo): String? = payment.counterparty?.let {
        "${payment.currency}:${if (payment.isDebit) "out" else "in"}:${PaymentSuggestion.key(it)}"
    }
    suspend fun payment(id: Long): ParsedPaymentInfo? = db.capturedMessageDao.findById(id)?.let {
        PaymentParser.parse(it.title, it.body, it.sender)
    }
    suspend fun suggest(payment: ParsedPaymentInfo): PaymentSuggestion.Result {
        val rule = ruleKey(payment)?.let { prefs.getString(it, null) }?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                PaymentSuggestion.Rule(PaymentSuggestion.Kind.valueOf(json.getString("kind")),
                    json.optString("person").takeIf { it.isNotBlank() }, json.optString("category").takeIf { it.isNotBlank() })
            }.getOrNull()
        }
        var result = PaymentSuggestion.suggest(payment, loans.choices(), rule)
        if (result.kind != PaymentSuggestion.Kind.LOAN && result.categoryId == null && payment.counterparty != null) {
            val type = if (payment.isDebit) com.ivy.base.model.TransactionType.EXPENSE else com.ivy.base.model.TransactionType.INCOME
            val latestCat = db.transactionDao.latestPaymentCategory(payment.counterparty, type)?.toString()
            if (latestCat != null) {
                result = result.copy(categoryId = latestCat)
            }
        }
        val categoryName = result.categoryId?.let { catId ->
            runCatching { java.util.UUID.fromString(catId) }.getOrNull()?.let { uuid ->
                db.categoryDao.findById(uuid)?.name
            }
        }
        val baseCurrency = db.settingsDao.findFirst().currency
        val accounts = db.accountDao.findAll(false).filter { it.id != com.ivy.data.model.SharedBill.clearingAccount(payment.currency).id }
        val matchingAccounts = accounts.filter { (it.currency ?: baseCurrency).equals(payment.currency, ignoreCase = true) }
        val preferredAccountId = matchingAccounts.firstOrNull()?.id
        val canQuickAdd = preferredAccountId != null &&
            payment.amount != null &&
            payment.amount > 0.0 &&
            ((result.kind != PaymentSuggestion.Kind.LOAN && result.categoryId != null && categoryName != null) ||
             (result.kind == PaymentSuggestion.Kind.LOAN && !result.name.isNullOrBlank() && result.loans.size <= 1))

        return result.copy(
            categoryName = categoryName,
            accountId = preferredAccountId,
            canQuickAdd = canQuickAdd
        )
    }
    /** Learn only after a successful reviewed save, separately for incoming and outgoing payments. */
    fun remember(payment: ParsedPaymentInfo?, kind: PaymentSuggestion.Kind, person: String? = null, categoryId: String? = null) {
        val key = payment?.let(::ruleKey) ?: return
        val json = JSONObject().put("kind", kind.name).put("person", person.orEmpty()).put("category", categoryId.orEmpty())
        prefs.edit().putString(key, json.toString()).apply()
    }
}
