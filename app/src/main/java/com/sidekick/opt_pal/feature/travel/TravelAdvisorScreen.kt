package com.sidekick.opt_pal.feature.travel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.data.model.TravelChecklistItem
import com.sidekick.opt_pal.data.model.TravelChecklistStatus
import com.sidekick.opt_pal.data.model.TravelOutcome
import com.sidekick.opt_pal.data.model.TravelScenario
import com.sidekick.opt_pal.data.model.TravelSourceCitation
import com.sidekick.opt_pal.di.AppModule
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelAdvisorRoute(
    onNavigateBack: () -> Unit,
    onUploadMissingDocument: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TravelAdvisorViewModel = viewModel(factory = TravelAdvisorViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var activeDateField by remember { mutableStateOf<TravelDateField?>(null) }

    LaunchedEffect(Unit) {
        AnalyticsLogger.logTravelAdvisorOpened()
    }

    if (activeDateField != null) {
        TravelDatePickerDialog(
            field = activeDateField!!,
            state = state,
            onDismiss = { activeDateField = null },
            onConfirm = { field, millis ->
                viewModel.onDateSelected(field, millis)
                activeDateField = null
            }
        )
    }

    TravelAdvisorScreen(
        state = state,
        onNavigateBack = onNavigateBack,
        onPickDate = { activeDateField = it },
        onDestinationCountryChanged = viewModel::onDestinationCountryChanged,
        onPassportIssuingCountryChanged = viewModel::onPassportIssuingCountryChanged,
        onVisaClassChanged = viewModel::onVisaClassChanged,
        onScenarioSelected = viewModel::onScenarioSelected,
        onOnlyContiguousTravelChanged = viewModel::onOnlyContiguousTravelChanged,
        onNeedsNewVisaChanged = viewModel::onNeedsNewVisaChanged,
        onVisaRenewalOutsideResidenceChanged = viewModel::onVisaRenewalOutsideResidenceChanged,
        onEmploymentProofChanged = viewModel::onEmploymentProofChanged,
        onCapGapChanged = viewModel::onCapGapChanged,
        onSensitiveIssueChanged = viewModel::onSensitiveIssueChanged,
        onHasOriginalEadChanged = viewModel::onHasOriginalEadChanged,
        onRunAssessment = viewModel::runAssessment,
        onRefreshPolicy = viewModel::refreshPolicyBundle,
        onUploadMissingDocument = onUploadMissingDocument,
        onOpenSource = { citation ->
            if (citation.url.isNotBlank()) {
                AnalyticsLogger.logTravelSourceOpened(citation.id)
                uriHandler.openUri(citation.url)
            }
        },
        onRequestI20Signature = {
            launchEmailComposer(
                context = context,
                subject = "Request for updated I-20 travel signature",
                body = "Hello,\n\nI am preparing for international travel and need an updated travel signature on my current I-20.\n\nThank you."
            )
        },
        onContactDsoAttorney = {
            launchEmailComposer(
                context = context,
                subject = "Travel risk review request",
                body = "Hello,\n\nI need guidance on an upcoming F-1 OPT travel scenario. Please review the facts and let me know the safest path before I travel.\n\nThank you."
            )
        },
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier
    )
}

@Composable
fun TravelAdvisorScreen(
    state: TravelAdvisorUiState,
    onNavigateBack: () -> Unit,
    onPickDate: (TravelDateField) -> Unit,
    onDestinationCountryChanged: (String) -> Unit,
    onPassportIssuingCountryChanged: (String) -> Unit,
    onVisaClassChanged: (String) -> Unit,
    onScenarioSelected: (TravelScenario) -> Unit,
    onOnlyContiguousTravelChanged: (Boolean) -> Unit,
    onNeedsNewVisaChanged: (Boolean) -> Unit,
    onVisaRenewalOutsideResidenceChanged: (Boolean) -> Unit,
    onEmploymentProofChanged: (Boolean) -> Unit,
    onCapGapChanged: (Boolean) -> Unit,
    onSensitiveIssueChanged: (Boolean) -> Unit,
    onHasOriginalEadChanged: (Boolean) -> Unit,
    onRunAssessment: () -> Unit,
    onRefreshPolicy: () -> Unit,
    onUploadMissingDocument: () -> Unit,
    onOpenSource: (TravelSourceCitation) -> Unit,
    onRequestI20Signature: () -> Unit,
    onContactDsoAttorney: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = onRefreshPolicy) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh policy")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Travel Risk",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Advisor.",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.errorMessage != null || state.infoMessage != null) {
                item {
                    MessageCard(
                        errorMessage = state.errorMessage,
                        infoMessage = state.infoMessage,
                        onDismiss = onDismissMessage
                    )
                }
            }

            item {
                PolicyStatusCard(
                    state = state,
                    onRefreshPolicy = onRefreshPolicy,
                    onOpenWaitTimes = {
                        state.policyBundle?.sources
                            ?.firstOrNull { source -> source.id == "dos_wait_times" }
                            ?.let(onOpenSource)
                    }
                )
            }

            if (!state.entitlement.isEnabled) {
                item {
                    LockedPreviewCard(
                        message = state.entitlement.message,
                        onContactDsoAttorney = onContactDsoAttorney
                    )
                }
            } else {
                item {
                    EvidenceSourcesCard(
                        sourceLabels = state.sourceLabels,
                        onUploadMissingDocument = onUploadMissingDocument
                    )
                }
                item {
                    TripPlannerCard(
                        state = state,
                        onPickDate = onPickDate,
                        onDestinationCountryChanged = onDestinationCountryChanged,
                        onPassportIssuingCountryChanged = onPassportIssuingCountryChanged,
                        onVisaClassChanged = onVisaClassChanged,
                        onScenarioSelected = onScenarioSelected,
                        onOnlyContiguousTravelChanged = onOnlyContiguousTravelChanged,
                        onNeedsNewVisaChanged = onNeedsNewVisaChanged,
                        onVisaRenewalOutsideResidenceChanged = onVisaRenewalOutsideResidenceChanged,
                        onEmploymentProofChanged = onEmploymentProofChanged,
                        onCapGapChanged = onCapGapChanged,
                        onSensitiveIssueChanged = onSensitiveIssueChanged,
                        onHasOriginalEadChanged = onHasOriginalEadChanged
                    )
                }
                item {
                    ActionCard(
                        canRunAssessment = state.canRunAssessment,
                        isRefreshingPolicy = state.isRefreshingPolicy,
                        onRunAssessment = onRunAssessment,
                        onUploadMissingDocument = onUploadMissingDocument,
                        onRequestI20Signature = onRequestI20Signature,
                        onContactDsoAttorney = onContactDsoAttorney
                    )
                }
            }

            state.assessment?.let { assessment ->
                item {
                    OutcomeCard(assessment.outcome, assessment.headline, assessment.summary)
                }
                items(assessment.checklistItems, key = { it.ruleId.name }) { item ->
                    ChecklistCard(
                        item = item,
                        onOpenSource = onOpenSource
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelDatePickerDialog(
    field: TravelDateField,
    state: TravelAdvisorUiState,
    onDismiss: () -> Unit,
    onConfirm: (TravelDateField, Long?) -> Unit
) {
    val selectedDate = when (field) {
        TravelDateField.DEPARTURE -> state.tripInput.departureDate
        TravelDateField.RETURN -> state.tripInput.plannedReturnDate
        TravelDateField.PASSPORT_EXPIRATION -> state.tripInput.passportExpirationDate
        TravelDateField.VISA_EXPIRATION -> state.tripInput.visaExpirationDate
        TravelDateField.I20_SIGNATURE -> state.tripInput.i20TravelSignatureDate
        TravelDateField.EAD_EXPIRATION -> state.tripInput.eadExpirationDate
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(field, datePickerState.selectedDateMillis) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun MessageCard(
    errorMessage: String?,
    infoMessage: String?,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (errorMessage != null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = errorMessage ?: infoMessage.orEmpty(),
                color = if (errorMessage != null) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun PolicyStatusCard(
    state: TravelAdvisorUiState,
    onRefreshPolicy: () -> Unit,
    onOpenWaitTimes: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Policy Bundle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (state.policyBundle != null) {
                    "Version ${state.policyBundle.version} reviewed ${formatDate(state.policyBundle.lastReviewedAt)}"
                } else {
                    "Loading current travel guidance..."
                }
            )
            if (state.policyBundle?.isStale(System.currentTimeMillis()) == true) {
                Text(
                    text = "This bundle is stale. OPTPal will not show a full green GO until it is refreshed.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefreshPolicy) {
                    Text(if (state.isRefreshingPolicy) "Refreshing..." else "Refresh policy")
                }
                OutlinedButton(onClick = onOpenWaitTimes) {
                    Text("Visa wait times")
                }
            }
        }
    }
}

@Composable
private fun LockedPreviewCard(
    message: String,
    onContactDsoAttorney: () -> Unit
) {
    Card(
        modifier = Modifier.testTag(UiTestTags.TRAVEL_LOCKED_PREVIEW),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null)
                Text(
                    text = "Beta access required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(text = message)
            OutlinedButton(onClick = onContactDsoAttorney) {
                Text("Contact DSO/attorney")
            }
        }
    }
}

@Composable
private fun EvidenceSourcesCard(
    sourceLabels: List<String>,
    onUploadMissingDocument: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Auto-filled evidence",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (sourceLabels.isEmpty()) {
                Text("No analyzed passport, visa, I-20, or EAD documents were found yet.")
            } else {
                sourceLabels.forEach { label ->
                    Text("- $label")
                }
            }
            OutlinedButton(
                onClick = onUploadMissingDocument,
                modifier = Modifier.testTag(UiTestTags.TRAVEL_UPLOAD_MISSING_BUTTON)
            ) {
                Text("Upload missing document")
            }
        }
    }
}

