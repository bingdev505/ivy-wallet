package com.ivy.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_messages")
data class CapturedMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val source: String,        // e.g. package name or "SMS"
    val sender: String,        // app name or phone number
    val title: String?,
    val body: String,
    val timestamp: Long,       // epoch millis
    val isRead: Boolean = false,
    val isDismissed: Boolean = false,
)
