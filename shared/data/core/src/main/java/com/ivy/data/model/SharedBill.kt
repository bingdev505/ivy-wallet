package com.ivy.data.model

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object SharedBill {
    const val NOTE_PREFIX = "Shared bill: "
    fun isTransferLoan(note: String?): Boolean = note?.startsWith(NOTE_PREFIX) == true || note?.startsWith(LoanPayment.NOTE_PREFIX) == true
    fun clearingAccount(currency: String) = AccountEntity(
        name = "Shared bills ($currency)", currency = currency, color = 0xFF607D8B.toInt(),
        includeInBalance = false,
        id = UUID.nameUUIDFromBytes("ivy.shared-bills.$currency".toByteArray(Charsets.UTF_8)),
    )

    data class Share(val person: String, val amount: BigDecimal)
    data class Plan(val loans: List<LoanEntity>, val transactions: List<TransactionEntity>)

    fun planSplit(payer: String?, total: BigDecimal, ownShare: BigDecimal, shares: List<Share>,
        accountId: UUID, currency: String, categoryId: UUID, title: String, time: Instant = Instant.now(),
    ): Plan {
        require(total > BigDecimal.ZERO && ownShare >= BigDecimal.ZERO && ownShare <= total) { "Check the bill total and your share" }
        require(total.toDouble().isFinite()) { "Amount is too large" }
        require(shares.isNotEmpty() && shares.all { it.person.isNotBlank() && it.amount >= BigDecimal.ZERO }) { "Enter each person's name and share" }
        require(shares.map { ExpenseSplit.key(it.person) }.distinct().size == shares.size) { "Use one share per person" }
        require((ownShare + shares.fold(BigDecimal.ZERO) { sum, share -> sum + share.amount }).compareTo(total) == 0) { "All shares must add up to the bill total" }
        if (payer == null) {
            val positive = shares.filter { it.amount > BigDecimal.ZERO }
            if (positive.isEmpty()) return Plan(emptyList(), listOf(TransactionEntity(accountId = accountId,
                type = TransactionType.EXPENSE, amount = ownShare.toDouble(), categoryId = categoryId, title = title, dateTime = time)))
            return plan(true, total, ownShare, positive, accountId, currency, categoryId, title, time)
        }
        require(shares.any { ExpenseSplit.key(it.person) == ExpenseSplit.key(payer) }) { "The payer must be one of the people in this expense" }
        if (ownShare == BigDecimal.ZERO) return Plan(emptyList(), emptyList())
        return plan(false, total, ownShare, listOf(Share(payer, total)), accountId, currency, categoryId, title, time)
    }

    fun loanTransfer(loanId: UUID, type: LoanType, accountId: UUID, currency: String,
        amount: Double, repayment: Boolean,
    ): TransactionEntity {
        require(amount > 0 && amount.isFinite()) { "Enter a valid loan amount" }
        val moneyOut = (type == LoanType.LEND) != repayment
        val clearing = clearingAccount(currency).id
        return TransactionEntity(accountId = if (moneyOut) accountId else clearing,
            toAccountId = if (moneyOut) clearing else accountId, type = TransactionType.TRANSFER,
            amount = amount, toAmount = amount, loanId = loanId)
    }

    fun plan(
        iPaid: Boolean, total: BigDecimal, ownShare: BigDecimal, shares: List<Share>,
        accountId: UUID, currency: String, categoryId: UUID, title: String,
        time: Instant = Instant.now(),
    ): Plan {
        require(total > BigDecimal.ZERO && ownShare >= BigDecimal.ZERO && ownShare <= total) { "Check the bill total and your share" }
        require(total.toDouble().isFinite() && ownShare.toDouble().isFinite()) { "Amount is too large" }
        require(shares.isNotEmpty() && shares.all { it.person.isNotBlank() && it.amount > BigDecimal.ZERO }) { "Enter each person's name and share" }
        require(shares.map { it.person.trim().lowercase() }.distinct().size == shares.size) { "Use one share per person" }
        require(iPaid || shares.size == 1) { "Choose one person who paid" }
        require((if (iPaid) ownShare + shares.fold(BigDecimal.ZERO) { sum, share -> sum + share.amount } else shares.single().amount).compareTo(total) == 0) { "All shares must add up to the bill total" }
        if (!iPaid) require(ownShare > BigDecimal.ZERO) { "Your share must be greater than zero" }
        val loans = shares.map { share ->
            LoanEntity(name = share.person.trim(), amount = (if (iPaid) share.amount else ownShare).toDouble(),
                type = if (iPaid) LoanType.LEND else LoanType.BORROW,
                accountId = accountId, note = NOTE_PREFIX + title.trim(),
                color = 0xFF607D8B.toInt(), dateTime = time.atZone(ZoneId.systemDefault()).toLocalDateTime())
        }
        val expense = if (ownShare > BigDecimal.ZERO) listOf(TransactionEntity(
            accountId = accountId, type = TransactionType.EXPENSE, amount = ownShare.toDouble(),
            categoryId = categoryId, title = title.trim(), description = "Your share of a shared bill", dateTime = time,
        )) else emptyList()
        val transfers = loans.map { loan ->
            loanTransfer(loan.id, loan.type, accountId, currency, loan.amount, repayment = false)
                .copy(title = loan.name, description = loan.note, dateTime = time)
        }
        return Plan(loans, expense + transfers)
    }
}
