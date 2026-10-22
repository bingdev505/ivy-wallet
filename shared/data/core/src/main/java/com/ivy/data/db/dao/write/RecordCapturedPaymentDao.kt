package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.data.db.entity.CapturedMessageEntity
import com.ivy.data.model.PaymentDuplicates

@Dao
interface RecordCapturedPaymentDao {
    @Query("SELECT * FROM captured_messages WHERE id = :id")
    suspend fun message(id: Long): CapturedMessageEntity?
    @Query("SELECT * FROM captured_messages WHERE timestamp BETWEEN :since AND :until")
    suspend fun nearby(since: Long, until: Long): List<CapturedMessageEntity>
    @Query("SELECT isDismissed FROM captured_messages WHERE id = :messageId")
    suspend fun capturedMessageDismissed(messageId: Long): Boolean?

    @Query("UPDATE captured_messages SET isDismissed = 1, isRead = 1 WHERE id = :messageId")
    suspend fun finishCapturedMessage(messageId: Long)

    @Upsert
    suspend fun save(value: TransactionEntity)

    @Transaction
    suspend fun saveCaptured(value: TransactionEntity, messageId: Long) {
        check(capturedMessageDismissed(messageId) == false) { "This payment has already been recorded or dismissed" }
        val target = checkNotNull(message(messageId))
        val copies = nearby(target.timestamp - PaymentDuplicates.REFERENCE_WINDOW, target.timestamp + PaymentDuplicates.REFERENCE_WINDOW)
            .filter { it.id == messageId || PaymentDuplicates.samePayment(target, it) }
        check(copies.none { it.isDismissed }) { "This payment has already been recorded or dismissed" }
        save(value)
        copies.forEach { finishCapturedMessage(it.id) }
    }
}
