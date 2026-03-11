package com.sidekick.opt_pal.feature.casestatus

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.sidekick.opt_pal.data.model.UscisCaseClassification
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UscisCaseStage
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun UscisCaseStatusRoute(
    selectedCaseId: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UscisCaseStatusViewModel = viewModel(factory = UscisCaseStatusViewModel.provideFactory(selectedCaseId))
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val notificationsEnabled = context.areCaseStatusNotificationsEnabled()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.syncMessagingEndpoint()
        }
    }

    LaunchedEffect(Unit) {
        AnalyticsLogger.logScreenView("UscisCaseStatus")
    }

    LaunchedEffect(state.cases.size, state.availability.isEnabled, notificationsEnabled) {
        if (state.availability.isEnabled && state.cases.isNotEmpty() && notificationsEnabled) {
            viewModel.syncMessagingEndpoint()
        }
    }

    UscisCaseStatusScreen(
        state = state,
        notificationsEnabled = notificationsEnabled,
        onNavigateBack = onNavigateBack,
        onReceiptNumberChanged = viewModel::onReceiptNumberChanged,
        onAddCase = viewModel::addCase,
        onSelectCase = viewModel::selectCase,
        onRefreshCase = viewModel::refreshCase,
        onRefreshSelectedCase = viewModel::refreshSelectedCase,
        onArchiveSelectedCase = viewModel::archiveSelectedCase,
        onRemoveSelectedCase = viewModel::removeSelectedCase,
        onDismissMessage = viewModel::dismissMessage,
        onEnableNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.syncMessagingEndpoint()
            }
        },
        modifier = modifier
    )
}

@Composable
fun UscisCaseStatusScreen(
    state: UscisCaseStatusUiState,
    notificationsEnabled: Boolean,
    onNavigateBack: () -> Unit,
    onReceiptNumberChanged: (String) -> Unit,
    onAddCase: () -> Unit,
    onSelectCase: (String) -> Unit,
    onRefreshCase: (String) -> Unit,
    onRefreshSelectedCase: () -> Unit,
    onArchiveSelectedCase: () -> Unit,
    onRemoveSelectedCase: () -> Unit,
    onDismissMessage: () -> Unit,
    onEnableNotifications: () -> Unit,
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
                    text = "USCIS Case Status",
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
                item {
                    AddCaseSection(
                        state = state,
                        isTrackerEnabled = state.availability.isEnabled,
                        onReceiptNumberChanged = onReceiptNumberChanged,
                        onAddCase = onAddCase
                    )
                }

                if (!state.availability.isEnabled) {
                    item {
                        MessageCard(
                            text = state.availability.reason.ifBlank {
                                "Case tracking is not enabled for this environment yet."
                            }
                        )
                    }
                }

                if (!state.infoMessage.isNullOrBlank()) {
                    item {
                        MessageCard(text = state.infoMessage.orEmpty(), onDismiss = onDismissMessage)
                    }
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    item {
                        MessageCard(
                            text = state.errorMessage.orEmpty(),
                            isError = true,
                            modifier = Modifier.testTag(UiTestTags.CASE_STATUS_ERROR),
                            onDismiss = onDismissMessage
                        )
                    }
                }

                if (state.cases.isNotEmpty() && !notificationsEnabled) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.CASE_STATUS_NOTIFICATIONS_BUTTON),
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
                                        text = "Enable case update notifications",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Get alerted when a tracked USCIS case changes.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = onEnableNotifications) {
                                    Icon(Icons.Filled.Notifications, contentDescription = "Enable notifications")
                                }
                            }
                        }
                    }
                }

                if (state.cases.isNotEmpty()) {
                    item {
                        Text(
                            text = "TRACKED CASES",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier.testTag(UiTestTags.CASE_STATUS_LIST),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            state.cases.forEach { tracker ->
                                CaseRow(
                                    tracker = tracker,
                                    isSelected = tracker.id == state.selectedCase?.id,
                                    onSelect = { onSelectCase(tracker.id) },
                                    onRefresh = { onRefreshCase(tracker.id) },
                                    isRefreshing = state.refreshingCaseId == tracker.id
                                )
                            }
                        }
                    }
                    item {
                        state.selectedCase?.let { tracker ->
                            SelectedCaseDetail(
                                tracker = tracker,
                                onRefresh = onRefreshSelectedCase,
                                onArchive = onArchiveSelectedCase,
                                onRemove = onRemoveSelectedCase,
                                isRefreshing = state.refreshingCaseId == tracker.id
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCaseSection(
    state: UscisCaseStatusUiState,
    isTrackerEnabled: Boolean,
    onReceiptNumberChanged: (String) -> Unit,
    onAddCase: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Track a USCIS receipt number",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = buildString {
                    append("Add one USCIS receipt number at a time. Format: ABC1234567890")
                    if (state.availability.supportedForms.isNotEmpty()) {
                        append(" • Supported forms: ${state.availability.supportedForms.joinToString()}")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.receiptNumberInput,
                onValueChange = onReceiptNumberChanged,
                enabled = isTrackerEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.CASE_STATUS_RECEIPT_FIELD),
                label = { Text("Receipt number") },
                singleLine = true
            )
            Button(
                onClick = onAddCase,
                enabled = isTrackerEnabled && !state.isSubmitting,
                modifier = Modifier.testTag(UiTestTags.CASE_STATUS_ADD_BUTTON)
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Track case")
                }
            }
        }
    }
}

