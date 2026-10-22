package com.ivy.data.model

import com.ivy.data.db.entity.LoanEntity
import com.ivy.data.repository.LoanPaymentRepository
import org.junit.Assert.*
import org.junit.Test

class PaymentSuggestionTest {
    @Test fun `notification title can supply a real recipient without including the amount`() {
        assertEquals("Shammas", PaymentParser.parse("Paid to Shammas INR 100", "INR 100 debited.").counterparty)
    }
    private fun payment(out: Boolean = true, name: String? = "Shammas") =
        ParsedPaymentInfo(100.0, out, name, counterparty = name)
    private fun loan(type: LoanType, remaining: Double = 500.0, currency: String = "INR") =
        LoanPaymentRepository.Choice(LoanEntity("Shammas", 500.0, type), currency, remaining)
    @Test fun `incoming money repays lent debt and outgoing money repays borrowed debt`() {
        for ((out, type) in listOf(false to LoanType.LEND, true to LoanType.BORROW)) {
            val choice = loan(type)
            val result = PaymentSuggestion.suggest(payment(out), listOf(choice))
            assertTrue(result.repayment)
            assertEquals(listOf(choice.loan.id), result.loans)
        }
    }
    @Test fun `repeated lending and borrowing increase the existing loan`() {
        for ((out, type) in listOf(true to LoanType.LEND, false to LoanType.BORROW)) {
            val choice = loan(type)
            val result = PaymentSuggestion.suggest(payment(out), listOf(choice))
            assertFalse(result.repayment)
            assertEquals(listOf(choice.loan.id), result.loans)
        }
    }
    @Test fun `unknown people and shops remain ordinary payments`() {
        assertEquals(PaymentSuggestion.Kind.EXPENSE, PaymentSuggestion.suggest(payment(), emptyList()).kind)
        assertEquals(PaymentSuggestion.Kind.INCOME, PaymentSuggestion.suggest(payment(false), emptyList()).kind)
    }
    @Test fun `bank sender fallback is never treated as a person`() {
        val parsed = PaymentParser.parse("Bank alert", "INR 100 debited.", "Bank")
        assertNull(parsed.counterparty)
        assertEquals(PaymentSuggestion.Kind.EXPENSE, PaymentSuggestion.suggest(parsed, listOf(loan(LoanType.LEND))).kind)
    }
    @Test fun `saved UPI alias resolves to the corrected name`() {
        val result = PaymentSuggestion.suggest(payment(name = "shammas@upi"), listOf(loan(LoanType.LEND)),
            PaymentSuggestion.Rule(PaymentSuggestion.Kind.LOAN, "Shammas"))
        assertEquals("Shammas", result.name)
        assertEquals(1, result.loans.size)
    }
    @Test fun `expense correction overrides a matching person and restores category`() {
        val result = PaymentSuggestion.suggest(payment(), listOf(loan(LoanType.LEND)),
            PaymentSuggestion.Rule(PaymentSuggestion.Kind.EXPENSE, categoryId = "food"))
        assertEquals(PaymentSuggestion.Kind.EXPENSE, result.kind)
        assertEquals("food", result.categoryId)
    }
    @Test fun `ambiguous loans are kept for the picker`() {
        val result = PaymentSuggestion.suggest(payment(), listOf(loan(LoanType.LEND), loan(LoanType.LEND)))
        assertEquals(2, result.loans.size)
    }
    @Test fun `wrong currency and completed opposite debt cannot be repaid`() {
        assertEquals(PaymentSuggestion.Kind.EXPENSE, PaymentSuggestion.suggest(payment(), listOf(loan(LoanType.BORROW, currency = "USD"))).kind)
        assertTrue(PaymentSuggestion.suggest(payment(), listOf(loan(LoanType.BORROW, remaining = 0.0))).loans.isEmpty())
    }
}
