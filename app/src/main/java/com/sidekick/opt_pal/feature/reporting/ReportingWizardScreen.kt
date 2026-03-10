package com.sidekick.opt_pal.feature.reporting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.ReportingChecklistItem
import com.sidekick.opt_pal.data.model.ReportingDraftClassification
import com.sidekick.opt_pal.data.model.ReportingWizardEventType
import com.sidekick.opt_pal.data.model.ReportingWizardOptRegime
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MAJOR_RELATIONSHIP_URL =
    "https://studyinthestates.dhs.gov/students/direct-relationship-between-employment-and-students-major-area-of-study"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportingWizardRoute(
    wizardId: String?,
    obligationId: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportingWizardViewModel = viewModel(
        factory = ReportingWizardViewModel.provideFactory(wizardId, obligationId)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    state.eventDate?.let { eventDate ->
        if (state.showEventDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = eventDate)
            DatePickerDialog(
                onDismissRequest = viewModel::dismissEventDatePicker,
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let(viewModel::onEventDateSelected)
                    }) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissEventDatePicker) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }

    ReportingWizardScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onEventTypeSelected = viewModel::onEventTypeSelected,
        onEmploymentSelected = viewModel::onEmploymentSelected,
        onShowEventDatePicker = viewModel::showEventDatePicker,
        onStartWizard = viewModel::startWizard,
        onEmployerNameChanged = viewModel::onEmployerNameChanged,
        onJobTitleChanged = viewModel::onJobTitleChanged,
        onMajorNameChanged = viewModel::onMajorNameChanged,
        onWorksiteAddressChanged = viewModel::onWorksiteAddressChanged,
        onSiteNameChanged = viewModel::onSiteNameChanged,
        onSupervisorNameChanged = viewModel::onSupervisorNameChanged,
        onSupervisorEmailChanged = viewModel::onSupervisorEmailChanged,
        onSupervisorPhoneChanged = viewModel::onSupervisorPhoneChanged,
        onJobDutiesChanged = viewModel::onJobDutiesChanged,
        onToolsAndSkillsChanged = viewModel::onToolsAndSkillsChanged,
        onUserExplanationChanged = viewModel::onUserExplanationChanged,
        onContinueToReview = viewModel::continueToReview,
        onBackToDetails = viewModel::backToDetails,
        onDocumentToggled = viewModel::onDocumentToggled,
        onGenerateDraft = viewModel::generateDraft,
        onEditedDraftChanged = viewModel::onEditedDraftChanged,
        onCopyDraft = {
            clipboardManager.setText(AnnotatedString(state.editedDraft))
            viewModel.markDraftCopied()
        },
        onCompleteWizard = viewModel::completeWizard,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier
    )
}

