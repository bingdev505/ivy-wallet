package com.ivy.data.model

import com.ivy.base.model.LoanRecordType
import com.ivy.data.db.entity.LoanEntity
import com.ivy.data.db.entity.LoanRecordEntity

object LoanPayment {
    const val NOTE_PREFIX = "Loan payment: "
    enum class Action { LEND, BORROW, REPAYMENT }

    fun moneyOut(action: Action, existingType: LoanType? = null): Boolean = when (action) {
        Action.LEND -> true
        Action.BORROW -> false
        Action.REPAYMENT -> {
            requireNotNull(existingType) { "Choose the loan being repaid" } == LoanType.BORROW
        }
    }

    fun remaining(loan: LoanEntity, records: List<LoanRecordEntity>): Double = loan.amount +
        records.filter { it.loanId == loan.id && !it.isDeleted }.sumOf {
            val amount = it.convertedAmount ?: it.amount
            if (it.loanRecordType == LoanRecordType.INCREASE) amount else if (!it.interest) -amount else 0.0
        }
}
