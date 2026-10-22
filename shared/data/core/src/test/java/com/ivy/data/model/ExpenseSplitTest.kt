package com.ivy.data.model

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

class ExpenseSplitTest {
    @Test fun `equal shares include me and preserve rounding remainder`() {
        assertEquals(listOf("300.00", "300.00", "300.00"), ExpenseSplit.equal(BigDecimal("900"), 3, "INR").map { it.toPlainString() })
        val shares = ExpenseSplit.equal(BigDecimal("100"), 3, "INR")
        assertEquals(listOf("33.34", "33.33", "33.33"), shares.map { it.toPlainString() })
        assertEquals(BigDecimal("100.00"), shares.reduce(BigDecimal::add))
        assertEquals(listOf("1", "0", "0"), ExpenseSplit.equal(BigDecimal.ONE, 3, "JPY").map { it.toPlainString() })
    }
    @Test fun `name search ignores case spaces and selected people`() {
        assertEquals(listOf("Rahul", "Arun"), ExpenseSplit.suggestions("r", listOf("Rahul", " rahul ", "Arun", "Ravi"), listOf("ravi")))
        assertEquals(listOf("Rahul"), ExpenseSplit.suggestions(" raH ", listOf("Rahul", "Anu")))
    }
    @Test fun `another group member pays and only my share becomes my borrowing`() {
        val account = UUID.randomUUID()
        val plan = SharedBill.planSplit("Rahul", BigDecimal("900"), BigDecimal("300"), listOf(
            SharedBill.Share("Rahul", BigDecimal("300")), SharedBill.Share("Anu", BigDecimal("300"))),
            account, "INR", UUID.randomUUID(), "Food")
        assertEquals(1, plan.loans.size)
        assertEquals("Rahul", plan.loans.single().name)
        assertEquals(LoanType.BORROW, plan.loans.single().type)
        assertEquals(300.0, plan.loans.single().amount, 0.0)
        val cash = plan.transactions.sumOf { (if (it.toAccountId == account) it.toAmount ?: 0.0 else 0.0) - (if (it.accountId == account) it.amount else 0.0) }
        assertEquals(0.0, cash, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun `payer must belong to the split`() {
        SharedBill.planSplit("Other", BigDecimal("600"), BigDecimal("300"), listOf(SharedBill.Share("Rahul", BigDecimal("300"))), UUID.randomUUID(), "INR", UUID.randomUUID(), "Food")
    }
    @Test fun `zero shares create no zero amount loans`() {
        val plan = SharedBill.planSplit(null, BigDecimal("300"), BigDecimal("300"), listOf(SharedBill.Share("Rahul", BigDecimal.ZERO)), UUID.randomUUID(), "INR", UUID.randomUUID(), "Food")
        assertTrue(plan.loans.isEmpty())
        assertEquals(1, plan.transactions.size)
    }
}
