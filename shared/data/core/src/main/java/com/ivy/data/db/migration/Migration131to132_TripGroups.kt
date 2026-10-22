package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration131to132_TripGroups : Migration(131, 132) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trip_groups (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, membersJson TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS trip_expenses (id TEXT NOT NULL PRIMARY KEY, groupId TEXT NOT NULL, title TEXT NOT NULL, total REAL NOT NULL, ownShare REAL NOT NULL, currency TEXT NOT NULL, payer TEXT, sharesJson TEXT NOT NULL, loanIdsJson TEXT NOT NULL, timestamp INTEGER NOT NULL)")
    }
}
