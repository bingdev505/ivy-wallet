package com.ivy.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.base.model.LoanRecordType
import com.ivy.base.model.TransactionType
import com.ivy.data.model.ExpenseSplit
import com.ivy.data.model.LoanPayment
import com.ivy.data.model.LoanType
import com.ivy.data.repository.LoanPaymentRepository
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.ui.SearchInput
import com.ivy.legacy.utils.selectEndTextFieldValue
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.modal.*
import java.time.Instant
import java.util.UUID

@Composable
internal fun BoxWithConstraintsScope.LoanPaymentForms(
    vm: EditTransactionViewModel,
    state: EditTransactionViewState,
    onSetDate: () -> Unit,
    onSetTime: () -> Unit,
    onCreateAccount: (com.ivy.wallet.domain.deprecated.logic.model.CreateAccountData) -> Unit,
    dismiss: () -> Unit,
) {
    val accounts = vm.loanFormAccounts()
    val currency = vm.loanFormCurrency()
    val account = state.account?.takeIf { selected -> accounts.any { it.id == selected.id } } ?: accounts.firstOrNull()
    val initial = remember {
        LoanModalData(loan = null, baseCurrency = currency, selectedAccount = account,
            autoFocusKeyboard = false, initialAmount = state.amount,
            initialName = vm.loanFormName(),
            initialType = if (state.transactionType == TransactionType.INCOME) LoanType.BORROW else LoanType.LEND,
            initialNote = state.description, requireTransaction = true)
    }
    val suggestedLoans = vm.paymentSuggestion?.loans.orEmpty()
    var selected by remember { mutableStateOf(vm.paymentLoans.singleOrNull { it.loan.id in suggestedLoans }) }
    var picker by remember { mutableStateOf(suggestedLoans.size > 1) }
    val pickerId = remember { UUID.randomUUID() }
    var search by remember { mutableStateOf(selectEndTextFieldValue("")) }
    val close = { if (!vm.savingPayment) dismiss() }
    val choices = vm.paymentLoans.filter { it.currency == currency }
    LoanModal(
        modal = initial.takeIf { selected == null && !picker }, dateTime = state.dateTime ?: Instant.now(),
        onSetDate = onSetDate, onSetTime = onSetTime, accounts = accounts, onCreateAccount = onCreateAccount,
        onCreateLoan = vm::submitNewLoan, onEditLoan = { _, _ -> }, dismiss = close, dismissOnSave = false,
        onChooseExistingLoan = { picker = true },
        NameSuggestions = { query, chooseName ->
            if (query.isNotBlank()) {
                ExpenseSplit.suggestions(query, vm.knownPeople, emptyList()).take(5).forEach { name ->
                    TextButton(modifier = Modifier.padding(horizontal = 24.dp), onClick = { chooseName(name) }) {
                        Text(name, style = UI.typo.b2.style(color = UI.colors.primary))
                    }
                }
                choices.filter { ExpenseSplit.key(it.loan.name) == ExpenseSplit.key(query) }.forEach { choice ->
                    TextButton(modifier = Modifier.padding(horizontal = 24.dp), onClick = { selected = choice }) {
                        Text("Use existing loan · ${choice.currency} ${"%.2f".format(choice.remaining)} remaining", style = UI.typo.b2.style(color = UI.colors.primary))
                    }
                }
            }
        },
    )
    val chosen = selected
    val recordModal = remember(chosen?.loan?.id) {
        chosen?.let {
            val outgoing = vm.loanPaymentDebit ?: (state.transactionType == TransactionType.EXPENSE)
            val repayment = (it.loan.type == LoanType.BORROW) == outgoing
            LoanRecordModalData(loanRecord = null, baseCurrency = currency, loanAccountCurrencyCode = it.currency,
                selectedAccount = account, initialAmount = state.amount, initialNote = state.description,
                initialRecordType = if (repayment) LoanRecordType.DECREASE else LoanRecordType.INCREASE,
                requireTransaction = true, title = "${it.loan.name} · Loan record")
        }
    }
    LoanRecordModal(modal = recordModal, dateTime = state.dateTime ?: Instant.now(), onSetDate = onSetDate, onSetTime = onSetTime,
        onCreate = { data -> chosen?.let { vm.submitLoanRecord(it, data) } }, onEdit = {}, onDelete = {},
        dismiss = close, accounts = accounts, onCreateAccount = onCreateAccount, dismissOnSave = false,
        onChooseLoan = { selected = null; picker = true })
    IvyModal(id = pickerId, visible = picker, dismiss = { picker = false }, PrimaryAction = { ModalSet { picker = false } }) {
        Spacer(Modifier.height(32.dp))
        ModalTitle("Choose existing loan")
        Spacer(Modifier.height(16.dp))
        IvyOutlinedButton(modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            text = "New loan", iconStart = R.drawable.ic_plus, onClick = { selected = null; picker = false })
        Spacer(Modifier.height(16.dp))
        SearchInput(searchQueryTextFieldValue = search, hint = "Search people", focus = false,
            showClearIcon = search.text.isNotEmpty(), onSetSearchQueryTextField = { search = it })
        Spacer(Modifier.height(16.dp))
        val matches = choices.filter { it.loan.name.contains(search.text.trim(), true) }
        if (matches.isEmpty()) Text("No existing loans in $currency.", modifier = Modifier.padding(horizontal = 24.dp), style = UI.typo.b2.style(color = UI.colors.gray))
        matches.forEach { choice ->
            IvyOutlinedButton(modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(), iconStart = R.drawable.ic_custom_loan_s,
                text = "${choice.loan.name} · ${if (choice.loan.type == LoanType.LEND) "Lent" else "Borrowed"} · ${choice.currency} ${"%.2f".format(choice.remaining)}") {
                selected = choice
                picker = false
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}
