package com.sidekick.opt_pal.feature.compliance

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.data.model.ComplianceAction
import com.sidekick.opt_pal.data.model.ComplianceBlocker
import com.sidekick.opt_pal.data.model.ComplianceFactorAssessment
import com.sidekick.opt_pal.data.model.ComplianceHealthScore
import com.sidekick.opt_pal.data.model.ComplianceReference
import com.sidekick.opt_pal.data.model.ComplianceScoreBand
import com.sidekick.opt_pal.data.model.ComplianceScoreQuality
import com.sidekick.opt_pal.navigation.AppScreen
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ComplianceHealthRoute(
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ComplianceHealthViewModel = viewModel(factory = ComplianceHealthViewModel.provideFactory())
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        AnalyticsLogger.logComplianceHealthOpened()
    }

    ComplianceHealthScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onRunAction = { action ->
            AnalyticsLogger.logComplianceHealthActionClicked(action.id)
            when {
                !action.route.isNullOrBlank() -> onNavigateToRoute(action.route)
                !action.externalUrl.isNullOrBlank() -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.externalUrl)))
                }
            }
        },
        onOpenPolicyAlert = { alertId ->
            AnalyticsLogger.logComplianceHealthActionClicked("open_policy_alert")
            onNavigateToRoute(AppScreen.PolicyAlerts.createRoute(alertId))
        },
        onOpenReference = { reference ->
            AnalyticsLogger.logComplianceHealthReferenceOpened(reference.id)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reference.url)))
        },
        onContactDso = {
            AnalyticsLogger.logComplianceHealthActionClicked("contact_dso")
            val schoolName = state.profile?.schoolName?.takeIf { it.isNotBlank() }
            val subject = if (schoolName == null) {
                "OPT compliance question"
            } else {
                "OPT compliance question for $schoolName"
            }
            val body = buildString {
                append("I need help reviewing a compliance issue in OPTPal.")
                schoolName?.let {
                    append("\nSchool: ")
                    append(it)
                }
            }
            context.startActivity(
                Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:")
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                }
            )
        },
        modifier = modifier
    )
}

