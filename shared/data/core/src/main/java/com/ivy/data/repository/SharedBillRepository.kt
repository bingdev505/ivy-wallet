package com.ivy.data.repository

import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.model.SharedBill
import java.time.Instant
import java.util.UUID
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.TagAssociationEntity
import javax.inject.Inject
import androidx.room.withTransaction
import com.ivy.data.db.entity.TripExpenseEntity

class SharedBillRepository @Inject constructor(private val db: IvyRoomDatabase) {
    suspend fun save(currency: String, plan: SharedBill.Plan, messageId: Long?, tagIds: List<UUID> = emptyList(), tripExpense: TripExpenseEntity? = null) {
        val tags = plan.transactions.filter { it.type == TransactionType.EXPENSE }.flatMap { expense ->
            tagIds.distinct().map { TagAssociationEntity(it, expense.id, Instant.now(), false) }
        }
        db.withTransaction {
            if (tripExpense != null) check(db.tripDao.groups().any { it.id == tripExpense.groupId }) { "Choose an existing trip group" }
            db.writeSharedBillDao.save(SharedBill.clearingAccount(currency), plan.loans, plan.transactions, messageId, tags)
            if (tripExpense != null) db.tripDao.saveExpenses(listOf(tripExpense))
        }
    }
}
