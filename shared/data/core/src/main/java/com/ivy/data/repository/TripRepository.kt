package com.ivy.data.repository

import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.entity.*
import com.ivy.data.model.ExpenseSplit
import com.ivy.data.model.LoanType
import com.ivy.base.model.LoanRecordType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

class TripRepository @Inject constructor(private val db: IvyRoomDatabase) {
    fun observeGroups() = db.tripDao.observeGroups()
    fun members(group: TripGroupEntity): List<String> = Json.decodeFromString(group.membersJson)
    suspend fun people(): List<String> = ExpenseSplit.names(db.loanDao.findAll().map { it.name } + db.tripDao.groups().flatMap(::members))
    suspend fun saveGroup(id: String?, name: String, people: List<String>) {
        require(name.isNotBlank()) { "Enter a group name" }
        val members = ExpenseSplit.names(people)
        require(members.isNotEmpty()) { "Add at least one person" }
        val groups = db.tripDao.groups()
        require(groups.none { it.id != id && it.name.trim().equals(name.trim(), true) }) { "This group name already exists" }
        db.tripDao.saveGroups(listOf(TripGroupEntity(id ?: UUID.randomUUID().toString(), name.trim(), Json.encodeToString(members))))
    }
    suspend fun expenses(id: String) = db.tripDao.expenses(id)
    data class Balance(val person: String, val currency: String, val amount: Double, val loanId: UUID)
    suspend fun balances(id: String): List<Balance> {
        val currencies = expenses(id).flatMap { expense -> Json.decodeFromString<List<String>>(expense.loanIdsJson).map { it to expense.currency } }.toMap()
        val records = db.loanRecordDao.findAll().groupBy { it.loanId }
        return db.loanDao.findAll().filter { it.id.toString() in currencies }.map { loan ->
            val left = loan.amount + records[loan.id].orEmpty().sumOf {
                val amount = it.convertedAmount ?: it.amount
                if (it.loanRecordType == LoanRecordType.INCREASE) amount else if (!it.interest) -amount else 0.0
            }
            Balance(loan.name, currencies.getValue(loan.id.toString()), if (loan.type == LoanType.LEND) left else -left, loan.id)
        }.filter { kotlin.math.abs(it.amount) > 0.000001 }
    }
}