@Composable
fun ComplianceHealthScreen(
    state: ComplianceHealthUiState,
    onNavigateBack: () -> Unit,
    onRunAction: (ComplianceAction) -> Unit,
    onOpenPolicyAlert: (String) -> Unit,
    onOpenReference: (ComplianceReference) -> Unit,
    onContactDso: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.COMPLIANCE_HEALTH_SCREEN),
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
                    text = "Compliance Score",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            !state.availability.isEnabled -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MessageCard(text = state.availability.message)
                }
            }

            state.score == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MessageCard(text = state.errorMessage ?: "Compliance data is still loading.")
                }
            }

            else -> {
                val score = state.score
                val policyAlertId = score.latestCriticalPolicyAlertId
                val policyAlertTitle = score.latestCriticalPolicyAlertTitle
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ScoreHeroCard(score = score)
                    }
                    if (score.blockers.isNotEmpty()) {
                        item {
                            BlockerCard(
                                blockers = score.blockers,
                                onRunAction = onRunAction,
                                onContactDso = onContactDso
                            )
                        }
                    }
                    if (!policyAlertId.isNullOrBlank() && !policyAlertTitle.isNullOrBlank()) {
                        item {
                            PolicyOverlayCard(
                                title = policyAlertTitle,
                                unreadCount = score.unreadCriticalPolicyCount,
                                onOpenPolicyAlert = { onOpenPolicyAlert(policyAlertId) }
                            )
                        }
                    }
                    item {
                        ScopeCard()
                    }
                    item {
                        Text(
                            text = "FACTORS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier.testTag(UiTestTags.COMPLIANCE_HEALTH_FACTOR_LIST),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            score.factors.forEach { factor ->
                                FactorCard(
                                    factor = factor,
                                    onRunAction = onRunAction,
                                    onOpenReference = onOpenReference
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreHeroCard(score: ComplianceHealthScore) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = complianceColor(score.band).copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = score.band.label(),
                        style = MaterialTheme.typography.titleLarge,
                        color = complianceColor(score.band),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = score.quality.label(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = score.score.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = deltaLabel(score.delta),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = complianceColor(score.band),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = (score.score / 100f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = complianceColor(score.band),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = score.headline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = score.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            score.topReasons.take(3).forEach { reason ->
                Text(
                    text = "- $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Last computed ${formatUtcDateTime(score.computedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BlockerCard(
    blockers: List<ComplianceBlocker>,
    onRunAction: (ComplianceAction) -> Unit,
    onContactDso: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Critical blockers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            blockers.forEach { blocker ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = blocker.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = blocker.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                blockers.firstNotNullOfOrNull { it.action }?.let { primaryAction ->
                    Button(onClick = { onRunAction(primaryAction) }) {
                        Text(primaryAction.label)
                    }
                }
                TextButton(onClick = onContactDso) {
                    Text("Contact DSO")
                }
            }
        }
    }
}

@Composable
private fun PolicyOverlayCard(
    title: String,
    unreadCount: Int,
    onOpenPolicyAlert: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Critical policy overlay",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (unreadCount > 0) "$unreadCount unread critical alert(s)" else "Review the linked alert for current policy context.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onOpenPolicyAlert) {
                Text("Open Policy Alert")
            }
        }
    }
}

@Composable
private fun ScopeCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "What this score can see",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Tracked unemployment exposure, open reporting items, document-expiration readiness, and the current USCIS case stage.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "What this score cannot see",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "School-specific instructions, unlinked USCIS cases, off-app reporting submissions, and personalized legal advice.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FactorCard(
    factor: ComplianceFactorAssessment,
    onRunAction: (ComplianceAction) -> Unit,
    onOpenReference: (ComplianceReference) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = factor.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (factor.isVerified) "Verified" else "Provisional",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${factor.score}/${factor.maxScore}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            LinearProgressIndicator(
                progress = (factor.score / factor.maxScore.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = factor.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = factor.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (factor.actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    factor.actions.take(2).forEach { action ->
                        TextButton(onClick = { onRunAction(action) }) {
                            Text(action.label)
                        }
                    }
                }
            }
            factor.references.forEach { reference ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = reference.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = reference.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = buildReferenceDates(reference),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { onOpenReference(reference) }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Open source")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun ComplianceScoreBand.label(): String {
    return when (this) {
        ComplianceScoreBand.STABLE -> "Stable"
        ComplianceScoreBand.WATCH -> "Watch"
        ComplianceScoreBand.ACTION_NEEDED -> "Action Needed"
        ComplianceScoreBand.CRITICAL -> "Critical"
    }
}

private fun ComplianceScoreQuality.label(): String {
    return when (this) {
        ComplianceScoreQuality.VERIFIED -> "Verified"
        ComplianceScoreQuality.PROVISIONAL -> "Provisional"
    }
}

@Composable
private fun complianceColor(band: ComplianceScoreBand): Color {
    return when (band) {
        ComplianceScoreBand.STABLE -> MaterialTheme.colorScheme.primary
        ComplianceScoreBand.WATCH -> MaterialTheme.colorScheme.tertiary
        ComplianceScoreBand.ACTION_NEEDED -> MaterialTheme.colorScheme.secondary
        ComplianceScoreBand.CRITICAL -> MaterialTheme.colorScheme.error
    }
}

private fun deltaLabel(delta: Int?): String {
    return when {
        delta == null -> "No previous daily snapshot"
        delta > 0 -> "+$delta vs previous daily snapshot"
        delta < 0 -> "$delta vs previous daily snapshot"
        else -> "No change vs previous daily snapshot"
    }
}

private fun buildReferenceDates(reference: ComplianceReference): String {
    val parts = mutableListOf<String>()
    reference.effectiveDate?.takeIf { it.isNotBlank() }?.let { parts += "Effective $it" }
    reference.lastReviewedDate?.takeIf { it.isNotBlank() }?.let { parts += "Reviewed $it" }
    return if (parts.isEmpty()) "Official source" else parts.joinToString(" | ")
}

private fun formatUtcDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "recently"
    val formatter = SimpleDateFormat("MMM d, h:mm a 'UTC'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(timestamp))
}
