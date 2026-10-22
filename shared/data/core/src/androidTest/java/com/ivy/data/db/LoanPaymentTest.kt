package com.ivy.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ivy.base.legacy.Theme
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.*
import com.ivy.data.model.*
import com.ivy.data.repository.LoanPaymentRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LoanPaymentTest {
    private suspend fun setup(db: IvyRoomDatabase): AccountEntity {
        db.writeSettingsDao.save(SettingsEntity(Theme.LIGHT, "INR", 0.0, "Test"))
        return AccountEntity(name = "Bank", currency = "INR", color = 0).also { db.writeAccountDao.save(it) }
    }
    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, IvyRoomDatabase::class.java).build()
    private suspend fun message(db: IvyRoomDatabase, body: String) = db.writeCapturedMessageDao.insert(CapturedMessageEntity(source = "test", sender = "Bank", title = null, body = body, timestamp = System.currentTimeMillis()))
    private suspend fun save(repo: LoanPaymentRepository, account: AccountEntity, action: LoanPayment.Action, amount: Double, loan: LoanEntity? = null, message: Long? = null) =
        repo.save(action, "Shammas", loan?.id, amount, account.id, "INR", null, null, Instant.now(), message)

    @Test fun notificationLendingCreatesDebtAndOnlyOneOutgoingTransfer() = runBlocking {
        val db = database()
        try {
            val account = setup(db); val repo = LoanPaymentRepository(db)
            val id = message(db, "Paid INR 500 to Shammas ref ABC123")
            save(repo, account, LoanPayment.Action.LEND, 500.0, message = id)
            val loan = db.loanDao.findAll().single()
            assertEquals(LoanType.LEND, loan.type); assertEquals(500.0, loan.amount, 0.0)
            val transaction = db.transactionDao.findAll().single()
            assertEquals(TransactionType.TRANSFER, transaction.type)
            assertEquals(account.id, transaction.accountId); assertEquals(loan.id, transaction.loanId)
            assertTrue(db.capturedMessageDao.findById(id)!!.isDismissed)
            try { save(repo, account, LoanPayment.Action.LEND, 500.0, message = id); fail("Duplicate must fail") } catch (_: IllegalStateException) {}
            assertEquals(1, db.loanDao.findAll().size); assertEquals(1, db.transactionDao.findAll().size)
        } finally { db.close() }
    }
    @Test fun borrowingAndRepayingUpdateDebtAndCashWithoutExpenses() = runBlocking {
        val db = database()
        try {
            val account = setup(db); val repo = LoanPaymentRepository(db)
            save(repo, account, LoanPayment.Action.BORROW, 500.0, message = message(db, "Received INR 500 from Shammas ref BOR123"))
            val loan = db.loanDao.findAll().single()
            assertEquals(account.id, db.transactionDao.findAll().single().toAccountId)
            save(repo, account, LoanPayment.Action.REPAYMENT, 200.0, loan, message(db, "Paid INR 200 to Shammas ref REP123"))
            assertEquals(300.0, repo.choices().single().remaining, 0.000001)
            assertTrue(db.transactionDao.findAll().all { it.type == TransactionType.TRANSFER })
            assertEquals(account.id, db.transactionDao.findAll().single { it.loanRecordId != null }.accountId)
        } finally { db.close() }
    }
    @Test fun lendingAgainUsesIncreaseRecordAndReceivingRepaymentReducesSameLoan() = runBlocking {
        val db = database()
        try {
            val account = setup(db); val repo = LoanPaymentRepository(db)
            save(repo, account, LoanPayment.Action.LEND, 500.0)
            val loan = db.loanDao.findAll().single()
            save(repo, account, LoanPayment.Action.LEND, 100.0, loan)
            save(repo, account, LoanPayment.Action.REPAYMENT, 200.0, loan, message(db, "Received INR 200 from Shammas ref REP456"))
            assertEquals(1, db.loanDao.findAll().size)
            assertEquals(400.0, repo.choices().single().remaining, 0.000001)
            assertEquals(2, db.loanRecordDao.findAll().size)
            assertTrue(db.transactionDao.findAll().all { it.type == TransactionType.TRANSFER })
        } finally { db.close() }
    }
    @Test fun wrongDirectionAndOverpaymentDoNotWriteOrConsumeNotification() = runBlocking {
        val db = database()
        try {
            val account = setup(db); val repo = LoanPaymentRepository(db)
            val wrong = message(db, "Paid INR 100 to Shammas ref WRONG1")
            try { save(repo, account, LoanPayment.Action.BORROW, 100.0, message = wrong); fail("Wrong direction must fail") } catch (_: IllegalArgumentException) {}
            assertTrue(db.loanDao.findAll().isEmpty()); assertFalse(db.capturedMessageDao.findById(wrong)!!.isDismissed)
            save(repo, account, LoanPayment.Action.LEND, 100.0)
            val loan = db.loanDao.findAll().single()
            try { save(repo, account, LoanPayment.Action.REPAYMENT, 101.0, loan); fail("Overpayment must fail") } catch (_: IllegalArgumentException) {}
            assertTrue(db.loanRecordDao.findAll().isEmpty()); assertEquals(1, db.transactionDao.findAll().size)
        } finally { db.close() }
    }
    @Test fun paymentsUpdateALoanCreatedThroughTheExistingLoansPage() = runBlocking {
        val db = database()
        try {
            val account = setup(db); val repo = LoanPaymentRepository(db)
            val existing = LoanEntity("Shammas", 500.0, LoanType.LEND, accountId = account.id, note = "Existing loan")
            db.writeLoanDao.save(existing)
            val original = TransactionEntity(accountId = account.id, type = TransactionType.EXPENSE, amount = 500.0, loanId = existing.id, title = "Shammas")
            db.writeTransactionDao.save(original)
            save(repo, account, LoanPayment.Action.LEND, 100.0, existing, message(db, "Paid INR 100 to Shammas ref OLD1"))
            save(repo, account, LoanPayment.Action.REPAYMENT, 600.0, existing, message(db, "Received INR 600 from Shammas ref OLD2"))
            assertEquals(existing, db.loanDao.findAll().single())
            assertEquals(0.0, repo.choices().single().remaining, 0.000001)
            assertEquals(original, db.transactionDao.findLoanTransaction(existing.id))
            val records = db.loanRecordDao.findAllByLoanId(existing.id)
            assertEquals(2, records.size)
            records.forEach { assertEquals(it.id, db.transactionDao.findLoanRecordTransaction(it.id)!!.loanRecordId) }
        } finally { db.close() }
    }

    @Test fun existingFormAppearanceAndNotesArePreservedWhenCreatingALoan() = runBlocking {
        val db = database()
        try {
            val account = setup(db); val repo = LoanPaymentRepository(db)
            repo.save(LoanPayment.Action.LEND, "Shammas", null, 100.0, account.id, "INR", null, "Lunch advance",
                Instant.now(), null, color = 12345, icon = "loan")
            val loan = db.loanDao.findAll().single()
            assertEquals(12345, loan.color); assertEquals("loan", loan.icon)
            assertTrue(loan.note!!.endsWith("Lunch advance"))
        } finally { db.close() }
    }

    @Test fun interestRepaymentUsesTheExistingRecordFlagWithoutReducingPrincipal() = runBlocking {
        val db = database()
        try {
            val account = setup(db); val repo = LoanPaymentRepository(db)
            save(repo, account, LoanPayment.Action.BORROW, 100.0)
            val loan = db.loanDao.findAll().single()
            repo.save(LoanPayment.Action.REPAYMENT, loan.name, loan.id, 10.0, account.id, "INR", null, "Interest",
                Instant.now(), message(db, "Paid INR 10 to Shammas ref INT1"), interest = true)
            assertTrue(db.loanRecordDao.findAll().single().interest)
            assertEquals(100.0, repo.choices().single().remaining, 0.000001)
        } finally { db.close() }
    }

    @Test fun aFailedCashMovementRollsBackTheLoanRecord() = runBlocking {
        val db = database()
        try {
            val account = setup(db); val repo = LoanPaymentRepository(db)
            save(repo, account, LoanPayment.Action.LEND, 100.0)
            val loan = db.loanDao.findAll().single()
            val id = message(db, "Received INR 50 from Shammas ref FAIL1")
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_loan_payment BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'Test failure'); END")
            try { save(repo, account, LoanPayment.Action.REPAYMENT, 50.0, loan, id); fail("Write must fail") } catch (_: android.database.sqlite.SQLiteException) {}
            assertTrue(db.loanRecordDao.findAll().isEmpty())
            assertFalse(db.capturedMessageDao.findById(id)!!.isDismissed)
            assertEquals(100.0, repo.choices().single().remaining, 0.000001)
        } finally { db.close() }
    }
}