@Composable
private fun TripPlannerCard(
    state: TravelAdvisorUiState,
    onPickDate: (TravelDateField) -> Unit,
    onDestinationCountryChanged: (String) -> Unit,
    onPassportIssuingCountryChanged: (String) -> Unit,
    onVisaClassChanged: (String) -> Unit,
    onScenarioSelected: (TravelScenario) -> Unit,
    onOnlyContiguousTravelChanged: (Boolean) -> Unit,
    onNeedsNewVisaChanged: (Boolean) -> Unit,
    onVisaRenewalOutsideResidenceChanged: (Boolean) -> Unit,
    onEmploymentProofChanged: (Boolean) -> Unit,
    onCapGapChanged: (Boolean) -> Unit,
    onSensitiveIssueChanged: (Boolean) -> Unit,
    onHasOriginalEadChanged: (Boolean) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Trip facts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            DateField(
                label = "Departure date",
                value = state.tripInput.departureDate,
                onClick = { onPickDate(TravelDateField.DEPARTURE) }
            )
            DateField(
                label = "Planned return date",
                value = state.tripInput.plannedReturnDate,
                onClick = { onPickDate(TravelDateField.RETURN) }
            )
            OutlinedTextField(
                value = state.tripInput.destinationCountry,
                onValueChange = onDestinationCountryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.TRAVEL_DESTINATION_FIELD),
                label = { Text("Destination country") }
            )
            BooleanChoiceRow(
                label = "Trip limited to Canada, Mexico, or adjacent islands",
                value = state.tripInput.onlyCanadaMexicoAdjacentIslands,
                onSelected = onOnlyContiguousTravelChanged
            )
            BooleanChoiceRow(
                label = "Will you need a new F-1 visa?",
                value = state.tripInput.needsNewVisa,
                onSelected = onNeedsNewVisaChanged
            )
            BooleanChoiceRow(
                label = "Any visa renewal outside your nationality/residence country?",
                value = state.tripInput.visaRenewalOutsideResidence,
                onSelected = onVisaRenewalOutsideResidenceChanged
            )
            ScenarioChoiceGroup(
                selected = state.tripInput.travelScenario,
                onSelected = onScenarioSelected
            )
            BooleanChoiceRow(
                label = "Do you have employment or offer proof?",
                value = state.tripInput.hasEmploymentOrOfferProof,
                onSelected = onEmploymentProofChanged
            )
            BooleanChoiceRow(
                label = "Is cap-gap active for this trip?",
                value = state.tripInput.capGapActive,
                onSelected = onCapGapChanged
            )
            BooleanChoiceRow(
                label = "Any RFE, status issue, or arrest history involved?",
                value = state.tripInput.hasRfeStatusIssueOrArrestHistory,
                onSelected = onSensitiveIssueChanged
            )
            BooleanChoiceRow(
                label = "Do you have the original EAD in hand?",
                value = state.tripInput.hasOriginalEadInHand,
                onSelected = onHasOriginalEadChanged
            )
            OutlinedTextField(
                value = state.tripInput.passportIssuingCountry,
                onValueChange = onPassportIssuingCountryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Passport issuing country") }
            )
            DateField(
                label = "Passport expiration date",
                value = state.tripInput.passportExpirationDate,
                onClick = { onPickDate(TravelDateField.PASSPORT_EXPIRATION) }
            )
            OutlinedTextField(
                value = state.tripInput.visaClass,
                onValueChange = onVisaClassChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Visa class") }
            )
            DateField(
                label = "Visa expiration date",
                value = state.tripInput.visaExpirationDate,
                onClick = { onPickDate(TravelDateField.VISA_EXPIRATION) }
            )
            DateField(
                label = "I-20 travel signature date",
                value = state.tripInput.i20TravelSignatureDate,
                onClick = { onPickDate(TravelDateField.I20_SIGNATURE) }
            )
            DateField(
                label = "EAD expiration date",
                value = state.tripInput.eadExpirationDate,
                onClick = { onPickDate(TravelDateField.EAD_EXPIRATION) }
            )
            state.tripInput.daysAbroad?.let { days ->
                Text(
                    text = "Planned time abroad: $days day(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    canRunAssessment: Boolean,
    isRefreshingPolicy: Boolean,
    onRunAssessment: () -> Unit,
    onUploadMissingDocument: () -> Unit,
    onRequestI20Signature: () -> Unit,
    onContactDsoAttorney: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRunAssessment,
                enabled = canRunAssessment,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.TRAVEL_RUN_ASSESSMENT_BUTTON)
            ) {
                Icon(Icons.Filled.FlightTakeoff, contentDescription = null)
                Text(
                    text = if (isRefreshingPolicy) "Refresh finishing..." else "Run assessment",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUploadMissingDocument, modifier = Modifier.weight(1f)) {
                    Text("Upload document")
                }
                OutlinedButton(onClick = onRequestI20Signature, modifier = Modifier.weight(1f)) {
                    Text("Request I-20 signature")
                }
            }
            OutlinedButton(onClick = onContactDsoAttorney, modifier = Modifier.fillMaxWidth()) {
                Text("Contact DSO/attorney")
            }
        }
    }
}

