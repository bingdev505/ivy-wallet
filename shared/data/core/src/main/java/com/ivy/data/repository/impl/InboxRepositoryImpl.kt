package com.ivy.data.repository.impl

import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.entity.CapturedMessageEntity
import com.ivy.data.repository.InboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class InboxRepositoryImpl @Inject constructor(
    private val db: IvyRoomDatabase
) : InboxRepository {
    override fun getAllActiveMessages(): Flow<List<CapturedMessageEntity>> {
        return db.capturedMessageDao.getAllMessages().map { messages ->
            val copies = com.ivy.data.model.PaymentDuplicates.duplicateIds(messages).toSet()
            messages.filter { !it.isDismissed && it.id !in copies }.map { message ->
                if (com.ivy.data.model.PaymentCaptureOrigin.isGeneratedAlert(message.body) ||
                    com.ivy.data.model.PaymentCaptureOrigin.isIvyPackage(message.source)) {
                    message.copy(body = com.ivy.data.model.PaymentCaptureOrigin.originalBody(message.body),
                        title = null, sender = if (com.ivy.data.model.PaymentCaptureOrigin.isIvyPackage(message.source)) "Payment" else message.sender)
                } else message
            }
        }.flowOn(Dispatchers.Default)
    }

    override fun getUnreadCount(): Flow<Int> {
        return getAllActiveMessages().map { list ->
            list.count { !it.isRead }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun insertMessage(message: CapturedMessageEntity) {
        db.writeCapturedMessageDao.insertIfNew(message)
    }

    override suspend fun markAsRead(id: Long) {
        db.writeCapturedMessageDao.markAsRead(id)
    }

    override suspend fun dismiss(id: Long) {
        db.writeCapturedMessageDao.dismiss(id)
    }

    override suspend fun dismissAll() {
        db.writeCapturedMessageDao.dismissAllActive()
    }
}
