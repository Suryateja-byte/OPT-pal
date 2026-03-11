package com.sidekick.opt_pal.feature.scenario

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.sidekick.opt_pal.data.model.EmployerChangeScenarioAssumptions
import com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions
import com.sidekick.opt_pal.data.model.H1bWorkflowStage
import com.sidekick.opt_pal.data.model.JobLossScenarioAssumptions
import com.sidekick.opt_pal.data.model.PendingStemExtensionScenarioAssumptions
import com.sidekick.opt_pal.data.model.ReportingDeadlineScenarioAssumptions
import com.sidekick.opt_pal.data.model.ScenarioAction
import com.sidekick.opt_pal.data.model.ScenarioCitation
import com.sidekick.opt_pal.data.model.ScenarioDraft
import com.sidekick.opt_pal.data.model.ScenarioImpactCard
import com.sidekick.opt_pal.data.model.ScenarioOutcome
import com.sidekick.opt_pal.data.model.ScenarioSimulationResult
import com.sidekick.opt_pal.data.model.ScenarioTemplateId
import com.sidekick.opt_pal.data.model.TravelScenario
import com.sidekick.opt_pal.data.model.TravelScenarioAssumptions
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ScenarioDateField {
    JOB_LOSS_START,
    JOB_LOSS_REPLACEMENT,
    EMPLOYER_CHANGE,
    REPORTING_DUE,
    TRAVEL_DEPARTURE,
    TRAVEL_RETURN,
    PENDING_STEM_FILING,
    PENDING_STEM_OPT_END
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioSimulatorRoute(
    initialTemplateId: String?,
    initialDraftId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScenarioSimulatorViewModel = viewModel(
        factory = ScenarioSimulatorViewModel.provideFactory(initialTemplateId, initialDraftId)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var activeDateField by remember { mutableStateOf<ScenarioDateField?>(null) }

    LaunchedEffect(Unit) { AnalyticsLogger.logScreenView("ScenarioSimulator") }
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

    if (activeDateField != null) {
        key(activeDateField) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDateForField(state.workingDraft, activeDateField)
            )
            DatePickerDialog(
                onDismissRequest = { activeDateField = null },
                confirmButton = {
                    TextButton(onClick = {
                        applySelectedDate(viewModel, activeDateField, pickerState.selectedDateMillis)
                        activeDateField = null
                    }) { Text("Confirm") }
                },
                dismissButton = {
                    TextButton(onClick = { activeDateField = null }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = pickerState)
            }
        }
    }

    ScenarioSimulatorScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onNavigateToRoute = onNavigateToRoute,
        onOpenExternalUrl = { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
        onPickDate = { activeDateField = it },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ScenarioSimulatorScreen(
    state: ScenarioSimulatorUiState,
    snackbarHostState: SnackbarHostState,
    viewModel: ScenarioSimulatorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onPickDate: (ScenarioDateField) -> Unit,
    modifier: Modifier = Modifier
) {
    val workingDraft = state.workingDraft
    val activeTemplate = workingDraft?.parsedTemplateId ?: state.activeTemplateId ?: ScenarioTemplateId.JOB_LOSS_OR_INTERRUPTION
    val activeDefinition = state.bundle?.definitionFor(activeTemplate)
    val visibleDrafts = state.drafts.filterNot { it.isArchived }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.SCENARIO_SIMULATOR_SCREEN),
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
                Text("Scenario Simulator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = viewModel::refreshBundles) {
                    if (state.isRefreshingBundles) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh bundles")
                    }
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ScenarioWorkspaceCard(
                        title = activeDefinition?.title ?: activeTemplate.label,
                        summary = activeDefinition?.summary ?: activeTemplate.summary,
                        hint = activeDefinition?.editorHint
                    )
                }
                item { ScenarioBundleStatusCard(state) }

                if (!state.entitlement.isEnabled) {
                    item { ScenarioLockedCard(message = state.entitlement.message) }
                } else {
                    if (state.baselineChanged) {
                        item { ScenarioBaselineChangedCard() }
                    }
                    item {
                        ScenarioTemplateLibraryCard(
                            activeTemplate = activeTemplate,
                            onSelectTemplate = viewModel::selectTemplate
                        )
                    }
                    item {
                        ScenarioDraftLibraryCard(
                            drafts = visibleDrafts,
                            selectedDraftId = workingDraft?.id,
                            baselineChanged = state.baselineChanged,
                            onSelectDraft = viewModel::selectDraft
                        )
                    }
                    item {
                        ScenarioDraftEditorCard(
                            draft = workingDraft,
                            bundleHint = activeDefinition?.editorHint,
                            isSavingDraft = state.isSavingDraft,
                            isDeletingDraft = state.isDeletingDraft,
                            onPickDate = onPickDate,
                            viewModel = viewModel
                        )
                    }
                    state.currentResult?.let { result ->
                        item {
                            ScenarioResultCard(
                                result = result,
                                onActionClicked = { action ->
                                    handleScenarioAction(action, onNavigateToRoute, onOpenExternalUrl)
                                },
                                onCitationClicked = { citation -> onOpenExternalUrl(citation.url) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioWorkspaceCard(title: String, summary: String, hint: String?) {
    ScenarioSectionCard("Workspace") {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        hint?.takeIf { it.isNotBlank() }?.let {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ScenarioBundleStatusCard(state: ScenarioSimulatorUiState) {
    ScenarioSectionCard("Freshness") {
        val bundle = state.bundle
        Text(
            text = if (bundle == null) {
                "Scenario policy bundle has not been loaded yet."
            } else {
                "Bundle version ${bundle.version.ifBlank { "unversioned" }}"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (bundle == null) {
                "Refresh before relying on what-if guidance that depends on reviewed policy dates."
            } else {
                buildString {
                    append("Last reviewed ")
                    append(formatScenarioDate(bundle.lastReviewedAt))
                    append(". Generated ")
                    append(formatScenarioDate(bundle.generatedAt))
                    append(". ")
                    append(if (bundle.isStale(System.currentTimeMillis())) "This bundle is stale." else "This bundle is current.")
                }
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        bundle?.changelog?.take(2)?.forEach { entry ->
            Text(
                text = "• ${entry.title} (${entry.effectiveDate})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScenarioLockedCard(message: String) {
    ScenarioSectionCard("Limited Rollout") {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScenarioBaselineChangedCard() {
    ScenarioSectionCard("Baseline Changed") {
        Text(
            text = "Your saved baseline changed since this scenario last ran. Re-run the scenario before acting on the previous result.",
            color = MaterialTheme.colorScheme.error
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenarioTemplateLibraryCard(
    activeTemplate: ScenarioTemplateId,
    onSelectTemplate: (ScenarioTemplateId) -> Unit
) {
    ScenarioSectionCard("Templates") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScenarioTemplateId.entries.forEach { templateId ->
                FilterChip(
                    selected = activeTemplate == templateId,
                    onClick = { onSelectTemplate(templateId) },
                    label = { Text(templateId.label) }
                )
            }
        }
    }
}

@Composable
private fun ScenarioDraftLibraryCard(
    drafts: List<ScenarioDraft>,
    selectedDraftId: String?,
    baselineChanged: Boolean,
    onSelectDraft: (String) -> Unit
) {
    ScenarioSectionCard("Saved Drafts") {
        if (drafts.isEmpty()) {
            Text(
                text = "No saved drafts yet. Pick a template, adjust the assumptions, then save the draft.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            drafts.forEach { draft ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = draft.id.isNotBlank()) { onSelectDraft(draft.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (draft.id == selectedDraftId) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(draft.name.ifBlank { draft.parsedTemplateId.label }, fontWeight = FontWeight.SemiBold)
                            if (draft.pinned) {
                                Icon(Icons.Filled.PushPin, contentDescription = "Pinned", modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            text = draft.parsedTemplateId.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        draft.lastOutcome?.let { summary ->
                            Text(
                                text = "${summary.parsedOutcome.label} • ${summary.parsedConfidence.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (summary.headline.isNotBlank()) {
                                Text(summary.headline, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (draft.id == selectedDraftId && baselineChanged) {
                            Text(
                                text = "Baseline changed since last run.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioDraftEditorCard(
    draft: ScenarioDraft?,
    bundleHint: String?,
    isSavingDraft: Boolean,
    isDeletingDraft: Boolean,
    onPickDate: (ScenarioDateField) -> Unit,
    viewModel: ScenarioSimulatorViewModel
) {
    ScenarioSectionCard("Assumptions") {
        if (draft == null) {
            Text("Select a template to start.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@ScenarioSectionCard
        }
        OutlinedTextField(
            value = draft.name,
            onValueChange = viewModel::updateDraftName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Draft name") }
        )
        bundleHint?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ScenarioTemplateEditor(
            assumptions = draft.assumptions,
            onPickDate = onPickDate,
            viewModel = viewModel
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = viewModel::runSimulation, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Run")
            }
            Button(
                onClick = viewModel::saveCurrentDraft,
                modifier = Modifier.weight(1f),
                enabled = !isSavingDraft
            ) {
                if (isSavingDraft) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Save")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = viewModel::togglePinned, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PushPin, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (draft.pinned) "Unpin" else "Pin")
            }
            OutlinedButton(onClick = viewModel::archiveCurrentDraft, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Archive, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Archive")
            }
            OutlinedButton(
                onClick = viewModel::deleteCurrentDraft,
                modifier = Modifier.weight(1f),
                enabled = !isDeletingDraft && draft.id.isNotBlank()
            ) {
                if (isDeletingDraft) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenarioTemplateEditor(
    assumptions: Any,
    onPickDate: (ScenarioDateField) -> Unit,
    viewModel: ScenarioSimulatorViewModel
) {
    when (assumptions) {
        is JobLossScenarioAssumptions -> {
            DateSelectorField("Interruption start", assumptions.interruptionStartDate) {
                onPickDate(ScenarioDateField.JOB_LOSS_START)
            }
            DateSelectorField("Replacement start", assumptions.replacementStartDate) {
                onPickDate(ScenarioDateField.JOB_LOSS_REPLACEMENT)
            }
            OutlinedTextField(
                value = assumptions.replacementEmployerName,
                onValueChange = viewModel::updateJobLossReplacementEmployer,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Replacement employer") }
            )
            OutlinedTextField(
                value = assumptions.replacementHoursPerWeek?.toString().orEmpty(),
                onValueChange = viewModel::updateJobLossReplacementHours,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Replacement hours per week") }
            )
            TernaryQuestion("Any unauthorized employment?", assumptions.hasUnauthorizedEmployment, viewModel::updateJobLossUnauthorizedEmployment)
            TernaryQuestion("Any status violation?", assumptions.hasStatusViolation, viewModel::updateJobLossStatusViolation)
        }

        is EmployerChangeScenarioAssumptions -> {
            DateSelectorField("Employer change date", assumptions.changeDate) {
                onPickDate(ScenarioDateField.EMPLOYER_CHANGE)
            }
            OutlinedTextField(
                value = assumptions.newEmployerName,
                onValueChange = viewModel::updateEmployerChangeName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New employer") }
            )
            OutlinedTextField(
                value = assumptions.newEmployerHoursPerWeek?.toString().orEmpty(),
                onValueChange = viewModel::updateEmployerChangeHours,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New employer hours per week") }
            )
            TernaryQuestion("Employer uses E-Verify?", assumptions.newEmployerUsesEVerify, viewModel::updateEmployerChangeUsesEVerify)
            TernaryQuestion("Role related to degree?", assumptions.roleRelatedToDegree, viewModel::updateEmployerChangeRoleRelation)
            TernaryQuestion("New Form I-983 ready?", assumptions.hasNewI983, viewModel::updateEmployerChangeHasI983)
            TernaryQuestion("New I-20 issued?", assumptions.hasNewI20, viewModel::updateEmployerChangeHasI20)
        }

        is ReportingDeadlineScenarioAssumptions -> {
            OutlinedTextField(
                value = assumptions.reportingLabel,
                onValueChange = viewModel::updateReportingLabel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reporting item") }
            )
            DateSelectorField("Due date", assumptions.dueDate) {
                onPickDate(ScenarioDateField.REPORTING_DUE)
            }
            TernaryQuestion("Submitted late?", assumptions.submittedLate, viewModel::updateReportingSubmittedLate)
            TernaryQuestion("STEM validation?", assumptions.isStemValidation, viewModel::updateReportingStemValidation)
            TernaryQuestion("Final evaluation?", assumptions.isFinalEvaluation, viewModel::updateReportingFinalEvaluation)
        }

        is TravelScenarioAssumptions -> {
            OutlinedTextField(
                value = assumptions.tripInput.destinationCountry,
                onValueChange = { value -> viewModel.updateTravelTripInput { copy(destinationCountry = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Destination country") }
            )
            OutlinedTextField(
                value = assumptions.tripInput.passportIssuingCountry,
                onValueChange = { value -> viewModel.updateTravelTripInput { copy(passportIssuingCountry = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Passport issuing country") }
            )
            OutlinedTextField(
                value = assumptions.tripInput.visaClass,
                onValueChange = { value -> viewModel.updateTravelTripInput { copy(visaClass = value) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Visa class") }
            )
            DateSelectorField("Departure date", assumptions.tripInput.departureDate) {
                onPickDate(ScenarioDateField.TRAVEL_DEPARTURE)
            }
            DateSelectorField("Return date", assumptions.tripInput.plannedReturnDate) {
                onPickDate(ScenarioDateField.TRAVEL_RETURN)
            }
            Text("Travel posture", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TravelScenario.entries.forEach { scenario ->
                    FilterChip(
                        selected = assumptions.tripInput.travelScenario == scenario,
                        onClick = { viewModel.updateTravelTripInput { copy(travelScenario = scenario) } },
                        label = { Text(travelScenarioLabel(scenario)) }
                    )
                }
            }
            TernaryQuestion("Automatic visa revalidation only?", assumptions.tripInput.onlyCanadaMexicoAdjacentIslands) {
                viewModel.updateTravelTripInput { copy(onlyCanadaMexicoAdjacentIslands = it) }
            }
            TernaryQuestion("Need a new visa stamp?", assumptions.tripInput.needsNewVisa) {
                viewModel.updateTravelTripInput { copy(needsNewVisa = it) }
            }
            TernaryQuestion("Visa renewal outside residence?", assumptions.tripInput.visaRenewalOutsideResidence) {
                viewModel.updateTravelTripInput { copy(visaRenewalOutsideResidence = it) }
            }
            TernaryQuestion("Employment proof available?", assumptions.tripInput.hasEmploymentOrOfferProof) {
                viewModel.updateTravelTripInput { copy(hasEmploymentOrOfferProof = it) }
            }
            TernaryQuestion("Cap-gap active during trip?", assumptions.tripInput.capGapActive) {
                viewModel.updateTravelTripInput { copy(capGapActive = it) }
            }
            TernaryQuestion("Sensitive issue, RFE, or arrest history?", assumptions.tripInput.hasRfeStatusIssueOrArrestHistory) {
                viewModel.updateTravelTripInput { copy(hasRfeStatusIssueOrArrestHistory = it) }
            }
            TernaryQuestion("Original EAD in hand?", assumptions.tripInput.hasOriginalEadInHand) {
                viewModel.updateTravelTripInput { copy(hasOriginalEadInHand = it) }
            }
        }

        is H1bContinuityScenarioAssumptions -> {
            Text("Workflow stage", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                H1bWorkflowStage.entries.forEach { stage ->
                    FilterChip(
                        selected = assumptions.parsedWorkflowStage == stage,
                        onClick = { viewModel.updateH1bWorkflowStage(stage.wireValue) },
                        label = { Text(stage.label) }
                    )
                }
            }
            TernaryQuestion("Selected registration exists?", assumptions.selectedRegistration, viewModel::updateH1bSelectedRegistration)
            TernaryQuestion("Petition filed?", assumptions.filedPetition, viewModel::updateH1bFiledPetition)
            TernaryQuestion("Requested change of status?", assumptions.requestedChangeOfStatus, viewModel::updateH1bRequestedCos)
            TernaryQuestion("Requested consular processing?", assumptions.requestedConsularProcessing, viewModel::updateH1bRequestedConsular)
            TernaryQuestion("Travel planned during cap-gap?", assumptions.travelPlanned, viewModel::updateH1bTravelPlanned)
            TernaryQuestion("Receipt notice available?", assumptions.hasReceiptNotice, viewModel::updateH1bReceiptNotice)
        }

        is PendingStemExtensionScenarioAssumptions -> {
            DateSelectorField("STEM filing date", assumptions.filingDate) {
                onPickDate(ScenarioDateField.PENDING_STEM_FILING)
            }
            DateSelectorField("Current OPT end date", assumptions.optEndDate) {
                onPickDate(ScenarioDateField.PENDING_STEM_OPT_END)
            }
            TernaryQuestion("Receipt notice available?", assumptions.hasReceiptNotice, viewModel::updatePendingStemReceiptNotice)
            TernaryQuestion("Travel planned while pending?", assumptions.travelPlanned, viewModel::updatePendingStemTravelPlanned)
            TernaryQuestion("Employer change planned?", assumptions.employerChangePlanned, viewModel::updatePendingStemEmployerChange)
            TernaryQuestion("New Form I-983 ready?", assumptions.hasNewI983, viewModel::updatePendingStemHasI983)
            TernaryQuestion("New I-20 issued?", assumptions.hasNewI20, viewModel::updatePendingStemHasI20)
        }
    }
}
@Composable
private fun ScenarioResultCard(
    result: ScenarioSimulationResult,
    onActionClicked: (ScenarioAction) -> Unit,
    onCitationClicked: (ScenarioCitation) -> Unit
) {
    ScenarioSectionCard("Result") {
        Text(result.headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(result.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = outcomeColor(result.outcome).copy(alpha = 0.18f)
        ) {
            Text(
                text = "${result.outcome.label} • ${result.confidence.label}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = outcomeColor(result.outcome),
                fontWeight = FontWeight.SemiBold
            )
        }
        if (result.dependencyWarnings.isNotEmpty()) {
            Text("Warnings", style = MaterialTheme.typography.labelLarge)
            result.dependencyWarnings.forEach { warning ->
                Text("• $warning", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (result.timeline.isNotEmpty()) {
            Text("Timeline", style = MaterialTheme.typography.labelLarge)
            result.timeline.forEach { event ->
                Text("• ${event.title}: ${event.detail}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (result.impactCards.isNotEmpty()) {
            Text("Impact Cards", style = MaterialTheme.typography.labelLarge)
            result.impactCards.forEach { card ->
                ScenarioImpactCardView(card, onActionClicked)
            }
        }
        if (result.nextActions.isNotEmpty()) {
            Text("Next Actions", style = MaterialTheme.typography.labelLarge)
            result.nextActions.forEach { action ->
                OutlinedButton(onClick = { onActionClicked(action) }, modifier = Modifier.fillMaxWidth()) {
                    Text(action.label)
                }
            }
        }
        if (result.citations.isNotEmpty()) {
            Text("Sources", style = MaterialTheme.typography.labelLarge)
            result.citations.forEach { citation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCitationClicked(citation) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(citation.label, fontWeight = FontWeight.Medium)
                        Text(citation.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = buildString {
                                citation.effectiveDate?.takeIf { it.isNotBlank() }?.let { append("Effective $it. ") }
                                citation.lastReviewedDate?.takeIf { it.isNotBlank() }?.let { append("Reviewed $it.") }
                            }.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open source", modifier = Modifier.size(18.dp))
                }
            }
        }
        if (result.whatThisDoesNotMean.isNotBlank()) {
            Text("What this does not mean", style = MaterialTheme.typography.labelLarge)
            Text(result.whatThisDoesNotMean, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScenarioSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private fun handleScenarioAction(
    action: ScenarioAction,
    onNavigateToRoute: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    when {
        !action.route.isNullOrBlank() -> onNavigateToRoute(action.route)
        !action.externalUrl.isNullOrBlank() -> onOpenExternalUrl(action.externalUrl)
    }
}

private fun formatScenarioDate(value: Long?): String {
    if (value == null || value <= 0L) return "Not set"
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(Date(value))
}

@Composable
private fun ScenarioImpactCardView(
    card: ScenarioImpactCard,
    onActionClicked: (ScenarioAction) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = outcomeColor(card.outcome).copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${card.group.label}: ${card.title}", fontWeight = FontWeight.SemiBold)
            Text(card.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            card.dueDate?.let { dueDate ->
                Text("Due ${formatScenarioDate(dueDate)}", style = MaterialTheme.typography.bodySmall)
            }
            card.action?.let { action ->
                OutlinedButton(onClick = { onActionClicked(action) }) {
                    Text(action.label)
                }
            }
        }
    }
}

@Composable
private fun DateSelectorField(
    label: String,
    value: Long?,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${formatScenarioDate(value)}")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TernaryQuestion(
    label: String,
    value: Boolean?,
    onValueChanged: (Boolean?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = value == true, onClick = { onValueChanged(true) }, label = { Text("Yes") })
            FilterChip(selected = value == false, onClick = { onValueChanged(false) }, label = { Text("No") })
            FilterChip(selected = value == null, onClick = { onValueChanged(null) }, label = { Text("Unknown") })
        }
    }
}

private fun selectedDateForField(draft: ScenarioDraft?, field: ScenarioDateField?): Long? {
    if (draft == null || field == null) return null
    return when (val assumptions = draft.assumptions) {
        is JobLossScenarioAssumptions -> when (field) {
            ScenarioDateField.JOB_LOSS_START -> assumptions.interruptionStartDate
            ScenarioDateField.JOB_LOSS_REPLACEMENT -> assumptions.replacementStartDate
            else -> null
        }

        is EmployerChangeScenarioAssumptions -> if (field == ScenarioDateField.EMPLOYER_CHANGE) assumptions.changeDate else null
        is ReportingDeadlineScenarioAssumptions -> if (field == ScenarioDateField.REPORTING_DUE) assumptions.dueDate else null
        is TravelScenarioAssumptions -> when (field) {
            ScenarioDateField.TRAVEL_DEPARTURE -> assumptions.tripInput.departureDate
            ScenarioDateField.TRAVEL_RETURN -> assumptions.tripInput.plannedReturnDate
            else -> null
        }

        is PendingStemExtensionScenarioAssumptions -> when (field) {
            ScenarioDateField.PENDING_STEM_FILING -> assumptions.filingDate
            ScenarioDateField.PENDING_STEM_OPT_END -> assumptions.optEndDate
            else -> null
        }

        else -> null
    }
}

private fun applySelectedDate(
    viewModel: ScenarioSimulatorViewModel,
    field: ScenarioDateField?,
    value: Long?
) {
    when (field) {
        ScenarioDateField.JOB_LOSS_START -> viewModel.updateJobLossStartDate(value)
        ScenarioDateField.JOB_LOSS_REPLACEMENT -> viewModel.updateJobLossReplacementStartDate(value)
        ScenarioDateField.EMPLOYER_CHANGE -> viewModel.updateEmployerChangeDate(value)
        ScenarioDateField.REPORTING_DUE -> viewModel.updateReportingDueDate(value)
        ScenarioDateField.TRAVEL_DEPARTURE -> viewModel.updateTravelTripInput { copy(departureDate = value) }
        ScenarioDateField.TRAVEL_RETURN -> viewModel.updateTravelTripInput { copy(plannedReturnDate = value) }
        ScenarioDateField.PENDING_STEM_FILING -> viewModel.updatePendingStemFilingDate(value)
        ScenarioDateField.PENDING_STEM_OPT_END -> viewModel.updatePendingStemOptEndDate(value)
        null -> Unit
    }
}

private fun outcomeColor(outcome: ScenarioOutcome) = when (outcome) {
    ScenarioOutcome.ON_TRACK -> androidx.compose.ui.graphics.Color(0xFF1B873F)
    ScenarioOutcome.ACTION_REQUIRED -> androidx.compose.ui.graphics.Color(0xFF986800)
    ScenarioOutcome.HIGH_RISK -> androidx.compose.ui.graphics.Color(0xFFB65C00)
    ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY -> androidx.compose.ui.graphics.Color(0xFFB3261E)
}

private fun travelScenarioLabel(value: TravelScenario): String = when (value) {
    TravelScenario.PENDING_INITIAL_OPT -> "Pending OPT"
    TravelScenario.APPROVED_POST_COMPLETION_OPT -> "Approved OPT"
    TravelScenario.PENDING_STEM_EXTENSION -> "Pending STEM"
    TravelScenario.APPROVED_STEM_OPT -> "Approved STEM"
    TravelScenario.GRACE_PERIOD -> "Grace period"
}
