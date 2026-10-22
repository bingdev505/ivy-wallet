package com.ivy.data.model

import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TransactionEntity
import java.util.UUID
import java.util.Locale

data class TitleSuggestion(val title: String, val categoryId: UUID?)

object TransactionTitleSuggestions {
    fun rank(rows: List<TransactionEntity>, type: TransactionType, query: String?, categoryId: UUID?, accountId: UUID?): List<TitleSuggestion> {
        val text = query.orEmpty().trim()
        val eligible = rows.filter { !it.isDeleted && it.type == type && it.loanId == null && it.loanRecordId == null && !it.title.isNullOrBlank() }
        return eligible.groupBy { it.title!!.trim().lowercase(Locale.ROOT) }.values
            .filter { group -> !group.first().title!!.trim().equals(text, true) }
            .sortedWith(compareBy<List<TransactionEntity>> { group ->
                when {
                    text.isNotEmpty() && group.first().title!!.trim().startsWith(text, true) -> 0
                    categoryId != null && group.any { it.categoryId == categoryId } -> 1
                    accountId != null && group.any { it.accountId == accountId } -> 2
                    else -> 3
                }
            }.thenByDescending { it.size }.thenByDescending { group -> group.mapNotNull { it.dateTime }.maxOrNull() })
            .take(10).map { group ->
                val categoryRows = group.groupBy { it.categoryId }.values.maxWithOrNull(
                    compareBy<List<TransactionEntity>> { it.size }.thenBy { rows -> rows.mapNotNull { it.dateTime }.maxOrNull() })!!
                TitleSuggestion(categoryRows.first().title!!.trim(), categoryRows.first().categoryId)
            }
    }
}
