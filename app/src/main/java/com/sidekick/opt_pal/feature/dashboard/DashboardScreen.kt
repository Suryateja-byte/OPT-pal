package com.sidekick.opt_pal.feature.dashboard

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun DashboardRoute(
    onAddEmployment: () -> Unit,
    onEditEmployment: (String) -> Unit,
    onOpenReporting: () -> Unit,
    onOpenVault: () -> Unit,
    onSendFeedback: () -> Unit,
    onOpenLegal: () -> Unit,
    onScanDocument: () -> Unit,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AnalyticsLogger.logScreenView("Dashboard") }

    DashboardScreen(
        state = uiState,
        onAddEmployment = onAddEmployment,
        onEditEmployment = onEditEmployment,
        onOpenReporting = onOpenReporting,
        onOpenVault = onOpenVault,
        onSendFeedback = onSendFeedback,
        onOpenLegal = onOpenLegal,
        onScanDocument = onScanDocument,
        onOpenChat = onOpenChat,
        onDeleteEmployment = viewModel::deleteEmployment,
        onSignOut = viewModel::onSignOut,
        onReprocessDocuments = viewModel::reprocessDocuments,
        modifier = modifier
    )
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun DashboardScreen(
    state: DashboardUiState,
    onAddEmployment: () -> Unit,
    onEditEmployment: (String) -> Unit,
    onOpenReporting: () -> Unit,
    onOpenVault: () -> Unit,
    onSendFeedback: () -> Unit,
    onOpenLegal: () -> Unit,
    onScanDocument: () -> Unit,
    onOpenChat: () -> Unit,
    onDeleteEmployment: (String) -> Unit,
    onSignOut: () -> Unit,
    onReprocessDocuments: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // Minimalist FAB: A simple Squircle, no heavy shadows
            FloatingActionButton(
                onClick = onAddEmployment,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium, // Soft square
                modifier = Modifier.testTag(UiTestTags.DASHBOARD_FAB)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Employment")
            }
        }
    ) { paddingValues ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp)
            ) {
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(700)) + slideInVertically(tween(700)) { 50 }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            HeaderSection(state)
                            Spacer(modifier = Modifier.height(60.dp))
                            MinimalHero(state)
                            Spacer(modifier = Modifier.height(60.dp))
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(700, 200)) + slideInVertically(tween(700, 200)) { 50 }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            ActionGrid(
                                state = state,
                                onOpenReporting = onOpenReporting,
                                onOpenVault = onOpenVault,
                                onScanDocument = onScanDocument,
                                onOpenChat = onOpenChat
                            )
                            Spacer(modifier = Modifier.height(60.dp))
                            
                            // Minimal Section Header
                            Text(
                                text = "TIMELINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                            
                            EmploymentList(state, onEditEmployment)
                            
                            Spacer(modifier = Modifier.height(60.dp))
                            
                            Text(
                                text = "SYSTEM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            MinimalSupportSection(onSendFeedback, onOpenLegal, onSignOut, onReprocessDocuments)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(state: DashboardUiState) {
    Column {
        // The tag is now just text, no box
        Text(
            text = state.optLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Hello,\n${state.displayName}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Normal // Keep it lighter for elegance
        )
    }
}

@Composable
private fun MinimalHero(state: DashboardUiState) {
    // Radical Minimalism: No Card. Just Data.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "${state.daysRemaining}",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Days Remaining",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${state.unemploymentDaysUsed} used of ${state.unemploymentDaysAllowed}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        // Minimal Ring
        Box(contentAlignment = Alignment.Center) {
            MinimalCircularProgress(
                progress = state.unemploymentProgress,
                size = 72.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            // Small icon inside ring instead of text
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun MinimalCircularProgress(
    progress: Float,
    size: Dp,
    color: Color,
    trackColor: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1500, delayMillis = 300),
        label = "progress"
    )

    Canvas(modifier = Modifier.size(size)) {
        // Track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round) // Very thin stroke
        )
        // Indicator
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360 * animatedProgress,
            useCenter = false,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun ActionGrid(
    state: DashboardUiState,
    onOpenReporting: () -> Unit,
    onOpenVault: () -> Unit,
    onScanDocument: () -> Unit,
    onOpenChat: () -> Unit
) {
    // 2x2 Grid - Flat Style
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MinimalActionItem(
                modifier = Modifier.weight(1f),
                title = "Report",
                icon = Icons.AutoMirrored.Filled.List,
                hasAlert = state.pendingReportingCount > 0,
                onClick = onOpenReporting
            )
            MinimalActionItem(
                modifier = Modifier.weight(1f),
                title = "Vault",
                icon = Icons.Filled.Lock,
                onClick = onOpenVault
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MinimalActionItem(
                modifier = Modifier.weight(1f),
                title = "Scan",
                icon = Icons.Filled.DocumentScanner,
                onClick = onScanDocument
            )
            MinimalActionItem(
                modifier = Modifier.weight(1f),
                title = "AI Chat",
                icon = Icons.Filled.SmartToy,
                onClick = onOpenChat
            )
        }
    }
}

@Composable
private fun MinimalActionItem(
    modifier: Modifier,
    title: String,
    icon: ImageVector,
    hasAlert: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), // Very subtle background
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (hasAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (hasAlert) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun EmploymentList(
    state: DashboardUiState,
    onEditEmployment: (String) -> Unit
) {
    if (state.employmentHistory.isEmpty()) {
        // Minimal Empty State
        Text(
            text = "No employment records yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            state.employmentHistory.forEach { job ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditEmployment(job.id) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = job.employerName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = job.jobTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Date aligned to the right, simple text
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (job.endDate == null) "Present" else formatDateYear(job.endDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (job.endDate == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalSupportSection(
    onSendFeedback: () -> Unit,
    onOpenLegal: () -> Unit,
    onSignOut: () -> Unit,
    onReprocessDocuments: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        MinimalSettingItem("Send Feedback", onSendFeedback)
        MinimalSettingItem("Legal Disclaimer", onOpenLegal)
        MinimalSettingItem("Refresh Data", onReprocessDocuments)
        MinimalSettingItem("Sign Out", onSignOut, isDestructive = true)
    }
}

@Composable
private fun MinimalSettingItem(
    text: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .size(14.dp)
                .alpha(0.3f),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDateYear(timestamp: Long?): String {
    if (timestamp == null) return ""
    val formatter = SimpleDateFormat("MMM yyyy", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(timestamp))
}