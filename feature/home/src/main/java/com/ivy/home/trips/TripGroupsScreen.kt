package com.ivy.home.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.data.db.entity.TripGroupEntity
import com.ivy.data.model.ExpenseSplit
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.BackBottomBar
import com.ivy.wallet.ui.theme.components.CircleButtonFilled
import com.ivy.wallet.ui.theme.components.ItemIconSDefaultIcon
import com.ivy.wallet.ui.theme.findContrastTextColor
import com.ivy.legacy.ui.SearchInput
import com.ivy.legacy.utils.selectEndTextFieldValue
import com.ivy.ui.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxWithConstraintsScope.TripGroupsScreen() {
    val vm: TripGroupsViewModel = screenScopedViewModel()
    val groups by vm.groups.collectAsState(emptyList())
    val nav = navigation()
    var editor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TripGroupEntity?>(null) }
    var search by remember { mutableStateOf(selectEndTextFieldValue("")) }
    var alphabetical by remember { mutableStateOf(false) }
    val back: () -> Unit = { if (vm.selected != null) vm.open(null) else { nav.back(); Unit } }
    LaunchedEffect(Unit) { vm.refreshPeople(); vm.selected?.let { vm.open(it) } }
    BackHandler(onBack = back)
    val selected = vm.selected
    val matchingGroups = groups.filter { group ->
        group.name.contains(search.text, ignoreCase = true) || group.members().any { it.contains(search.text, ignoreCase = true) }
    }.let { if (alphabetical) it.sortedBy { group -> ExpenseSplit.key(group.name) } else it }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(UI.colors.pure).statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(top = 32.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selected?.name ?: "Trip groups", modifier = Modifier.weight(1f),
                    style = UI.typo.h2.style(color = UI.colors.pureInverse, fontWeight = FontWeight.ExtraBold),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (selected == null) CircleButtonFilled(icon = R.drawable.ic_sort_by_alpha_24,
                    onClick = { alphabetical = !alphabetical }, clickAreaPadding = 12.dp)
            }
        }
        if (selected == null) {
            item {
                SearchInput(searchQueryTextFieldValue = search, hint = "Search trip groups", focus = false,
                    showClearIcon = search.text.isNotEmpty(), onSetSearchQueryTextField = { search = it })
            }
            if (matchingGroups.isEmpty()) item {
                TripCard {
                    Text(if (groups.isEmpty()) "No trip groups yet" else "No matching groups",
                        style = UI.typo.b1.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold))
                    Text(if (groups.isEmpty()) "Create a group, add people, then choose it when splitting an expense." else "Try another group or person's name.",
                        style = UI.typo.b2.style(color = UI.colors.gray))
                }
            }
            items(matchingGroups, key = { it.id }) { group ->
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(UI.shapes.r4)
                    .border(2.dp, UI.colors.medium, UI.shapes.r4).clickable { vm.open(group) }) {
                    GroupHeader(group)
                    Text("You, ${group.members().joinToString()}", modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        style = UI.typo.b2.style(color = UI.colors.pureInverse), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(UI.shapes.r4).border(2.dp, UI.colors.medium, UI.shapes.r4)) {
                    GroupHeader(selected)
                    Text("You, ${selected.members().joinToString()}", modifier = Modifier.padding(20.dp),
                        style = UI.typo.b2.style(color = UI.colors.pureInverse))
                }
            }
            if (vm.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = UI.colors.primary) } }
            else {
                item { TripSectionTitle("Your balances") }
                val net = vm.balances.groupBy { ExpenseSplit.key(it.person) to it.currency }.map { (_, rows) -> rows.first() to rows.sumOf { it.amount } }.filter { kotlin.math.abs(it.second) > 0.000001 }
                if (net.isEmpty()) item { TripCard { Text("No outstanding balance", style = UI.typo.b2.style(color = UI.colors.gray)) } }
                items(net) { (person, amount) ->
                    TripCard {
                        Text(person.person, style = UI.typo.b1.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold))
                        Text("${if (amount > 0) "Owes you" else "You owe"} ${person.currency} ${"%.2f".format(kotlin.math.abs(amount))}",
                            style = UI.typo.b2.style(color = if (amount > 0) UI.colors.primary else UI.colors.red))
                    }
                }
                // Repayments open the original loan so its balance stays in sync.
                items(vm.balances) { balance ->
                    TripCard(onClick = { nav.navigateTo(com.ivy.navigation.LoanDetailsScreen(balance.loanId)) }) {
                        Text("${balance.person} · ${balance.currency} ${"%.2f".format(kotlin.math.abs(balance.amount))}",
                            style = UI.typo.b2.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold))
                        Text("Record repayment", style = UI.typo.c.style(color = UI.colors.primary))
                    }
                }
                item { TripSectionTitle("Expenses") }
                if (vm.expenses.isEmpty()) item { TripCard { Text("Choose this group in Add expense → Split expense.", style = UI.typo.b2.style(color = UI.colors.gray)) } }
                items(vm.expenses, key = { it.id }) { expense ->
                    TripCard {
                        Text(expense.title, style = UI.typo.b1.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold))
                        Text("${expense.currency} ${"%.2f".format(expense.total)}", style = UI.typo.b1.style(color = UI.colors.pureInverse))
                        Text("Your share: ${expense.currency} ${"%.2f".format(expense.ownShare)} · Paid by ${expense.payer ?: "you"}", style = UI.typo.b2.style(color = UI.colors.gray))
                        Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(expense.timestamp)), style = UI.typo.c.style(color = UI.colors.gray))
                    }
                }
            }
        }
        vm.error?.let { error -> item { Text(error, modifier = Modifier.padding(horizontal = 24.dp), style = UI.typo.b2.style(color = UI.colors.red)) } }
    }
    BackBottomBar(onBack = back) {
        IvyButton(text = if (selected == null) "Create group" else "Edit group", iconStart = if (selected == null) R.drawable.ic_plus else R.drawable.ic_edit,
            onClick = { editing = selected; editor = true })
    }
    if (editor) {
        var name by remember { mutableStateOf(editing?.name.orEmpty()) }
        var members by remember { mutableStateOf(editing?.members().orEmpty()) }
        var query by remember { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = { if (!vm.saving) editor = false }, containerColor = UI.colors.pure,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (editing == null) "Create trip group" else "Edit trip group", style = UI.typo.h2.style(color = UI.colors.pureInverse))
                GroupField("Group name", name) { name = it }
                Text("Your share is included automatically. Add the other people below.", color = UI.colors.gray)
                members.forEach { person -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(person, color = UI.colors.pureInverse)
                    TextButton(onClick = { members = members - person }) { Text("Remove", color = UI.colors.gray) }
                } }
                GroupField("Search or enter a name", query) { query = it }
                ExpenseSplit.suggestions(query, vm.people, members).forEach { person -> TextButton(onClick = { members = members + person; query = "" }) { Text(person, color = UI.colors.primary) } }
                if (query.isNotBlank() && (vm.people + members).none { ExpenseSplit.key(it) == ExpenseSplit.key(query) }) TextButton(onClick = { members = members + query.trim(); query = "" }) { Text("Add ${query.trim()}", color = UI.colors.primary) }
                vm.error?.let { Text(it, color = UI.colors.red) }
                if (!vm.saving && name.isNotBlank() && members.isNotEmpty()) IvyButton(modifier = Modifier.fillMaxWidth(), wrapContentMode = false, text = "Save group", hasGlow = false, onClick = { vm.save(editing?.id, name, members) { editor = false } })
                if (vm.saving) CircularProgressIndicator(color = UI.colors.primary)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GroupHeader(group: TripGroupEntity) {
    val textColor = findContrastTextColor(UI.colors.primary)
    Row(Modifier.fillMaxWidth().background(UI.colors.primary).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemIconSDefaultIcon(iconName = null, defaultIcon = R.drawable.ic_vue_people_people, tint = textColor)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(group.name, style = UI.typo.b1.style(color = textColor, fontWeight = FontWeight.ExtraBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${group.members().size + 1} people", style = UI.typo.b2.style(color = textColor))
        }
    }
}

@Composable
private fun TripSectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(horizontal = 24.dp), style = UI.typo.b1.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold))
}

@Composable
private fun TripCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(UI.shapes.r4).border(2.dp, UI.colors.medium, UI.shapes.r4)
    Column((if (onClick != null) modifier.clickable(onClick = onClick) else modifier).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun GroupField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = UI.colors.pureInverse, unfocusedTextColor = UI.colors.pureInverse,
            focusedBorderColor = UI.colors.primary, focusedLabelColor = UI.colors.primary, unfocusedLabelColor = UI.colors.gray))
}