@Composable
private fun CaseRow(
    tracker: UscisCaseTracker,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
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
                .clickable(onClick = onSelect)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tracker.receiptNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = tracker.officialStatusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = tracker.parsedStage.toDisplayName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh case")
                }
            }
        }
    }
}

@Composable
private fun SelectedCaseDetail(
    tracker: UscisCaseTracker,
    onRefresh: () -> Unit,
    onArchive: () -> Unit,
    onRemove: () -> Unit,
    isRefreshing: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.CASE_STATUS_DETAIL),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tracker.officialStatusText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = tracker.plainEnglishSummary.ifBlank { tracker.officialStatusDescription },
                style = MaterialTheme.typography.bodyLarge
            )
            if (tracker.parsedClassification == UscisCaseClassification.CONSULT_DSO_ATTORNEY) {
                MessageCard(
                    text = "This status may need extra review. Contact your DSO or an immigration attorney before acting.",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            DetailLine("Stage", tracker.parsedStage.toDisplayName())
            DetailLine("Form", tracker.formType.ifBlank { "Unknown" })
            DetailLine("Recommended action", tracker.recommendedAction)
            DetailLine("Official source", TRACKER_SOURCE_LABEL)
            DetailLine("Last checked", formatUtcDateTime(tracker.lastCheckedAt))
            if (tracker.lastError.isNotBlank()) {
                DetailLine("Last error", tracker.lastError)
            }
            if (tracker.watchFor.isNotEmpty()) {
                DetailLine("Watch for", tracker.watchFor.joinToString(", "))
            }
            Text(
                text = "History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (tracker.officialHistory.isEmpty()) {
                Text(
                    text = "No USCIS history details were returned yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tracker.officialHistory.take(5).forEach { history ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.3f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(history.statusText, fontWeight = FontWeight.Medium)
                                if (history.statusDescription.isNotBlank()) {
                                    Text(
                                        history.statusDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                history.statusDate?.let {
                                    Text(
                                        formatUtcDate(it),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.testTag(UiTestTags.CASE_STATUS_REFRESH_BUTTON)
                ) {
                    Text("Refresh now")
                }
                TextButton(
                    onClick = onArchive,
                    modifier = Modifier.testTag(UiTestTags.CASE_STATUS_ARCHIVE_BUTTON)
                ) {
                    Text("Archive")
                }
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag(UiTestTags.CASE_STATUS_REMOVE_BUTTON)
                ) {
                    Text("Remove")
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
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MessageCard(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
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
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}

private fun UscisCaseStage.toDisplayName(): String {
    return name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun formatUtcDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(timestamp))
}

private fun formatUtcDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Not checked yet"
    val formatter = SimpleDateFormat("MMM d, yyyy h:mm a 'UTC'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(timestamp))
}

private fun android.content.Context.areCaseStatusNotificationsEnabled(): Boolean {
    return NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
}

private const val TRACKER_SOURCE_LABEL = "USCIS Case Status Online"
