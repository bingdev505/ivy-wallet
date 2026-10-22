package com.ivy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlinx.serialization.json.Json

@Serializable
@Entity(tableName = "trip_groups")
data class TripGroupEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val membersJson: String,
) {
    fun members(): List<String> = Json.decodeFromString(membersJson)
}

@Serializable
@Entity(tableName = "trip_expenses")
data class TripExpenseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val title: String,
    val total: Double,
    val ownShare: Double,
    val currency: String,
    val payer: String?,
    val sharesJson: String,
    val loanIdsJson: String,
    val timestamp: Long,
)
