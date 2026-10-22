package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ivy.data.db.entity.CapturedMessageEntity

@Dao
interface WriteCapturedMessageDao {
    @Query("SELECT * FROM captured_messages WHERE timestamp BETWEEN :since AND :until ORDER BY timestamp ASC, id ASC")
    suspend fun findNearby(since: Long, until: Long): List<CapturedMessageEntity>

    @Query("SELECT * FROM captured_messages ORDER BY timestamp ASC, id ASC")
    suspend fun allCaptures(): List<CapturedMessageEntity>

    suspend fun duplicateCopyIds(): List<Long> = com.ivy.data.model.PaymentDuplicates.duplicateIds(allCaptures())
    @Transaction
    suspend fun insertIfNew(message: CapturedMessageEntity): Long? {
        val nearby = findNearby(message.timestamp - com.ivy.data.model.PaymentDuplicates.REFERENCE_WINDOW,
            message.timestamp + com.ivy.data.model.PaymentDuplicates.REFERENCE_WINDOW)
        return if (nearby.any { com.ivy.data.model.PaymentDuplicates.samePayment(it, message) }) null else insert(message)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: CapturedMessageEntity): Long

    @Query("UPDATE captured_messages SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE captured_messages SET isDismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("UPDATE captured_messages SET isDismissed = 1")
    suspend fun dismissAllActive()

    @Query("DELETE FROM captured_messages WHERE isDismissed = 1")
    suspend fun deleteDismissed()

    @Query("DELETE FROM captured_messages")
    suspend fun deleteAll()
}
