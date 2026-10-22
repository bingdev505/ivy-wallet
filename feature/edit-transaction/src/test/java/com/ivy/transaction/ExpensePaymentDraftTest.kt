package com.ivy.transaction

import org.junit.Assert.*
import org.junit.Test

class ExpensePaymentDraftTest {
    @Test fun `opening a fresh split fills the first amount before adding people`() {
        val opened = ExpensePaymentDraft().equal(900.0, "INR")
        assertEquals("900.00", opened.ownShare)
        assertFalse(opened.customSplit)
        val added = opened.copy(people = listOf("A" to "")).equal(900.0, "INR")
        assertEquals("450.00", added.ownShare)
        assertEquals("450.00", added.people.single().second)
    }
    @Test fun `removing the last person returns the bill to my share`() {
        val split = ExpensePaymentDraft(people = listOf("A" to "")).equal(600.0, "INR")
        assertEquals("600.00", split.copy(people = emptyList()).equal(600.0, "INR").ownShare)
    }
    @Test fun `a bill entered after opening initializes automatic amounts`() {
        val early = ExpensePaymentDraft(people = listOf("A" to "", "B" to "")).equal(0.0, "INR")
        val ready = early.equal(900.0, "INR")
        assertEquals("300.00", ready.ownShare)
        assertEquals(listOf("300.00", "300.00"), ready.people.map { it.second })
    }
    @Test fun `adding and removing people recalculates equal shares`() {
        val initial = ExpensePaymentDraft(people = listOf("A" to "")).equal(900.0, "INR")
        assertEquals("450.00", initial.ownShare)
        val added = initial.copy(people = initial.people + ("B" to "")).equal(900.0, "INR")
        assertEquals("300.00", added.ownShare)
        assertEquals(listOf("300.00", "300.00"), added.people.map { it.second })
        val changedTotal = added.equal(600.0, "INR")
        assertEquals("200.00", changedTotal.ownShare)
        assertEquals("450.00", added.copy(people = added.people.take(1)).equal(900.0, "INR").ownShare)
    }
    @Test fun `custom amounts survive amount or membership changes until reset`() {
        val custom = ExpensePaymentDraft(customSplit = true, ownShare = "100", people = listOf("A" to "500"))
        assertEquals(custom, custom.equal(900.0, "INR"))
        assertEquals("450.00", custom.copy(customSplit = false).equal(900.0, "INR").ownShare)
    }
}
