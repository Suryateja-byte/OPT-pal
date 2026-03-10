package com.sidekick.opt_pal.feature.reporting

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ReportingSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private fun Long.toDateDisplay(): String {
    val formatter = SimpleDateFormat("MMM d", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(this))
}

private fun daysUntil(timestamp: Long): Long {
    val diff = timestamp - System.currentTimeMillis()
    return TimeUnit.MILLISECONDS.toDays(diff)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportingRoute(
    onNavigateBack: () -> Unit,
    onAddManualTask: () -> Unit,
    onEditManualTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportingViewModel = viewModel(factory = ReportingViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AnalyticsLogger.logScreenView("Reporting") }
    ReportingScreen(
        state = uiState,
        onToggleComplete = viewModel::toggleCompletion,
        onDelete = viewModel::deleteObligation,
        onNavigateBack = onNavigateBack,
        onAddManualTask = onAddManualTask,
        onEditManualTask = onEditManualTask,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportingScreen(
    state: ReportingUiState,
    onToggleComplete: (ReportingObligation) -> Unit,
    onDelete: (ReportingObligation) -> Unit,
    onNavigateBack: () -> Unit,
    onAddManualTask: () -> Unit,
    onEditManualTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Minimal Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddManualTask,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Task")
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
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 40 }
                    ) {
                        Column {
                            Text(
                                text = "Reporting",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Hub.",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(600, 100)) + slideInVertically(tween(600, 100)) { 40 }
                    ) {
                        Text(
                            text = "PENDING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp
                        )
                    }
                }

                if (state.pendingObligations.isEmpty()) {
                    item {
                        MinimalEmptyState("All caught up.")
                    }
                } else {
                    items(state.pendingObligations, key = { it.id }) { obligation ->
                        MinimalTaskItem(
                            obligation = obligation,
                            onToggleComplete = { onToggleComplete(obligation) },
                            onDelete = { onDelete(obligation) },
                            onEdit = onEditHandler(obligation, onEditManualTask)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "HISTORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 2.sp
                    )
                }

                if (state.completedObligations.isEmpty()) {
                    item {
                        MinimalEmptyState("No history yet.")
                    }
                } else {
                    items(state.completedObligations, key = { it.id }) { obligation ->
                        MinimalTaskItem(
                            obligation = obligation,
                            onToggleComplete = { onToggleComplete(obligation) },
                            onDelete = { onDelete(obligation) },
                            onEdit = onEditHandler(obligation, onEditManualTask),
                            isHistory = true
                        )
                    }
                }
                
                item {
                     Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
                }
            }
        }
    }
}

@Composable
private fun MinimalTaskItem(
    obligation: ReportingObligation,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)?,
    isHistory: Boolean = false
) {
    val daysRemaining = daysUntil(obligation.dueDate)
    val isOverdue = daysRemaining < 0 && !obligation.isCompleted

    val textColor = if (isHistory) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Custom Minimal Checkbox area
        Checkbox(
            checked = obligation.isCompleted,
            onCheckedChange = { onToggleComplete() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = obligation.description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (obligation.isCompleted) TextDecoration.LineThrough else null
                ),
                color = textColor
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Date
                Text(
                    text = obligation.dueDate.toDateDisplay(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Status Pill (Text only)
                if (!obligation.isCompleted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOverdue) "Overdue" else "$daysRemaining days left",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Minimal Actions
        Row {
            if (onEdit != null) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), // Very subtle delete
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MinimalEmptyState(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

private fun onEditHandler(
    obligation: ReportingObligation,
    onEditManualTask: (String) -> Unit
): (() -> Unit)? {
    return if (obligation.createdBy.equals(ReportingSource.MANUAL.name, ignoreCase = true) && obligation.id.isNotBlank()) {
        { onEditManualTask(obligation.id) }
    } else {
        null
    }
}


