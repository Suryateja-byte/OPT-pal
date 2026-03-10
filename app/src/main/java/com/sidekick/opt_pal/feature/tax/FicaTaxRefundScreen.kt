package com.sidekick.opt_pal.feature.tax

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sidekick.opt_pal.data.model.EmployerRefundOutcome
import com.sidekick.opt_pal.data.model.FicaEligibilityClassification
import com.sidekick.opt_pal.data.model.FicaRefundCase
import com.sidekick.opt_pal.data.model.FicaRefundPacket
import com.sidekick.opt_pal.data.model.W2ExtractionDraft
import com.sidekick.opt_pal.feature.vault.SecureUploadDialog
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.NumberFormat
import java.util.Locale

@Composable
fun FicaTaxRefundRoute(
    onNavigateBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FicaTaxRefundViewModel = viewModel(factory = FicaTaxRefundViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.onFileSelected(uri)
    }

    LaunchedEffect(Unit) {
        AnalyticsLogger.logScreenView("FicaTaxRefund")
    }

    LaunchedEffect(state.pendingOpenDocumentId) {
        val documentId = state.pendingOpenDocumentId ?: return@LaunchedEffect
        onOpenDocument(documentId)
        viewModel.openPendingDocumentHandled()
    }

    if (state.showSecurityDialog) {
        SecureUploadDialog(
            title = "Secure W-2 Upload",
            initialTag = "W-2",
            confirmLabel = "Upload",
            onDismiss = viewModel::dismissSecurityDialog,
            onConfirm = { tag, consent ->
                viewModel.confirmUpload(tag = tag, consent = consent, contentResolver = context.contentResolver)
            }
        )
    }

    FicaTaxRefundScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onPickW2 = { filePicker.launch("*/*") },
        onSelectExistingCase = viewModel::selectExistingCase,
        onBeginNewCaseSelection = viewModel::beginNewCaseSelection,
        onSelectW2Document = viewModel::onW2Selected,
        onUseSelectedW2 = viewModel::startCaseFromSelectedW2,
        onFirstUsStudentTaxYearChanged = viewModel::onFirstUsStudentTaxYearChanged,
        onAuthorizedEmploymentConfirmedChanged = viewModel::onAuthorizedEmploymentConfirmedChanged,
        onMaintainedStudentStatusChanged = viewModel::onMaintainedStudentStatusChanged,
        onNoResidencyStatusChangeChanged = viewModel::onNoResidencyStatusChangeChanged,
        onEvaluateEligibility = viewModel::evaluateEligibility,
        onGenerateEmployerPacket = viewModel::generateEmployerPacket,
        onEmployerOutcomeSelected = viewModel::updateEmployerOutcome,
        onFullSsnChanged = viewModel::onFullSsnChanged,
        onFullEmployerEinChanged = viewModel::onFullEmployerEinChanged,
        onMailingAddressChanged = viewModel::onMailingAddressChanged,
        onGenerateIrsPacket = viewModel::generateIrsPacket,
        onOpenPacket = onOpenDocument,
        onArchiveCase = viewModel::archiveSelectedCase,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier
    )
}