@Composable
internal fun ReportingWizardScreen(
    state: ReportingWizardUiState,
    onNavigateBack: () -> Unit,
    onEventTypeSelected: (ReportingWizardEventType) -> Unit,
    onEmploymentSelected: (String) -> Unit,
    onShowEventDatePicker: () -> Unit,
    onStartWizard: () -> Unit,
    onEmployerNameChanged: (String) -> Unit,
    onJobTitleChanged: (String) -> Unit,
    onMajorNameChanged: (String) -> Unit,
    onWorksiteAddressChanged: (String) -> Unit,
    onSiteNameChanged: (String) -> Unit,
    onSupervisorNameChanged: (String) -> Unit,
    onSupervisorEmailChanged: (String) -> Unit,
    onSupervisorPhoneChanged: (String) -> Unit,
    onJobDutiesChanged: (String) -> Unit,
    onToolsAndSkillsChanged: (String) -> Unit,
    onUserExplanationChanged: (String) -> Unit,
    onContinueToReview: () -> Unit,
    onBackToDetails: () -> Unit,
    onDocumentToggled: (String) -> Unit,
    onGenerateDraft: () -> Unit,
    onEditedDraftChanged: (String) -> Unit,
    onCopyDraft: () -> Unit,
    onCompleteWizard: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var isVisible by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) { isVisible = true }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.shapes.small
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading || state.isPreparingWizard) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 40 }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column {
                            Text(
                                text = "SEVP",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Wizard.",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        WizardMessageBlock(
                            infoMessage = state.infoMessage,
                            errorMessage = state.errorMessage,
                            onDismiss = onDismissMessage
                        )

                        when (state.step) {
                            ReportingWizardStep.EVENT -> {
                                EventStep(
                                    state = state,
                                    onEventTypeSelected = onEventTypeSelected,
                                    onEmploymentSelected = onEmploymentSelected,
                                    onShowEventDatePicker = onShowEventDatePicker,
                                    onStartWizard = onStartWizard
                                )
                            }
                            ReportingWizardStep.DETAILS -> {
                                DetailsStep(
                                    state = state,
                                    onEmployerNameChanged = onEmployerNameChanged,
                                    onJobTitleChanged = onJobTitleChanged,
                                    onMajorNameChanged = onMajorNameChanged,
                                    onWorksiteAddressChanged = onWorksiteAddressChanged,
                                    onSiteNameChanged = onSiteNameChanged,
                                    onSupervisorNameChanged = onSupervisorNameChanged,
                                    onSupervisorEmailChanged = onSupervisorEmailChanged,
                                    onSupervisorPhoneChanged = onSupervisorPhoneChanged,
                                    onJobDutiesChanged = onJobDutiesChanged,
                                    onToolsAndSkillsChanged = onToolsAndSkillsChanged,
                                    onUserExplanationChanged = onUserExplanationChanged,
                                    onContinueToReview = onContinueToReview
                                )
                            }
                            ReportingWizardStep.REVIEW -> {
                                ReviewStep(
                                    state = state,
                                    onBackToDetails = onBackToDetails,
                                    onDocumentToggled = onDocumentToggled,
                                    onGenerateDraft = onGenerateDraft,
                                    onEditedDraftChanged = onEditedDraftChanged,
                                    onCopyDraft = onCopyDraft,
                                    onCompleteWizard = onCompleteWizard,
                                    onOpenUri = uriHandler::openUri
                                )
                            }
                            ReportingWizardStep.COMPLETE -> {
                                CompletionStep(onNavigateBack = onNavigateBack)
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventStep(
    state: ReportingWizardUiState,
    onEventTypeSelected: (ReportingWizardEventType) -> Unit,
    onEmploymentSelected: (String) -> Unit,
    onShowEventDatePicker: () -> Unit,
    onStartWizard: () -> Unit
) {
    Column(
        modifier = Modifier.testTag(UiTestTags.REPORTING_WIZARD_EVENT_STEP),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Choose the employment change you need to report. This wizard stays focused on employment-related SEVP reporting only.",
            style = MaterialTheme.typography.bodyLarge
        )

        ReportingWizardEventType.entries.forEach { eventType ->
            WizardSelectionRow(
                title = eventType.toDisplayName(),
                selected = state.selectedEventType == eventType,
                onClick = { onEventTypeSelected(eventType) }
            )
        }

        Text("Employment record", style = MaterialTheme.typography.titleMedium)
        if (state.employments.isEmpty()) {
            Text(
                text = "Add an employment record first, then come back to launch the wizard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.employments.forEach { employment ->
                WizardSelectionRow(
                    title = employment.employerName,
                    subtitle = employment.jobTitle,
                    selected = state.selectedEmploymentId == employment.id,
                    onClick = { onEmploymentSelected(employment.id) }
                )
            }
        }

        Text("Event date", style = MaterialTheme.typography.titleMedium)
        Surface(
            onClick = onShowEventDatePicker,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(state.eventDate?.let(::formatUtcDate) ?: "Select event date")
                Icon(imageVector = Icons.Filled.CalendarToday, contentDescription = null)
            }
        }

        Button(
            onClick = onStartWizard,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.REPORTING_WIZARD_EVENT_CONTINUE),
            enabled = state.selectedEmploymentId != null && state.eventDate != null
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun DetailsStep(
    state: ReportingWizardUiState,
    onEmployerNameChanged: (String) -> Unit,
    onJobTitleChanged: (String) -> Unit,
    onMajorNameChanged: (String) -> Unit,
    onWorksiteAddressChanged: (String) -> Unit,
    onSiteNameChanged: (String) -> Unit,
    onSupervisorNameChanged: (String) -> Unit,
    onSupervisorEmailChanged: (String) -> Unit,
    onSupervisorPhoneChanged: (String) -> Unit,
    onJobDutiesChanged: (String) -> Unit,
    onToolsAndSkillsChanged: (String) -> Unit,
    onUserExplanationChanged: (String) -> Unit,
    onContinueToReview: () -> Unit
) {
    Column(
        modifier = Modifier.testTag(UiTestTags.REPORTING_WIZARD_DETAILS_STEP),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Add the details the checklist and draft need. The app prepares the language; you still review and submit it yourself.",
            style = MaterialTheme.typography.bodyLarge
        )

        state.complianceWarning?.let { warning ->
            WarningSurface(warning)
        }

        WizardTextField(
            value = state.userInputs.employerName,
            onValueChange = onEmployerNameChanged,
            label = "Employer name"
        )
        WizardTextField(
            value = state.userInputs.jobTitle,
            onValueChange = onJobTitleChanged,
            label = "Job title"
        )
        WizardTextField(
            value = state.userInputs.majorName,
            onValueChange = onMajorNameChanged,
            label = "Major name",
            testTag = UiTestTags.REPORTING_WIZARD_MAJOR_FIELD
        )
        WizardTextField(
            value = state.userInputs.worksiteAddress,
            onValueChange = onWorksiteAddressChanged,
            label = "Worksite address"
        )
        WizardTextField(
            value = state.userInputs.siteName,
            onValueChange = onSiteNameChanged,
            label = "Site name (optional)"
        )

        if (state.parsedOptRegime == ReportingWizardOptRegime.STEM) {
            WizardTextField(
                value = state.userInputs.supervisorName,
                onValueChange = onSupervisorNameChanged,
                label = "Supervisor name"
            )
            WizardTextField(
                value = state.userInputs.supervisorEmail,
                onValueChange = onSupervisorEmailChanged,
                label = "Supervisor email"
            )
            WizardTextField(
                value = state.userInputs.supervisorPhone,
                onValueChange = onSupervisorPhoneChanged,
                label = "Supervisor phone"
            )
        }

        WizardTextField(
            value = state.userInputs.jobDuties,
            onValueChange = onJobDutiesChanged,
            label = "Job duties",
            minLines = 4,
            testTag = UiTestTags.REPORTING_WIZARD_DUTIES_FIELD
        )
        WizardTextField(
            value = state.userInputs.toolsAndSkills,
            onValueChange = onToolsAndSkillsChanged,
            label = "Tools and skills used (optional)",
            minLines = 3
        )
        WizardTextField(
            value = state.userInputs.userExplanationNotes,
            onValueChange = onUserExplanationChanged,
            label = "Extra explanation notes (optional)",
            minLines = 3
        )

        Button(onClick = onContinueToReview, modifier = Modifier.fillMaxWidth()) {
            Text("Review checklist")
        }
    }
}

@Composable
private fun ReviewStep(
    state: ReportingWizardUiState,
    onBackToDetails: () -> Unit,
    onDocumentToggled: (String) -> Unit,
    onGenerateDraft: () -> Unit,
    onEditedDraftChanged: (String) -> Unit,
    onCopyDraft: () -> Unit,
    onCompleteWizard: () -> Unit,
    onOpenUri: (String) -> Unit
) {
    Column(
        modifier = Modifier.testTag(UiTestTags.REPORTING_WIZARD_REVIEW_STEP),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TextButton(onClick = onBackToDetails, modifier = Modifier.fillMaxWidth()) {
            Text("Back to details")
        }

        Text(
            text = "Use the official checklist below before you report externally. The draft is optional and must be reviewed before use.",
            style = MaterialTheme.typography.bodyLarge
        )

        state.complianceWarning?.let { warning ->
            WarningSurface(warning)
        }

        state.wizard?.generatedChecklist.orEmpty().forEach { item ->
            ChecklistCard(item = item, onOpenUri = onOpenUri)
        }

        Text("Analyzed document context", style = MaterialTheme.typography.titleMedium)
        if (state.availableDocuments.isEmpty()) {
            Text(
                text = "No analyzed documents are available. You can still generate a draft from your typed details.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.availableDocuments.forEach { document ->
                WizardSelectionRow(
                    title = document.userTag.ifBlank { document.fileName },
                    subtitle = document.documentType.ifBlank { document.fileName },
                    selected = document.id in state.selectedDocumentIds,
                    onClick = { onDocumentToggled(document.id) }
                )
            }
        }

        Button(
            onClick = onGenerateDraft,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.REPORTING_WIZARD_GENERATE),
            enabled = !state.isGeneratingDraft
        ) {
            if (state.isGeneratingDraft) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(if (state.generatedDraft == null) "Generate relationship draft" else "Regenerate draft")
            }
        }

        state.generatedDraft?.let { draft ->
            if (draft.parsedClassification == ReportingDraftClassification.CONSULT_DSO_ATTORNEY) {
                WarningSurface(
                    warning = draft.warnings.firstOrNull()
                        ?: "This explanation needs review by your DSO or an immigration attorney before you rely on it."
                )
            }
            if (draft.missingInputs.isNotEmpty()) {
                WarningSurface(
                    warning = "Missing inputs: ${draft.missingInputs.joinToString(", ")}"
                )
            }
            draft.whyThisDraftFits.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (draft.warnings.isNotEmpty()) {
                draft.warnings.forEach { warning ->
                    WarningSurface(warning)
                }
            }
        }

        TextButton(onClick = { onOpenUri(MAJOR_RELATIONSHIP_URL) }) {
            Text("Open the official major-relationship guidance")
        }

        WizardTextField(
            value = state.editedDraft,
            onValueChange = onEditedDraftChanged,
            label = "Editable draft assistance",
            minLines = 6,
            testTag = UiTestTags.REPORTING_WIZARD_DRAFT_FIELD
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCopyDraft,
                modifier = Modifier.weight(1f),
                enabled = state.editedDraft.isNotBlank()
            ) {
                Text("Copy draft")
            }
            Button(
                onClick = onCompleteWizard,
                modifier = Modifier
                    .weight(1f)
                    .testTag(UiTestTags.REPORTING_WIZARD_COMPLETE),
                enabled = !state.isCompleting
            ) {
                if (state.isCompleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Mark done")
                }
            }
        }
    }
}

