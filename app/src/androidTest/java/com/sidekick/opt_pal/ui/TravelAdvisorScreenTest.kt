package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.data.model.TravelAssessment
import com.sidekick.opt_pal.data.model.TravelChecklistItem
import com.sidekick.opt_pal.data.model.TravelChecklistStatus
import com.sidekick.opt_pal.data.model.TravelEntitlementSource
import com.sidekick.opt_pal.data.model.TravelEntitlementState
import com.sidekick.opt_pal.data.model.TravelOutcome
import com.sidekick.opt_pal.data.model.TravelPolicyBundle
import com.sidekick.opt_pal.data.model.TravelSourceCitation
import com.sidekick.opt_pal.feature.travel.TravelAdvisorScreen
import com.sidekick.opt_pal.feature.travel.TravelAdvisorUiState
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TravelAdvisorScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersManualFallbackAndUploadAction() {
        var uploadClicked = false
        composeRule.setContent {
            OPTPalTheme {
                TravelAdvisorScreen(
                    state = baseState(),
                    onNavigateBack = {},
                    onPickDate = {},
                    onDestinationCountryChanged = {},
                    onPassportIssuingCountryChanged = {},
                    onVisaClassChanged = {},
                    onScenarioSelected = {},
                    onOnlyContiguousTravelChanged = {},
                    onNeedsNewVisaChanged = {},
                    onVisaRenewalOutsideResidenceChanged = {},
                    onEmploymentProofChanged = {},
                    onCapGapChanged = {},
                    onSensitiveIssueChanged = {},
                    onHasOriginalEadChanged = {},
                    onRunAssessment = {},
                    onRefreshPolicy = {},
                    onUploadMissingDocument = { uploadClicked = true },
                    onOpenSource = {},
                    onRequestI20Signature = {},
                    onContactDsoAttorney = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.TRAVEL_UPLOAD_MISSING_BUTTON).performClick()
        assertTrue(uploadClicked)
    }

    @Test
    fun rendersCapGapEscalationAndCitationAction() {
        var openedSource = false
        composeRule.setContent {
            OPTPalTheme {
                TravelAdvisorScreen(
                    state = baseState().copy(
                        assessment = TravelAssessment(
                            outcome = TravelOutcome.NO_GO_CONTACT_DSO_OR_ATTORNEY,
                            headline = "This trip needs DSO review.",
                            summary = "Cap-gap travel is a hard stop in v1.",
                            checklistItems = listOf(
                                TravelChecklistItem(
                                    ruleId = com.sidekick.opt_pal.data.model.TravelRuleId.ESCALATION,
                                    title = "Cap-gap scenario",
                                    status = TravelChecklistStatus.ESCALATE,
                                    detail = "Cap-gap travel is escalated out of the app.",
                                    citations = listOf(
                                        TravelSourceCitation(
                                            id = "source-1",
                                            label = "USCIS cap-gap context",
                                            url = "https://example.com",
                                            effectiveDate = "2025-01-17",
                                            lastReviewedDate = "2026-03-10"
                                        )
                                    )
                                )
                            ),
                            computedAt = 0L,
                            policyVersion = "test"
                        )
                    ),
                    onNavigateBack = {},
                    onPickDate = {},
                    onDestinationCountryChanged = {},
                    onPassportIssuingCountryChanged = {},
                    onVisaClassChanged = {},
                    onScenarioSelected = {},
                    onOnlyContiguousTravelChanged = {},
                    onNeedsNewVisaChanged = {},
                    onVisaRenewalOutsideResidenceChanged = {},
                    onEmploymentProofChanged = {},
                    onCapGapChanged = {},
                    onSensitiveIssueChanged = {},
                    onHasOriginalEadChanged = {},
                    onRunAssessment = {},
                    onRefreshPolicy = {},
                    onUploadMissingDocument = {},
                    onOpenSource = { openedSource = true },
                    onRequestI20Signature = {},
                    onContactDsoAttorney = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.TRAVEL_OUTCOME_CARD).assertTextContains("NO GO CONTACT DSO OR ATTORNEY")
        composeRule.onNodeWithText("USCIS cap-gap context • effective 2025-01-17 • reviewed 2026-03-10").performClick()
        assertTrue(openedSource)
    }

    @Test
    fun showsLockedPreviewCard() {
        composeRule.setContent {
            OPTPalTheme {
                TravelAdvisorScreen(
                    state = baseState().copy(
                        entitlement = TravelEntitlementState(
                            isEnabled = false,
                            source = TravelEntitlementSource.LOCKED_PREVIEW,
                            message = "Limited rollout."
                        )
                    ),
                    onNavigateBack = {},
                    onPickDate = {},
                    onDestinationCountryChanged = {},
                    onPassportIssuingCountryChanged = {},
                    onVisaClassChanged = {},
                    onScenarioSelected = {},
                    onOnlyContiguousTravelChanged = {},
                    onNeedsNewVisaChanged = {},
                    onVisaRenewalOutsideResidenceChanged = {},
                    onEmploymentProofChanged = {},
                    onCapGapChanged = {},
                    onSensitiveIssueChanged = {},
                    onHasOriginalEadChanged = {},
                    onRunAssessment = {},
                    onRefreshPolicy = {},
                    onUploadMissingDocument = {},
                    onOpenSource = {},
                    onRequestI20Signature = {},
                    onContactDsoAttorney = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.TRAVEL_LOCKED_PREVIEW).assertIsDisplayed()
    }

    private fun baseState(): TravelAdvisorUiState {
        return TravelAdvisorUiState(
            isLoading = false,
            entitlement = TravelEntitlementState(
                isEnabled = true,
                source = TravelEntitlementSource.OPEN_BETA,
                message = "Enabled."
            ),
            policyBundle = TravelPolicyBundle(
                version = "test",
                generatedAt = 0L,
                lastReviewedAt = 0L,
                staleAfterDays = 30
            )
        )
    }
}
