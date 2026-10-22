package com.ivy.home.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivy.navigation.screenScopedViewModel
import com.ivy.data.db.entity.CapturedMessageEntity
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.ui.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InboxScreen(
    onBack: () -> Unit,
) {
    val viewModel: InboxViewModel = screenScopedViewModel()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val reviewLabels by viewModel.reviewLabels.collectAsState()
    androidx.activity.compose.BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(UI.colors.pure)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = UI.colors.pureInverse
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Transaction Inbox",
                style = UI.typo.h2.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = UI.colors.pureInverse
                ),
                modifier = Modifier.weight(1f)
            )
            if (messages.isNotEmpty()) {
                IconButton(onClick = { viewModel.dismissAll() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = "Clear All",
                        tint = UI.colors.pureInverse
                    )
                }
            }
        }

        CapturePermissions()

        if (messages.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_email),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = UI.colors.gray
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No messages yet",
                        style = UI.typo.b1.style(
                            color = UI.colors.gray,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Captured payment notifications and SMS\nwill appear here.",
                        style = UI.typo.b2.style(color = UI.colors.gray),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(messages, key = { it.id }) { message ->
                    InboxMessageCard(
                        reviewLabel = reviewLabels[message.id] ?: "Review payment",
                        message = message,
                        onDismiss = { viewModel.dismiss(message.id) },
                        onMarkRead = { viewModel.markRead(message.id) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun CapturePermissions() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        revision++
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val listenerEnabled = remember(revision) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val alertsEnabled = remember(revision) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val smsEnabled = remember(revision) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        if (!listenerEnabled) {
            PermissionLink("Enable payment notification access") {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        if (!alertsEnabled) {
            PermissionLink("Enable alerts to review detected payments") {
                if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissions.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
                } else {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }
        }
        if (!smsEnabled) {
            PermissionLink("Enable SMS payment capture") {
                permissions.launch(arrayOf(android.Manifest.permission.RECEIVE_SMS))
            }
        }
        if (!smsEnabled || !alertsEnabled) {
            PermissionLink("App permission settings") {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
            }
        }
    }
}

@Composable
private fun PermissionLink(label: String, onClick: () -> Unit) {
    Text(label, color = UI.colors.primary, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp))
}

@Composable
private fun InboxMessageCard(
    reviewLabel: String,
    message: CapturedMessageEntity,
    onDismiss: () -> Unit,
    onMarkRead: () -> Unit,
) {
    val nav = com.ivy.navigation.navigation()
    val time = remember(message.timestamp) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }
    val parsed = remember(message.id, message.title, message.body, message.sender) {
        com.ivy.data.model.PaymentParser.parse(message.title, message.body, message.sender)
    }
    var expanded by remember(message.id) { mutableStateOf(false) }
    val amountColor = if (parsed.isDebit) UI.colors.red else UI.colors.green
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(UI.shapes.r4).background(UI.colors.medium)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onMarkRead,
            ).padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(UI.shapes.circle)
                    .background(UI.colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_bell), contentDescription = null,
                    tint = UI.colors.primary, modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    parsed.merchant ?: message.sender,
                    style = UI.typo.b2.style(color = UI.colors.pureInverse, fontWeight = FontWeight.Bold),
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(time, style = UI.typo.c.style(color = UI.colors.gray))
            }
            if (!message.isRead) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(8.dp).clip(UI.shapes.circle).background(UI.colors.primary))
            }
        }
        parsed.amount?.let { amount ->
            Spacer(Modifier.height(16.dp))
            val direction = if (!parsed.directionKnown) "" else if (parsed.isDebit) "− " else "+ "
            Text(
                direction + parsed.currency + " " + "%,.2f".format(amount),
                style = UI.typo.h2.style(
                    color = if (parsed.directionKnown) amountColor else UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            message.body, style = UI.typo.c.style(color = UI.colors.gray),
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            if (expanded) "Show less" else "Show message",
            style = UI.typo.c.style(color = UI.colors.primary),
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        com.ivy.wallet.ui.theme.components.IvyButton(
            modifier = Modifier.fillMaxWidth(),
            text = reviewLabel,
            wrapContentMode = false,
            hasGlow = false,
            onClick = {
                onMarkRead()
                nav.navigateTo(
                    com.ivy.navigation.EditTransactionScreen(
                        initialTransactionId = null,
                        type = if (parsed.isDebit) com.ivy.base.model.TransactionType.EXPENSE else com.ivy.base.model.TransactionType.INCOME,
                        initialAmount = parsed.amount?.let { java.math.BigDecimal.valueOf(it) },
                        initialTitle = parsed.merchant ?: message.sender,
                        initialDescription = parsed.cleanDescription ?: message.body,
                        capturedMessageId = message.id,
                        initialCurrency = parsed.currency,
                    )
                )
            },
        )
        Spacer(Modifier.height(10.dp))
        com.ivy.wallet.ui.theme.components.IvyOutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            text = "Dismiss", iconStart = null, onClick = onDismiss,
        )
    }
}
