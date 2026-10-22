package com.ivy.data.model

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TransactionEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.time.Instant

class TransactionTitleSuggestionsTest {
    private val account = UUID.randomUUID()
    private val food = UUID.randomUUID()
    private fun row(title: String, type: TransactionType = TransactionType.EXPENSE, category: UUID? = food) =
        TransactionEntity(accountId = account, type = type, amount = 10.0, title = title, categoryId = category, dateTime = Instant.now())
    @Test fun `expense suggestions exclude income transfers and loan names`() {
        val rows = listOf(row("Lunch"), row("Salary", TransactionType.INCOME), row("Transfer", TransactionType.TRANSFER), row("Person").copy(loanId = UUID.randomUUID()))
        assertEquals(listOf(TitleSuggestion("Lunch", food)), TransactionTitleSuggestions.rank(rows, TransactionType.EXPENSE, "", null, account))
        assertEquals(listOf("Salary"), TransactionTitleSuggestions.rank(rows, TransactionType.INCOME, "", null, account).map { it.title })
    }
    @Test fun `prefix matches rank before frequently used titles`() {
        val rows = List(5) { row("Dinner") } + row("Lunch")
        assertEquals("Lunch", TransactionTitleSuggestions.rank(rows, TransactionType.EXPENSE, "lu", food, account).first().title)
    }
    @Test fun `same title uses its most frequent category`() {
        val other = UUID.randomUUID()
        val rows = listOf(row("Lunch"), row("lunch"), row("Lunch", category = other))
        val result = TransactionTitleSuggestions.rank(rows, TransactionType.EXPENSE, "", null, account)
        assertEquals(1, result.size)
        assertEquals(food, result.single().categoryId)
    }
}
