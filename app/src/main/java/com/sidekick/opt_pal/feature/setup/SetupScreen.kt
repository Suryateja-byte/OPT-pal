package com.sidekick.opt_pal.feature.setup

import androidx.annotation.VisibleForTesting
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.data.model.OnboardingDocumentCandidate
import com.sidekick.opt_pal.data.model.OnboardingField
import com.sidekick.opt_pal.data.model.OnboardingSource
import com.sidekick.opt_pal.data.model.OptType
import com.sidekick.opt_pal.feature.vault.SecureUploadDialog
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupRoute(
    onOpenLegal: () -> Unit,
    onScanDocument: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.onUploadFileSelected(uri)
    }

    LaunchedEffect(Unit) { AnalyticsLogger.logScreenView("Setup") }

    uiState.showDatePickerFor?.let { field ->
        val initialDate = when (field) {
            SetupDateField.START -> uiState.draft.optStartDate
            SetupDateField.END -> uiState.draft.optEndDate
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)
        DatePickerDialog(
            onDismissRequest = viewModel::onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = { datePickerState.selectedDateMillis?.let(viewModel::onDateSelected) }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDatePicker) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (uiState.showSecurityDialog) {
        SecureUploadDialog(
            title = "Upload for AI Onboarding",
            initialTag = "",
            confirmLabel = "Upload",
            onDismiss = viewModel::dismissSecurityDialog,
            onConfirm = { tag, consent ->
                viewModel.confirmDocumentUpload(
                    tag = tag,
                    consent = consent,
                    contentResolver = context.contentResolver
                )
            }
        )
    }

    SetupScreen(
        state = uiState,
        onOptTypeSelected = viewModel::onOptTypeSelected,
        onShowStartDatePicker = { viewModel.onShowDatePicker(SetupDateField.START) },
        onShowEndDatePicker = { viewModel.onShowDatePicker(SetupDateField.END) },
        onSave = viewModel::onSave,
        onOpenLegal = onOpenLegal,
        onScanDocument = {
            viewModel.onScanDocumentRequested()
            onScanDocument()
        },
        onUploadDocument = { filePicker.launch("*/*") },
        onShowVaultDocuments = viewModel::showVaultDocuments,
        onSkipManual = viewModel::skipToManualSetup,
        onDismissMessage = viewModel::dismissMessage,
        onDocumentCandidateToggled = viewModel::onDocumentCandidateToggled,
        onUseSelectedDocuments = viewModel::useSelectedDocuments,
        onSevisIdChanged = viewModel::onSevisIdChanged,
        onSchoolNameChanged = viewModel::onSchoolNameChanged,
        onCipCodeChanged = viewModel::onCipCodeChanged,
        onMajorNameChanged = viewModel::onMajorNameChanged,
        onBackToSource = viewModel::backToSource,
        modifier = modifier
    )
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun SetupScreen(
    state: SetupUiState,
    onOptTypeSelected: (OptType) -> Unit,
    onShowStartDatePicker: () -> Unit,
    onShowEndDatePicker: () -> Unit,
    onSave: () -> Unit,
    onOpenLegal: () -> Unit,
    onScanDocument: () -> Unit,
    onUploadDocument: () -> Unit,
    onShowVaultDocuments: () -> Unit,
    onSkipManual: () -> Unit,
    onDismissMessage: () -> Unit,
    onDocumentCandidateToggled: (OnboardingDocumentCandidate) -> Unit,
    onUseSelectedDocuments: () -> Unit,
    onSevisIdChanged: (String) -> Unit,
    onSchoolNameChanged: (String) -> Unit,
    onCipCodeChanged: (String) -> Unit,
    onMajorNameChanged: (String) -> Unit,
    onBackToSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) { isVisible = true }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(1000)) + slideInVertically(tween(1000, delayMillis = 100)) { 40 },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    SetupHeader(stage = state.stage)

                    when (state.stage) {
                        SetupStage.SOURCE -> SetupSourceSection(
                            state = state,
                            onScanDocument = onScanDocument,
                            onUploadDocument = onUploadDocument,
                            onShowVaultDocuments = onShowVaultDocuments,
                            onSkipManual = onSkipManual,
                            onDismissMessage = onDismissMessage,
                            onDocumentCandidateToggled = onDocumentCandidateToggled,
                            onUseSelectedDocuments = onUseSelectedDocuments
                        )
                        SetupStage.REVIEW -> SetupReviewSection(
                            state = state,
                            onOptTypeSelected = onOptTypeSelected,
                            onShowStartDatePicker = onShowStartDatePicker,
                            onShowEndDatePicker = onShowEndDatePicker,
                            onSave = onSave,
                            onOpenLegal = onOpenLegal,
                            onDismissMessage = onDismissMessage,
                            onSevisIdChanged = onSevisIdChanged,
                            onSchoolNameChanged = onSchoolNameChanged,
                            onCipCodeChanged = onCipCodeChanged,
                            onMajorNameChanged = onMajorNameChanged,
                            onBackToSource = onBackToSource
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupHeader(stage: SetupStage) {
    Column {
        Text(
            text = "Setup",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = if (stage == SetupStage.SOURCE) "Get started" else "Review",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (stage == SetupStage.SOURCE) "with documents first." else "your profile draft.",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SetupSourceSection(
    state: SetupUiState,
    onScanDocument: () -> Unit,
    onUploadDocument: () -> Unit,
    onShowVaultDocuments: () -> Unit,
    onSkipManual: () -> Unit,
    onDismissMessage: () -> Unit,
    onDocumentCandidateToggled: (OnboardingDocumentCandidate) -> Unit,
    onUseSelectedDocuments: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = "Scan or upload an I-20 or EAD to prefill your setup. You can skip and enter details manually at any time.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        SourceActionRow(
            title = "Scan document",
            subtitle = "Capture an I-20 or EAD with the camera.",
            icon = Icons.Filled.CameraAlt,
            onClick = onScanDocument,
            testTag = UiTestTags.SETUP_SCAN_BUTTON
        )
        SourceActionRow(
            title = "Upload file",
            subtitle = "Choose a PDF or image from this device.",
            icon = Icons.Filled.UploadFile,
            onClick = onUploadDocument,
            testTag = UiTestTags.SETUP_UPLOAD_BUTTON
        )
        SourceActionRow(
            title = "Use existing vault documents",
            subtitle = "Reuse analyzed I-20 and EAD files already in your secure vault.",
            icon = Icons.Filled.Description,
            onClick = onShowVaultDocuments,
            testTag = UiTestTags.SETUP_USE_VAULT_BUTTON
        )

        TextButton(
            onClick = onSkipManual,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.SETUP_SKIP_MANUAL_BUTTON)
        ) {
            Text("Skip and enter manually")
        }

        MessageBlock(
            infoMessage = state.infoMessage,
            errorMessage = state.errorMessage,
            onDismiss = onDismissMessage
        )

        state.uploadProgress?.let { progress ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.uploadLabel?.let { "Uploading $it" } ?: "Uploading document",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (state.processingDocuments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ANALYZING",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                state.processingDocuments.forEach { document ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(document.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = document.status.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (state.showVaultDocuments || state.eligibleDocuments.isNotEmpty()) {
            VaultSelectionSection(
                state = state,
                onDocumentCandidateToggled = onDocumentCandidateToggled,
                onUseSelectedDocuments = onUseSelectedDocuments
            )
        }
    }
}

@Composable
private fun SetupReviewSection(
    state: SetupUiState,
    onOptTypeSelected: (OptType) -> Unit,
    onShowStartDatePicker: () -> Unit,
    onShowEndDatePicker: () -> Unit,
    onSave: () -> Unit,
    onOpenLegal: () -> Unit,
    onDismissMessage: () -> Unit,
    onSevisIdChanged: (String) -> Unit,
    onSchoolNameChanged: (String) -> Unit,
    onCipCodeChanged: (String) -> Unit,
    onMajorNameChanged: (String) -> Unit,
    onBackToSource: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (state.draft.onboardingSource == OnboardingSource.DOCUMENT_AI) {
            TextButton(onClick = onBackToSource, modifier = Modifier.fillMaxWidth()) {
                Text("Back to document options")
            }
        }

        MessageBlock(
            infoMessage = state.infoMessage,
            errorMessage = state.errorMessage,
            onDismiss = onDismissMessage
        )

        Text(
            text = if (state.draft.onboardingSource == OnboardingSource.DOCUMENT_AI) {
                "Confirm the extracted fields before finishing setup."
            } else {
                "Manual setup stays available if you do not want to use document analysis."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Which OPT type?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MinimalSelectable(
                    modifier = Modifier
                        .weight(1f)
                        .testTag(UiTestTags.SETUP_INITIAL_CHIP),
                    text = "Initial (12 Mo)",
                    selected = state.draft.optType == OptType.INITIAL,
                    onClick = { onOptTypeSelected(OptType.INITIAL) }
                )
                MinimalSelectable(
                    modifier = Modifier
                        .weight(1f)
                        .testTag(UiTestTags.SETUP_STEM_CHIP),
                    text = "STEM (24 Mo)",
                    selected = state.draft.optType == OptType.STEM,
                    onClick = { onOptTypeSelected(OptType.STEM) }
                )
            }
            FieldSourceLabel(state, OnboardingField.OPT_TYPE)
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Start date?", style = MaterialTheme.typography.titleMedium)
            MinimalDateTrigger(
                dateMillis = state.draft.optStartDate,
                onClick = onShowStartDatePicker,
                testTag = UiTestTags.SETUP_DATE_FIELD
            )
            FieldSourceLabel(state, OnboardingField.OPT_START_DATE)
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("End date? (optional)", style = MaterialTheme.typography.titleMedium)
            MinimalDateTrigger(
                dateMillis = state.draft.optEndDate,
                onClick = onShowEndDatePicker,
                testTag = UiTestTags.SETUP_END_DATE_FIELD
            )
            FieldSourceLabel(state, OnboardingField.OPT_END_DATE)
        }

        SetupTextField(
            value = state.draft.sevisId,
            onValueChange = onSevisIdChanged,
            label = "SEVIS ID",
            testTag = UiTestTags.SETUP_SEVIS_FIELD
        )
        FieldSourceLabel(state, OnboardingField.SEVIS_ID)

        SetupTextField(
            value = state.draft.schoolName,
            onValueChange = onSchoolNameChanged,
            label = "School name",
            testTag = UiTestTags.SETUP_SCHOOL_FIELD
        )
        FieldSourceLabel(state, OnboardingField.SCHOOL_NAME)

        SetupTextField(
            value = state.draft.cipCode,
            onValueChange = onCipCodeChanged,
            label = "CIP code",
            testTag = UiTestTags.SETUP_CIP_FIELD
        )
        FieldSourceLabel(state, OnboardingField.CIP_CODE)

        SetupTextField(
            value = state.draft.majorName,
            onValueChange = onMajorNameChanged,
            label = "Major name",
            testTag = UiTestTags.SETUP_MAJOR_FIELD
        )
        FieldSourceLabel(state, OnboardingField.MAJOR_NAME)

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag(UiTestTags.SETUP_SAVE_BUTTON),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            enabled = !state.isSaving
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Complete", style = MaterialTheme.typography.titleMedium)
            }
        }

        Text(
            text = "Not legal advice. Tap for details.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenLegal() }
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun SourceActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VaultSelectionSection(
    state: SetupUiState,
    onDocumentCandidateToggled: (OnboardingDocumentCandidate) -> Unit,
    onUseSelectedDocuments: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "ANALYZED DOCUMENTS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (state.eligibleDocuments.isEmpty()) {
            Text(
                text = "No analyzed I-20 or EAD documents are ready yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.eligibleDocuments.forEach { candidate ->
                val selected = candidate.documentId in state.selectedDocumentIds
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = selected,
                            onValueChange = { onDocumentCandidateToggled(candidate) }
                        ),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = selected, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(candidate.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = when (candidate.documentType) {
                                    com.sidekick.opt_pal.data.model.OnboardingDocumentType.I20 -> "I-20"
                                    com.sidekick.opt_pal.data.model.OnboardingDocumentType.EAD -> "EAD"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onUseSelectedDocuments,
                enabled = state.selectedDocumentIds.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.SETUP_USE_SELECTED_DOCS_BUTTON)
            ) {
                Text("Review selected documents")
            }
        }
    }
}

@Composable
private fun SetupTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
private fun MessageBlock(
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

@Composable
private fun FieldSourceLabel(
    state: SetupUiState,
    field: OnboardingField
) {
    val label = state.draft.fieldSources[field] ?: return
    Text(
        text = "Source: $label",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MinimalSelectable(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        label = "color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "text"
    )

    Surface(
        modifier = modifier.height(64.dp),
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MinimalDateTrigger(
    dateMillis: Long?,
    onClick: () -> Unit,
    testTag: String
) {
    val text = dateMillis?.let {
        SimpleDateFormat("MMMM d, yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(it))
    } ?: "Select Date"

    val isPlaceholder = dateMillis == null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag(testTag),
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Outlined.CalendarToday,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
