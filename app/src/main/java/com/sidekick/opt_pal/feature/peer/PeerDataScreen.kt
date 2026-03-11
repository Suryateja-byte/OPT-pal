package com.sidekick.opt_pal.feature.peer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.data.model.PeerDataBundle
import com.sidekick.opt_pal.data.model.PeerDataCitation
import com.sidekick.opt_pal.data.model.PeerOfficialContextCard
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun PeerDataRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeerDataViewModel = viewModel(factory = PeerDataViewModel.Factory)
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { AnalyticsLogger.logScreenView("PeerData") }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    PeerDataScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refreshAll,
        onSetContributionEnabled = viewModel::setContributionEnabled,
        onOpenCitation = { citation ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(citation.url)))
        },
        modifier = modifier
    )
}

@Composable
fun PeerDataScreen(
    state: PeerDataUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetContributionEnabled: (Boolean) -> Unit,
    onOpenCitation: (PeerDataCitation) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.PEER_DATA_SCREEN),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                Text("Peer Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onRefresh) {
                    if (state.isRefreshingBundle || state.isRefreshingSnapshot) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Refresh")
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            !state.entitlement.isEnabled -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SectionCard("Limited rollout") {
                        Text(state.entitlement.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { CohortSection(state, onOpenCitation) }
                    item {
                        OfficialContextSection(
                            bundle = state.bundle,
                            cards = state.snapshot?.officialContextCards.orEmpty(),
                            onOpenCitation = onOpenCitation
                        )
                    }
                    item { MethodologySection(state.bundle, onOpenCitation) }
                    item {
                        ContributionSection(
                            state = state,
                            onSetContributionEnabled = onSetContributionEnabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CohortSection(
    state: PeerDataUiState,
    onOpenCitation: (PeerDataCitation) -> Unit
) {
    SectionCard("Your Cohort") {
        val snapshot = state.snapshot
        val descriptors = snapshot?.cohortDescriptors.orEmpty()
        if (descriptors.isNotEmpty()) {
            Text(
                text = descriptors.joinToString("  •  ") { "${it.label}: ${it.value}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (snapshot == null || snapshot.benchmarkCards.isEmpty()) {
            Text(
                text = "Not enough similar opt-in data yet. Official context remains available below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val bundle = state.bundle
            snapshot.benchmarkCards.forEach { card ->
                InsightCard(
                    title = card.title,
                    summary = card.summary,
                    sourceLabel = card.parsedSource.label,
                    lastUpdatedAt = card.lastUpdatedAt,
                    cohortBasis = card.cohortBasis,
                    sampleSizeBand = card.sampleSizeBand,
                    whatThisDoesNotMean = card.whatThisDoesNotMean,
                    citations = card.citationIds.mapNotNull { citationId -> bundle?.citationById(citationId) },
                    onOpenCitation = onOpenCitation
                )
            }
        }
        snapshot?.caveats?.forEach { caveat ->
            Text("• $caveat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OfficialContextSection(
    bundle: PeerDataBundle?,
    cards: List<PeerOfficialContextCard>,
    onOpenCitation: (PeerDataCitation) -> Unit
) {
    SectionCard("Official Context") {
        if (cards.isEmpty()) {
            Text("Official context is unavailable right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            cards.forEach { card ->
                InsightCard(
                    title = card.title,
                    summary = card.summary,
                    sourceLabel = card.parsedSource.label,
                    lastUpdatedAt = card.lastUpdatedAt,
                    cohortBasis = card.cohortBasis,
                    sampleSizeBand = card.sampleSizeBand,
                    whatThisDoesNotMean = card.whatThisDoesNotMean,
                    citations = card.citationIds.mapNotNull { citationId -> bundle?.citationById(citationId) },
                    onOpenCitation = onOpenCitation
                )
            }
        }
    }
}

@Composable
private fun MethodologySection(
    bundle: PeerDataBundle?,
    onOpenCitation: (PeerDataCitation) -> Unit
) {
    SectionCard("Methodology") {
        if (bundle == null) {
            Text("Methodology bundle unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        Text(
            "Last reviewed ${formatUtcDateTime(bundle.lastReviewedAt)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (bundle.freshnessSummary.isNotBlank()) {
            Text(bundle.freshnessSummary, style = MaterialTheme.typography.bodyMedium)
        }
        bundle.methodologyNotes.forEach { note ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(note.title, fontWeight = FontWeight.SemiBold)
                    Text(note.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        bundle.changelog.forEach { entry ->
            val citation = bundle.citationById(entry.citationId)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(entry.title, fontWeight = FontWeight.SemiBold)
                    Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Effective ${entry.effectiveDate}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    citation?.let { CitationRow(it, onOpenCitation) }
                }
            }
        }
    }
}

@Composable
private fun ContributionSection(
    state: PeerDataUiState,
    onSetContributionEnabled: (Boolean) -> Unit
) {
    SectionCard("Contribute") {
        Text(
            if (state.settings.contributionEnabled) "Contribution is enabled." else "Contribution is currently off.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "We share your OPT track, major family, coarse time bucket, and benchmark outcomes.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "We do not share your name, school, employer, documents, receipt numbers, or exact dates.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.settings.previewedAt?.let {
            Text(
                "Preview reviewed ${formatUtcDateTime(it)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.settings.withdrawnAt?.let {
            Text(
                "Contribution withdrawn ${formatUtcDateTime(it)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = { onSetContributionEnabled(!state.settings.contributionEnabled) },
            enabled = !state.isSavingParticipation
        ) {
            if (state.isSavingParticipation) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(if (state.settings.contributionEnabled) "Stop contributing" else "Enable contribution")
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title.uppercase(Locale.US),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun InsightCard(
    title: String,
    summary: String,
    sourceLabel: String,
    lastUpdatedAt: Long,
    cohortBasis: String,
    sampleSizeBand: String?,
    whatThisDoesNotMean: String,
    citations: List<PeerDataCitation>,
    onOpenCitation: (PeerDataCitation) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Source: $sourceLabel",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (cohortBasis.isNotBlank()) {
                Text(
                    "Cohort basis: $cohortBasis",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            sampleSizeBand?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Sample size band: $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (lastUpdatedAt > 0L) {
                Text(
                    "Last updated ${formatUtcDateTime(lastUpdatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                whatThisDoesNotMean,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            citations.forEach { citation ->
                CitationRow(citation, onOpenCitation)
            }
        }
    }
}

@Composable
private fun CitationRow(
    citation: PeerDataCitation,
    onOpenCitation: (PeerDataCitation) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenCitation(citation) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
        Column {
            Text(citation.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                buildString {
                    citation.effectiveDate?.let { append("Effective $it") }
                    citation.lastReviewedDate?.let {
                        if (isNotEmpty()) append(" • ")
                        append("Reviewed $it")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatUtcDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "unknown"
    val formatter = SimpleDateFormat("MMM d, yyyy h:mm a 'UTC'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(timestamp))
}
