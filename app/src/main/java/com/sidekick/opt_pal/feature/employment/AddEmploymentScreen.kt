package com.sidekick.opt_pal.feature.employment

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.ui.UiTestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun Long.toDateText(): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(this))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEmploymentRoute(
    onNavigateBack: () -> Unit,
    employmentId: String? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: AddEmploymentViewModel = viewModel(
        factory = AddEmploymentViewModel.provideFactory(employmentId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) { AnalyticsLogger.logScreenView("AddEmployment") }

    val startDateState = rememberDatePickerState(initialSelectedDateMillis = uiState.startDate)
    val endDateState = rememberDatePickerState(initialSelectedDateMillis = uiState.endDate)

    LaunchedEffect(uiState.startDate) {
        if (startDateState.selectedDateMillis != uiState.startDate) {
            startDateState.selectedDateMillis = uiState.startDate
        }
    }

    LaunchedEffect(uiState.endDate) {
        if (endDateState.selectedDateMillis != uiState.endDate) {
            endDateState.selectedDateMillis = uiState.endDate
        }
    }

    LaunchedEffect(uiState.onSaveComplete) {
        if (uiState.onSaveComplete) onNavigateBack()
    }

    // Minimal Date Picker Dialogs
    if (uiState.showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = viewModel::dismissPickers,
            confirmButton = {
                TextButton(onClick = {
                    startDateState.selectedDateMillis?.let(viewModel::onStartDateSelected)
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPickers) { Text("Cancel") }
            }
        ) {
            DatePicker(state = startDateState)
        }
    }

    if (uiState.showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = viewModel::dismissPickers,
            confirmButton = {
                TextButton(onClick = {
                    endDateState.selectedDateMillis?.let(viewModel::onEndDateSelected)
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPickers) { Text("Cancel") }
            }
        ) {
            DatePicker(state = endDateState)
        }
    }

    AddEmploymentScreen(
        state = uiState,
        onEmployerNameChange = viewModel::onEmployerNameChange,
        onJobTitleChange = viewModel::onJobTitleChange,
        onIsCurrentJobChange = viewModel::onIsCurrentJobChange,
        onRequestStartDate = viewModel::showStartDatePicker,
        onRequestEndDate = viewModel::showEndDatePicker,
        onSave = viewModel::saveEmployment,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        scrollState = scrollState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun AddEmploymentScreen(
    state: AddEmploymentUiState,
    onEmployerNameChange: (String) -> Unit,
    onJobTitleChange: (String) -> Unit,
    onIsCurrentJobChange: (Boolean) -> Unit,
    onRequestStartDate: () -> Unit,
    onRequestEndDate: () -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Minimal Custom Header - Replaces Standard TopAppBar
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { 40 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    // 1. Editorial Title
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = if (state.editingEmploymentId != null) "Edit" else "New",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Position.",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    // 2. Invisible Forms
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        MinimalTextInput(
                            value = state.employerName,
                            onValueChange = onEmployerNameChange,
                            label = "Employer Name",
                            placeholder = "e.g. Tech Corp",
                            testTag = UiTestTags.ADD_EMPLOYER_FIELD
                        )

                        MinimalTextInput(
                            value = state.jobTitle,
                            onValueChange = onJobTitleChange,
                            label = "Job Title",
                            placeholder = "e.g. Software Engineer",
                            testTag = UiTestTags.ADD_JOB_TITLE_FIELD
                        )
                    }

                    // 3. Date Logic
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Timeline",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        MinimalDateSelector(
                            label = "Start Date",
                            dateMillis = state.startDate,
                            onClick = onRequestStartDate,
                            testTag = UiTestTags.ADD_START_DATE_FIELD
                        )

                        // Animated End Date
                        AnimatedVisibility(
                            visible = !state.isCurrentJob,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            MinimalDateSelector(
                                label = "End Date",
                                dateMillis = state.endDate,
                                onClick = onRequestEndDate,
                                testTag = UiTestTags.ADD_END_DATE_FIELD,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        // Minimal Toggle Row
                        MinimalToggleRow(
                            text = "I currently work here",
                            isChecked = state.isCurrentJob,
                            onCheckedChange = onIsCurrentJobChange
                        )
                    }

                    // Error State
                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(UiTestTags.ADD_ERROR_TEXT)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 4. Action Button
                    Button(
                        onClick = onSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag(UiTestTags.ADD_SAVE_BUTTON),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        enabled = !state.isLoading,
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Save Position",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

// --- Minimal Components ---

@Composable
private fun MinimalTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            ),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            )
        )
    }
}

@Composable
private fun MinimalDateSelector(
    label: String,
    dateMillis: Long?,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val text = dateMillis?.toDateText() ?: "Select Date"
    val isPlaceholder = dateMillis == null

    Surface(
        modifier = modifier
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
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MinimalToggleRow(
    text: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

// Helper for missing fadeOut animation
private fun fadeOut() = androidx.compose.animation.fadeOut()
