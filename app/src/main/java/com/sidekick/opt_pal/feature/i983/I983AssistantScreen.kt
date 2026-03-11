package com.sidekick.opt_pal.feature.i983

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.sidekick.opt_pal.core.documents.SecureDocumentShareHelper
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.I983Assessment
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983WorkflowType
import com.sidekick.opt_pal.di.AppModule
import com.sidekick.opt_pal.ui.UiTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun I983AssistantRoute(
    draftId: String?,
    obligationId: String?,
    employmentId: String?,
    workflowType: I983WorkflowType?,
    onNavigateBack: () -> Unit,
    onOpenReporting: () -> Unit,
    onOpenVisaPathwayPlanner: () -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: I983AssistantViewModel = viewModel(
        factory = I983AssistantViewModel.provideFactory(draftId, obligationId, employmentId, workflowType)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val shareHelper = remember { SecureDocumentShareHelper(context, AppModule.documentRepository) }
    var pendingDateField by remember { mutableStateOf<I983DateField?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val tag = "Signed I-983 ${state.draft?.id?.take(8).orEmpty()}"
            AppModule.secureDocumentIntakeUseCase.uploadDocument(
                fileUri = uri,
                userTag = tag,
                consent = com.sidekick.opt_pal.data.model.DocumentUploadConsent(com.sidekick.opt_pal.data.model.DocumentProcessingMode.STORAGE_ONLY),
                contentResolver = context.contentResolver
            ).onSuccess {
                val linked = state.documents.firstOrNull { it.userTag == tag } ?: AppModule.documentRepository
                    .getDocuments(AppModule.userSessionProvider.currentUserId ?: return@onSuccess)
                    .collectIndexedFirst(tag)
                linked?.let { viewModel.linkSignedDocument(it.id) }
            }
        }
    }

    LaunchedEffect(Unit) { AnalyticsLogger.logI983AssistantOpened() }
    LaunchedEffect(state.errorMessage) { state.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.dismissMessage() } }
    LaunchedEffect(state.infoMessage) { state.infoMessage?.let { snackbarHostState.showSnackbar(it); viewModel.dismissMessage() } }

    pendingDateField?.let { field ->
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateFor(state.draft, field))
        DatePickerDialog(
            onDismissRequest = { pendingDateField = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDateSelected(field, datePickerState.selectedDateMillis)
                    pendingDateField = null
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDateField = null }) { Text("Cancel") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    I983AssistantScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onSelectDraft = viewModel::selectDraft,
        onSelectWorkflow = viewModel::selectWorkflowType,
        onSelectEmployment = viewModel::selectEmployment,
        onSelectObligation = viewModel::selectObligation,
        onStartDraft = viewModel::startDraft,
        onSaveDraft = viewModel::saveDraft,
        onGenerateNarrative = viewModel::generateNarrativeDraft,
        onExportPdf = viewModel::exportOfficialPdf,
        onShareExport = {
            state.draft?.latestExportDocumentId?.let { documentId ->
                state.documents.firstOrNull { it.id == documentId }?.let { document ->
                    coroutineScope.launch { shareHelper.share(document) }
                }
            }
        },
        onOpenExport = {
            state.draft?.latestExportDocumentId?.takeIf(String::isNotBlank)?.let(onOpenDocument)
        },
        onUploadSigned = { filePicker.launch("*/*") },
        onOpenReporting = onOpenReporting,
        onOpenVisaPathwayPlanner = onOpenVisaPathwayPlanner,
        onRefreshPolicy = viewModel::refreshPolicyBundle,
        onTextFieldChanged = viewModel::onTextFieldChanged,
        onPickDate = { pendingDateField = it },
        onToggleDocument = viewModel::toggleSelectedDocument,
        onOpenSource = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        modifier = modifier
    )
}

