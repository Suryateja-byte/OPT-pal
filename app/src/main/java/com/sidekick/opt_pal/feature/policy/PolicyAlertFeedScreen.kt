package com.sidekick.opt_pal.feature.policy

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertConfidence
import com.sidekick.opt_pal.data.model.PolicyAlertFinality
import com.sidekick.opt_pal.data.model.PolicyAlertSeverity
import com.sidekick.opt_pal.navigation.AppScreen
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun PolicyAlertFeedRoute(
    selectedAlertId: String?,
    onNavigateBack: () -> Unit,
    onOpenTravelAdvisor: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PolicyAlertFeedViewModel = viewModel(factory = PolicyAlertFeedViewModel.provideFactory(selectedAlertId))
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val systemNotificationsEnabled = context.arePolicyAlertNotificationsAvailable()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.enablePolicyNotifications()
        }
    }

    LaunchedEffect(Unit) {
        AnalyticsLogger.logPolicyAlertFeedOpened()
    }

    PolicyAlertFeedScreen(
        state = state,
        systemNotificationsEnabled = systemNotificationsEnabled,
        onNavigateBack = onNavigateBack,
        onSelectFilter = viewModel::selectFilter,
        onSelectAlert = viewModel::selectAlert,
        onEnableNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.enablePolicyNotifications()
            }
        },
        onOpenSource = { alert ->
            AnalyticsLogger.logPolicyAlertSourceOpened(alert.id)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(alert.source.url)))
        },
        onPrimaryAction = { alert ->
            if (alert.callToActionRoute == AppScreen.TravelAdvisor.route) {
                onOpenTravelAdvisor()
            } else {
                AnalyticsLogger.logPolicyAlertSourceOpened(alert.id)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(alert.source.url)))
            }
        },
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier
    )
}

@Composable
fun PolicyAlertFeedScreen(
    state: PolicyAlertFeedUiState,
    systemNotificationsEnabled: Boolean,
    onNavigateBack: () -> Unit,
    onSelectFilter: (PolicyAlertFilter) -> Unit,
    onSelectAlert: (String) -> Unit,
    onEnableNotifications: () -> Unit,
    onOpenSource: (PolicyAlertCard) -> Unit,
    onPrimaryAction: (PolicyAlertCard) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Policy Alerts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!state.availability.isEnabled) {
                    item {
                        PolicyMessageCard(text = state.availability.message)
                    }
                }

                if (!state.infoMessage.isNullOrBlank()) {
                    item {
                        PolicyMessageCard(text = state.infoMessage.orEmpty(), onDismiss = onDismissMessage)
                    }
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    item {
                        PolicyMessageCard(
                            text = state.errorMessage.orEmpty(),
                            isError = true,
                            onDismiss = onDismissMessage
                        )
                    }
                }

                if (!systemNotificationsEnabled || !state.policyNotificationsEnabled) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.POLICY_ALERT_NOTIFICATIONS_BUTTON),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enable policy-alert notifications",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Get notified when a reviewed OPT policy alert is published.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = onEnableNotifications) {
                                    Icon(Icons.Filled.Notifications, contentDescription = "Enable policy alerts")
                                }
                            }
                        }
                    }
                }

                item {
                    FilterRow(
                        selected = state.selectedFilter,
                        onSelectFilter = onSelectFilter
                    )
                }

                state.selectedAlert?.let { selectedAlert ->
                    item {
                        PolicyAlertDetailCard(
                            alert = selectedAlert,
                            modifier = Modifier.testTag(UiTestTags.POLICY_ALERT_DETAIL),
                            onOpenSource = { onOpenSource(selectedAlert) },
                            onPrimaryAction = { onPrimaryAction(selectedAlert) }
                        )
                    }
                }

                item {
                    Text(
                        text = "FEED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Column(
                        modifier = Modifier.testTag(UiTestTags.POLICY_ALERT_FEED_LIST),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.visibleAlerts.forEach { alert ->
                            PolicyAlertRow(
                                alert = alert,
                                isSelected = alert.id == state.selectedAlert?.id,
                                isUnread = state.alertStates.none { it.alertId == alert.id && it.openedAt != null },
                                onClick = { onSelectAlert(alert.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    selected: PolicyAlertFilter,
    onSelectFilter: (PolicyAlertFilter) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.POLICY_ALERT_FILTER_ROW),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PolicyAlertFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelectFilter(filter) },
                label = { Text(filter.toLabel()) }
            )
        }
    }
}

@Composable
private fun PolicyAlertDetailCard(
    alert: PolicyAlertCard,
    modifier: Modifier = Modifier,
    onOpenSource: () -> Unit,
    onPrimaryAction: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = alert.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            AlertBadgeRow(alert)
            Text(alert.whatChanged, style = MaterialTheme.typography.bodyLarge)
            DetailLine("Who's affected", alert.whoIsAffected)
            DetailLine("Why it matters", alert.whyItMatters)
            DetailLine("Recommended action", alert.recommendedAction)
            DetailLine("Source", alert.source.label)
            DetailLine("Effective", alert.effectiveDateText ?: formatUtcDate(alert.effectiveDate))
            DetailLine("Reviewed", formatUtcDate(alert.lastReviewedAt))
            if (alert.isArchived || alert.isSuperseded) {
                Text(
                    text = if (alert.isSuperseded) "This alert has been superseded." else "This alert has been archived.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPrimaryAction) {
                    Text(alert.callToActionLabel ?: "Open source")
                }
                TextButton(onClick = onOpenSource) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Source")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertBadgeRow(alert: PolicyAlertCard) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, label = { Text(alert.parsedSeverity.name.replace('_', ' ')) })
        AssistChip(onClick = {}, label = { Text(alert.parsedConfidence.name) })
        AssistChip(onClick = {}, label = { Text(alert.parsedFinality.name.replace('_', ' ')) })
    }
}

@Composable
private fun PolicyAlertRow(
    alert: PolicyAlertCard,
    isSelected: Boolean,
    isUnread: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = alert.whatChanged,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${alert.source.label} - ${formatUtcDate(alert.source.publishedAt ?: alert.publishedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                )
            }
        }
    }
}

@Composable
private fun PolicyMessageCard(
    text: String,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
            )
            onDismiss?.let {
                TextButton(onClick = it) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun PolicyAlertFilter.toLabel(): String {
    return when (this) {
        PolicyAlertFilter.ALL -> "All"
        PolicyAlertFilter.CRITICAL -> "Critical"
        PolicyAlertFilter.TRAVEL -> "Travel"
        PolicyAlertFilter.EMPLOYMENT -> "Employment"
        PolicyAlertFilter.REPORTING -> "Reporting"
        PolicyAlertFilter.APPLICATIONS -> "Applications"
    }
}

private fun formatUtcDate(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "Not provided"
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(timestamp))
}

private fun android.content.Context.arePolicyAlertNotificationsAvailable(): Boolean {
    return NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
}
