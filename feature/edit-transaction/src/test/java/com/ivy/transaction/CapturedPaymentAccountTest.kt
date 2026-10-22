package com.ivy.transaction

import com.ivy.data.model.SharedBill
import com.ivy.legacy.datamodel.Account
import org.junit.Assert.*
import org.junit.Test

class CapturedPaymentAccountTest {
    @Test fun `uses a real payment account instead of shared bill bookkeeping`() {
        val clearing = Account("Shared bills", 0, "INR", id = SharedBill.clearingAccount("INR").id)
        val bank = Account("Bank", 0, "INR")
        assertEquals(bank, capturedPaymentAccount(listOf(clearing, bank), "INR", "GBP", clearing.id))
    }

    @Test fun `prefers the last used matching account and resolves base currency`() {
        val first = Account("Cash", 0, "INR")
        val bank = Account("Bank", 0)
        assertEquals(bank, capturedPaymentAccount(listOf(first, bank), "INR", "INR", bank.id))
    }

    @Test fun `does not use a foreign currency account or clearing account as fallback`() {
        val foreign = Account("Cash", 0, "GBP")
        val clearing = Account("Shared bills", 0, "INR", id = SharedBill.clearingAccount("INR").id)
        assertNull(capturedPaymentAccount(listOf(foreign, clearing), "INR", "GBP", foreign.id))
    }
}
