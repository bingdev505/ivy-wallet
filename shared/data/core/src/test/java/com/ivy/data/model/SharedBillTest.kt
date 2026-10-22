package com.ivy.data.model

import com.ivy.base.model.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

class SharedBillTest {
    private val account = UUID.randomUUID()
    private val category = UUID.randomUUID()
    private fun plan(paid: Boolean, own: String = "300", shares: List<SharedBill.Share> = listOf(
        SharedBill.Share("Rahul", BigDecimal(if (paid) "600" else "900")),
    )) = SharedBill.plan(paid, BigDecimal("900"), BigDecimal(own), shares, account, "INR", category, "Food")

    @Test fun `paying for everyone records only my expense and lends the rest`() {
        val result = plan(true)
        assertEquals(LoanType.LEND, result.loans.single().type)
        assertEquals(600.0, result.loans.single().amount, 0.0)
        assertEquals(300.0, result.transactions.single { it.type == TransactionType.EXPENSE }.amount, 0.0)
        assertEquals(900.0, result.transactions.filter { it.accountId == account }.sumOf { it.amount }, 0.0)
        assertEquals(result.loans.single().id, result.transactions.single { it.type == TransactionType.TRANSFER }.loanId)
    }
    @Test fun `someone else paying records expense plus borrowing with no cash movement`() {
        val result = plan(false)
        assertEquals(LoanType.BORROW, result.loans.single().type)
        assertEquals(300.0, result.loans.single().amount, 0.0)
        val netCash = result.transactions.sumOf { if (it.type == TransactionType.EXPENSE) -it.amount else it.toAmount ?: 0.0 }
        assertEquals(0.0, netCash, 0.0)
        assertTrue(result.transactions.none { it.type == TransactionType.INCOME })
    }
    @Test fun `multiple people have separate recoverable balances`() {
        val result = plan(true, shares = listOf(SharedBill.Share("A", BigDecimal("200")), SharedBill.Share("B", BigDecimal("400"))))
        assertEquals(2, result.loans.size)
        assertEquals(2, result.transactions.count { it.type == TransactionType.TRANSFER })
        assertFalse(SharedBill.clearingAccount("INR").includeInBalance)
    }
    @Test(expected = IllegalArgumentException::class) fun `rejects incorrect totals`() { plan(true, "400") }
    @Test(expected = IllegalArgumentException::class) fun `rejects unnamed person`() {
        plan(true, shares = listOf(SharedBill.Share(" ", BigDecimal("600"))))
    }
    @Test(expected = IllegalArgumentException::class) fun `rejects duplicate people`() {
        plan(true, shares = listOf(SharedBill.Share("A", BigDecimal("300")), SharedBill.Share(" a ", BigDecimal("300"))))
    }
    @Test fun `zero personal share can be fully lent`() {
        assertTrue(plan(true, "0", listOf(SharedBill.Share("A", BigDecimal("900")))).transactions.none { it.type == TransactionType.EXPENSE })
    }
    @Test fun `decimal shares remain exact`() {
        val result = SharedBill.plan(true, BigDecimal("1.00"), BigDecimal("0.33"), listOf(
            SharedBill.Share("A", BigDecimal("0.33")), SharedBill.Share("B", BigDecimal("0.34")),
        ), account, "INR", category, "Food")
        assertEquals(2, result.loans.size)
    }

    @Test fun `full repayment in either direction leaves only my food cost`() {
        for (paid in listOf(true, false)) {
            val result = plan(paid)
            val loan = result.loans.single()
            val repayment = SharedBill.loanTransfer(loan.id, loan.type, account, "INR", loan.amount, true)
            val history = result.transactions + repayment
            val cash = history.sumOf {
                (if (it.toAccountId == account) it.toAmount ?: 0.0 else 0.0) -
                    (if (it.accountId == account) it.amount else 0.0)
            }
            assertEquals(-300.0, cash, 0.0)
            assertEquals(300.0, history.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }, 0.0)
            assertEquals(0, history.count { it.type == TransactionType.INCOME })
            val clearing = SharedBill.clearingAccount("INR").id
            val outstanding = history.sumOf {
                (if (it.toAccountId == clearing) it.toAmount ?: 0.0 else 0.0) -
                    (if (it.accountId == clearing) it.amount else 0.0)
            }
            assertEquals(0.0, outstanding, 0.0)
        }
    }
    @Test fun `partial repayment reduces debt without another expense`() {
        val result = plan(false)
        val loan = result.loans.single()
        val repayment = SharedBill.loanTransfer(loan.id, loan.type, account, "INR", 100.0, true)
        assertEquals(account, repayment.accountId)
        assertEquals(200.0, loan.amount - repayment.amount, 0.0)
        assertEquals(1, (result.transactions + repayment).count { it.type == TransactionType.EXPENSE })
    }
}
