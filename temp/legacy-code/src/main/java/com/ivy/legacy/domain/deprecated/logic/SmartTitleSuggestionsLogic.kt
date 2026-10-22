package com.ivy.wallet.domain.deprecated.logic

import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.model.TransactionTitleSuggestions
import java.util.UUID
import javax.inject.Inject

const val SUGGESTIONS_LIMIT = 10

class SmartTitleSuggestionsLogic @Inject constructor(private val transactionDao: TransactionDao) {
    suspend fun suggest(title: String?, categoryId: UUID?, accountId: UUID?, type: TransactionType) =
        TransactionTitleSuggestions.rank(transactionDao.findAllByType(type), type, title, categoryId, accountId)
}
