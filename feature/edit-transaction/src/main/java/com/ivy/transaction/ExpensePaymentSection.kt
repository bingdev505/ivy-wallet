package com.ivy.transaction

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.data.model.ExpenseSplit
import com.ivy.data.db.entity.TripGroupEntity
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.components.IvyBorderButton
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import java.math.BigDecimal

data class ExpensePaymentDraft(
    val someoneElsePaid: Boolean = false,
    val sharedWithOthers: Boolean = false,
    val payer: String = "",
    val ownShare: String = "",
    val people: List<Pair<String, String>> = emptyList(),
    val customSplit: Boolean = false,
    val groupId: String? = null,
) {
    fun equal(total: Double, currency: String): ExpensePaymentDraft {
        if (customSplit || total <= 0 || !total.isFinite()) return this
        val amounts = ExpenseSplit.equal(BigDecimal.valueOf(total), people.size + 1, currency)
        return copy(ownShare = amounts.first().toPlainString(), people = people.mapIndexed { i, p -> p.first to amounts[i + 1].toPlainString() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpensePaymentSection(draft: ExpensePaymentDraft, currency: String, total: Double,
    knownPeople: List<String>, groups: List<TripGroupEntity>, onChange: (ExpensePaymentDraft) -> Unit,
) {
    var payerOpen by remember { mutableStateOf(false) }
    var splitOpen by remember { mutableStateOf(false) }
    LaunchedEffect(total, currency) {
        if (draft.sharedWithOthers && !draft.customSplit) onChange(draft.equal(total, currency))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AttributeButton(if (draft.someoneElsePaid) "Paid by ${draft.payer}" else "Someone else paid", draft.someoneElsePaid) { payerOpen = true }
        val group = groups.firstOrNull { it.id == draft.groupId }
        AttributeButton(if (draft.sharedWithOthers) group?.name ?: "Split with ${draft.people.size} people" else "Split expense", draft.sharedWithOthers) { splitOpen = true }
    }
    if (payerOpen) {
        NameSheet("Who paid?", knownPeople, emptyList(), onDismiss = { payerOpen = false },
            onSelect = { name -> onChange(draft.copy(someoneElsePaid = true, payer = name)); payerOpen = false },
            remove = if (draft.someoneElsePaid) ({ onChange(draft.copy(someoneElsePaid = false, payer = "")); payerOpen = false }) else null)
    }
    if (splitOpen) {
        var split by remember { mutableStateOf(draft.equal(total, currency)) }
        LaunchedEffect(total, currency) { split = split.equal(total, currency) }
        var addPerson by remember { mutableStateOf(false) }
        var groupSearch by remember { mutableStateOf("") }
        var chooseGroup by remember { mutableStateOf(false) }
        if (addPerson) {
            NameSheet("Add person", knownPeople, split.people.map { it.first }, onDismiss = { addPerson = false }, onSelect = { name ->
                split = split.copy(people = split.people + (name to "")).equal(total, currency); addPerson = false
            })
        } else ModalBottomSheet(onDismissRequest = { splitOpen = false }, containerColor = UI.colors.pure,
            dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 32.dp, height = 4.dp).clip(UI.shapes.rFull).background(UI.colors.gray)) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Split expense", style = UI.typo.h2.style(color = UI.colors.pureInverse))
                Text("Bill total: $currency ${BigDecimal.valueOf(total).stripTrailingZeros().toPlainString()}", style = UI.typo.c.style(color = UI.colors.gray))
                if (groups.isNotEmpty()) {
                    IvyOutlinedButton(text = groups.firstOrNull { it.id == split.groupId }?.name ?: "Choose trip group", iconStart = null, onClick = { chooseGroup = !chooseGroup })
                    if (chooseGroup) {
                        PaymentField("Search groups", groupSearch) { groupSearch = it }
                        groups.filter { it.name.contains(groupSearch.trim(), true) }.forEach { group ->
                            TextButton(onClick = {
                                split = split.copy(groupId = group.id, people = group.members().map { it to "" }, customSplit = false).equal(total, currency)
                                chooseGroup = false
                            }) { Text(group.name, color = UI.colors.primary) }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your share", style = UI.typo.b2.style(color = UI.colors.pureInverse))
                    PaymentField("Amount ($currency)", split.ownShare, true) {
                        if (it != split.ownShare) split = split.copy(ownShare = it, customSplit = true)
                    }
                }
                split.people.forEachIndexed { index, person ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(person.first, style = UI.typo.b2.style(color = UI.colors.pureInverse))
                        PaymentField("Amount ($currency)", person.second, true) { amount ->
                            if (amount != person.second) split = split.copy(customSplit = true, people = split.people.mapIndexed { i, p -> if (i == index) p.first to amount else p })
                        }
                        TextButton(modifier = Modifier.align(Alignment.End).height(40.dp), contentPadding = PaddingValues(horizontal = 8.dp), onClick = {
                            split = split.copy(people = split.people.filterIndexed { i, _ -> i != index }).equal(total, currency)
                        }) { Text("Remove", color = UI.colors.gray) }
                    }
                }
                IvyOutlinedButton(text = "Add person", iconStart = R.drawable.ic_plus, onClick = { addPerson = true })
                val values = listOf(split.ownShare) + split.people.map { it.second }
                val validAmounts = values.all { it.toBigDecimalOrNull()?.let { value -> value >= BigDecimal.ZERO } == true }
                val remaining = BigDecimal.valueOf(total) - values.fold(BigDecimal.ZERO) { sum, text -> sum + (text.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
                val payerValid = !split.someoneElsePaid || split.people.any { ExpenseSplit.key(it.first) == ExpenseSplit.key(split.payer) }
                val valid = total > 0 && split.people.isNotEmpty() && validAmounts && remaining.compareTo(BigDecimal.ZERO) == 0 && payerValid
                val error = !payerValid || (split.people.isNotEmpty() && (!validAmounts || remaining.compareTo(BigDecimal.ZERO) != 0))
                Text(when {
                    split.people.isEmpty() -> "Add a person to split this expense."
                    !payerValid -> "Add ${split.payer} to this split, or change who paid."
                    !validAmounts -> "Enter valid share amounts."
                    remaining.signum() >= 0 -> "Remaining: $currency ${remaining.toPlainString()}"
                    else -> "Over total: $currency ${remaining.abs().toPlainString()}"
                }, color = if (error && split.people.isNotEmpty()) UI.colors.red else UI.colors.gray)
                IvyButton(modifier = Modifier.fillMaxWidth(), text = "Done", wrapContentMode = false, enabled = valid, hasGlow = false, onClick = { onChange(split.copy(sharedWithOthers = true)); splitOpen = false })
                if (draft.sharedWithOthers) TextButton(onClick = { onChange(draft.copy(sharedWithOthers = false, groupId = null)); splitOpen = false }) { Text("Remove split", color = UI.colors.red) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameSheet(title: String, people: List<String>, excluded: List<String>, onDismiss: () -> Unit,
    onSelect: (String) -> Unit, remove: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val matches = ExpenseSplit.suggestions(query, people, excluded)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = UI.colors.pure, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = UI.typo.h2.style(color = UI.colors.pureInverse))
            PaymentField("Search or enter a name", query) { query = it }
            matches.forEach { name -> IvyOutlinedButton(text = name, iconStart = null, onClick = { onSelect(name) }) }
            val name = query.trim()
            if (name.isNotBlank() && (people + excluded).none { ExpenseSplit.key(it) == ExpenseSplit.key(name) }) {
                IvyButton(text = "Add $name", hasGlow = false, onClick = { onSelect(name) })
            }
            if (remove != null) TextButton(onClick = remove) { Text("Remove payer", color = UI.colors.red) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AttributeButton(label: String, selected: Boolean, click: () -> Unit) {
    if (selected) IvyButton(text = label, iconEnd = R.drawable.ic_onboarding_next_arrow, wrapContentMode = true, hasGlow = false, onClick = click)
    else IvyBorderButton(text = label, iconStart = R.drawable.ic_plus, iconTint = UI.colors.pureInverse, onClick = click)
}

@Composable
private fun PaymentField(label: String, value: String, amount: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = if (amount) KeyboardType.Decimal else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = UI.colors.pureInverse, unfocusedTextColor = UI.colors.pureInverse,
            focusedBorderColor = UI.colors.primary, unfocusedBorderColor = UI.colors.gray, focusedLabelColor = UI.colors.primary, unfocusedLabelColor = UI.colors.gray))
}
