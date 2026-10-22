package com.ivy.data.repository

import com.ivy.data.db.entity.CapturedMessageEntity
import kotlinx.coroutines.flow.Flow

interface InboxRepository {
    fun getAllActiveMessages(): Flow<List<CapturedMessageEntity>>
    fun getUnreadCount(): Flow<Int>
    suspend fun insertMessage(message: CapturedMessageEntity)
    suspend fun markAsRead(id: Long)
    suspend fun dismiss(id: Long)
    suspend fun dismissAll()
}
