package com.ivy.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.ivy.data.db.entity.*
import com.ivy.data.db.migration.Migration131to132_TripGroups
import com.ivy.data.repository.*
import com.ivy.data.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class TripGroupsTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), IvyRoomDatabase::class.java,
        listOf(IvyRoomDatabase.DeleteSEMigration()), FrameworkSQLiteOpenHelperFactory())
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun migrationKeepsExistingCapturedPayments() {
        helper.createDatabase("trip-migration", 131).apply {
            execSQL("INSERT INTO captured_messages (source,sender,title,body,timestamp,isRead,isDismissed) VALUES ('test','test',NULL,'Paid INR 200',123,0,0)")
            close()
        }
        helper.runMigrationsAndValidate("trip-migration", 132, true, Migration131to132_TripGroups()).apply {
            query("SELECT COUNT(*) FROM captured_messages").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            execSQL("INSERT INTO trip_groups VALUES ('group','Trip','[\"Arun\"]')")
            close()
        }
    }

    @Test fun groupNamesAndMembersAreReusableAndRepaymentsUpdateTheirBalance() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, IvyRoomDatabase::class.java).build()
        try {
            val trips = TripRepository(db)
            trips.saveGroup(null, " Trip ", listOf(" Rahul ", "rahul", "Anu"))
            val group = db.tripDao.groups().single()
            assertEquals(listOf("Rahul", "Anu"), group.members())
            assertEquals(listOf("Rahul", "Anu"), trips.people())
            val account = UUID.randomUUID()
            val plan = SharedBill.planSplit(null, BigDecimal("900"), BigDecimal("300"), listOf(
                SharedBill.Share("Rahul", BigDecimal("300")), SharedBill.Share("Anu", BigDecimal("300"))), account, "INR", UUID.randomUUID(), "Food")
            val expense = TripExpenseEntity(groupId = group.id, title = "Food", total = 900.0, ownShare = 300.0, currency = "INR", payer = null,
                sharesJson = "[]", loanIdsJson = Json.encodeToString(plan.loans.map { it.id.toString() }), timestamp = 123)
            SharedBillRepository(db).save("INR", plan, null, tripExpense = expense)
            assertEquals(2, trips.balances(group.id).size)
            val loan = plan.loans.first()
            db.writeLoanRecordDao.save(LoanRecordEntity(loanId = loan.id, amount = 100.0, dateTime = Instant.now()))
            assertEquals(200.0, trips.balances(group.id).single { it.loanId == loan.id }.amount, 0.0)
            trips.saveGroup(group.id, "Renamed", listOf("Anu"))
            assertEquals(2, trips.balances(group.id).size)
            assertEquals(1, trips.expenses(group.id).size)
            db.tripDao.deleteAll()
            assertTrue(db.tripDao.groups().isEmpty())
            assertTrue(db.tripDao.expenses().isEmpty())
        } finally { db.close() }
    }

    @Test fun failingGroupExpenseWriteRollsBackLoansAndTransactions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, IvyRoomDatabase::class.java).build()
        try {
            TripRepository(db).saveGroup(null, "Trip", listOf("A"))
            val group = db.tripDao.groups().single()
            val plan = SharedBill.plan(true, BigDecimal("600"), BigDecimal("300"), listOf(SharedBill.Share("A", BigDecimal("300"))), UUID.randomUUID(), "INR", UUID.randomUUID(), "Food")
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_trip BEFORE INSERT ON trip_expenses BEGIN SELECT RAISE(ABORT, 'Test write failure'); END")
            try {
                SharedBillRepository(db).save("INR", plan, null, tripExpense = TripExpenseEntity(groupId = group.id, title = "Food", total = 600.0, ownShare = 300.0, currency = "INR", payer = null, sharesJson = "[]", loanIdsJson = "[]", timestamp = 123))
                fail("Group write must fail")
            } catch (_: android.database.sqlite.SQLiteException) { }
            assertTrue(db.loanDao.findAll().isEmpty())
            assertTrue(db.transactionDao.findAll().isEmpty())
        } finally { db.close() }
    }
}
