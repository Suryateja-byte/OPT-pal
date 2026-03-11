package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.data.model.VisaPathwayAssessment
import com.sidekick.opt_pal.data.model.VisaPathwayEntitlementSource
import com.sidekick.opt_pal.data.model.VisaPathwayEntitlementState
import com.sidekick.opt_pal.data.model.VisaPathwayId
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerSummary
import com.sidekick.opt_pal.data.model.VisaPathwayRecommendation
import com.sidekick.opt_pal.feature.pathway.VisaPathwayPlannerScreen
import com.sidekick.opt_pal.feature.pathway.VisaPathwayPlannerUiState
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisaPathwayPlannerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsLockedPreviewState() {
        composeRule.setContent {
            OPTPalTheme {
                VisaPathwayPlannerScreen(
                    state = VisaPathwayPlannerUiState(
                        isLoading = false,
                        entitlement = VisaPathwayEntitlementState(
                            isEnabled = false,
                            source = VisaPathwayEntitlementSource.LOCKED_PREVIEW,
                            message = "Limited rollout."
                        )
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onNavigateBack = {},
                    onRefreshBundle = {},
                    onPickContinuityDate = {},
                    onUpdateEmployerType = {},
                    onUpdateEmployerUsesEVerify = {},
                    onUpdateEmployerWillSponsorH1b = {},
                    onUpdateH1bRegistrationStatus = {},
                    onUpdateDegreeLevel = {},
                    onUpdateHasPriorUsStemDegree = {},
                    onUpdateRoleRelatedToDegree = {},
                    onUpdateHasPetitioningEmployerOrAgent = {},
                    onToggleO1Evidence = {},
                    onUpdateStatusViolation = {},
                    onUpdateArrestHistory = {},
                    onUpdateUnauthorizedEmployment = {},
                    onUpdateRfeOrNoid = {},
                    onSelectPathway = {},
                    onMarkPreferredPathway = {},
                    onOpenScenarioSimulator = {},
                    onRunAction = {},
                    onOpenCitation = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.VISA_PATHWAY_LOCKED_PREVIEW).assertIsDisplayed()
    }

    @Test
    fun rendersQuestionnaireAndCards() {
        val assessment = VisaPathwayAssessment(
            pathwayId = VisaPathwayId.STEM_OPT,
            title = "STEM OPT",
            recommendation = VisaPathwayRecommendation.STRONG_FIT,
            summary = "Strong fit from current evidence."
        )
        composeRule.setContent {
            OPTPalTheme {
                VisaPathwayPlannerScreen(
                    state = VisaPathwayPlannerUiState(
                        isLoading = false,
                        entitlement = VisaPathwayEntitlementState(
                            isEnabled = true,
                            source = VisaPathwayEntitlementSource.OPEN_BETA,
                            message = "Enabled."
                        ),
                        assessments = listOf(assessment),
                        summary = VisaPathwayPlannerSummary(topAssessment = assessment)
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onNavigateBack = {},
                    onRefreshBundle = {},
                    onPickContinuityDate = {},
                    onUpdateEmployerType = {},
                    onUpdateEmployerUsesEVerify = {},
                    onUpdateEmployerWillSponsorH1b = {},
                    onUpdateH1bRegistrationStatus = {},
                    onUpdateDegreeLevel = {},
                    onUpdateHasPriorUsStemDegree = {},
                    onUpdateRoleRelatedToDegree = {},
                    onUpdateHasPetitioningEmployerOrAgent = {},
                    onToggleO1Evidence = {},
                    onUpdateStatusViolation = {},
                    onUpdateArrestHistory = {},
                    onUpdateUnauthorizedEmployment = {},
                    onUpdateRfeOrNoid = {},
                    onSelectPathway = {},
                    onMarkPreferredPathway = {},
                    onOpenScenarioSimulator = {},
                    onRunAction = {},
                    onOpenCitation = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.VISA_PATHWAY_QUESTIONNAIRE).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.VISA_PATHWAY_CARD_LIST).assertIsDisplayed()
        composeRule.onNodeWithText("STEM OPT").assertIsDisplayed()
    }
}
