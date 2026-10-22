package com.ivy.data.model

import com.ivy.base.model.LoanRecordType
import com.ivy.data.db.entity.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LoanPaymentTest {
    @Test fun `lending and paying back borrowed money leave the account`() {
        assertTrue(LoanPayment.moneyOut(LoanPayment.Action.LEND))
        assertFalse(LoanPayment.moneyOut(LoanPayment.Action.BORROW))
        assertTrue(LoanPayment.moneyOut(LoanPayment.Action.REPAYMENT, LoanType.BORROW))
        assertFalse(LoanPayment.moneyOut(LoanPayment.Action.REPAYMENT, LoanType.LEND))
    }
    @Test fun `remaining debt includes repeated loans and excludes interest and deleted repayments`() {
        val loan = LoanEntity("Shammas", 100.0, LoanType.LEND)
        fun record(amount: Double, type: LoanRecordType = LoanRecordType.DECREASE) = LoanRecordEntity(loan.id, amount, dateTime = Instant.now(), loanRecordType = type)
        assertEquals(120.0, LoanPayment.remaining(loan, listOf(record(50.0, LoanRecordType.INCREASE), record(30.0),
            record(10.0).copy(interest = true), record(80.0).copy(isDeleted = true))), 0.000001)
    }
    @Test fun `new loan payments and shared bills use transfer accounting`() {
        assertTrue(SharedBill.isTransferLoan(LoanPayment.NOTE_PREFIX + "Shammas"))
        assertTrue(SharedBill.isTransferLoan(SharedBill.NOTE_PREFIX + "Food"))
        assertFalse(SharedBill.isTransferLoan("Ordinary loan"))
    }
}