@Composable
private fun OutcomeCard(
    outcome: TravelOutcome,
    headline: String,
    summary: String
) {
    val colors = when (outcome) {
        TravelOutcome.GO -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        TravelOutcome.CAUTION -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        TravelOutcome.NO_GO -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        TravelOutcome.NO_GO_CONTACT_DSO_OR_ATTORNEY -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    Card(
        modifier = Modifier.testTag(UiTestTags.TRAVEL_OUTCOME_CARD),
        colors = colors
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = outcome.name.replace('_', ' '),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = summary)
        }
    }
}

@Composable
private fun ChecklistCard(
    item: TravelChecklistItem,
    onOpenSource: (TravelSourceCitation) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when (item.status) {
                    TravelChecklistStatus.PASS -> "Pass"
                    TravelChecklistStatus.CAUTION -> "Caution"
                    TravelChecklistStatus.BLOCK -> "Block"
                    TravelChecklistStatus.ESCALATE -> "Escalate"
                },
                color = when (item.status) {
                    TravelChecklistStatus.PASS -> MaterialTheme.colorScheme.primary
                    TravelChecklistStatus.CAUTION -> MaterialTheme.colorScheme.tertiary
                    TravelChecklistStatus.BLOCK,
                    TravelChecklistStatus.ESCALATE -> MaterialTheme.colorScheme.error
                }
            )
            Text(text = item.detail)
            item.citations.forEach { citation ->
                TextButton(onClick = { onOpenSource(citation) }) {
                    Text(buildString {
                        append(citation.label)
                        citation.effectiveDate?.let { append(" • effective $it") }
                        citation.lastReviewedDate?.let { append(" • reviewed $it") }
                    })
                }
            }
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: Long?,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = formatDate(value))
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            }
        }
    }
}

