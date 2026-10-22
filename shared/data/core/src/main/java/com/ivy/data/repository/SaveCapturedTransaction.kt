package com.ivy.data.repository

import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.model.Transaction
import com.ivy.data.repository.mapper.TransactionMapper
import javax.inject.Inject

class SaveCapturedTransaction @Inject constructor(
    private val db: IvyRoomDatabase,
    private val mapper: TransactionMapper,
) {
    suspend fun save(transaction: Transaction, messageId: Long) {
        db.recordCapturedPaymentDao.saveCaptured(with(mapper) { transaction.toEntity() }, messageId)
    }
}
