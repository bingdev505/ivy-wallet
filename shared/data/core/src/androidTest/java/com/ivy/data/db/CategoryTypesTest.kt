package com.ivy.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.ivy.data.db.entity.*
import com.ivy.data.db.migration.Migration132to133_CategoryTypes
import com.ivy.data.model.CategoryType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CategoryTypesTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), IvyRoomDatabase::class.java,
        listOf(IvyRoomDatabase.DeleteSEMigration()), FrameworkSQLiteOpenHelperFactory())
    @Test fun migrationSeparatesHistoricalCategoriesAndKeepsMixedAndUnusedOnBoth() {
        helper.createDatabase("category-types", 132).apply {
            for (id in listOf("food", "salary", "mixed", "unused", "loan")) execSQL("INSERT INTO categories (id,name,color,icon,orderNum,isSynced,isDeleted) VALUES ('$id','$id',0,NULL,0,0,0)")
            var index = 0
            for ((category, type) in listOf("food" to "EXPENSE", "salary" to "INCOME", "mixed" to "EXPENSE", "mixed" to "INCOME", "loan" to "EXPENSE")) {
                val loan = if (category == "loan") "'loan-id'" else "NULL"
                execSQL("INSERT INTO transactions (id,accountId,type,amount,isSynced,isDeleted,categoryId,loanId) VALUES ('test-${index++}','account','$type',10,0,0,'$category',$loan)")
            }
            close()
        }
        helper.runMigrationsAndValidate("category-types", 133, true, Migration132to133_CategoryTypes()).apply {
            val actual = mutableMapOf<String, String?>()
            query("SELECT name,transactionType FROM categories").use { while (it.moveToNext()) actual[it.getString(0)] = if (it.isNull(1)) null else it.getString(1) }
            assertEquals("EXPENSE", actual["food"])
            assertEquals("INCOME", actual["salary"])
            assertNull(actual["mixed"]); assertNull(actual["unused"]); assertNull(actual["loan"])
            query("SELECT COUNT(*) FROM transactions").use { it.moveToFirst(); assertEquals(5, it.getInt(0)) }
            close()
        }
    }
    @Test fun categoryTypePersistsAndCanBeChanged() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, IvyRoomDatabase::class.java).build()
        try {
            val category = CategoryEntity(name = "Salary", color = 0, transactionType = CategoryType.INCOME)
            db.writeCategoryDao.save(category)
            assertEquals(CategoryType.INCOME, db.categoryDao.findById(category.id)!!.transactionType)
            db.writeCategoryDao.save(category.copy(transactionType = CategoryType.EXPENSE))
            assertEquals(CategoryType.EXPENSE, db.categoryDao.findById(category.id)!!.transactionType)
            db.writeCategoryDao.save(category.copy(transactionType = null))
            assertNull(db.categoryDao.findById(category.id)!!.transactionType)
        } finally { db.close() }
    }
}
