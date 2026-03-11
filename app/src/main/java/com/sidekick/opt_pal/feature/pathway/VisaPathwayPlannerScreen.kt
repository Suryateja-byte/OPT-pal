package com.sidekick.opt_pal.feature.pathway

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.OpenInNew
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.data.model.VisaPathwayAction
import com.sidekick.opt_pal.data.model.VisaPathwayAssessment
import com.sidekick.opt_pal.data.model.VisaPathwayCitation
import com.sidekick.opt_pal.data.model.VisaPathwayEmployerType
import com.sidekick.opt_pal.data.model.VisaPathwayH1bRegistrationStatus
import com.sidekick.opt_pal.data.model.VisaPathwayId
import com.sidekick.opt_pal.data.model.VisaPathwayO1EvidenceBucket
import com.sidekick.opt_pal.data.model.VisaPathwayRecommendation
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisaPathwayPlannerRoute(
    initialPathwayId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onOpenPeerData: () -> Unit,
    onOpenScenarioSimulator: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VisaPathwayPlannerViewModel = viewModel(
        factory = VisaPathwayPlannerViewModel.provideFactory(initialPathwayId)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showContinuityDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AnalyticsLogger.logVisaPathwayPlannerOpened()
    }
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

    if (showContinuityDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.plannerProfile.desiredContinuityDate)
        DatePickerDialog(
            onDismissRequest = { showContinuityDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateDesiredContinuityDate(pickerState.selectedDateMillis)
                    showContinuityDatePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showContinuityDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    VisaPathwayPlannerScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRefreshBundle = viewModel::refreshBundle,
        onPickContinuityDate = { showContinuityDatePicker = true },
        onUpdateEmployerType = viewModel::updateEmployerType,
        onUpdateEmployerUsesEVerify = viewModel::updateEmployerUsesEVerify,
        onUpdateEmployerWillSponsorH1b = viewModel::updateEmployerWillSponsorH1b,
        onUpdateH1bRegistrationStatus = viewModel::updateH1bRegistrationStatus,
        onUpdateDegreeLevel = viewModel::updateDegreeLevel,
        onUpdateHasPriorUsStemDegree = viewModel::updateHasPriorUsStemDegree,
        onUpdateRoleRelatedToDegree = viewModel::updateRoleRelatedToDegree,
        onUpdateHasPetitioningEmployerOrAgent = viewModel::updateHasPetitioningEmployerOrAgent,
        onToggleO1Evidence = viewModel::toggleO1Evidence,
        onUpdateStatusViolation = viewModel::updateStatusViolation,
        onUpdateArrestHistory = viewModel::updateArrestHistory,
        onUpdateUnauthorizedEmployment = viewModel::updateUnauthorizedEmployment,
        onUpdateRfeOrNoid = viewModel::updateRfeOrNoid,
        onSelectPathway = viewModel::selectPathway,
        onMarkPreferredPathway = viewModel::markPreferredPathway,
        onRunAction = { action ->
            AnalyticsLogger.logVisaPathwayPlannerActionClicked(action.id)
            when {
                !action.route.isNullOrBlank() -> onNavigateToRoute(action.route)
                !action.externalUrl.isNullOrBlank() -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.externalUrl)))
            }
        },
        onOpenCitation = { citation ->
            AnalyticsLogger.logVisaPathwayPlannerSourceOpened(citation.id)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(citation.url)))
        },
        onOpenPeerData = onOpenPeerData,
        onOpenScenarioSimulator = onOpenScenarioSimulator,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisaPathwayPlannerScreen(
    state: VisaPathwayPlannerUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onRefreshBundle: () -> Unit,
    onPickContinuityDate: () -> Unit,
    onUpdateEmployerType: (VisaPathwayEmployerType) -> Unit,
    onUpdateEmployerUsesEVerify: (Boolean?) -> Unit,
    onUpdateEmployerWillSponsorH1b: (Boolean?) -> Unit,
    onUpdateH1bRegistrationStatus: (VisaPathwayH1bRegistrationStatus) -> Unit,
    onUpdateDegreeLevel: (String) -> Unit,
    onUpdateHasPriorUsStemDegree: (Boolean?) -> Unit,
    onUpdateRoleRelatedToDegree: (Boolean?) -> Unit,
    onUpdateHasPetitioningEmployerOrAgent: (Boolean?) -> Unit,
    onToggleO1Evidence: (VisaPathwayO1EvidenceBucket) -> Unit,
    onUpdateStatusViolation: (Boolean?) -> Unit,
    onUpdateArrestHistory: (Boolean?) -> Unit,
    onUpdateUnauthorizedEmployment: (Boolean?) -> Unit,
    onUpdateRfeOrNoid: (Boolean?) -> Unit,
    onSelectPathway: (VisaPathwayId) -> Unit,
    onMarkPreferredPathway: (VisaPathwayId) -> Unit,
    onRunAction: (VisaPathwayAction) -> Unit,
    onOpenCitation: (VisaPathwayCitation) -> Unit,
    onOpenPeerData: () -> Unit,
    onOpenScenarioSimulator: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.VISA_PATHWAY_SCREEN),
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
                Text(
                    text = "Visa Planner",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onRefreshBundle) {
                    Text("Refresh")
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
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.VISA_PATHWAY_LOCKED_PREVIEW),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Limited rollout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(state.entitlement.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                    item {
                        CurrentPositionCard(
                            state = state,
                            onOpenPeerData = onOpenPeerData,
                            onOpenScenarioSimulator = onOpenScenarioSimulator
                        )
                    }
                    item {
                        QuestionnaireCard(
                            state = state,
                            onPickContinuityDate = onPickContinuityDate,
                            onUpdateEmployerType = onUpdateEmployerType,
                            onUpdateEmployerUsesEVerify = onUpdateEmployerUsesEVerify,
                            onUpdateEmployerWillSponsorH1b = onUpdateEmployerWillSponsorH1b,
                            onUpdateH1bRegistrationStatus = onUpdateH1bRegistrationStatus,
                            onUpdateDegreeLevel = onUpdateDegreeLevel,
                            onUpdateHasPriorUsStemDegree = onUpdateHasPriorUsStemDegree,
                            onUpdateRoleRelatedToDegree = onUpdateRoleRelatedToDegree,
                            onUpdateHasPetitioningEmployerOrAgent = onUpdateHasPetitioningEmployerOrAgent,
                            onToggleO1Evidence = onToggleO1Evidence,
                            onUpdateStatusViolation = onUpdateStatusViolation,
                            onUpdateArrestHistory = onUpdateArrestHistory,
                            onUpdateUnauthorizedEmployment = onUpdateUnauthorizedEmployment,
                            onUpdateRfeOrNoid = onUpdateRfeOrNoid
                        )
                    }
                    item {
                        Text(
                            text = "BEST NEXT PATHS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier.testTag(UiTestTags.VISA_PATHWAY_CARD_LIST),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            state.temporaryAssessments.forEach { assessment ->
                                PathwayCard(
                                    assessment = assessment,
                                    isSelected = assessment.pathwayId == state.selectedAssessment?.pathwayId,
                                    isPreferred = state.summary.preferredPathwayId == assessment.pathwayId,
                                    onSelect = { onSelectPathway(assessment.pathwayId) },
                                    onMarkPreferred = { onMarkPreferredPathway(assessment.pathwayId) },
                                    onRunAction = onRunAction,
                                    onOpenCitation = onOpenCitation
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            text = "LONG-TERM TRACKS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.longTermAssessments.forEach { assessment ->
                                PathwayCard(
                                    assessment = assessment,
                                    isSelected = false,
                                    isPreferred = false,
                                    onSelect = {},
                                    onMarkPreferred = {},
                                    onRunAction = onRunAction,
                                    onOpenCitation = onOpenCitation
                                )
                            }
                        }
                    }
                    item {
                        ScopeCard()
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentPositionCard(
    state: VisaPathwayPlannerUiState,
    onOpenPeerData: () -> Unit,
    onOpenScenarioSimulator: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("CURRENT POSITION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = state.summary.topAssessment?.title ?: "Planner is waiting for enough evidence",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.summary.topAssessment?.summary ?: state.entitlement.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.summary.topAssessment?.nextMilestone?.let { milestone ->
                Text(
                    text = "Next milestone: ${milestone.title}${milestone.dueDate?.let { " by ${formatUtcDate(it)}" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = "OPT: ${state.userProfile?.optType?.uppercase().orEmpty()}  •  CIP: ${state.userProfile?.cipCode ?: "Unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onOpenPeerData) {
                Text("Open Peer Data")
            }
            TextButton(onClick = onOpenScenarioSimulator) {
                Text("Run Scenario Simulator")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionnaireCard(
    state: VisaPathwayPlannerUiState,
    onPickContinuityDate: () -> Unit,
    onUpdateEmployerType: (VisaPathwayEmployerType) -> Unit,
    onUpdateEmployerUsesEVerify: (Boolean?) -> Unit,
    onUpdateEmployerWillSponsorH1b: (Boolean?) -> Unit,
    onUpdateH1bRegistrationStatus: (VisaPathwayH1bRegistrationStatus) -> Unit,
    onUpdateDegreeLevel: (String) -> Unit,
    onUpdateHasPriorUsStemDegree: (Boolean?) -> Unit,
    onUpdateRoleRelatedToDegree: (Boolean?) -> Unit,
    onUpdateHasPetitioningEmployerOrAgent: (Boolean?) -> Unit,
    onToggleO1Evidence: (VisaPathwayO1EvidenceBucket) -> Unit,
    onUpdateStatusViolation: (Boolean?) -> Unit,
    onUpdateArrestHistory: (Boolean?) -> Unit,
    onUpdateUnauthorizedEmployment: (Boolean?) -> Unit,
    onUpdateRfeOrNoid: (Boolean?) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.VISA_PATHWAY_QUESTIONNAIRE),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("QUESTIONNAIRE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onPickContinuityDate) {
                Text(
                    text = state.plannerProfile.desiredContinuityDate?.let {
                        "Continuity date: ${formatUtcDate(it)}"
                    } ?: "Set work-authorization continuity date"
                )
            }
            FieldLabel("Employer type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VisaPathwayEmployerType.entries.forEach { employerType ->
                    FilterChip(
                        selected = state.plannerProfile.parsedEmployerType == employerType,
                        onClick = { onUpdateEmployerType(employerType) },
                        label = { Text(employerType.label) }
                    )
                }
            }
            OutlinedTextField(
                value = state.plannerProfile.degreeLevel,
                onValueChange = onUpdateDegreeLevel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Degree level") }
            )
            YesNoRow("Employer uses E-Verify", state.plannerProfile.employerUsesEVerify, onUpdateEmployerUsesEVerify)
            YesNoRow("Employer will sponsor H-1B", state.plannerProfile.employerWillSponsorH1b, onUpdateEmployerWillSponsorH1b)
            YesNoRow("Prior U.S. STEM degree", state.plannerProfile.hasPriorUsStemDegree, onUpdateHasPriorUsStemDegree)
            YesNoRow("Role directly related to degree", state.plannerProfile.roleDirectlyRelatedToDegree, onUpdateRoleRelatedToDegree)
            YesNoRow("Petitioning employer or agent for O-1A", state.plannerProfile.hasPetitioningEmployerOrAgent, onUpdateHasPetitioningEmployerOrAgent)
            FieldLabel("H-1B registration status")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VisaPathwayH1bRegistrationStatus.entries.forEach { status ->
                    FilterChip(
                        selected = state.plannerProfile.parsedH1bRegistrationStatus == status,
                        onClick = { onUpdateH1bRegistrationStatus(status) },
                        label = { Text(status.label) }
                    )
                }
            }
            FieldLabel("O-1A evidence signals")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VisaPathwayO1EvidenceBucket.entries.forEach { bucket ->
                    FilterChip(
                        selected = state.plannerProfile.parsedO1EvidenceSignals.contains(bucket),
                        onClick = { onToggleO1Evidence(bucket) },
                        label = { Text(bucket.label) }
                    )
                }
            }
            FieldLabel("Escalation checks")
            YesNoRow("Status violation", state.plannerProfile.hasStatusViolation, onUpdateStatusViolation)
            YesNoRow("Arrest history", state.plannerProfile.hasArrestHistory, onUpdateArrestHistory)
            YesNoRow("Unauthorized employment", state.plannerProfile.hasUnauthorizedEmployment, onUpdateUnauthorizedEmployment)
            YesNoRow("RFE or NOID", state.plannerProfile.hasRfeOrNoid, onUpdateRfeOrNoid)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PathwayCard(
    assessment: VisaPathwayAssessment,
    isSelected: Boolean,
    isPreferred: Boolean,
    onSelect: () -> Unit,
    onMarkPreferred: () -> Unit,
    onRunAction: (VisaPathwayAction) -> Unit,
    onOpenCitation: (VisaPathwayCitation) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(assessment.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = assessment.recommendation.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = recommendationColor(assessment.recommendation)
                    )
                }
                if (!assessment.isEducationalOnly) {
                    TextButton(onClick = onMarkPreferred) {
                        Text(if (isPreferred) "Preferred" else "Mark preferred")
                    }
                }
            }
            Text(
                text = assessment.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            assessment.policyOverlayTitle?.let { overlay ->
                Text(
                    text = "Policy overlay: $overlay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            assessment.whyItFits.take(3).forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodySmall)
            }
            assessment.gaps.take(3).forEach { gap ->
                Text(
                    text = "Gap: ${gap.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (gap.isBlocking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            assessment.nextMilestone?.let { milestone ->
                Text(
                    text = "Next: ${milestone.title}${milestone.dueDate?.let { " by ${formatUtcDate(it)}" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                assessment.actions.take(3).forEach { action ->
                    Button(onClick = { onRunAction(action) }) {
                        Text(action.label)
                    }
                }
            }
            assessment.citations.take(2).forEach { citation ->
                Row(
                    modifier = Modifier.clickable { onOpenCitation(citation) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "${citation.label} • reviewed ${citation.lastReviewedDate ?: "unknown"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("WHAT THIS PLANNER CAN AND CANNOT SEE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("It can rank current temporary options from your OPT/STEM records, I-983 state, reporting data, USCIS I-765 tracking, and reviewed policy sources.")
            Text("It cannot predict lottery odds, approval chances, visa-bulletin waiting times, nationality-specific routes, or employer cap-exempt verification.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YesNoRow(
    label: String,
    value: Boolean?,
    onValueChanged: (Boolean?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = value == true, onClick = { onValueChanged(true) }, label = { Text("Yes") })
            FilterChip(selected = value == false, onClick = { onValueChanged(false) }, label = { Text("No") })
            FilterChip(selected = value == null, onClick = { onValueChanged(null) }, label = { Text("Unknown") })
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun recommendationColor(recommendation: VisaPathwayRecommendation) = when (recommendation) {
    VisaPathwayRecommendation.STRONG_FIT -> MaterialTheme.colorScheme.primary
    VisaPathwayRecommendation.POSSIBLE_WITH_GAPS -> MaterialTheme.colorScheme.tertiary
    VisaPathwayRecommendation.EXPLORATORY -> MaterialTheme.colorScheme.secondary
    VisaPathwayRecommendation.NOT_A_CURRENT_FIT -> MaterialTheme.colorScheme.error
    VisaPathwayRecommendation.CONSULT_DSO_OR_ATTORNEY -> MaterialTheme.colorScheme.error
}

private fun formatUtcDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(timestamp))
}
