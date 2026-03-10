package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.ReportingDraftResult
import com.sidekick.opt_pal.data.model.ReportingWizard
import com.sidekick.opt_pal.data.model.ReportingWizardEventType
import com.sidekick.opt_pal.data.model.ReportingWizardInput
import com.sidekick.opt_pal.data.model.ReportingWizardOptRegime
import com.sidekick.opt_pal.feature.reporting.ReportingWizardScreen
import com.sidekick.opt_pal.feature.reporting.ReportingWizardStep
import com.sidekick.opt_pal.feature.reporting.ReportingWizardUiState
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportingWizardScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun eventStepShowsContinueButton() {
        var started = false
        composeRule.setContent {
            OPTPalTheme {
                ReportingWizardScreen(
                    state = ReportingWizardUiState(
                        isLoading = false,
                        step = ReportingWizardStep.EVENT,
                        selectedEmploymentId = "job-1",
                        eventDate = 1_000L,
                        employments = listOf(
                            Employment(
                                id = "job-1",
                                employerName = "Acme",
                                jobTitle = "Engineer",
                                startDate = 1_000L,
                                hoursPerWeek = 40
                            )
                        )
                    ),
                    onNavigateBack = {},
                    onEventTypeSelected = {},
                    onEmploymentSelected = {},
                    onShowEventDatePicker = {},
                    onStartWizard = { started = true },
                    onEmployerNameChanged = {},
                    onJobTitleChanged = {},
                    onMajorNameChanged = {},
                    onWorksiteAddressChanged = {},
                    onSiteNameChanged = {},
                    onSupervisorNameChanged = {},
                    onSupervisorEmailChanged = {},
                    onSupervisorPhoneChanged = {},
                    onJobDutiesChanged = {},
                    onToolsAndSkillsChanged = {},
                    onUserExplanationChanged = {},
                    onContinueToReview = {},
                    onBackToDetails = {},
                    onDocumentToggled = {},
                    onGenerateDraft = {},
                    onEditedDraftChanged = {},
                    onCopyDraft = {},
                    onCompleteWizard = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.REPORTING_WIZARD_EVENT_CONTINUE).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.REPORTING_WIZARD_EVENT_CONTINUE).performClick()
        assertTrue(started)
    }

    @Test
    fun reviewStepShowsDraftAndWarning() {
        composeRule.setContent {
            OPTPalTheme {
                ReportingWizardScreen(
                    state = ReportingWizardUiState(
                        isLoading = false,
                        step = ReportingWizardStep.REVIEW,
                        complianceWarning = "Jobs below 20 hours/week may not stop the unemployment clock.",
                        wizardId = "wizard-1",
                        wizard = ReportingWizard(
                            id = "wizard-1",
                            eventType = ReportingWizardEventType.NEW_EMPLOYER.wireValue,
                            optRegime = ReportingWizardOptRegime.POST_COMPLETION.wireValue,
                            userInputs = ReportingWizardInput(
                                majorName = "Computer Science",
                                jobDuties = "Build Android features."
                            ),
                            generatedDraft = ReportingDraftResult(
                                draftParagraph = "My role applies software engineering concepts from my Computer Science degree."
                            )
                        ),
                        editedDraft = "My role applies software engineering concepts from my Computer Science degree."
                    ),
                    onNavigateBack = {},
                    onEventTypeSelected = {},
                    onEmploymentSelected = {},
                    onShowEventDatePicker = {},
                    onStartWizard = {},
                    onEmployerNameChanged = {},
                    onJobTitleChanged = {},
                    onMajorNameChanged = {},
                    onWorksiteAddressChanged = {},
                    onSiteNameChanged = {},
                    onSupervisorNameChanged = {},
                    onSupervisorEmailChanged = {},
                    onSupervisorPhoneChanged = {},
                    onJobDutiesChanged = {},
                    onToolsAndSkillsChanged = {},
                    onUserExplanationChanged = {},
                    onContinueToReview = {},
                    onBackToDetails = {},
                    onDocumentToggled = {},
                    onGenerateDraft = {},
                    onEditedDraftChanged = {},
                    onCopyDraft = {},
                    onCompleteWizard = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.REPORTING_WIZARD_WARNING).assertTextContains("Jobs below 20 hours/week")
        composeRule.onNodeWithTag(UiTestTags.REPORTING_WIZARD_DRAFT_FIELD)
            .assertTextContains("Computer Science")
    }
}
