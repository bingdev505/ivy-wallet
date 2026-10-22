package com.ivy.data.db.dao.write

import androidx.room.*
import com.ivy.data.db.entity.*

@Dao
interface WriteSharedBillDao {
    @Query("SELECT * FROM captured_messages WHERE id = :id")
    suspend fun message(id: Long): CapturedMessageEntity?
    @Query("SELECT * FROM captured_messages WHERE timestamp BETWEEN :since AND :until")
    suspend fun nearby(since: Long, until: Long): List<CapturedMessageEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensureAccount(value: AccountEntity)
    @Insert
    suspend fun insertLoans(values: List<LoanEntity>)
    @Insert
    suspend fun insertTransactions(values: List<TransactionEntity>)
    @Insert
    suspend fun insertTags(values: List<TagAssociationEntity>)
    @Query("SELECT isDismissed FROM captured_messages WHERE id = :id")
    suspend fun dismissed(id: Long): Boolean?
    @Query("UPDATE captured_messages SET isDismissed = 1, isRead = 1 WHERE id = :id")
    suspend fun consume(id: Long)
    @Transaction
    suspend fun save(account: AccountEntity, loans: List<LoanEntity>, transactions: List<TransactionEntity>, messageId: Long?, tags: List<TagAssociationEntity> = emptyList()) {
        if (messageId != null) check(dismissed(messageId) == false) { "This payment has already been recorded or dismissed" }
        val copies = messageId?.let { id ->
            val target = checkNotNull(message(id))
            nearby(target.timestamp - com.ivy.data.model.PaymentDuplicates.REFERENCE_WINDOW,
                target.timestamp + com.ivy.data.model.PaymentDuplicates.REFERENCE_WINDOW)
                .filter { it.id == id || com.ivy.data.model.PaymentDuplicates.samePayment(target, it) }
        }.orEmpty()
        check(copies.none { it.isDismissed }) { "This payment has already been recorded or dismissed" }
        ensureAccount(account)
        insertLoans(loans)
        insertTransactions(transactions)
        insertTags(tags)
        copies.forEach { consume(it.id) }
    }
}