@Composable
fun I983AssistantScreen(
    state: I983AssistantUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSelectDraft: (String?) -> Unit,
    onSelectWorkflow: (I983WorkflowType) -> Unit,
    onSelectEmployment: (String) -> Unit,
    onSelectObligation: (String) -> Unit,
    onStartDraft: () -> Unit,
    onSaveDraft: () -> Unit,
    onGenerateNarrative: () -> Unit,
    onExportPdf: () -> Unit,
    onShareExport: () -> Unit,
    onOpenExport: () -> Unit,
    onUploadSigned: () -> Unit,
    onOpenReporting: () -> Unit,
    onOpenVisaPathwayPlanner: () -> Unit,
    onRefreshPolicy: () -> Unit,
    onTextFieldChanged: (I983TextField, String) -> Unit,
    onPickDate: (I983DateField) -> Unit,
    onToggleDocument: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val draft = state.draft
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).testTag(UiTestTags.I983_SCREEN),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Text("I-983 Assistant", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
                item { Text(state.entitlement.message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (!state.entitlement.isEnabled) {
                    item { Surface(Modifier.fillMaxWidth().testTag(UiTestTags.I983_LOCKED_PREVIEW), color = MaterialTheme.colorScheme.surfaceVariant) { Text("Limited rollout", Modifier.padding(16.dp)) } }
                } else {
                    item {
                        WorkflowCard(state, onSelectDraft, onSelectWorkflow, onSelectEmployment, onSelectObligation, onStartDraft, onRefreshPolicy)
                    }
                    if (draft == null) {
                        if (state.drafts.isNotEmpty()) item { Text("Select a saved draft or start a new workflow.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        item { AssessmentCard(state, onOpenReporting, onOpenVisaPathwayPlanner, onGenerateNarrative, onSaveDraft, onExportPdf, onOpenExport, onShareExport, onUploadSigned) }
                        item { DocumentContextCard(state, onToggleDocument) }
                        item {
                            TextSection("Student details", listOf(
                                "Student name" to (I983TextField.STUDENT_NAME to draft.studentSection.studentName),
                                "Student email" to (I983TextField.STUDENT_EMAIL to draft.studentSection.studentEmailAddress),
                                "School recommending STEM OPT" to (I983TextField.SCHOOL_RECOMMENDING to draft.studentSection.schoolRecommendingStemOpt),
                                "School where degree was earned" to (I983TextField.SCHOOL_DEGREE_EARNED to draft.studentSection.schoolWhereDegreeWasEarned),
                                "SEVIS school code" to (I983TextField.SEVIS_SCHOOL_CODE to draft.studentSection.sevisSchoolCode),
                                "DSO contact" to (I983TextField.DSO_CONTACT to draft.studentSection.dsoNameAndContact),
                                "SEVIS ID" to (I983TextField.STUDENT_SEVIS_ID to draft.studentSection.studentSevisId),
                                "Major and CIP code" to (I983TextField.QUALIFYING_MAJOR_CIP to draft.studentSection.qualifyingMajorAndCipCode),
                                "Degree level" to (I983TextField.DEGREE_LEVEL to draft.studentSection.degreeLevel),
                                "EAD number" to (I983TextField.EMPLOYER_AUTH_NUMBER to draft.studentSection.employmentAuthorizationNumber)
                            ), onTextFieldChanged)
                        }
                        item { DateCard("Student dates", listOf("Requested start" to (I983DateField.REQUESTED_START to draft.studentSection.requestedStartDate), "Requested end" to (I983DateField.REQUESTED_END to draft.studentSection.requestedEndDate), "Degree awarded" to (I983DateField.DEGREE_AWARDED to draft.studentSection.degreeAwardedDate)), onPickDate) }
                        item {
                            TextSection("Employer details", listOf(
                                "Employer name" to (I983TextField.EMPLOYER_NAME to draft.employerSection.employerName),
                                "Street address" to (I983TextField.EMPLOYER_STREET to draft.employerSection.streetAddress),
                                "Suite" to (I983TextField.EMPLOYER_SUITE to draft.employerSection.suite),
                                "Website" to (I983TextField.EMPLOYER_WEBSITE to draft.employerSection.employerWebsiteUrl),
                                "City" to (I983TextField.EMPLOYER_CITY to draft.employerSection.city),
                                "State" to (I983TextField.EMPLOYER_STATE to draft.employerSection.state),
                                "ZIP code" to (I983TextField.EMPLOYER_ZIP to draft.employerSection.zipCode),
                                "Employer EIN" to (I983TextField.EMPLOYER_EIN to draft.employerSection.employerEin),
                                "Full-time employees in U.S." to (I983TextField.FULL_TIME_EMPLOYEES to draft.employerSection.fullTimeEmployeesInUs),
                                "NAICS code" to (I983TextField.NAICS_CODE to draft.employerSection.naicsCode),
                                "Hours per week" to (I983TextField.HOURS_PER_WEEK to (draft.employerSection.hoursPerWeek?.toString() ?: "")),
                                "Compensation" to (I983TextField.SALARY_AMOUNT to draft.employerSection.salaryAmountAndFrequency),
                                "Employer official name and title" to (I983TextField.EMPLOYER_OFFICIAL_NAME_AND_TITLE to draft.employerSection.employerOfficialNameAndTitle),
                                "Employing organization" to (I983TextField.EMPLOYING_ORGANIZATION_NAME to draft.employerSection.employingOrganizationName)
                            ), onTextFieldChanged)
                        }
                        item { DateCard("Employer dates", listOf("Employment start" to (I983DateField.EMPLOYMENT_START to draft.employerSection.employmentStartDate)), onPickDate) }
                        item {
                            TextSection("Training plan", listOf(
                                "Site name" to (I983TextField.SITE_NAME to draft.trainingPlanSection.siteName),
                                "Site address" to (I983TextField.SITE_ADDRESS to draft.trainingPlanSection.siteAddress),
                                "Official name" to (I983TextField.OFFICIAL_NAME to draft.trainingPlanSection.officialName),
                                "Official title" to (I983TextField.OFFICIAL_TITLE to draft.trainingPlanSection.officialTitle),
                                "Official email" to (I983TextField.OFFICIAL_EMAIL to draft.trainingPlanSection.officialEmail),
                                "Official phone" to (I983TextField.OFFICIAL_PHONE to draft.trainingPlanSection.officialPhoneNumber),
                                "Student role" to (I983TextField.STUDENT_ROLE to draft.trainingPlanSection.studentRole),
                                "Goals and objectives" to (I983TextField.GOALS_OBJECTIVES to draft.trainingPlanSection.goalsAndObjectives),
                                "Employer oversight" to (I983TextField.EMPLOYER_OVERSIGHT to draft.trainingPlanSection.employerOversight),
                                "Measures and assessments" to (I983TextField.MEASURES_ASSESSMENTS to draft.trainingPlanSection.measuresAndAssessments),
                                "Additional remarks" to (I983TextField.ADDITIONAL_REMARKS to draft.trainingPlanSection.additionalRemarks)
                            ), onTextFieldChanged, multiLine = true)
                        }
                        if (draft.parsedWorkflowType == I983WorkflowType.ANNUAL_EVALUATION || draft.parsedWorkflowType == I983WorkflowType.FINAL_EVALUATION) {
                            item {
                                TextSection("Evaluation", listOf(
                                    "Annual evaluation text" to (I983TextField.ANNUAL_EVALUATION_TEXT to draft.evaluationSection.annualEvaluationText),
                                    "Final evaluation text" to (I983TextField.FINAL_EVALUATION_TEXT to draft.evaluationSection.finalEvaluationText)
                                ), onTextFieldChanged, multiLine = true)
                            }
                            item {
                                DateCard("Evaluation dates", listOf(
                                    "Annual from" to (I983DateField.ANNUAL_FROM to draft.evaluationSection.annualEvaluationFromDate),
                                    "Annual to" to (I983DateField.ANNUAL_TO to draft.evaluationSection.annualEvaluationToDate),
                                    "Final from" to (I983DateField.FINAL_FROM to draft.evaluationSection.finalEvaluationFromDate),
                                    "Final to" to (I983DateField.FINAL_TO to draft.evaluationSection.finalEvaluationToDate)
                                ), onPickDate)
                            }
                        }
                        state.assessment?.let { assessment ->
                            item { IssueCard(assessment) }
                            item { CitationCard(assessment.citations.map { it.label to it.url }, onOpenSource) }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun WorkflowCard(state: I983AssistantUiState, onSelectDraft: (String?) -> Unit, onSelectWorkflow: (I983WorkflowType) -> Unit, onSelectEmployment: (String) -> Unit, onSelectObligation: (String) -> Unit, onStartDraft: () -> Unit, onRefreshPolicy: () -> Unit) { Surface { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Workflow", fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag(UiTestTags.I983_WORKFLOW_SELECTOR))
    I983WorkflowType.entries.forEach { type -> FilterChip(selected = state.selectedWorkflowType == type, onClick = { onSelectWorkflow(type) }, label = { Text(type.wireValue.replace('_', ' ').replaceFirstChar { it.uppercase() }) }) }
    state.employments.forEach { employment -> AssistChip(onClick = { onSelectEmployment(employment.id) }, label = { Text(if (state.selectedEmploymentId == employment.id) "Employment: ${employment.employerName} (selected)" else "Employment: ${employment.employerName}") }) }
    state.obligations.take(3).forEach { obligation -> AssistChip(onClick = { onSelectObligation(obligation.id) }, label = { Text(if (state.selectedObligationId == obligation.id) "Obligation selected" else obligation.description) }) }
    state.drafts.take(3).forEach { draft -> TextButton(onClick = { onSelectDraft(draft.id) }) { Text("Open ${draft.parsedWorkflowType.wireValue.replace('_', ' ')} draft") } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onStartDraft, enabled = state.policyBundle != null) { Text("Start draft") }; TextButton(onClick = onRefreshPolicy) { Text("Refresh policy") } }
} } }

@Composable private fun AssessmentCard(state: I983AssistantUiState, onOpenReporting: () -> Unit, onOpenVisaPathwayPlanner: () -> Unit, onGenerateNarrative: () -> Unit, onSaveDraft: () -> Unit, onExportPdf: () -> Unit, onOpenExport: () -> Unit, onShareExport: () -> Unit, onUploadSigned: () -> Unit) { val assessment = state.assessment; Surface(Modifier.fillMaxWidth().testTag(UiTestTags.I983_REVIEW_EXPORT_SECTION)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(assessment?.headline ?: "Drafting", fontWeight = FontWeight.SemiBold)
    Text(assessment?.summary ?: "Fill the tracked sections and export the official form when ready.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onSaveDraft, enabled = !state.isSaving) { Text("Save") }; Button(onClick = onGenerateNarrative, enabled = !state.isGeneratingNarrative) { Text("Generate draft") }; Button(onClick = onExportPdf, enabled = !state.isExporting) { Text("Export PDF") } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = onOpenExport, enabled = !state.draft?.latestExportDocumentId.isNullOrBlank()) { Text("Open PDF") }; TextButton(onClick = onShareExport, enabled = !state.draft?.latestExportDocumentId.isNullOrBlank()) { Text("Share PDF") }; TextButton(onClick = onUploadSigned) { Text("Upload signed") }; TextButton(onClick = onOpenReporting) { Text("Open Reporting") }; TextButton(onClick = onOpenVisaPathwayPlanner) { Text("Open Planner") } }
} } }

@Composable private fun DocumentContextCard(state: I983AssistantUiState, onToggleDocument: (String) -> Unit) { val selected = state.draft?.selectedDocumentIds.orEmpty().toSet(); Surface { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Document context", fontWeight = FontWeight.SemiBold)
    state.documents.take(6).forEach { document -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = selected.contains(document.id), onCheckedChange = { onToggleDocument(document.id) }); Text(document.userTag.ifBlank { document.fileName }) } }
    if (state.sourceLabels.isNotEmpty()) Text("Autofill sources: ${state.sourceLabels.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurfaceVariant)
} } }

@Composable private fun TextSection(title: String, fields: List<Pair<String, Pair<I983TextField, String>>>, onTextFieldChanged: (I983TextField, String) -> Unit, multiLine: Boolean = false) { Surface { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, fontWeight = FontWeight.SemiBold); fields.forEach { (label, config) -> OutlinedTextField(value = config.second, onValueChange = { onTextFieldChanged(config.first, it) }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, minLines = if (multiLine && label.length > 10) 3 else 1) } } } }

@Composable private fun DateCard(title: String, fields: List<Pair<String, Pair<I983DateField, Long?>>>, onPickDate: (I983DateField) -> Unit) { Surface { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontWeight = FontWeight.SemiBold); fields.forEach { (label, config) -> TextButton(onClick = { onPickDate(config.first) }) { Text("$label: ${config.second?.let(::formatDate) ?: "Pick date"}") } } } } }