@Composable
private fun CompletionStep(onNavigateBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Reporting prepared and marked complete.",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "This only marks the task done in the app. Make sure you already completed the report through your school or the SEVP Portal, depending on your OPT stage.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

@Composable
private fun ChecklistCard(item: ReportingChecklistItem, onOpenUri: (String) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Owner: ${item.actor.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { onOpenUri(item.sourceUrl) }) {
                Text(item.sourceLabel)
            }
        }
    }
}

@Composable
private fun WizardSelectionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WizardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int = 1,
    testTag: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (testTag != null) base.testTag(testTag) else base },
        minLines = minLines
    )
}

@Composable
private fun WarningSurface(warning: String) {
    Surface(
        modifier = Modifier.testTag(UiTestTags.REPORTING_WIZARD_WARNING),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = warning,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun WizardMessageBlock(
    infoMessage: String?,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    when {
        !errorMessage.isNullOrBlank() -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
        !infoMessage.isNullOrBlank() -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(infoMessage, color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

private fun ReportingWizardEventType.toDisplayName(): String {
    return when (this) {
        ReportingWizardEventType.NEW_EMPLOYER -> "New employer"
        ReportingWizardEventType.EMPLOYMENT_ENDED -> "Employment ended"
        ReportingWizardEventType.MATERIAL_CHANGE -> "Material change"
    }
}

private fun formatUtcDate(millis: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(millis))
}
