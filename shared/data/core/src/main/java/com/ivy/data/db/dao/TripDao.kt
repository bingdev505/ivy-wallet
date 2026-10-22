package com.ivy.data.db.dao

import androidx.room.*
import com.ivy.data.db.entity.TripGroupEntity
import com.ivy.data.db.entity.TripExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trip_groups ORDER BY name COLLATE NOCASE")
    fun observeGroups(): Flow<List<TripGroupEntity>>
    @Query("SELECT * FROM trip_groups ORDER BY name COLLATE NOCASE")
    suspend fun groups(): List<TripGroupEntity>
    @Query("SELECT * FROM trip_expenses ORDER BY timestamp DESC")
    suspend fun expenses(): List<TripExpenseEntity>
    @Query("SELECT * FROM trip_expenses WHERE groupId = :groupId ORDER BY timestamp DESC")
    suspend fun expenses(groupId: String): List<TripExpenseEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGroups(groups: List<TripGroupEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveExpenses(expenses: List<TripExpenseEntity>)
    @Query("DELETE FROM trip_expenses")
    suspend fun deleteExpenses()
    @Query("DELETE FROM trip_groups")
    suspend fun deleteGroups()
    @Transaction
    suspend fun deleteAll() { deleteExpenses(); deleteGroups() }
}