@Composable private fun IssueCard(assessment: I983Assessment) { Surface { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Validation", fontWeight = FontWeight.SemiBold); assessment.issues.forEach { issue -> Text("${issue.severity.name}: ${issue.message}") } } } }

@Composable private fun CitationCard(citations: List<Pair<String, String>>, onOpenSource: (String) -> Unit) { Surface { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Sources", fontWeight = FontWeight.SemiBold); citations.forEach { (label, url) -> TextButton(onClick = { onOpenSource(url) }) { Text(label) } } } } }

private fun formatDate(value: Long): String { val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US); formatter.timeZone = TimeZone.getTimeZone("UTC"); return formatter.format(Date(value)) }
private fun selectedDateFor(draft: I983Draft?, field: I983DateField): Long? = when (field) {
    I983DateField.REQUESTED_START -> draft?.studentSection?.requestedStartDate
    I983DateField.REQUESTED_END -> draft?.studentSection?.requestedEndDate
    I983DateField.DEGREE_AWARDED -> draft?.studentSection?.degreeAwardedDate
    I983DateField.EMPLOYMENT_START -> draft?.employerSection?.employmentStartDate
    I983DateField.ANNUAL_FROM -> draft?.evaluationSection?.annualEvaluationFromDate
    I983DateField.ANNUAL_TO -> draft?.evaluationSection?.annualEvaluationToDate
    I983DateField.FINAL_FROM -> draft?.evaluationSection?.finalEvaluationFromDate
    I983DateField.FINAL_TO -> draft?.evaluationSection?.finalEvaluationToDate
}

private suspend fun kotlinx.coroutines.flow.Flow<List<DocumentMetadata>>.collectIndexedFirst(tag: String): DocumentMetadata? {
    return withTimeoutOrNull(12_000L) { first { docs -> docs.any { it.userTag == tag } }.firstOrNull { it.userTag == tag } }
}
