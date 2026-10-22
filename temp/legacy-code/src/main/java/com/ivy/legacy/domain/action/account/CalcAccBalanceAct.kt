package com.ivy.wallet.domain.action.account

import arrow.core.nonEmptyListOf
import com.ivy.base.model.LoanRecordType
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.LoanDao
import com.ivy.data.db.dao.read.LoanRecordDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.model.Account
import com.ivy.data.model.LoanType
import com.ivy.frp.action.FPAction
import com.ivy.frp.then
import com.ivy.legacy.domain.pure.transaction.AccountValueFunctions
import com.ivy.wallet.domain.pure.data.ClosedTimeRange
import com.ivy.wallet.domain.pure.transaction.foldTransactions
import java.math.BigDecimal
import java.time.ZoneId
import javax.inject.Inject

class CalcAccBalanceAct @Inject constructor(
    private val accTrnsAct: AccTrnsAct,
    private val timeProvider: TimeProvider,
    private val loanDao: LoanDao,
    private val loanRecordDao: LoanRecordDao,
    private val transactionDao: TransactionDao,
) : FPAction<CalcAccBalanceAct.Input, CalcAccBalanceAct.Output>() {

    override suspend fun Input.compose(): suspend () -> Output = suspend {
        AccTrnsAct.Input(
            accountId = account.id.value,
            range = range ?: ClosedTimeRange.allTimeIvy(timeProvider)
        )
    } then accTrnsAct then { accTrns ->
        foldTransactions(
            transactions = accTrns,
            arg = account.id.value,
            valueFunctions = nonEmptyListOf(AccountValueFunctions::balance)
        ).head
    } then { balance ->
        val effectiveRange = range ?: ClosedTimeRange.allTimeIvy(timeProvider)
        val allLoans = loanDao.findAll()
        val loansById = allLoans.associateBy { it.id }

        var loanBalanceAdjustment = BigDecimal.ZERO

        // Loans directly associated with this account that do NOT have a transaction
        allLoans.filter { it.accountId == account.id.value }.forEach { loan ->
            val inRange = loan.dateTime?.let { dt ->
                val instant = dt.atZone(ZoneId.systemDefault()).toInstant()
                instant in effectiveRange.from..effectiveRange.to
            } ?: true

            if (inRange) {
                val hasTransaction = transactionDao.findLoanTransaction(loan.id) != null
                if (!hasTransaction) {
                    val loanAmount = BigDecimal.valueOf(loan.amount)
                    when (loan.type) {
                        LoanType.LEND -> loanBalanceAdjustment = loanBalanceAdjustment.subtract(loanAmount)
                        LoanType.BORROW -> loanBalanceAdjustment = loanBalanceAdjustment.add(loanAmount)
                    }
                }
            }
        }

        // Loan records (repayments / adjustments) associated with this account that do NOT have a transaction
        val allRecords = loanRecordDao.findAll()
        allRecords.forEach { record ->
            val parentLoan = loansById[record.loanId]
            val recordAccountId = record.accountId ?: parentLoan?.accountId
            if (recordAccountId == account.id.value && parentLoan != null) {
                val inRange = record.dateTime in effectiveRange.from..effectiveRange.to
                if (inRange) {
                    val hasTransaction = transactionDao.findLoanRecordTransaction(record.id) != null
                    if (!hasTransaction) {
                        val recordAmount = BigDecimal.valueOf(record.amount)
                        when (parentLoan.type) {
                            LoanType.LEND -> {
                                when (record.loanRecordType) {
                                    LoanRecordType.DECREASE -> loanBalanceAdjustment = loanBalanceAdjustment.add(recordAmount)
                                    LoanRecordType.INCREASE -> loanBalanceAdjustment = loanBalanceAdjustment.subtract(recordAmount)
                                }
                            }
                            LoanType.BORROW -> {
                                when (record.loanRecordType) {
                                    LoanRecordType.DECREASE -> loanBalanceAdjustment = loanBalanceAdjustment.subtract(recordAmount)
                                    LoanRecordType.INCREASE -> loanBalanceAdjustment = loanBalanceAdjustment.add(recordAmount)
                                }
                            }
                        }
                    }
                }
            }
        }

        Output(
            account = account,
            balance = balance.add(loanBalanceAdjustment)
        )
    }

    @Suppress("DataClassDefaultValues")
    data class Input(
        val account: Account,
        val range: ClosedTimeRange? = null
    )

    data class Output(
        val account: Account,
        val balance: BigDecimal,
    )
}
