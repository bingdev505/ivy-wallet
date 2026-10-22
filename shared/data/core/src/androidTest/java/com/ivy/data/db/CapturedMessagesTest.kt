package com.ivy.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ivy.data.db.entity.CapturedMessageEntity
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.base.model.TransactionType
import java.util.UUID
import com.ivy.data.db.migration.Migration130to131_CapturedMessages
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapturedMessagesTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), IvyRoomDatabase::class.java,
        listOf(IvyRoomDatabase.DeleteSEMigration()), FrameworkSQLiteOpenHelperFactory(),
    )

    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, IvyRoomDatabase::class.java,
    ).build()

    private suspend fun withDatabase(block: suspend (IvyRoomDatabase) -> Unit) {
        val db = database()
        try {
            block(db)
        } finally {
            db.close()
        }
    }

    private val message = CapturedMessageEntity(source = "bank", sender = "Bank", title = null, body = "Paid INR 200 at Shop", timestamp = 500_000)

    @Test fun smsAndAppCopiesAreCapturedOnceButNewReferenceIsKept() = runBlocking {
        withDatabase { db ->
            val sms = message.copy(source = "SMS", body = "Paid INR 200 to Shop. UPI Ref: 123456789012")
            assertNotNull(db.writeCapturedMessageDao.insertIfNew(sms))
            assertNull(db.writeCapturedMessageDao.insertIfNew(sms.copy(source = "bank.app", sender = "Alert", title = "Bank alert",
                body = "INR 200 debited. Ref No: 123456789012", timestamp = sms.timestamp + 1000)))
            assertNotNull(db.writeCapturedMessageDao.insertIfNew(sms.copy(body = sms.body.replace("123456789012", "333456789012"))))
            assertEquals(2, db.capturedMessageDao.getAllActiveMessages().first().size)
        }
    }

    @Test fun oldCopiesShowOnceAndSavingConsumesEveryCopy() = runBlocking {
        withDatabase { db ->
            val first = db.writeCapturedMessageDao.insert(message)
            val copy = db.writeCapturedMessageDao.insert(message.copy(source = "SMS", timestamp = message.timestamp + 1000))
            db.writeCapturedMessageDao.insert(message.copy(source = "com.ivy.wallet", timestamp = message.timestamp + 240000,
                body = message.body + "\n\nReview before recording in Ivy Wallet.\n\nReview before recording in Ivy Wallet."))
            val repository = com.ivy.data.repository.impl.InboxRepositoryImpl(db)
            assertEquals(1, repository.getAllActiveMessages().first().size)
            assertEquals(1, repository.getAllActiveMessages().first().size)
            assertEquals(3, db.capturedMessageDao.getAllActiveMessages().first().size)
            val transaction = TransactionEntity(accountId = UUID.randomUUID(), type = TransactionType.EXPENSE, amount = 200.0)
            db.recordCapturedPaymentDao.saveCaptured(transaction, first)
            assertTrue(db.capturedMessageDao.getAllActiveMessages().first().isEmpty())
            try { db.recordCapturedPaymentDao.saveCaptured(transaction.copy(id = UUID.randomUUID()), copy); fail("Duplicate payment must not be saved") }
            catch (_: IllegalStateException) { }
            assertEquals(1, db.transactionDao.findAll().size)
        }
    }

    @Test fun sharedBillSavesExpenseLoansAndConsumesInboxTogether() = runBlocking {
        withDatabase { db ->
            val id = db.writeCapturedMessageDao.insertIfNew(message)!!
            val account = UUID.randomUUID()
            val plan = com.ivy.data.model.SharedBill.plan(true, java.math.BigDecimal("200"), java.math.BigDecimal("50"),
                listOf(com.ivy.data.model.SharedBill.Share("Friend", java.math.BigDecimal("150"))),
                account, "INR", UUID.randomUUID(), "Food")
            val writer = db.writeSharedBillDao
            val expenseId = plan.transactions.single { it.type == TransactionType.EXPENSE }.id
            val tag = com.ivy.data.db.entity.TagAssociationEntity(UUID.randomUUID(), expenseId, java.time.Instant.now(), false)
            writer.save(com.ivy.data.model.SharedBill.clearingAccount("INR"), plan.loans, plan.transactions, id, listOf(tag))
            assertEquals(expenseId, db.tagAssociationDao.findAll().single().associatedId)
            assertEquals(1, db.loanDao.findAll().size)
            assertEquals(2, db.transactionDao.findAll().size)
            assertTrue(db.capturedMessageDao.getAllActiveMessages().first().isEmpty())
            try {
                writer.save(com.ivy.data.model.SharedBill.clearingAccount("INR"), plan.loans, plan.transactions, id)
                fail("Duplicate capture must be rejected")
            } catch (_: IllegalStateException) { }
            assertEquals(2, db.transactionDao.findAll().size)
        }
    }

    @Test fun sharedBillWriteFailureRollsBackEveryPart() = runBlocking {
        withDatabase { db ->
            val id = db.writeCapturedMessageDao.insertIfNew(message)!!
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_split BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            val plan = com.ivy.data.model.SharedBill.plan(false, java.math.BigDecimal("200"), java.math.BigDecimal("50"),
                listOf(com.ivy.data.model.SharedBill.Share("Friend", java.math.BigDecimal("200"))),
                UUID.randomUUID(), "INR", UUID.randomUUID(), "Food")
            try {
                db.writeSharedBillDao.save(com.ivy.data.model.SharedBill.clearingAccount("INR"), plan.loans, plan.transactions, id)
                fail("Trigger should reject the save")
            } catch (_: android.database.sqlite.SQLiteException) { }
            assertTrue(db.loanDao.findAll().isEmpty())
            assertTrue(db.accountDao.findAll().isEmpty())
            assertEquals(1, db.capturedMessageDao.getAllActiveMessages().first().size)
        }
    }

    @Test fun migrationPreservesExistingDataAndMatchesRoomSchema() {
        helper.createDatabase("captured-migration-test", 130).apply {
            execSQL("INSERT INTO accounts (id, name, color, orderNum, includeInBalance, isSynced, isDeleted) VALUES ('test-account', 'Savings', 0, 0, 1, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate("captured-migration-test", 131, true, Migration130to131_CapturedMessages()).use { db ->
            db.query("SELECT name FROM accounts").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Savings", cursor.getString(0))
            }
        }
    }

    @Test fun concurrentNotificationUpdatesCreateOneInboxItem() = runBlocking {
        withDatabase { db ->
            val results = (1..12).map { async { db.writeCapturedMessageDao.insertIfNew(message) } }.awaitAll()
            assertEquals(1, results.count { it != null })
            assertEquals(1, db.capturedMessageDao.getAllActiveMessages().first().size)
        }
    }

    @Test fun repeatedPaymentLaterIsKeptAndDismissedUpdateDoesNotReappear() = runBlocking {
        withDatabase { db ->
            val dao = db.writeCapturedMessageDao
            val id = dao.insertIfNew(message)!!
            dao.dismiss(id)
            assertNull(dao.insertIfNew(message.copy(timestamp = message.timestamp + 1000)))
            assertNotNull(dao.insertIfNew(message.copy(timestamp = message.timestamp + 180_000)))
            assertEquals(1, db.capturedMessageDao.getAllActiveMessages().first().size)
        }
    }

    @Test fun clearAllHidesActiveMessages() = runBlocking {
        withDatabase { db ->
            db.writeCapturedMessageDao.insertIfNew(message)
            db.writeCapturedMessageDao.insertIfNew(message.copy(body = "Paid INR 300"))
            db.writeCapturedMessageDao.dismissAllActive()
            assertTrue(db.capturedMessageDao.getAllActiveMessages().first().isEmpty())
        }
    }

    @Test fun savingConsumesPaymentOnceAndDuplicateSaveDoesNotCreateAnotherTransaction() = runBlocking {
        withDatabase { db ->
            val id = db.writeCapturedMessageDao.insertIfNew(message)!!
            val transaction = TransactionEntity(accountId = UUID.randomUUID(), type = TransactionType.EXPENSE, amount = 200.0)
            assertEquals(1, db.capturedMessageDao.getAllActiveMessages().first().size)
            db.recordCapturedPaymentDao.saveCaptured(transaction, id)
            assertTrue(db.capturedMessageDao.getAllActiveMessages().first().isEmpty())
            try {
                db.recordCapturedPaymentDao.saveCaptured(transaction.copy(id = UUID.randomUUID()), id)
                fail("The same payment must not be recorded twice")
            } catch (_: IllegalStateException) {
                // The original transaction remains the only record.
            }
            assertEquals(1, db.transactionDao.findAll().size)
        }
    }

    @Test fun failedTransactionSaveLeavesInboxItemAvailable() = runBlocking {
        withDatabase { db ->
            val id = db.writeCapturedMessageDao.insertIfNew(message)!!
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_test_transaction BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'Test write failure'); END"
            )
            try {
                db.recordCapturedPaymentDao.saveCaptured(
                    TransactionEntity(accountId = UUID.randomUUID(), type = TransactionType.EXPENSE, amount = 200.0), id,
                )
                fail("The injected write failure must be reported")
            } catch (_: android.database.sqlite.SQLiteException) {
                // A failed save must never consume the inbox item.
            }
            assertEquals(1, db.capturedMessageDao.getAllActiveMessages().first().size)
            assertTrue(db.transactionDao.findAll().isEmpty())
        }
    }
}
