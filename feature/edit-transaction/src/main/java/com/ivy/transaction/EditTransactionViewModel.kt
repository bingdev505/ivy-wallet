package com.ivy.transaction

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewModelScope
import com.ivy.base.Toaster
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.Transaction
import com.ivy.base.legacy.refreshWidget
import com.ivy.base.model.TransactionType
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.LoanDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Tag
import com.ivy.data.model.TagId
import com.ivy.data.model.TransactionId
import com.ivy.data.model.primitive.AssociationId
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TagRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TagMapper
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.domain.features.Features
import com.ivy.legacy.data.EditTransactionDisplayLoan
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.datamodel.temp.toDomain
import com.ivy.legacy.domain.deprecated.logic.AccountCreator
import com.ivy.legacy.utils.computationThread
import com.ivy.legacy.utils.ioThread
import com.ivy.legacy.utils.toLowerCaseLocal
import com.ivy.legacy.utils.uiThread
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.MainScreen
import com.ivy.navigation.Navigation
import com.ivy.ui.ComposeViewModel
import com.ivy.ui.R
import com.ivy.ui.time.impl.DateTimePicker
import com.ivy.wallet.domain.action.account.AccountByIdAct
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.domain.action.transaction.TrnByIdAct
import com.ivy.wallet.domain.data.CustomExchangeRateState
import com.ivy.wallet.domain.deprecated.logic.CategoryCreator
import com.ivy.wallet.domain.deprecated.logic.PlannedPaymentsLogic
import com.ivy.wallet.domain.deprecated.logic.SmartTitleSuggestionsLogic
import com.ivy.wallet.domain.deprecated.logic.currency.ExchangeRatesLogic
import com.ivy.wallet.domain.deprecated.logic.loantrasactions.LoanTransactionsLogic
import com.ivy.wallet.domain.deprecated.logic.model.CreateAccountData
import com.ivy.wallet.domain.deprecated.logic.model.CreateCategoryData
import com.ivy.widget.balance.WalletBalanceWidgetReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@Suppress("LargeClass")
@Stable
@SuppressLint("StaticFieldLeak")
@HiltViewModel
class EditTransactionViewModel @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val toaster: Toaster,
    private val loanDao: LoanDao,
    private val settingsDao: SettingsDao,
    private val nav: Navigation,
    private val sharedPrefs: SharedPrefs,
    private val exchangeRatesLogic: ExchangeRatesLogic,
    private val categoryCreator: CategoryCreator,
    private val accountCreator: AccountCreator,
    private val plannedPaymentsLogic: PlannedPaymentsLogic,
    private val smartTitleSuggestionsLogic: SmartTitleSuggestionsLogic,
    private val loanTransactionsLogic: LoanTransactionsLogic,
    private val accountsAct: AccountsAct,
    private val categoryRepository: CategoryRepository,
    private val trnByIdAct: TrnByIdAct,
    private val accountByIdAct: AccountByIdAct,
    private val transactionRepo: TransactionRepository,
    private val transactionMapper: TransactionMapper,
    private val tagRepository: TagRepository,
    private val tagMapper: TagMapper,
    private val features: Features,
    private val timeConverter: TimeConverter,
    private val timeProvider: TimeProvider,
    private val dateTimePicker: DateTimePicker,
    private val saveCapturedTransaction: com.ivy.data.repository.SaveCapturedTransaction,
    private val sharedBillRepository: com.ivy.data.repository.SharedBillRepository,
    private val tripRepository: com.ivy.data.repository.TripRepository,
    private val loanPaymentRepository: com.ivy.data.repository.LoanPaymentRepository,
    private val paymentSuggestions: com.ivy.data.repository.PaymentSuggestionRepository,
    private val autoBackupManager: com.ivy.data.backup.auto.AutoBackupManager,
) : ComposeViewModel<EditTransactionViewState, EditTransactionViewEvent>() {

    private var transactionType by mutableStateOf(TransactionType.EXPENSE)
    private var initialTitle by mutableStateOf<String?>(null)
    private var titleSuggestions by mutableStateOf(persistentSetOf<String>())
    private var titleSuggestionCategories by mutableStateOf<Map<String, String>>(emptyMap())
    private var titleSuggestionsJob: Job? = null
    private var currency by mutableStateOf("")
    private var description by mutableStateOf<String?>(null)
    private var dateTime by mutableStateOf<Instant?>(null)
    private var dueDate by mutableStateOf<Instant?>(null)
    private var accounts by mutableStateOf<ImmutableList<Account>>(persistentListOf())
    private var categories by mutableStateOf<ImmutableList<Category>>(persistentListOf())
    private var tags by mutableStateOf<ImmutableList<Tag>>(persistentListOf())
    private var transactionAssociatedTags by mutableStateOf<ImmutableList<TagId>>(persistentListOf())
    private var account by mutableStateOf<Account?>(null)
    private var toAccount by mutableStateOf<Account?>(null)
    private var category by mutableStateOf<Category?>(null)
    private var amount by mutableDoubleStateOf(0.0)
    private var hasChanges by mutableStateOf(false)
    private var displayLoanHelper by mutableStateOf(EditTransactionDisplayLoan())

    private var paidHistory: Instant? = null

    // This is used to when the transaction is associated with a loan/loan record,
    // used to indicate the background updating of loan/loanRecord data
    private var backgroundProcessingStarted by mutableStateOf(false)

    private var customExchangeRateState by mutableStateOf(CustomExchangeRateState())

    private var loadedTransaction: Transaction? = null
    private var editMode = false
    private var capturedMessageId: Long? = null
    private var capturedAmount: BigDecimal? = null
    private var capturedCurrency: String? = null
    var pendingPaymentCurrency by mutableStateOf<String?>(null)
        private set
    private var saving by mutableStateOf(false)
    val savingPayment: Boolean get() = saving
    var loanFormReady by mutableStateOf(false)
        private set
    var paymentDraft by mutableStateOf(ExpensePaymentDraft())
        private set
    var loanPaymentAction by mutableStateOf<com.ivy.data.model.LoanPayment.Action?>(null)
        private set
    var paymentLoans by mutableStateOf<List<com.ivy.data.repository.LoanPaymentRepository.Choice>>(emptyList())
        private set
    private var capturedDebit: Boolean? = null
    val loanPaymentDebit: Boolean? get() = capturedDebit
    var paymentSuggestion by mutableStateOf<com.ivy.data.model.PaymentSuggestion.Result?>(null)
        private set
    private var detectedPayment: com.ivy.data.model.ParsedPaymentInfo? = null
    fun loanFormName(): String? = if (detectedPayment != null) paymentSuggestion?.name else title?.takeIf { it.isNotBlank() }

    fun chooseLoanAction(action: com.ivy.data.model.LoanPayment.Action) {
        if (editMode || saving) return
        loanPaymentAction = action
        paymentDraft = ExpensePaymentDraft()
        transactionType = when (action) {
            com.ivy.data.model.LoanPayment.Action.LEND -> TransactionType.EXPENSE
            com.ivy.data.model.LoanPayment.Action.BORROW -> TransactionType.INCOME
            com.ivy.data.model.LoanPayment.Action.REPAYMENT -> if (capturedDebit == false) TransactionType.INCOME else TransactionType.EXPENSE
        }
        hasChanges = true
    }

    fun leaveLoanMode() { loanPaymentAction = null }
    fun loanFormCurrency(): String = capturedCurrency ?: currency
    fun loanFormAccounts(): List<Account> = accounts.filter {
        val code = it.currency ?: baseUserCurrency
        it.id != com.ivy.data.model.SharedBill.clearingAccount(code).id && (capturedCurrency == null || code == capturedCurrency)
    }

    fun submitNewLoan(data: com.ivy.wallet.domain.deprecated.logic.model.CreateLoanData) {
        val action = if (data.type == com.ivy.data.model.LoanType.LEND) com.ivy.data.model.LoanPayment.Action.LEND else com.ivy.data.model.LoanPayment.Action.BORROW
        val code = data.account?.currency ?: baseUserCurrency
        val matching = paymentLoans.filter { it.currency == code && it.loan.type == data.type &&
            com.ivy.data.model.ExpenseSplit.key(it.loan.name) == com.ivy.data.model.ExpenseSplit.key(data.name) }
        if (matching.size > 1) {
            android.widget.Toast.makeText(context, "Choose which existing loan to update", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        saveExistingLoanForm(action, data.name, matching.singleOrNull()?.loan?.id, data.amount, data.account,
            with(timeConverter) { data.dateTime.toUTC() }, data.note, data.color.toArgb(), data.icon, false)
    }

    fun submitLoanRecord(choice: com.ivy.data.repository.LoanPaymentRepository.Choice,
        data: com.ivy.wallet.domain.deprecated.logic.model.CreateLoanRecordData) {
        val action = if (data.loanRecordType == com.ivy.base.model.LoanRecordType.DECREASE) com.ivy.data.model.LoanPayment.Action.REPAYMENT
            else if (choice.loan.type == com.ivy.data.model.LoanType.LEND) com.ivy.data.model.LoanPayment.Action.LEND else com.ivy.data.model.LoanPayment.Action.BORROW
        saveExistingLoanForm(action, choice.loan.name, choice.loan.id, data.amount, data.account, data.dateTime, data.note, null, null, data.interest)
    }

    private fun saveExistingLoanForm(action: com.ivy.data.model.LoanPayment.Action, person: String, id: UUID?,
        value: Double, selected: Account?, time: Instant, note: String?, color: Int?, icon: String?, interest: Boolean) {
        if (saving) return
        if (selected == null) return
        saving = true
        viewModelScope.launch {
            try {
                ioThread {
                    loanPaymentRepository.save(action, person, id, value, selected.id, selected.currency ?: baseUserCurrency,
                        title, note, time, capturedMessageId, transactionAssociatedTags.map { it.value }, color, icon, interest)
                    refreshWidget(WalletBalanceWidgetReceiver::class.java)
                }
                capturedMessageId?.let { context.getSystemService(android.app.NotificationManager::class.java)?.cancel("captured_payment", it.toInt()) }
                paymentSuggestions.remember(detectedPayment, com.ivy.data.model.PaymentSuggestion.Kind.LOAN, person)
                capturedMessageId = null
                leaveLoanMode()
                closeScreen()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, e.message ?: "Unable to save loan", android.widget.Toast.LENGTH_LONG).show()
            } finally { saving = false }
        }
    }
    var knownPeople by mutableStateOf<List<String>>(emptyList())
        private set
    var tripGroups by mutableStateOf<List<com.ivy.data.db.entity.TripGroupEntity>>(emptyList())
        private set

    fun updatePaymentDraft(draft: ExpensePaymentDraft) {
        if (!saving && paymentDraft != draft) { paymentDraft = draft; hasChanges = true }
    }

    // Used for optimising in updating all loan/loanRecords
    private var accountsChanged = false

    private var title: String? = null
    private lateinit var baseUserCurrency: String
    private var tagSearchJob: Job? = null
    private val tagSearchDebounceTimeInMills: Long = 500

    fun start(screen: EditTransactionScreen) {
        loanFormReady = false
        viewModelScope.launch {
            editMode = screen.initialTransactionId != null
            transactionType = screen.type
            titleSuggestionsJob?.cancel()
            titleSuggestions = persistentSetOf()
            titleSuggestionCategories = emptyMap()
            capturedMessageId = screen.capturedMessageId
            capturedAmount = screen.initialAmount
            capturedCurrency = screen.initialCurrency
            pendingPaymentCurrency = null
            paymentDraft = ExpensePaymentDraft()
            loanPaymentAction = null
            capturedDebit = if (screen.capturedMessageId != null) screen.type == TransactionType.EXPENSE else null
            paymentSuggestion = null
            detectedPayment = screen.capturedMessageId?.let { ioThread { paymentSuggestions.payment(it) } }
            paymentLoans = ioThread { loanPaymentRepository.choices() }
            knownPeople = ioThread { tripRepository.people() }
            tripGroups = ioThread { tripRepository.observeGroups().first() }

            baseUserCurrency = baseCurrency()

            val tagList = async { getAllTags() }

            val getAccounts = accountsAct(Unit)
            if (getAccounts.isEmpty()) {
                closeScreen()
                return@launch
            }
            accounts = getAccounts

            categories = sortCategories()

            reset()

            loadedTransaction = screen.initialTransactionId?.let {
                trnByIdAct(it)
            } ?: Transaction(
                accountId = defaultAccountId(
                    screen = screen,
                    accounts = getAccounts
                ),
                categoryId = screen.categoryId,
                type = screen.type,
                amount = screen.initialAmount ?: BigDecimal.ZERO,
                toAmount = screen.initialAmount ?: BigDecimal.ZERO,
                title = screen.initialTitle,
                description = screen.initialDescription
            )

            tags = tagList.await()
            transactionAssociatedTags =
                tagRepository.findByAssociatedId(AssociationId(loadedTransaction().id)).map(Tag::id)
                    .toImmutableList()
            display(loadedTransaction!!)
            val detectedCurrency = screen.initialCurrency
            if (!editMode && detectedCurrency != null && detectedCurrency != (account?.currency ?: baseUserCurrency)) {
                // Keep the detected amount and its currency together while the user
                // chooses a payment account. Never relabel it as another currency.
                pendingPaymentCurrency = detectedCurrency
                currency = detectedCurrency
            }
            detectedPayment?.let { payment ->
                val suggestion = ioThread { paymentSuggestions.suggest(payment) }
                paymentSuggestion = suggestion
                if (suggestion.kind == com.ivy.data.model.PaymentSuggestion.Kind.LOAN) {
                    chooseLoanAction(if (suggestion.repayment) com.ivy.data.model.LoanPayment.Action.REPAYMENT
                        else if (payment.isDebit) com.ivy.data.model.LoanPayment.Action.LEND else com.ivy.data.model.LoanPayment.Action.BORROW)
                } else {
                    if (suggestion.name != null && (title.isNullOrBlank() || title == payment.counterparty || title == payment.merchant)) {
                        onTitleChanged(suggestion.name)
                    }
                    if (category == null) {
                        category = categories.firstOrNull { it.id.value.toString() == suggestion.categoryId &&
                            (it.transactionType == null || it.transactionType?.name == transactionType.name) }
                    }
                }
            }
            loanFormReady = true
        }
    }

    @Composable
    override fun uiState(): EditTransactionViewState {
        return EditTransactionViewState(
            transactionType = getTransactionType(),
            initialTitle = getInitialTitle(),
            titleSuggestions = getTitleSuggestions(),
            titleSuggestionCategories = titleSuggestionCategories,
            currency = getCurrency(),
            description = getDescription(),
            dateTime = getDateTime(),
            dueDate = getDueDate(),
            accounts = getAccounts(),
            categories = getCategories(),
            account = getAccount(),
            toAccount = getToAccount(),
            category = getCategory(),
            amount = getAmount(),
            hasChanges = getHasChanges(),
            displayLoanHelper = getDisplayLoanHelper(),
            backgroundProcessingStarted = getBackgroundProcessingStarted(),
            customExchangeRateState = getCustomExchangeRateState(),
            tags = getTags(),
            transactionAssociatedTags = getTransactionAssociatedTags()
        )
    }

    @Composable
    private fun getTransactionType(): TransactionType {
        return transactionType
    }

    @Composable
    private fun getInitialTitle(): String? {
        return initialTitle
    }

    @Composable
    private fun getTitleSuggestions(): ImmutableSet<String> {
        return if (features.showTitleSuggestions.asEnabledState()) {
            titleSuggestions
        } else {
            persistentSetOf()
        }
    }

    @Composable
    private fun getCurrency(): String {
        return currency
    }

    @Composable
    private fun getDescription(): String? {
        return description
    }

    @Composable
    private fun getDateTime(): Instant? {
        return dateTime
    }

    @Composable
    private fun getDueDate(): Instant? {
        return dueDate
    }

    @Composable
    private fun getAccounts(): ImmutableList<Account> {
        return accounts
    }

    @Composable
    private fun getCategories(): ImmutableList<Category> {
        return categories
    }

    @Composable
    private fun getAccount(): Account? {
        return account
    }

    @Composable
    private fun getToAccount(): Account? {
        return toAccount
    }

    @Composable
    private fun getCategory(): Category? {
        return category
    }

    @Composable
    private fun getAmount(): Double {
        return amount
    }

    @Composable
    private fun getHasChanges(): Boolean {
        return hasChanges
    }

    @Composable
    private fun getDisplayLoanHelper(): EditTransactionDisplayLoan {
        return displayLoanHelper
    }

    @Composable
    private fun getBackgroundProcessingStarted(): Boolean {
        return backgroundProcessingStarted
    }

    @Composable
    private fun getCustomExchangeRateState(): CustomExchangeRateState {
        return customExchangeRateState
    }

    @Composable
    private fun getTags(): ImmutableList<Tag> {
        return tags
    }

    @Composable
    private fun getTransactionAssociatedTags(): ImmutableList<TagId> {
        return transactionAssociatedTags
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onEvent(event: EditTransactionViewEvent) {
        when (event) {
            is EditTransactionViewEvent.CreateAccount -> createAccount(event.data)
            is EditTransactionViewEvent.CreateCategory -> createCategory(event.data)
            EditTransactionViewEvent.Delete -> delete()
            EditTransactionViewEvent.Duplicate -> duplicate()
            is EditTransactionViewEvent.EditCategory -> editCategory(event.updatedCategory)
            is EditTransactionViewEvent.OnAccountChanged -> onAccountChanged(event.newAccount)
            is EditTransactionViewEvent.OnAmountChanged -> onAmountChanged(event.newAmount)
            is EditTransactionViewEvent.OnCategoryChanged -> onCategoryChanged(event.newCategory)
            is EditTransactionViewEvent.OnDescriptionChanged ->
                onDescriptionChanged(event.newDescription)

            is EditTransactionViewEvent.OnDueDateChanged -> onDueDateChanged(event.newDueDate)
            EditTransactionViewEvent.OnPayPlannedPayment -> onPayPlannedPayment()
            is EditTransactionViewEvent.OnChangeDate -> handleChangeDate()
            is EditTransactionViewEvent.OnChangeTime -> handleChangeTime()
            is EditTransactionViewEvent.OnSetTransactionType ->
                onSetTransactionType(event.newTransactionType)

            is EditTransactionViewEvent.OnTitleChanged -> onTitleChanged(event.newTitle)
            is EditTransactionViewEvent.OnToAccountChanged -> onToAccountChanged(event.newAccount)
            is EditTransactionViewEvent.Save -> save(event.closeScreen)
            is EditTransactionViewEvent.SetHasChanges -> setHasChanges(event.hasChangesValue)
            is EditTransactionViewEvent.UpdateExchangeRate -> updateExchangeRate(event.exRate)
            is EditTransactionViewEvent.TagEvent -> handleTagEvent(event)
        }
    }

    private fun handleTagEvent(event: EditTransactionViewEvent.TagEvent) {
        when (event) {
            is EditTransactionViewEvent.TagEvent.SaveTag -> onTagSaved(event.name)
            is EditTransactionViewEvent.TagEvent.OnTagSelect -> associateTagToTransaction(event.selectedTag)
            is EditTransactionViewEvent.TagEvent.OnTagDeSelect -> removeTagAssociation(event.selectedTag)
            is EditTransactionViewEvent.TagEvent.OnTagSearch -> searchTag(event.query)
            is EditTransactionViewEvent.TagEvent.OnTagDelete -> deleteTag(event.selectedTag)
            is EditTransactionViewEvent.TagEvent.OnTagEdit -> updateTagInformation(event.newTag)
        }
    }

    private suspend fun defaultAccountId(
        screen: EditTransactionScreen,
        accounts: List<Account>,
    ): UUID {
        if (screen.accountId != null) {
            return screen.accountId!!
        }

        val lastSelectedId = sharedPrefs.getString(
            SharedPrefs.LAST_SELECTED_ACCOUNT_ID,
            null
        )?.let { UUID.fromString(it) }
        screen.initialCurrency?.let { detectedCurrency ->
            capturedPaymentAccount(accounts, detectedCurrency, baseUserCurrency, lastSelectedId)?.let {
                return it.id
            }
        }
        if (lastSelectedId != null && ioThread {
                accounts.find {
                    it.id == lastSelectedId
                }
            } != null
        ) {
            // use last selected account
            return lastSelectedId
        }

        return accounts.first().id
    }

    private suspend fun display(transaction: Transaction) {
        this.title = transaction.title

        transactionType = transaction.type
        initialTitle = transaction.title
        dateTime = transaction.dateTime
        description = transaction.description
        dueDate = transaction.dueDate
        paidHistory = transaction.paidFor
        val selectedAccount = accountByIdAct(transaction.accountId)!!
        account = selectedAccount
        toAccount = transaction.toAccountId?.let {
            accountByIdAct(it)
        }
        category = transaction.categoryId?.let {
            categoryRepository.findById(CategoryId(it))
        }
        amount = transaction.amount.toDouble()

        updateCurrency(account = selectedAccount)

        customExchangeRateState = if (transaction.toAccountId == null) {
            CustomExchangeRateState()
        } else {
            val exchangeRate = transaction.toAmount / transaction.amount
            val toAccountCurrency =
                accounts.find { acc -> acc.id == transaction.toAccountId }?.currency
            CustomExchangeRateState(
                showCard = toAccountCurrency != account?.currency,
                exchangeRate = exchangeRate.toDouble(),
                convertedAmount = transaction.toAmount.toDouble(),
                toCurrencyCode = toAccountCurrency,
                fromCurrencyCode = currency
            )
        }

        displayLoanHelper = getDisplayLoanHelper(trans = transaction)
    }

    private suspend fun getDisplayLoanHelper(trans: Transaction): EditTransactionDisplayLoan {
        if (trans.loanId == null) {
            return EditTransactionDisplayLoan()
        }

        val loan =
            ioThread { loanDao.findById(trans.loanId!!) } ?: return EditTransactionDisplayLoan()
        val isLoanRecord = trans.loanRecordId != null

        val loanWarningDescription = if (isLoanRecord) {
            context.getString(
                R.string.note_transaction_associated_with_loan_record_of_loan,
                loan.name
            )
        } else {
            context.getString(
                R.string.note_you_are_trying_to_change_the_account_associated_with_the_loan,
                loan.name
            )
        }

        val loanCaption =
            if (isLoanRecord) {
                context.getString(
                    R.string.this_transaction_is_associated_with_loan_record,
                    loan.name
                )
            } else {
                context.getString(
                    R.string.this_transaction_is_associated_with_loan,
                    loan.name
                )
            }

        return EditTransactionDisplayLoan(
            isLoan = true,
            isLoanRecord = isLoanRecord,
            loanCaption = loanCaption,
            loanWarningDescription = loanWarningDescription
        )
    }

    private fun onAmountChanged(newAmount: Double) {
        viewModelScope.launch {
            if (pendingPaymentCurrency == null) account?.let { updateCurrency(it) }
            loadedTransaction = loadedTransaction().copy(
                amount = newAmount.toBigDecimal()
            )
            amount = newAmount
            updateCustomExchangeRateState(amt = newAmount)

            saveIfEditMode()
        }
    }

    private fun onTitleChanged(newTitle: String?) {
        loadedTransaction = loadedTransaction().copy(
            title = newTitle
        )
        this.title = newTitle

        saveIfEditMode()

        updateTitleSuggestions(newTitle)
    }

    private fun onDescriptionChanged(newDescription: String?) {
        loadedTransaction = loadedTransaction().copy(
            description = newDescription
        )
        description = newDescription

        saveIfEditMode()
    }

    private fun onAccountChanged(newAccount: Account) {
        viewModelScope.launch {
            loadedTransaction = loadedTransaction().copy(
                accountId = newAccount.id
            )
            account = newAccount
            updateCurrency(account = newAccount)

            if (pendingPaymentCurrency != null && (newAccount.currency ?: baseUserCurrency) == capturedCurrency) {
                amount = capturedAmount?.toDouble() ?: 0.0
                loadedTransaction = loadedTransaction().copy(amount = capturedAmount ?: BigDecimal.ZERO)
                pendingPaymentCurrency = null
            }
            pendingPaymentCurrency?.let { currency = it }

            updateCustomExchangeRateState(fromAccount = newAccount)

            accountsChanged = true

            // update last selected account
            sharedPrefs.putString(SharedPrefs.LAST_SELECTED_ACCOUNT_ID, newAccount.id.toString())

            saveIfEditMode()

            updateTitleSuggestions()
        }
    }

    private suspend fun updateCurrency(account: Account) {
        currency = account.currency ?: baseCurrency()
    }

    private fun onToAccountChanged(newAccount: Account) {
        viewModelScope.launch {
            loadedTransaction = loadedTransaction().copy(
                toAccountId = newAccount.id
            )
            toAccount = newAccount
            updateCustomExchangeRateState(toAccountValue = newAccount)

            saveIfEditMode()
        }
    }

    private fun onDueDateChanged(newDueDate: LocalDateTime?) {
        val newDueDateUtc = with(timeConverter) { newDueDate?.toUTC() }
        loadedTransaction = loadedTransaction().copy(
            dueDate = newDueDateUtc
        )
        dueDate = newDueDateUtc

        saveIfEditMode()
    }

    private fun handleChangeDate() {
        dateTimePicker.pickDate(
            initialDate = loadedTransaction?.dateTime,
        ) { localDate ->
            val localTime = loadedTransaction().dateTime?.let {
                with(timeConverter) { it.toLocalTime() }
            } ?: timeProvider.localTimeNow()
            loadedTransaction = loadedTransaction().copy(
                date = localDate,
            )
            updateDateTime(localDate.atTime(localTime))
        }
    }

    private fun handleChangeTime() {
        dateTimePicker.pickTime(
            initialTime = loadedTransaction?.dateTime?.let {
                with(timeConverter) {
                    it.toLocalDateTime()
                }
            }?.toLocalTime()
        ) { localTime ->
            val localDate = loadedTransaction().dateTime?.let {
                with(timeConverter) { it.toLocalDate() }
            } ?: timeProvider.localDateNow()
            loadedTransaction = loadedTransaction().copy(
                time = localTime,
            )
            updateDateTime(localDate.atTime(localTime))
        }
    }

    private fun updateDateTime(newDateTime: LocalDateTime) {
        val newDateTimeUtc = with(timeConverter) { newDateTime.toUTC() }
        loadedTransaction = loadedTransaction().copy(
            dateTime = newDateTimeUtc,
        )
        dateTime = newDateTimeUtc

        saveIfEditMode()
    }

    private fun onSetTransactionType(newTransactionType: TransactionType) {
        loadedTransaction = loadedTransaction().copy(
            type = newTransactionType
        )
        transactionType = newTransactionType
        titleSuggestions = persistentSetOf()
        titleSuggestionCategories = emptyMap()
        if (category?.transactionType != null && category?.transactionType?.name != newTransactionType.name) {
            category = null
            loadedTransaction = loadedTransaction().copy(categoryId = null)
        }
        viewModelScope.launch { categories = sortCategories(); updateTitleSuggestions() }
        saveIfEditMode()
    }

    private fun onPayPlannedPayment() {
        viewModelScope.launch {
            plannedPaymentsLogic.payOrGetLegacy(
                transaction = loadedTransaction(),
                syncTransaction = false
            ) { paidTransaction ->
                loadedTransaction = paidTransaction
                paidHistory = paidTransaction.paidFor
                dueDate = paidTransaction.dueDate
                dateTime = paidTransaction.dateTime

                saveIfEditMode(
                    closeScreen = true
                )
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            ioThread {
                loadedTransaction?.let {
                    transactionRepo.deleteById(TransactionId(it.id))
                }
                closeScreen()
            }
        }
    }

    private fun duplicate() {
        viewModelScope.launch {
            ioThread {
                val id = UUID.randomUUID()
                 loadedTransaction()
                    .copy(
                        id = id,
                        dateTime = timeProvider.utcNow(),
                    )
                    .toDomain(transactionMapper)
                    ?.let {
                        transactionRepo.save(it)
                    }

                tagRepository.findByIds(transactionAssociatedTags).forEach {
                    associateTagToTransaction(it, id)
                }

                refreshWidget(WalletBalanceWidgetReceiver::class.java)
            }

            closeScreen()
        }
    }

    private fun createCategory(data: CreateCategoryData) {
        viewModelScope.launch {
            categoryCreator.createCategory(data) {
                categories = sortCategories()

                // Select the newly created category
                onCategoryChanged(it)
            }
        }
    }

    private fun onCategoryChanged(newCategory: Category?) {
        if (transactionType != TransactionType.TRANSFER && newCategory?.transactionType != null && newCategory.transactionType?.name != transactionType.name) {
            android.widget.Toast.makeText(context, "Choose a category for ${transactionType.name.lowercase()}", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        loadedTransaction = loadedTransaction().copy(
            categoryId = newCategory?.id?.value
        )
        category = newCategory

        saveIfEditMode()

        updateTitleSuggestions()
    }

    private fun updateTitleSuggestions(title: String? = loadedTransaction().title) {
        titleSuggestionsJob?.cancel()
        titleSuggestionsJob = viewModelScope.launch {
            val type = transactionType
            val (suggestions, categoryNames) = ioThread {
                val suggestions = smartTitleSuggestionsLogic.suggest(
                    title = title,
                    categoryId = category?.id?.value,
                    accountId = account?.id,
                    type = type,
                )
                suggestions to categoryRepository.findAll().associate { it.id.value to it.name.value }
            }
            if (type == transactionType) {
                titleSuggestions = suggestions.map { it.title }.toPersistentSet()
                titleSuggestionCategories = suggestions.associate { it.title to (categoryNames[it.categoryId] ?: "No category") }
            }
        }
    }

    private fun editCategory(updatedCategory: Category) {
        viewModelScope.launch {
            categoryCreator.editCategory(updatedCategory) {
                categories = sortCategories()
                if (category?.id == it.id) {
                    category = it.takeIf { edited -> transactionType == TransactionType.TRANSFER || edited.transactionType == null || edited.transactionType?.name == transactionType.name }
                    loadedTransaction = loadedTransaction().copy(categoryId = category?.id?.value)
                }
                updateTitleSuggestions()
            }
        }
    }

    private fun createAccount(data: CreateAccountData) {
        viewModelScope.launch {
            val previousIds = accounts.map { it.id }.toSet()
            accountCreator.createAccount(data) {
                accounts = accountsAct(Unit)
                accounts.firstOrNull { it.id !in previousIds }?.let { newAccount ->
                    onAccountChanged(newAccount)
                }
            }
        }
    }

    private fun save(closeScreen: Boolean = true) {
        if (saving) return
        if (!validTransaction()) {
            return
        }

        saving = true
        viewModelScope.launch {
            try {
                if (!editMode && transactionType == TransactionType.EXPENSE &&
                    (paymentDraft.someoneElsePaid || paymentDraft.sharedWithOthers)) {
                    backgroundProcessingStarted = true
                    saveExpensePayment()
                } else saveInternal(closeScreen = closeScreen)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, e.message ?: "Unable to save payment", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                saving = false
                backgroundProcessingStarted = false
            }
        }
    }

    private suspend fun saveExpensePayment() {
        try {
            require(dueDate == null) { "Record who paid after the expense has been paid" }
            val selectedAccount = requireNotNull(account) { "Choose an account" }
            val selectedCategory = requireNotNull(category) { "Choose an expense category" }
            val billCurrency = selectedAccount.currency ?: baseUserCurrency
            require(selectedAccount.id != com.ivy.data.model.SharedBill.clearingAccount(billCurrency).id) { "Choose your payment account" }
            val draft = paymentDraft.equal(amount, billCurrency)
            val total = BigDecimal.valueOf(amount)
            if (capturedMessageId != null) {
                require(!draft.someoneElsePaid) { "This outgoing payment was paid by you" }
                require(capturedCurrency == null || billCurrency == capturedCurrency) { "Choose an account in $capturedCurrency" }
                require(capturedAmount?.compareTo(total) == 0) { "The total must match the detected payment" }
            }
            val own = if (draft.sharedWithOthers) BigDecimal(draft.ownShare) else total
            val shares = if (draft.sharedWithOthers) draft.people.map { com.ivy.data.model.SharedBill.Share(it.first, BigDecimal(it.second)) }
                else listOf(com.ivy.data.model.SharedBill.Share(draft.payer, total))
            val billTitle = title?.trim()?.takeIf { it.isNotBlank() } ?: selectedCategory.name.value
            val billTime = dateTime ?: timeProvider.utcNow()
            val plan = if (draft.sharedWithOthers) com.ivy.data.model.SharedBill.planSplit(
                draft.payer.takeIf { draft.someoneElsePaid }, total, own, shares,
                selectedAccount.id, billCurrency, selectedCategory.id.value, billTitle, billTime)
            else com.ivy.data.model.SharedBill.plan(false, total, own, shares,
                selectedAccount.id, billCurrency, selectedCategory.id.value, billTitle, billTime)
            val result = plan.copy(transactions = plan.transactions.map {
                if (it.type == TransactionType.EXPENSE) it.copy(description = description?.trim()?.takeIf { text -> text.isNotBlank() } ?: it.description) else it
            })
            val tripExpense = draft.groupId?.takeIf { draft.sharedWithOthers }?.let { groupId ->
                com.ivy.data.db.entity.TripExpenseEntity(groupId = groupId, title = billTitle, total = total.toDouble(),
                    ownShare = own.toDouble(), currency = billCurrency, payer = draft.payer.takeIf { draft.someoneElsePaid },
                    sharesJson = Json.encodeToString(draft.people),
                    loanIdsJson = Json.encodeToString(result.loans.map { it.id.toString() }),
                    timestamp = billTime.toEpochMilli())
            }
            ioThread { sharedBillRepository.save(billCurrency, result, capturedMessageId, transactionAssociatedTags.map { it.value }, tripExpense) }
            rememberOrdinaryPayment()
            paymentDraft = ExpensePaymentDraft()
            capturedMessageId?.let { context.getSystemService(android.app.NotificationManager::class.java)?.cancel("captured_payment", it.toInt()) }
            capturedMessageId = null
            refreshWidget(WalletBalanceWidgetReceiver::class.java)
            closeScreen()
        } catch (e: NumberFormatException) {
            android.widget.Toast.makeText(context, "Enter valid shares, for example 300.50", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Unable to save expense. ${e.message.orEmpty()}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun saveInternal(closeScreen: Boolean) {
        try {
            ioThread {
                val amount = amount.toBigDecimal()

                loadedTransaction = loadedTransaction().copy(
                    accountId = account?.id ?: error("no accountId"),
                    toAccountId = toAccount?.id,
                    toAmount = customExchangeRateState.convertedAmount?.toBigDecimal()
                        ?: amount,
                    title = title?.trim(),
                    description = description?.trim(),
                    amount = amount,
                    type = transactionType,
                    dueDate = dueDate,
                    paidFor = paidHistory,
                    dateTime = when {
                        loadedTransaction().dateTime == null &&
                                dueDate == null -> {
                            timeProvider.utcNow()
                        }

                        else -> loadedTransaction().dateTime
                    },
                    categoryId = category?.id?.value,
                    isSynced = false
                )

                if (loadedTransaction?.loanId != null) {
                    loanTransactionsLogic.updateAssociatedLoanData(
                        loadedTransaction!!.copy(),
                        onBackgroundProcessingStart = {
                            backgroundProcessingStarted = true
                        },
                        onBackgroundProcessingEnd = {
                            backgroundProcessingStarted = false
                        },
                        accountsChanged = accountsChanged
                    )

                    // Reset Counter
                    accountsChanged = false
                }

                val transaction = loadedTransaction().toDomain(transactionMapper)
                    ?: error("Invalid transaction")
                val messageId = capturedMessageId
                if (messageId != null) {
                    saveCapturedTransaction.save(transaction, messageId)
                    rememberOrdinaryPayment()
                    capturedMessageId = null
                    context.getSystemService(android.app.NotificationManager::class.java)
                        ?.cancel("captured_payment", messageId.toInt())
                } else {
                    transactionRepo.save(transaction)
                }

                refreshWidget(WalletBalanceWidgetReceiver::class.java)

                autoBackupManager.triggerInstantBackupIfEnabled()
            }

            paymentDraft = ExpensePaymentDraft()
            if (closeScreen) {
                closeScreen()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Unable to save transaction. ${e.message.orEmpty()}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun rememberOrdinaryPayment() {
        if (transactionType == TransactionType.TRANSFER) return
        paymentSuggestions.remember(detectedPayment,
            if (transactionType == TransactionType.EXPENSE) com.ivy.data.model.PaymentSuggestion.Kind.EXPENSE else com.ivy.data.model.PaymentSuggestion.Kind.INCOME,
            person = title?.takeIf { it.isNotBlank() },
            categoryId = category?.id?.value?.toString())
    }

    @JvmName("setHasChangesMethod")
    private fun setHasChanges(hasChangesValue: Boolean) {
        hasChanges = hasChangesValue
    }

    private suspend fun transferToAmount(
        amount: Double
    ): Double? {
        if (transactionType != TransactionType.TRANSFER) return null
        val toCurrency = toAccount?.currency ?: baseCurrency()
        val fromCurrency = account?.currency ?: baseCurrency()

        return exchangeRatesLogic.convertAmount(
            baseCurrency = baseCurrency(),
            amount = amount,
            fromCurrency = fromCurrency,
            toCurrency = toCurrency
        )
    }

    private suspend fun baseCurrency(): String = ioThread { settingsDao.findFirst().currency }

    private fun closeScreen() {
        if (nav.backStackEmpty()) {
            nav.resetBackStack()
            nav.navigateTo(MainScreen)
        } else {
            nav.back()
        }
    }

    // Comment for push
    @Suppress("ReturnCount")
    private fun validTransaction(): Boolean {
        pendingPaymentCurrency?.let {
            android.widget.Toast.makeText(context, "Choose a $it account under Pay with, or add one", android.widget.Toast.LENGTH_LONG).show()
            return false
        }
        if (hasChosenSameSourceAndDestinationAccountToTransfer()) {
            viewModelScope.launch {
                toaster.show(R.string.msg_source_account_destination_account_same_for_transfer)
            }
            return false
        }
        if (hasNotChosenAccountToTransfer()) {
            viewModelScope.launch {
                toaster.show(R.string.msg_select_account_to_transfer)
            }
            return false
        }

        if (amount == 0.0) {
            return false
        }

        return true
    }

    private fun hasNotChosenAccountToTransfer(): Boolean {
        return transactionType == TransactionType.TRANSFER && toAccount == null
    }

    private fun hasChosenSameSourceAndDestinationAccountToTransfer(): Boolean {
        return transactionType == TransactionType.TRANSFER && toAccount == account
    }

    private fun reset() {
        loadedTransaction = null

        initialTitle = null
        description = null
        dueDate = null
        category = null
        hasChanges = false
    }

    private fun loadedTransaction() = loadedTransaction ?: error("Loaded transaction is null")

    private fun updateExchangeRate(exRate: Double?) {
        viewModelScope.launch {
            updateCustomExchangeRateState(exchangeRate = exRate, resetRate = exRate == null)
        }
    }

    private suspend fun updateCustomExchangeRateState(
        toAccountValue: Account? = null,
        fromAccount: Account? = null,
        amt: Double? = null,
        exchangeRate: Double? = null,
        resetRate: Boolean = false
    ) {
        computationThread {
            val toAcc = toAccountValue ?: toAccount
            val fromAcc = fromAccount ?: account

            val toAccCurrencyCode = toAcc?.currency ?: baseUserCurrency
            val fromAccCurrencyCode = fromAcc?.currency ?: baseUserCurrency

            if (toAcc == null || fromAcc == null || (toAccCurrencyCode == fromAccCurrencyCode)) {
                customExchangeRateState = CustomExchangeRateState()
                return@computationThread
            }

            val exRate = exchangeRate
                ?: if (isCustomExchangeRateCurrencyCodeMatchingWithSourceAndDestinationAccountCurrencyCode(
                        toAccCurrencyCode = toAccCurrencyCode,
                        fromAccCurrencyCode = fromAccCurrencyCode
                    ) && !resetRate
                ) {
                    customExchangeRateState.exchangeRate
                } else {
                    exchangeRatesLogic.convertAmount(
                        baseCurrency = baseUserCurrency,
                        amount = 1.0,
                        fromCurrency = fromAccCurrencyCode,
                        toCurrency = toAccCurrencyCode
                    )
                }

            val amount = amt ?: amount

            val customTransferExchangeRateState = CustomExchangeRateState(
                showCard = true,
                toCurrencyCode = toAccCurrencyCode,
                fromCurrencyCode = fromAccCurrencyCode,
                exchangeRate = exRate,
                convertedAmount = exRate * amount
            )

            customExchangeRateState = customTransferExchangeRateState
            uiThread {
                saveIfEditMode()
            }
        }
    }

    private fun isCustomExchangeRateCurrencyCodeMatchingWithSourceAndDestinationAccountCurrencyCode(
        toAccCurrencyCode: String,
        fromAccCurrencyCode: String
    ): Boolean {
        return customExchangeRateState.showCard &&
                toAccCurrencyCode == customExchangeRateState.toCurrencyCode &&
                fromAccCurrencyCode == customExchangeRateState.fromCurrencyCode
    }

    private fun saveIfEditMode(closeScreen: Boolean = false) {
        if (editMode) {
            hasChanges = true

            save(closeScreen)
        }
    }

    private suspend fun getAllTags(): ImmutableList<Tag> =
        tagRepository.findAll().toImmutableList()

    private fun onTagSaved(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            NotBlankTrimmedString.from(name.toLowerCaseLocal())
                .onRight {
                    val tag = with(tagMapper) { createNewTag(name = it) }
                    tagRepository.save(tag)
                    this@EditTransactionViewModel.tags = getAllTags()
                }

            saveIfEditMode()
        }
    }

    private fun associateTagToTransaction(selectedTag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            val associatedId = AssociationId(loadedTransaction().id)
            tagRepository.associateTagToEntity(associatedId, selectedTag.id)
            transactionAssociatedTags =
                tagRepository.findByAssociatedId(associatedId).map(Tag::id).toImmutableList()
        }
    }

    private fun associateTagToTransaction(selectedTag: Tag, id: UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            val associatedId = AssociationId(id)
            tagRepository.associateTagToEntity(associatedId, selectedTag.id)
            transactionAssociatedTags =
                tagRepository.findByAssociatedId(associatedId).map(Tag::id).toImmutableList()
        }
    }

    private fun removeTagAssociation(selectedTag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            val associatedId = AssociationId(loadedTransaction().id)
            tagRepository.removeTagAssociation(associatedId, selectedTag.id)
            transactionAssociatedTags =
                tagRepository.findByAssociatedId(associatedId).map(Tag::id).toImmutableList()
        }
    }

    private fun searchTag(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            tagSearchJob?.cancelAndJoin()
            delay(tagSearchDebounceTimeInMills) // Debounce effect
            tagSearchJob = launch(Dispatchers.IO) {
                NotBlankTrimmedString.from(query.toLowerCaseLocal())
                    .onRight {
                        tags =
                            tagRepository.findByText(text = it.value).toImmutableList()
                    }
                    .onLeft {
                        tags = tagRepository.findAll().toImmutableList()
                    }
            }
        }
    }

    private fun deleteTag(selectedTag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            tagRepository.deleteById(selectedTag.id)
            tags = tagRepository.findAll().toImmutableList()
        }
    }

    private fun updateTagInformation(newTag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            tagRepository.save(newTag)
            tags = tagRepository.findAll().toImmutableList()
        }
    }

    private suspend fun sortCategories(): ImmutableList<Category> {
        val categories = categoryRepository.findAll().filter {
            transactionType == TransactionType.TRANSFER || it.transactionType == null || it.transactionType?.name == transactionType.name
        }
        return if (shouldSortCategoriesAscending()) {
            categories.sortedBy { it.name.value }.toImmutableList()
        } else {
            categories.toImmutableList()
        }
    }

    private suspend fun shouldSortCategoriesAscending(): Boolean {
        return features.sortCategoriesAscending.isEnabled(context)
    }
}
