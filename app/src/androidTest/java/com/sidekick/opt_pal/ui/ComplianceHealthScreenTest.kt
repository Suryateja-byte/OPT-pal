package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.data.model.ComplianceAction
import com.sidekick.opt_pal.data.model.ComplianceFactorAssessment
import com.sidekick.opt_pal.data.model.ComplianceFactorId
import com.sidekick.opt_pal.data.model.ComplianceHealthScore
import com.sidekick.opt_pal.data.model.ComplianceReference
import com.sidekick.opt_pal.data.model.ComplianceScoreBand
import com.sidekick.opt_pal.data.model.ComplianceScoreQuality
import com.sidekick.opt_pal.feature.compliance.ComplianceHealthScreen
import com.sidekick.opt_pal.feature.compliance.ComplianceHealthUiState
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComplianceHealthScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersComplianceBreakdown() {
        val score = ComplianceHealthScore(
            score = 84,
            band = ComplianceScoreBand.WATCH,
            quality = ComplianceScoreQuality.VERIFIED,
            headline = "Your tracked compliance signals need monitoring.",
            summary = "One or more tracked factors need attention before they become higher-risk problems.",
            topReasons = listOf("You have reporting work coming due soon."),
            factors = listOf(
                ComplianceFactorAssessment(
                    id = ComplianceFactorId.REPORTING,
                    title = "Reporting status",
                    score = 24,
                    maxScore = 30,
                    summary = "You have reporting work coming due soon.",
                    detail = "Next tracked deadline: Mar 18, 2026.",
                    actions = listOf(
                        ComplianceAction(
                            id = "open_reporting",
                            label = "Open Reporting",
                            route = "reporting"
                        )
                    ),
                    references = listOf(
                        ComplianceReference(
                            id = "opt_reporting",
                            label = "Study in the States: OPT Student Reporting Requirements",
                            url = "https://studyinthestates.dhs.gov/opt-student-reporting-requirements",
                            lastReviewedDate = "Mar 10, 2026",
                            summary = "Official OPT reporting windows and required updates."
                        )
                    )
                )
            )
        )

        composeRule.setContent {
            OPTPalTheme {
                ComplianceHealthScreen(
                    state = ComplianceHealthUiState(
                        isLoading = false,
                        availability = com.sidekick.opt_pal.data.model.ComplianceHealthAvailability(
                            isEnabled = true,
                            message = "Enabled."
                        ),
                        score = score
                    ),
                    onNavigateBack = {},
                    onRunAction = {},
                    onOpenPolicyAlert = {},
                    onOpenReference = {},
                    onContactDso = {},
                    onOpenVisaPathwayPlanner = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.COMPLIANCE_HEALTH_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.COMPLIANCE_HEALTH_FACTOR_LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Compliance Score").assertIsDisplayed()
        composeRule.onNodeWithText("Open Reporting").assertIsDisplayed()
        composeRule.onNodeWithText("Study in the States: OPT Student Reporting Requirements").assertTextContains("Reporting")
    }
}
