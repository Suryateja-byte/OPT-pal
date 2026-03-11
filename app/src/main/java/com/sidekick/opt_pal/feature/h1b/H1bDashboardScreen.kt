package com.sidekick.opt_pal.feature.h1b

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.sidekick.opt_pal.data.model.H1B_EVERIFY_SEARCH_URL
import com.sidekick.opt_pal.data.model.H1bDashboardBundle
import com.sidekick.opt_pal.data.model.H1bEVerifyStatus
import com.sidekick.opt_pal.data.model.H1bReadinessLevel
import com.sidekick.opt_pal.data.model.H1bWorkflowStage
import com.sidekick.opt_pal.data.model.PolicyCitation
import com.sidekick.opt_pal.data.model.VisaPathwayEmployerType
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun H1bDashboardRoute(
    onNavigateBack: () -> Unit,
    onOpenCaseStatus: () -> Unit,
    onOpenVisaPathwayPlanner: () -> Unit,
    onOpenPeerData: () -> Unit,
    onOpenScenarioSimulator: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: H1bDashboardViewModel = viewModel(factory = H1bDashboardViewModel.Factory)
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { AnalyticsLogger.logScreenView("H1bDashboard") }
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

    H1bDashboardScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRefreshBundle = viewModel::refreshBundle,
        onUpdateEmployerName = viewModel::updateEmployerName,
        onUpdateEmployerCity = viewModel::updateEmployerCity,
        onUpdateEmployerState = viewModel::updateEmployerState,
        onUpdateFeinLastFour = viewModel::updateFeinLastFour,
        onUpdateEmployerType = viewModel::updateEmployerType,
        onUpdateSponsorIntent = viewModel::updateSponsorIntent,
        onUpdateRoleMatchesSpecialtyOccupation = viewModel::updateRoleMatchesSpecialtyOccupation,
        onOpenEVerifySearch = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(H1B_EVERIFY_SEARCH_URL)))
        },
        onSaveEVerifySnapshot = viewModel::saveEVerifySnapshot,
        onRefreshEmployerHistory = viewModel::refreshEmployerHistory,
        onUpdateWorkflowStage = viewModel::updateWorkflowStage,
        onUpdateSelectedRegistration = viewModel::updateSelectedRegistration,
        onUpdateFiledPetition = viewModel::updateFiledPetition,
        onUpdateRequestedChangeOfStatus = viewModel::updateRequestedChangeOfStatus,
        onUpdateRequestedConsularProcessing = viewModel::updateRequestedConsularProcessing,
        onReceiptNumberChanged = viewModel::onReceiptNumberChanged,
        onTrackI129Case = viewModel::trackI129Case,
        onRefreshLinkedCase = viewModel::refreshLinkedCase,
        onOpenCaseStatus = onOpenCaseStatus,
        onUpdateHasEmployerLetter = viewModel::updateHasEmployerLetter,
        onUpdateHasWageInfo = viewModel::updateHasWageInfo,
        onUpdateHasDegreeMatchEvidence = viewModel::updateHasDegreeMatchEvidence,
        onUpdateHasRegistrationConfirmation = viewModel::updateHasRegistrationConfirmation,
        onUpdateHasReceiptNotice = viewModel::updateHasReceiptNotice,
        onUpdateHasCapExemptSupport = viewModel::updateHasCapExemptSupport,
        onUpdateCapGapTravelPlanned = viewModel::updateCapGapTravelPlanned,
        onUpdateHasRfeOrNoid = viewModel::updateHasRfeOrNoid,
        onOpenVisaPathwayPlanner = onOpenVisaPathwayPlanner,
        onOpenPeerData = onOpenPeerData,
        onOpenScenarioSimulator = onOpenScenarioSimulator,
        onOpenCitation = { citation ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(citation.url)))
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun H1bDashboardScreen(
    state: H1bDashboardUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRefreshBundle: () -> Unit,
    onUpdateEmployerName: (String) -> Unit,
    onUpdateEmployerCity: (String) -> Unit,
    onUpdateEmployerState: (String) -> Unit,
    onUpdateFeinLastFour: (String) -> Unit,
    onUpdateEmployerType: (VisaPathwayEmployerType) -> Unit,
    onUpdateSponsorIntent: (Boolean?) -> Unit,
    onUpdateRoleMatchesSpecialtyOccupation: (Boolean?) -> Unit,
    onOpenEVerifySearch: () -> Unit,
    onSaveEVerifySnapshot: (H1bEVerifyStatus) -> Unit,
    onRefreshEmployerHistory: () -> Unit,
    onUpdateWorkflowStage: (H1bWorkflowStage) -> Unit,
    onUpdateSelectedRegistration: (Boolean?) -> Unit,
    onUpdateFiledPetition: (Boolean?) -> Unit,
    onUpdateRequestedChangeOfStatus: (Boolean?) -> Unit,
    onUpdateRequestedConsularProcessing: (Boolean?) -> Unit,
    onReceiptNumberChanged: (String) -> Unit,
    onTrackI129Case: () -> Unit,
    onRefreshLinkedCase: () -> Unit,
    onOpenCaseStatus: () -> Unit,
    onUpdateHasEmployerLetter: (Boolean?) -> Unit,
    onUpdateHasWageInfo: (Boolean?) -> Unit,
    onUpdateHasDegreeMatchEvidence: (Boolean?) -> Unit,
    onUpdateHasRegistrationConfirmation: (Boolean?) -> Unit,
    onUpdateHasReceiptNotice: (Boolean?) -> Unit,
    onUpdateHasCapExemptSupport: (Boolean?) -> Unit,
    onUpdateCapGapTravelPlanned: (Boolean?) -> Unit,
    onUpdateHasRfeOrNoid: (Boolean?) -> Unit,
    onOpenVisaPathwayPlanner: () -> Unit,
    onOpenPeerData: () -> Unit,
    onOpenScenarioSimulator: () -> Unit,
    onOpenCitation: (PolicyCitation) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.H1B_DASHBOARD_SCREEN),
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
                Text("H-1B Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onRefreshBundle) {
                    if (state.isRefreshingBundle) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Refresh")
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { SummaryCard(state, onOpenVisaPathwayPlanner, onOpenPeerData, onOpenScenarioSimulator) }
                item {
                    EmployerProfileCard(
                        state = state,
                        onUpdateEmployerName = onUpdateEmployerName,
                        onUpdateEmployerCity = onUpdateEmployerCity,
                        onUpdateEmployerState = onUpdateEmployerState,
                        onUpdateFeinLastFour = onUpdateFeinLastFour,
                        onUpdateEmployerType = onUpdateEmployerType,
                        onUpdateSponsorIntent = onUpdateSponsorIntent,
                        onUpdateRoleMatchesSpecialtyOccupation = onUpdateRoleMatchesSpecialtyOccupation
                    )
                }
                item {
                    EmployerVerificationCard(
                        state = state,
                        onOpenEVerifySearch = onOpenEVerifySearch,
                        onSaveEVerifySnapshot = onSaveEVerifySnapshot,
                        onRefreshEmployerHistory = onRefreshEmployerHistory
                    )
                }
                item {
                    TimelineCard(
                        state = state,
                        onUpdateWorkflowStage = onUpdateWorkflowStage,
                        onUpdateSelectedRegistration = onUpdateSelectedRegistration,
                        onUpdateFiledPetition = onUpdateFiledPetition,
                        onUpdateRequestedChangeOfStatus = onUpdateRequestedChangeOfStatus,
                        onUpdateRequestedConsularProcessing = onUpdateRequestedConsularProcessing
                    )
                }
                item { CaseTrackingCard(state, onReceiptNumberChanged, onTrackI129Case, onRefreshLinkedCase, onOpenCaseStatus) }
                item { CapGapCard(state) }
                item {
                    EvidenceCard(
                        state = state,
                        onUpdateHasEmployerLetter = onUpdateHasEmployerLetter,
                        onUpdateHasWageInfo = onUpdateHasWageInfo,
                        onUpdateHasDegreeMatchEvidence = onUpdateHasDegreeMatchEvidence,
                        onUpdateHasRegistrationConfirmation = onUpdateHasRegistrationConfirmation,
                        onUpdateHasReceiptNotice = onUpdateHasReceiptNotice,
                        onUpdateHasCapExemptSupport = onUpdateHasCapExemptSupport,
                        onUpdateCapGapTravelPlanned = onUpdateCapGapTravelPlanned,
                        onUpdateHasRfeOrNoid = onUpdateHasRfeOrNoid
                    )
                }
                item { PolicyCardSection(state.bundle, onOpenCitation) }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    state: H1bDashboardUiState,
    onOpenVisaPathwayPlanner: () -> Unit,
    onOpenPeerData: () -> Unit,
    onOpenScenarioSimulator: () -> Unit
) {
    SectionCard("Readiness Summary") {
        Text(state.readinessSummary.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "${state.readinessSummary.level.label}  •  ${state.readinessSummary.capTrack.label}  •  ${state.readinessSummary.verificationConfidence.label}",
            style = MaterialTheme.typography.labelLarge,
            color = readinessColor(state.readinessSummary.level)
        )
        Text(state.readinessSummary.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DetailLine("Next action", state.readinessSummary.nextAction)
        state.readinessSummary.whyThisStatus.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        if (state.readinessSummary.escalationFlags.isNotEmpty()) {
            Text(
                "Escalations: ${state.readinessSummary.escalationFlags.joinToString()}",
                color = MaterialTheme.colorScheme.error
            )
        }
        TextButton(onClick = onOpenVisaPathwayPlanner) { Text("Open Visa Planner") }
        TextButton(onClick = onOpenPeerData) { Text("Open Peer Data") }
        TextButton(onClick = onOpenScenarioSimulator) { Text("Simulate Cap-Gap") }
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
            Text(title.uppercase(Locale.US), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YesNoQuestion(label: String, value: Boolean?, onValueChanged: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = value == true, onClick = { onValueChanged(true) }, label = { Text("Yes") })
            FilterChip(selected = value == false, onClick = { onValueChanged(false) }, label = { Text("No") })
            FilterChip(selected = value == null, onClick = { onValueChanged(null) }, label = { Text("Unknown") })
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun readinessColor(level: H1bReadinessLevel) = when (level) {
    H1bReadinessLevel.READY,
    H1bReadinessLevel.IN_PROGRESS -> MaterialTheme.colorScheme.primary
    H1bReadinessLevel.NEEDS_INPUTS -> MaterialTheme.colorScheme.secondary
    H1bReadinessLevel.WAITING_ON_EMPLOYER -> MaterialTheme.colorScheme.tertiary
    H1bReadinessLevel.ATTENTION_NEEDED -> MaterialTheme.colorScheme.error
}

private fun formatUtcDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return "unknown"
    val formatter = SimpleDateFormat("MMM d, yyyy h:mm a 'UTC'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(timestamp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmployerProfileCard(
    state: H1bDashboardUiState,
    onUpdateEmployerName: (String) -> Unit,
    onUpdateEmployerCity: (String) -> Unit,
    onUpdateEmployerState: (String) -> Unit,
    onUpdateFeinLastFour: (String) -> Unit,
    onUpdateEmployerType: (VisaPathwayEmployerType) -> Unit,
    onUpdateSponsorIntent: (Boolean?) -> Unit,
    onUpdateRoleMatchesSpecialtyOccupation: (Boolean?) -> Unit
) {
    SectionCard("Employer Profile") {
        OutlinedTextField(
            value = state.h1bProfile.employerName,
            onValueChange = onUpdateEmployerName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Employer name") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.h1bProfile.employerCity,
                onValueChange = onUpdateEmployerCity,
                modifier = Modifier.weight(1f),
                label = { Text("City") },
                singleLine = true
            )
            OutlinedTextField(
                value = state.h1bProfile.employerState,
                onValueChange = onUpdateEmployerState,
                modifier = Modifier.weight(1f),
                label = { Text("State") },
                singleLine = true
            )
        }
        OutlinedTextField(
            value = state.h1bProfile.feinLastFour,
            onValueChange = onUpdateFeinLastFour,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("FEIN last four") },
            singleLine = true
        )
        Text("Employer type", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VisaPathwayEmployerType.entries.forEach { employerType ->
                FilterChip(
                    selected = state.h1bProfile.parsedEmployerType == employerType,
                    onClick = { onUpdateEmployerType(employerType) },
                    label = { Text(employerType.label) }
                )
            }
        }
        YesNoQuestion("Employer will sponsor H-1B", state.h1bProfile.selfReportedSponsorIntent, onUpdateSponsorIntent)
        YesNoQuestion(
            "Role clearly matches a specialty occupation",
            state.h1bProfile.roleMatchesSpecialtyOccupation,
            onUpdateRoleMatchesSpecialtyOccupation
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmployerVerificationCard(
    state: H1bDashboardUiState,
    onOpenEVerifySearch: () -> Unit,
    onSaveEVerifySnapshot: (H1bEVerifyStatus) -> Unit,
    onRefreshEmployerHistory: () -> Unit
) {
    SectionCard("Employer Verification") {
        Text(
            "${state.employerVerificationSummary.capTrack.label}  •  ${state.employerVerificationSummary.verificationConfidence.label}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        DetailLine("E-Verify snapshot", state.employerVerificationSummary.eVerifyStatusLabel)
        state.employerVerificationSummary.lastVerifiedAt?.let {
            DetailLine("E-Verify looked up", formatUtcDateTime(it))
        }
        DetailLine("Cap-track note", state.employerVerificationSummary.capTrackDetail)
        DetailLine("Employer history", state.employerVerificationSummary.employerHistoryHeadline)
        Text(state.employerVerificationSummary.employerHistoryDetail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.employerVerificationSummary.lastIngestedAt?.let {
            Text(
                "Employer data last ingested ${formatUtcDateTime(it)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOpenEVerifySearch) { Text("Open E-Verify") }
            Button(onClick = onRefreshEmployerHistory, enabled = !state.isSearchingEmployerHistory) {
                if (state.isSearchingEmployerHistory) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Refresh USCIS history")
                }
            }
        }
        Text("Save the result you saw in the official E-Verify search:", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            H1bEVerifyStatus.entries.filter { it != H1bEVerifyStatus.UNKNOWN }.forEach { status ->
                FilterChip(
                    selected = state.employerVerification.parsedEVerifyStatus == status,
                    onClick = { onSaveEVerifySnapshot(status) },
                    label = { Text(status.label) }
                )
            }
        }
        state.employerVerificationSummary.caveats.forEach { caveat ->
            Text("• $caveat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineCard(
    state: H1bDashboardUiState,
    onUpdateWorkflowStage: (H1bWorkflowStage) -> Unit,
    onUpdateSelectedRegistration: (Boolean?) -> Unit,
    onUpdateFiledPetition: (Boolean?) -> Unit,
    onUpdateRequestedChangeOfStatus: (Boolean?) -> Unit,
    onUpdateRequestedConsularProcessing: (Boolean?) -> Unit
) {
    SectionCard("Cap Season Timeline") {
        Text("FY ${state.capSeasonTimeline.fiscalYear}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(state.capSeasonTimeline.phaseLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        DetailLine(
            "Next deadline",
            buildString {
                append(state.capSeasonTimeline.nextDeadlineLabel)
                state.capSeasonTimeline.nextDeadlineAt?.let { append(" • ${formatUtcDateTime(it)}") }
            }
        )
        Text("Workflow stage", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            H1bWorkflowStage.entries.forEach { stage ->
                FilterChip(
                    selected = state.timelineState.parsedWorkflowStage == stage,
                    onClick = { onUpdateWorkflowStage(stage) },
                    label = { Text(stage.label) }
                )
            }
        }
        YesNoQuestion("Selected registration exists", state.timelineState.selectedRegistration, onUpdateSelectedRegistration)
        YesNoQuestion("Petition was filed", state.timelineState.filedPetition, onUpdateFiledPetition)
        YesNoQuestion("Change of status was requested", state.timelineState.requestedChangeOfStatus, onUpdateRequestedChangeOfStatus)
        YesNoQuestion("Consular processing was requested", state.timelineState.requestedConsularProcessing, onUpdateRequestedConsularProcessing)
        state.capSeasonTimeline.milestones.forEach { milestone ->
            DetailLine(
                milestone.title,
                buildString {
                    append(milestone.detail)
                    milestone.timestamp?.let { append(" • ${formatUtcDateTime(it)}") }
                }
            )
        }
    }
}

@Composable
private fun CaseTrackingCard(
    state: H1bDashboardUiState,
    onReceiptNumberChanged: (String) -> Unit,
    onTrackI129Case: () -> Unit,
    onRefreshLinkedCase: () -> Unit,
    onOpenCaseStatus: () -> Unit
) {
    SectionCard("Registration / Petition Status") {
        Text(
            "Track an I-129 receipt if the employer already filed the petition.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.receiptNumberInput,
            onValueChange = onReceiptNumberChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("I-129 receipt number") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onTrackI129Case, enabled = !state.isTrackingCase) {
                if (state.isTrackingCase) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Track I-129")
                }
            }
            TextButton(onClick = onOpenCaseStatus) { Text("Open full tracker") }
        }
        state.caseTrackingState.linkedCase?.let { linkedCase ->
            DetailLine("Linked receipt", linkedCase.receiptNumber)
            DetailLine(
                "Current stage",
                linkedCase.parsedStage.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            )
            DetailLine("Official status", linkedCase.officialStatusText)
            Text(
                linkedCase.plainEnglishSummary.ifBlank { linkedCase.officialStatusDescription },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRefreshLinkedCase, enabled = state.refreshingCaseId == null) {
                    if (state.refreshingCaseId == linkedCase.id) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Refresh linked case")
                    }
                }
                Text(
                    "Last checked ${formatUtcDateTime(linkedCase.lastCheckedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CapGapCard(state: H1bDashboardUiState) {
    SectionCard("Cap-Gap Continuity") {
        Text(
            "${state.capGapAssessment.state.label}  •  ${state.capGapAssessment.title}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (state.capGapAssessment.legalReviewRequired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Text(state.capGapAssessment.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DetailLine("Work authorization", state.capGapAssessment.workAuthorizationText)
        state.capGapAssessment.travelWarning?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EvidenceCard(
    state: H1bDashboardUiState,
    onUpdateHasEmployerLetter: (Boolean?) -> Unit,
    onUpdateHasWageInfo: (Boolean?) -> Unit,
    onUpdateHasDegreeMatchEvidence: (Boolean?) -> Unit,
    onUpdateHasRegistrationConfirmation: (Boolean?) -> Unit,
    onUpdateHasReceiptNotice: (Boolean?) -> Unit,
    onUpdateHasCapExemptSupport: (Boolean?) -> Unit,
    onUpdateCapGapTravelPlanned: (Boolean?) -> Unit,
    onUpdateHasRfeOrNoid: (Boolean?) -> Unit
) {
    SectionCard("Evidence & Checklist") {
        YesNoQuestion("Employer support letter saved", state.evidence.hasEmployerLetter, onUpdateHasEmployerLetter)
        YesNoQuestion("Wage or offer details saved", state.evidence.hasWageInfo, onUpdateHasWageInfo)
        YesNoQuestion("Degree-match evidence saved", state.evidence.hasDegreeMatchEvidence, onUpdateHasDegreeMatchEvidence)
        YesNoQuestion("Registration confirmation saved", state.evidence.hasRegistrationConfirmation, onUpdateHasRegistrationConfirmation)
        YesNoQuestion("Receipt notice saved", state.evidence.hasReceiptNotice, onUpdateHasReceiptNotice)
        YesNoQuestion("Cap-exempt support saved", state.evidence.hasCapExemptSupport, onUpdateHasCapExemptSupport)
        YesNoQuestion("Travel planned during cap-gap", state.evidence.capGapTravelPlanned, onUpdateCapGapTravelPlanned)
        YesNoQuestion("RFE or NOID received", state.evidence.hasRfeOrNoid, onUpdateHasRfeOrNoid)
    }
}

@Composable
private fun PolicyCardSection(bundle: H1bDashboardBundle?, onOpenCitation: (PolicyCitation) -> Unit) {
    SectionCard("Policy Cards") {
        if (bundle == null) {
            Text("Policy bundle unavailable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        Text(
            "Last reviewed ${formatUtcDateTime(bundle.lastReviewedAt)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        bundle.ruleCards.forEach { card ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(card.summary, style = MaterialTheme.typography.bodyMedium)
                    Text("Confidence: ${card.confidence}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(card.whatThisDoesNotMean, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    card.citationIds.mapNotNull(bundle::citationById).forEach { citation ->
                        CitationRow(citation, onOpenCitation)
                    }
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
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.title, fontWeight = FontWeight.SemiBold)
                    Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
                    Text("Effective ${entry.effectiveDate}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    citation?.let { CitationRow(it, onOpenCitation) }
                }
            }
        }
    }
}

@Composable
private fun CitationRow(citation: PolicyCitation, onOpenCitation: (PolicyCitation) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpenCitation(citation) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
        Column {
            Text(citation.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                buildString {
                    citation.effectiveDate?.let { append("Effective $it") }
                    citation.lastReviewedAt?.let {
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
