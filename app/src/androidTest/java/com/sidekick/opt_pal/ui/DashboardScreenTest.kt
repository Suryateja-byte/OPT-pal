package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.data.model.ComplianceHealthScore
import com.sidekick.opt_pal.data.model.ComplianceScoreBand
import com.sidekick.opt_pal.data.model.ComplianceScoreQuality
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.feature.dashboard.DashboardScreen
import com.sidekick.opt_pal.feature.dashboard.DashboardUiState
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersGreetingAndHandlesFabClick() {
        var addEmploymentClicked = false
        var travelClicked = false
        var complianceClicked = false
        val state = DashboardUiState(
            isLoading = false,
            displayName = "Alex",
            optLabel = "Initial 12-Month",
            complianceScore = ComplianceHealthScore(
                score = 92,
                band = ComplianceScoreBand.STABLE,
                quality = ComplianceScoreQuality.VERIFIED,
                headline = "Your tracked compliance signals look stable.",
                topReasons = listOf("Clock paused")
            ),
            employmentHistory = listOf(
                Employment(
                    id = "1",
                    employerName = "Acme",
                    jobTitle = "Engineer",
                    startDate = System.currentTimeMillis() - 1_000,
                    endDate = null,
                    hoursPerWeek = 40
                )
            ),
            pendingReportingCount = 2
        )

        composeRule.setContent {
            OPTPalTheme {
                DashboardScreen(
                    state = state,
                    onAddEmployment = { addEmploymentClicked = true },
                    onEditEmployment = {},
                    onOpenTaxRefund = {},
                    onOpenComplianceScore = { complianceClicked = true },
                    onOpenTravelAdvisor = { travelClicked = true },
                    onOpenPolicyAlerts = {},
                    onOpenCaseStatus = {},
                    onOpenReporting = {},
                    onOpenVault = {},
                    onSendFeedback = {},
                    onOpenLegal = {},
                    onScanDocument = {},
                    onOpenChat = {},
                    onDeleteEmployment = {},
                    onSignOut = {},
                    onReprocessDocuments = {},
                    onCounterAction = {},
                    showEnableAlertsAction = false,
                    onEnableAlerts = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.DASHBOARD_GREETING).assertTextContains("Hello")
        composeRule.onNodeWithTag(UiTestTags.DASHBOARD_COMPLIANCE_CARD).performClick()
        composeRule.onNodeWithTag(UiTestTags.DASHBOARD_TRAVEL_ADVISOR_CARD).performClick()
        composeRule.onNodeWithTag(UiTestTags.DASHBOARD_FAB).performClick()
        assertTrue(complianceClicked)
        assertTrue(travelClicked)
        assertTrue(addEmploymentClicked)
    }
}
