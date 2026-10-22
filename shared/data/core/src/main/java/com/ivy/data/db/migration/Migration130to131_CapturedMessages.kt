package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration130to131_CapturedMessages : Migration(130, 131) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `captured_messages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `source` TEXT NOT NULL,
                `sender` TEXT NOT NULL,
                `title` TEXT,
                `body` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `isRead` INTEGER NOT NULL,
                `isDismissed` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