@Composable
internal fun FicaTaxRefundScreen(
    state: FicaTaxRefundUiState,
    onNavigateBack: () -> Unit,
    onPickW2: () -> Unit,
    onSelectExistingCase: (String) -> Unit,
    onBeginNewCaseSelection: () -> Unit,
    onSelectW2Document: (String) -> Unit,
    onUseSelectedW2: () -> Unit,
    onFirstUsStudentTaxYearChanged: (String) -> Unit,
    onAuthorizedEmploymentConfirmedChanged: (Boolean) -> Unit,
    onMaintainedStudentStatusChanged: (Boolean) -> Unit,
    onNoResidencyStatusChangeChanged: (Boolean) -> Unit,
    onEvaluateEligibility: () -> Unit,
    onGenerateEmployerPacket: () -> Unit,
    onEmployerOutcomeSelected: (EmployerRefundOutcome) -> Unit,
    onFullSsnChanged: (String) -> Unit,
    onFullEmployerEinChanged: (String) -> Unit,
    onMailingAddressChanged: (String) -> Unit,
    onGenerateIrsPacket: () -> Unit,
    onOpenPacket: (String) -> Unit,
    onArchiveCase: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
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
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { 40 }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item {
                            Column {
                                Text(
                                    text = "Tax",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Refund.",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        item { StepHeader(step = state.step) }
                        if (state.infoMessage != null || state.errorMessage != null) {
                            item {
                                MessageCard(
                                    infoMessage = state.infoMessage,
                                    errorMessage = state.errorMessage,
                                    onDismiss = onDismissMessage
                                )
                            }
                        }
                        if (state.isUploading) {
                            item { UploadProgressCard(progress = state.uploadProgress) }
                        }
                        item {
                            when (state.step) {
                                TaxRefundStep.W2_SOURCE -> SourceStep(
                                    state = state,
                                    onPickW2 = onPickW2,
                                    onSelectExistingCase = onSelectExistingCase,
                                    onSelectW2Document = onSelectW2Document,
                                    onUseSelectedW2 = onUseSelectedW2
                                )
                                TaxRefundStep.ELIGIBILITY -> EligibilityStep(
                                    state = state,
                                    onBeginNewCaseSelection = onBeginNewCaseSelection,
                                    onFirstUsStudentTaxYearChanged = onFirstUsStudentTaxYearChanged,
                                    onAuthorizedEmploymentConfirmedChanged = onAuthorizedEmploymentConfirmedChanged,
                                    onMaintainedStudentStatusChanged = onMaintainedStudentStatusChanged,
                                    onNoResidencyStatusChangeChanged = onNoResidencyStatusChangeChanged,
                                    onEvaluateEligibility = onEvaluateEligibility
                                )
                                TaxRefundStep.EMPLOYER_REFUND -> EmployerRefundStep(
                                    state = state,
                                    onBeginNewCaseSelection = onBeginNewCaseSelection,
                                    onGenerateEmployerPacket = onGenerateEmployerPacket,
                                    onEmployerOutcomeSelected = onEmployerOutcomeSelected,
                                    onOpenPacket = onOpenPacket
                                )
                                TaxRefundStep.IRS_PACKET -> IrsPacketStep(
                                    state = state,
                                    onBeginNewCaseSelection = onBeginNewCaseSelection,
                                    onFullSsnChanged = onFullSsnChanged,
                                    onFullEmployerEinChanged = onFullEmployerEinChanged,
                                    onMailingAddressChanged = onMailingAddressChanged,
                                    onGenerateIrsPacket = onGenerateIrsPacket
                                )
                                TaxRefundStep.COMPLETE -> CompleteStep(
                                    state = state,
                                    onBeginNewCaseSelection = onBeginNewCaseSelection,
                                    onOpenPacket = onOpenPacket,
                                    onArchiveCase = onArchiveCase
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
private fun StepHeader(step: TaxRefundStep) {
    val (stepNumber, label) = when (step) {
        TaxRefundStep.W2_SOURCE -> "Step 1" to "Choose or upload a W-2"
        TaxRefundStep.ELIGIBILITY -> "Step 2" to "Confirm eligibility facts"
        TaxRefundStep.EMPLOYER_REFUND -> "Step 3" to "Ask the employer first"
        TaxRefundStep.IRS_PACKET -> "Step 4" to "Prepare the IRS claim packet"
        TaxRefundStep.COMPLETE -> "Step 5" to "Review and close the case"
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stepNumber.uppercase(Locale.US),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SourceStep(
    state: FicaTaxRefundUiState,
    onPickW2: () -> Unit,
    onSelectExistingCase: (String) -> Unit,
    onSelectW2Document: (String) -> Unit,
    onUseSelectedW2: () -> Unit
) {
    Column(
        modifier = Modifier.testTag(UiTestTags.TAX_REFUND_SOURCE_STEP),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Start with one W-2 for one employer and one tax year. Upload and analyze a new W-2 or reuse one that is already processed in your secure vault.",
            style = MaterialTheme.typography.bodyLarge
        )

        if (state.shouldShowExistingCases) {
            Text("Resume a refund case", style = MaterialTheme.typography.titleMedium)
            state.cases.forEach { taxCase ->
                SelectableSurface(
                    title = "${taxCase.employerName.ifBlank { "Employer" }} • ${taxCase.taxYear}",
                    subtitle = taxCase.parsedStatus.wireValue.replace('_', ' '),
                    selected = state.selectedCaseId == taxCase.id,
                    onClick = { onSelectExistingCase(taxCase.id) }
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.TAX_REFUND_UPLOAD_BUTTON),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            onClick = onPickW2
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Upload a new W-2", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Use the secure upload flow. Only analyzed W-2s can continue into refund checking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Filled.UploadFile, contentDescription = null)
            }
        }

        Text("Use an analyzed W-2 from your vault", style = MaterialTheme.typography.titleMedium)
        if (state.availableW2Documents.isEmpty()) {
            Text(
                text = "No processed W-2 documents are available yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.availableW2Documents.forEach { document ->
                SelectableSurface(
                    title = document.displayName.ifBlank { document.fileName },
                    subtitle = buildString {
                        append(document.employerName.ifBlank { "Employer pending" })
                        document.taxYear?.let {
                            append(" • ")
                            append(it)
                        }
                    },
                    selected = state.selectedW2DocumentId == document.documentId,
                    onClick = { onSelectW2Document(document.documentId) }
                )
            }
        }

        Button(
            onClick = onUseSelectedW2,
            enabled = state.canUseSelectedW2,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.TAX_REFUND_USE_SELECTED_BUTTON)
        ) {
            if (state.isCreatingCase) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Continue with selected W-2")
            }
        }
    }
}

@Composable
private fun EligibilityStep(
    state: FicaTaxRefundUiState,
    onBeginNewCaseSelection: () -> Unit,
    onFirstUsStudentTaxYearChanged: (String) -> Unit,
    onAuthorizedEmploymentConfirmedChanged: (Boolean) -> Unit,
    onMaintainedStudentStatusChanged: (Boolean) -> Unit,
    onNoResidencyStatusChangeChanged: (Boolean) -> Unit,
    onEvaluateEligibility: () -> Unit
) {
    Column(
        modifier = Modifier.testTag(UiTestTags.TAX_REFUND_ELIGIBILITY_STEP),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TextButton(onClick = onBeginNewCaseSelection) {
            Text("Choose a different W-2")
        }
        W2SummaryCard(state.selectedW2Document, state.selectedCase)
        Text(
            text = "The app only auto-approves clear F-1 OPT/STEM cases. If the facts are mixed or incomplete, it will stop at guidance only.",
            style = MaterialTheme.typography.bodyLarge
        )
        OutlinedTextField(
            value = state.firstUsStudentTaxYearInput,
            onValueChange = onFirstUsStudentTaxYearChanged,
            label = { Text("First U.S. student tax year") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.TAX_REFUND_FIRST_YEAR_FIELD),
            singleLine = true
        )
        CheckboxRow(
            checked = state.userInputs.authorizedEmploymentConfirmed,
            title = "This W-2 reflects authorized F-1 / OPT / STEM OPT employment.",
            onCheckedChange = onAuthorizedEmploymentConfirmedChanged
        )
        CheckboxRow(
            checked = state.userInputs.maintainedStudentStatusForEntireTaxYear,
            title = "I maintained student status for the full tax year tied to this W-2.",
            onCheckedChange = onMaintainedStudentStatusChanged
        )
        CheckboxRow(
            checked = state.userInputs.noResidencyStatusChangeConfirmed,
            title = "There was no residency-status change, dual-status year, or green-card transition during that tax year.",
            onCheckedChange = onNoResidencyStatusChangeChanged
        )
        Button(
            onClick = onEvaluateEligibility,
            enabled = state.canEvaluateEligibility,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.TAX_REFUND_EVALUATE_BUTTON)
        ) {
            if (state.isEvaluatingEligibility) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Evaluate refund eligibility")
            }
        }
        state.eligibilityResult?.let { result ->
            EligibilityResultCard(result = result)
        }
    }
}

@Composable
private fun EmployerRefundStep(
    state: FicaTaxRefundUiState,
    onBeginNewCaseSelection: () -> Unit,
    onGenerateEmployerPacket: () -> Unit,
    onEmployerOutcomeSelected: (EmployerRefundOutcome) -> Unit,
    onOpenPacket: (String) -> Unit
) {
    Column(
        modifier = Modifier.testTag(UiTestTags.TAX_REFUND_EMPLOYER_STEP),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TextButton(onClick = onBeginNewCaseSelection) {
            Text("Start another W-2")
        }
        state.eligibilityResult?.let { result ->
            EligibilityResultCard(result = result)
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Employer-first remediation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ask the employer or payroll provider to refund the FICA withholding first. Only move to the IRS packet if the employer refuses or does not respond.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(
            onClick = onGenerateEmployerPacket,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.TAX_REFUND_EMPLOYER_PACKET_BUTTON),
            enabled = !state.isGeneratingEmployerPacket
        ) {
            if (state.isGeneratingEmployerPacket) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(if (state.employerPacket == null) "Generate employer packet" else "Regenerate employer packet")
            }
        }
        state.employerPacket?.let { packet ->
            PacketCard(packet = packet, onOpenPacket = onOpenPacket)
        }
        Text("Employer response", style = MaterialTheme.typography.titleMedium)
        EmployerRefundOutcome.entries
            .filterNot { it == EmployerRefundOutcome.UNKNOWN }
            .forEach { outcome ->
                SelectableSurface(
                    title = outcome.toDisplayLabel(),
                    subtitle = outcome.toDisplayHelp(),
                    selected = state.selectedCase?.parsedEmployerOutcome == outcome,
                    onClick = { onEmployerOutcomeSelected(outcome) }
                )
            }
    }
}

@Composable
private fun IrsPacketStep(
    state: FicaTaxRefundUiState,
    onBeginNewCaseSelection: () -> Unit,
    onFullSsnChanged: (String) -> Unit,
    onFullEmployerEinChanged: (String) -> Unit,
    onMailingAddressChanged: (String) -> Unit,
    onGenerateIrsPacket: () -> Unit
) {
    val selectedW2 = state.selectedW2Document
    Column(
        modifier = Modifier.testTag(UiTestTags.TAX_REFUND_IRS_STEP),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TextButton(onClick = onBeginNewCaseSelection) {
            Text("Start another W-2")
        }
        state.eligibilityResult?.let { result ->
            EligibilityResultCard(result = result)
        }
        WarningCard(
            warning = "Review the packet carefully before mailing. OPTPal prepares a reviewed packet, not official IRS form overlays."
        )
        OutlinedTextField(
            value = state.fullSsn,
            onValueChange = onFullSsnChanged,
            label = { Text("Full SSN") },
            supportingText = { Text("Only used to build the IRS packet. Not stored in document metadata.") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.TAX_REFUND_SSN_FIELD)
        )
        OutlinedTextField(
            value = state.fullEmployerEin,
            onValueChange = onFullEmployerEinChanged,
            label = { Text("Full employer EIN") },
            supportingText = {
                Text(
                    selectedW2?.employerEinMasked?.takeIf { it.isNotBlank() }
                        ?.let { "W-2 shows masked EIN: $it" }
                        ?: "Confirm the full employer EIN before generating the packet."
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.TAX_REFUND_EIN_FIELD)
        )
        OutlinedTextField(
            value = state.mailingAddress,
            onValueChange = onMailingAddressChanged,
            label = { Text("Current mailing address") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.TAX_REFUND_ADDRESS_FIELD),
            minLines = 3
        )
        Button(
            onClick = onGenerateIrsPacket,
            enabled = state.canGenerateIrsPacket,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.TAX_REFUND_IRS_PACKET_BUTTON)
        ) {
            if (state.isGeneratingIrsPacket) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Generate IRS claim packet")
            }
        }
    }
}

@Composable
private fun CompleteStep(
    state: FicaTaxRefundUiState,
    onBeginNewCaseSelection: () -> Unit,
    onOpenPacket: (String) -> Unit,
    onArchiveCase: () -> Unit
) {
    Column(
        modifier = Modifier.testTag(UiTestTags.TAX_REFUND_COMPLETE_STEP),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        state.eligibilityResult?.let { result ->
            EligibilityResultCard(result = result)
        }
        when (state.eligibilityResult?.parsedClassification) {
            FicaEligibilityClassification.ELIGIBLE -> {
                Text(
                    text = when (state.selectedCase?.parsedEmployerOutcome) {
                        EmployerRefundOutcome.REFUNDED ->
                            "Employer refund recorded. Keep the packet and payroll response for your records."
                        EmployerRefundOutcome.REFUSED,
                        EmployerRefundOutcome.NO_RESPONSE ->
                            "IRS packet ready. Review it before mailing with the required attachments."
                        EmployerRefundOutcome.PROMISED_CORRECTION ->
                            "Employer correction pending. Keep monitoring the refund until it is actually issued."
                        else ->
                            "This refund case is ready for the next action you selected."
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> {
                Text(
                    text = "The app stopped at guidance for this W-2. Review the issues above before taking action.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        state.employerPacket?.let { packet ->
            PacketCard(packet = packet, onOpenPacket = onOpenPacket)
        }
        state.irsPacket?.let { packet ->
            PacketCard(packet = packet, onOpenPacket = onOpenPacket)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBeginNewCaseSelection, modifier = Modifier.weight(1f)) {
                Text("Start another W-2")
            }
            TextButton(onClick = onArchiveCase, modifier = Modifier.weight(1f)) {
                Text("Archive case")
            }
        }
    }
}

@Composable
private fun SelectableSurface(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun W2SummaryCard(
    w2Document: W2ExtractionDraft?,
    taxCase: FicaRefundCase?
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "W-2 summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            DetailLine("Employer", w2Document?.employerName ?: taxCase?.employerName.orEmpty().ifBlank { "Pending" })
            DetailLine("Tax year", (w2Document?.taxYear ?: taxCase?.taxYear.takeIf { it != 0 })?.toString() ?: "Pending")
            DetailLine(
                "Employee",
                w2Document?.employeeName.orEmpty().ifBlank { "Review on the original W-2" }
            )
            DetailLine("Box 4 + Box 6", formatCurrency((w2Document?.socialSecurityTaxBox4 ?: 0.0) + (w2Document?.medicareTaxBox6 ?: 0.0)))
        }
    }
}

@Composable
private fun EligibilityResultCard(result: com.sidekick.opt_pal.data.model.FicaEligibilityResult) {
    val surfaceColor = when (result.parsedClassification) {
        FicaEligibilityClassification.ELIGIBLE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        FicaEligibilityClassification.NOT_APPLICABLE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        FicaEligibilityClassification.MANUAL_REVIEW_REQUIRED,
        FicaEligibilityClassification.OUT_OF_SCOPE -> MaterialTheme.colorScheme.errorContainer
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = surfaceColor
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = result.parsedClassification.toHeadline(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            result.refundAmount?.let {
                Text("Estimated refund amount: ${formatCurrency(it)}", style = MaterialTheme.typography.bodyLarge)
            }
            result.eligibilityReasons.forEach { reason ->
                Text("• $reason", style = MaterialTheme.typography.bodyMedium)
            }
            result.blockingIssues.forEach { issue ->
                Text("• $issue", style = MaterialTheme.typography.bodyMedium)
            }
            if (result.requiredAttachments.isNotEmpty()) {
                Text("Required attachments", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                result.requiredAttachments.forEach { attachment ->
                    Text("• $attachment", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (result.recommendedNextStep.isNotBlank()) {
                Text(result.recommendedNextStep, style = MaterialTheme.typography.bodyMedium)
            }
            if (result.statuteWarning.isNotBlank()) {
                WarningCard(result.statuteWarning)
            }
        }
    }
}

@Composable
private fun PacketCard(packet: FicaRefundPacket, onOpenPacket: (String) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (packet.kind == "irs") "IRS packet ready" else "Employer packet ready",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(packet.fileName, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onOpenPacket(packet.documentId) }) {
                Text("Open secure document")
            }
        }
    }
}

@Composable
private fun MessageCard(
    infoMessage: String?,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    val isError = !errorMessage.isNullOrBlank()
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = errorMessage ?: infoMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun UploadProgressCard(progress: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Uploading W-2 securely", style = MaterialTheme.typography.bodyMedium)
            Text("$progress%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    title: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WarningCard(warning: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = warning,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun EmployerRefundOutcome.toDisplayLabel(): String {
    return when (this) {
        EmployerRefundOutcome.REFUNDED -> "Employer refunded the withholding"
        EmployerRefundOutcome.PROMISED_CORRECTION -> "Employer promised a correction"
        EmployerRefundOutcome.REFUSED -> "Employer refused"
        EmployerRefundOutcome.NO_RESPONSE -> "No employer response"
        EmployerRefundOutcome.UNKNOWN -> "Unknown"
    }
}

private fun EmployerRefundOutcome.toDisplayHelp(): String {
    return when (this) {
        EmployerRefundOutcome.REFUNDED ->
            "Close the case once the payroll correction and refund are actually complete."
        EmployerRefundOutcome.PROMISED_CORRECTION ->
            "Keep waiting for the actual refund or corrected wage statement."
        EmployerRefundOutcome.REFUSED ->
            "This unlocks the IRS claim packet."
        EmployerRefundOutcome.NO_RESPONSE ->
            "This also unlocks the IRS claim packet."
        EmployerRefundOutcome.UNKNOWN -> ""
    }
}

private fun FicaEligibilityClassification.toHeadline(): String {
    return when (this) {
        FicaEligibilityClassification.ELIGIBLE -> "Clear refund path"
        FicaEligibilityClassification.NOT_APPLICABLE -> "No refund detected"
        FicaEligibilityClassification.MANUAL_REVIEW_REQUIRED -> "Manual review required"
        FicaEligibilityClassification.OUT_OF_SCOPE -> "Outside this version's scope"
    }
}

private fun formatCurrency(value: Double?): String {
    if (value == null) return "Pending"
    return NumberFormat.getCurrencyInstance(Locale.US).format(value)
}
