package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.CapturedMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedMessageDao {
    @Query("SELECT * FROM captured_messages ORDER BY timestamp DESC, id DESC")
    fun getAllMessages(): Flow<List<CapturedMessageEntity>>
    @Query("SELECT * FROM captured_messages WHERE id = :id")
    suspend fun findById(id: Long): CapturedMessageEntity?
    @Query("SELECT * FROM captured_messages WHERE isDismissed = 0 ORDER BY timestamp DESC")
    fun getAllActiveMessages(): Flow<List<CapturedMessageEntity>>

    @Query("SELECT COUNT(*) FROM captured_messages WHERE isDismissed = 0 AND isRead = 0")
    fun getUnreadCount(): Flow<Int>
}
