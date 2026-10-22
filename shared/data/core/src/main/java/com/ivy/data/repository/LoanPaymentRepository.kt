package com.ivy.data.repository

import androidx.room.withTransaction
import com.ivy.base.model.LoanRecordType
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.entity.LoanEntity
import com.ivy.data.db.entity.LoanRecordEntity
import com.ivy.data.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

class LoanPaymentRepository @Inject constructor(private val db: IvyRoomDatabase) {
    data class Choice(val loan: LoanEntity, val currency: String, val remaining: Double)

    suspend fun choices(): List<Choice> {
        val base = db.settingsDao.findFirst().currency
        val accounts = db.accountDao.findAll().associateBy { it.id }
        val records = db.loanRecordDao.findAll()
        return db.loanDao.findAll().map { loan ->
            Choice(loan, accounts[loan.accountId]?.currency ?: base, LoanPayment.remaining(loan, records))
        }
    }

    suspend fun save(action: LoanPayment.Action, person: String, loanId: UUID?, amount: Double,
        accountId: UUID, currency: String, title: String?, description: String?, time: Instant,
        messageId: Long?, tagIds: List<UUID> = emptyList(), color: Int? = null, icon: String? = null,
        interest: Boolean = false) = db.withTransaction {
        require(person.isNotBlank()) { "Enter a person's name" }
        require(amount > 0 && amount.isFinite()) { "Enter a valid amount" }
        require(accountId != SharedBill.clearingAccount(currency).id) { "Choose your payment account" }
        val account = requireNotNull(db.accountDao.findById(accountId)) { "Choose a payment account" }
        require((account.currency ?: db.settingsDao.findFirst().currency) == currency) { "Choose an account in $currency" }
        val existing = loanId?.let { id -> requireNotNull(db.loanDao.findById(id)?.takeUnless { it.isDeleted }) { "This loan no longer exists" } }
        val repayment = action == LoanPayment.Action.REPAYMENT
        require(!interest || repayment) { "Interest payments must be repayment records" }
        val type = if (repayment) requireNotNull(existing) { "Choose the loan being repaid" }.type
            else if (action == LoanPayment.Action.LEND) LoanType.LEND else LoanType.BORROW
        if (existing != null) {
            require(ExpenseSplit.key(existing.name) == ExpenseSplit.key(person) && existing.type == type) { "Choose a matching loan" }
            val loanCurrency = existing.accountId?.let { db.accountDao.findById(it)?.currency } ?: db.settingsDao.findFirst().currency
            require(loanCurrency == currency) { "Choose an account in $loanCurrency for this loan" }
            if (repayment && !interest) require(amount <= LoanPayment.remaining(existing, db.loanRecordDao.findAllByLoanId(existing.id)) + 0.000001) { "Repayment cannot exceed the remaining loan" }
        }
        if (messageId != null) {
            val message = requireNotNull(db.capturedMessageDao.findById(messageId)) { "Payment message not found" }
            val detected = PaymentParser.parse(message.title, message.body, message.sender)
            require(detected.currency == currency && detected.amount?.let { kotlin.math.abs(it - amount) < 0.000001 } == true) { "Amount and currency must match the detected payment" }
            require(!detected.directionKnown || detected.isDebit == LoanPayment.moneyOut(action, existing?.type)) { "Choose a loan action matching this payment's direction" }
        }
        val loan = existing ?: LoanEntity(name = person.trim(), amount = amount, type = type,
            accountId = accountId, color = color ?: account.color, icon = icon, note = LoanPayment.NOTE_PREFIX + description.orEmpty(),
            dateTime = time.atZone(ZoneId.systemDefault()).toLocalDateTime(), orderNum = (db.loanDao.findMaxOrderNum() ?: 0.0) + 1)
        val record = existing?.let { LoanRecordEntity(loanId = it.id, amount = amount, note = description,
            dateTime = time, accountId = accountId, interest = interest,
            loanRecordType = if (repayment) LoanRecordType.DECREASE else LoanRecordType.INCREASE) }
        if (record != null) db.writeLoanRecordDao.save(record)
        val transaction = SharedBill.loanTransfer(loan.id, type, accountId, currency, amount, repayment)
            .copy(loanRecordId = record?.id, title = person.trim(), description = description ?: title, dateTime = time)
        val tags = tagIds.distinct().map { com.ivy.data.db.entity.TagAssociationEntity(it, transaction.id, time, false) }
        db.writeSharedBillDao.save(SharedBill.clearingAccount(currency), if (existing == null) listOf(loan) else emptyList(), listOf(transaction), messageId, tags)
    }
}
