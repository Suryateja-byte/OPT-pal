package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.data.model.OnboardingProfileDraft
import com.sidekick.opt_pal.data.model.OptType
import com.sidekick.opt_pal.feature.setup.SetupScreen
import com.sidekick.opt_pal.feature.setup.SetupStage
import com.sidekick.opt_pal.feature.setup.SetupUiState
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SetupScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sourceStageShowsScanFirstActionsAndSkip() {
        composeRule.setContent {
            OPTPalTheme {
                SetupScreen(
                    state = SetupUiState(stage = SetupStage.SOURCE),
                    onOptTypeSelected = {},
                    onShowStartDatePicker = {},
                    onShowEndDatePicker = {},
                    onSave = {},
                    onOpenLegal = {},
                    onScanDocument = {},
                    onUploadDocument = {},
                    onShowVaultDocuments = {},
                    onSkipManual = {},
                    onDismissMessage = {},
                    onDocumentCandidateToggled = {},
                    onUseSelectedDocuments = {},
                    onSevisIdChanged = {},
                    onSchoolNameChanged = {},
                    onCipCodeChanged = {},
                    onMajorNameChanged = {},
                    onBackToSource = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.SETUP_SCAN_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SETUP_UPLOAD_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SETUP_USE_VAULT_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SETUP_SKIP_MANUAL_BUTTON).assertIsDisplayed()
    }

    @Test
    fun reviewStageDisplaysFormattedDateAndHandlesSave() {
        var saveTriggered = false
        val startDate = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()
        composeRule.setContent {
            OPTPalTheme {
                SetupScreen(
                    state = SetupUiState(
                        stage = SetupStage.REVIEW,
                        draft = OnboardingProfileDraft(
                            optType = OptType.STEM,
                            optStartDate = startDate
                        )
                    ),
                    onOptTypeSelected = {},
                    onShowStartDatePicker = {},
                    onShowEndDatePicker = {},
                    onSave = { saveTriggered = true },
                    onOpenLegal = {},
                    onScanDocument = {},
                    onUploadDocument = {},
                    onShowVaultDocuments = {},
                    onSkipManual = {},
                    onDismissMessage = {},
                    onDocumentCandidateToggled = {},
                    onUseSelectedDocuments = {},
                    onSevisIdChanged = {},
                    onSchoolNameChanged = {},
                    onCipCodeChanged = {},
                    onMajorNameChanged = {},
                    onBackToSource = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.SETUP_DATE_FIELD).assertTextContains("January")
        composeRule.onNodeWithTag(UiTestTags.SETUP_SAVE_BUTTON).performClick()
        assertTrue(saveTriggered)
    }
}
