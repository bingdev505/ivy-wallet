package com.ivy.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import com.ivy.base.legacy.Theme
import com.ivy.base.model.TransactionType
import com.ivy.data.db.entity.*
import com.ivy.data.model.*
import com.ivy.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class PaymentSuggestionsTest {
    @Test fun learnedAliasPersistsAndExpenseCorrectionOverridesLoan() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("payment_suggestions", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(context, IvyRoomDatabase::class.java).build()
        try {
            db.writeSettingsDao.save(SettingsEntity(Theme.LIGHT, "INR", 0.0, "Test"))
            db.writeLoanDao.save(LoanEntity("Shammas", 500.0, LoanType.LEND))
            val payment = PaymentParser.parse(null, "Paid INR 100 to shammas@upi")
            val repo = PaymentSuggestionRepository(context, db, LoanPaymentRepository(db))
            repo.remember(payment, PaymentSuggestion.Kind.LOAN, "Shammas")
            val reopened = PaymentSuggestionRepository(context, db, LoanPaymentRepository(db))
            assertEquals("Shammas", reopened.suggest(payment).name)
            assertEquals(1, reopened.suggest(payment).loans.size)
            reopened.remember(payment, PaymentSuggestion.Kind.EXPENSE, categoryId = "food")
            assertEquals(PaymentSuggestion.Kind.EXPENSE, reopened.suggest(payment).kind)
            assertEquals("food", reopened.suggest(payment).categoryId)
            assertEquals(PaymentSuggestion.Kind.INCOME, reopened.suggest(payment.copy(isDebit = false)).kind)
        } finally { db.close(); prefs.edit().clear().commit() }
    }
    @Test fun shopCategoryUsesMatchingTypeAndExcludesLoanTransactions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, IvyRoomDatabase::class.java).build()
        try {
            db.writeSettingsDao.save(SettingsEntity(Theme.LIGHT, "INR", 0.0, "Test"))
            val account = UUID.randomUUID(); val food = UUID.randomUUID()
            db.writeTransactionDao.save(TransactionEntity(account, TransactionType.EXPENSE, 100.0,
                title = "Cafe", categoryId = food, dateTime = Instant.now().minusSeconds(10)))
            db.writeTransactionDao.save(TransactionEntity(account, TransactionType.EXPENSE, 100.0,
                title = "Cafe", categoryId = UUID.randomUUID(), loanId = UUID.randomUUID(), dateTime = Instant.now()))
            db.writeTransactionDao.save(TransactionEntity(account, TransactionType.INCOME, 100.0,
                title = "Cafe", categoryId = UUID.randomUUID(), dateTime = Instant.now()))
            val repo = PaymentSuggestionRepository(context, db, LoanPaymentRepository(db))
            val result = repo.suggest(PaymentParser.parse(null, "Paid INR 100 at Cafe"))
            assertEquals(PaymentSuggestion.Kind.EXPENSE, result.kind)
            assertEquals(food.toString(), result.categoryId)
        } finally { db.close() }
    }
}
