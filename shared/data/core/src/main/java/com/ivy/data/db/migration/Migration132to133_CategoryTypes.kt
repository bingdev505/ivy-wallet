package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration132to133_CategoryTypes : Migration(132, 133) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN transactionType TEXT")
        for (type in listOf("EXPENSE", "INCOME")) {
            val other = if (type == "EXPENSE") "INCOME" else "EXPENSE"
            db.execSQL("""UPDATE categories SET transactionType = '$type'
                WHERE EXISTS (SELECT 1 FROM transactions WHERE categoryId = categories.id AND type = '$type' AND isDeleted = 0 AND loanId IS NULL AND loanRecordId IS NULL)
                AND NOT EXISTS (SELECT 1 FROM transactions WHERE categoryId = categories.id AND type = '$other' AND isDeleted = 0 AND loanId IS NULL AND loanRecordId IS NULL)""")
        }
    }
}
