package com.ivy.data.model

import com.ivy.data.repository.LoanPaymentRepository
import java.util.Locale
import java.util.UUID

/** Suggestions never write a transaction; the existing forms remain the review step. */
object PaymentSuggestion {
    enum class Kind { LOAN, EXPENSE, INCOME }
    data class Rule(val kind: Kind, val person: String? = null, val categoryId: String? = null)
    data class Result(
        val kind: Kind,
        val name: String?,
        val loans: List<UUID> = emptyList(),
        val repayment: Boolean = false,
        val categoryId: String? = null,
        val categoryName: String? = null,
        val accountId: UUID? = null,
        val loanId: UUID? = null,
        val loanAction: LoanPayment.Action? = null,
        val canQuickAdd: Boolean = false
    ) {
        val reviewLabel get() = when (kind) { Kind.LOAN -> "Review loan"; Kind.EXPENSE -> "Review expense"; Kind.INCOME -> "Review income" }
    }
    fun key(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun suggest(payment: ParsedPaymentInfo, choices: List<LoanPaymentRepository.Choice>, rule: Rule? = null): Result {
        val name = rule?.person ?: payment.counterparty
        val ordinary = if (payment.isDebit) Kind.EXPENSE else Kind.INCOME
        // A saved expense/income correction takes priority over a coincidental loan name.
        if (rule != null && rule.kind != Kind.LOAN) return Result(ordinary, name,
            categoryId = rule.categoryId.takeIf { rule.kind == ordinary })
        val matching = choices.filter { it.currency == payment.currency && name != null && key(it.loan.name) == key(name) }
        val repaying = matching.filter { it.remaining > 0.000001 && (it.loan.type == LoanType.BORROW) == payment.isDebit }
        val adding = matching.filter { (it.loan.type == LoanType.LEND) == payment.isDebit }
        val selected = repaying.ifEmpty { adding }
        if (matching.isNotEmpty() || rule?.kind == Kind.LOAN) {
            val repayment = repaying.isNotEmpty()
            val action = if (repayment) LoanPayment.Action.REPAYMENT
                else if (payment.isDebit) LoanPayment.Action.LEND else LoanPayment.Action.BORROW
            val singleLoanId = selected.singleOrNull()?.loan?.id ?: matching.singleOrNull()?.loan?.id
            return Result(Kind.LOAN,
                matching.firstOrNull()?.loan?.name ?: name, selected.map { it.loan.id }, repayment,
                loanId = singleLoanId, loanAction = action)
        }
        return Result(ordinary, name)
    }
}