@Composable
private fun BooleanChoiceRow(
    label: String,
    value: Boolean?,
    onSelected: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = value == true,
                onClick = { onSelected(true) },
                label = { Text("Yes") }
            )
            FilterChip(
                selected = value == false,
                onClick = { onSelected(false) },
                label = { Text("No") }
            )
        }
    }
}

@Composable
private fun ScenarioChoiceGroup(
    selected: TravelScenario?,
    onSelected: (TravelScenario) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Current OPT travel scenario", style = MaterialTheme.typography.bodyMedium)
        TravelScenario.entries.forEach { scenario ->
            val label = when (scenario) {
                TravelScenario.PENDING_INITIAL_OPT -> "Pending initial OPT"
                TravelScenario.APPROVED_POST_COMPLETION_OPT -> "Approved post-completion OPT"
                TravelScenario.PENDING_STEM_EXTENSION -> "Pending STEM extension"
                TravelScenario.APPROVED_STEM_OPT -> "Approved STEM OPT"
                TravelScenario.GRACE_PERIOD -> "60-day grace period"
            }
            FilterChip(
                selected = selected == scenario,
                onClick = { onSelected(scenario) },
                label = { Text(label) }
            )
        }
    }
}

private fun launchEmailComposer(
    context: Context,
    subject: String,
    body: String
) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(intent)
}

private fun formatDate(value: Long?): String {
    if (value == null) return "Select"
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(value))
}
