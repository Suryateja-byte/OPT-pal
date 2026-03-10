package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        val state = DashboardUiState(
            isLoading = false,
            displayName = "Alex",
            optLabel = "Initial 12-Month",
            employmentHistory = listOf(
                Employment(
                    id = "1",
                    employerName = "Acme",
                    jobTitle = "Engineer",
                    startDate = System.currentTimeMillis() - 1_000,
                    endDate = null
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
                    onOpenReporting = {},
                    onOpenVault = {},
                    onSendFeedback = {},
                    onOpenLegal = {},
                    onScanDocument = {},
                    onOpenChat = {},
                    onDeleteEmployment = {},
                    onSignOut = {},
                    onReprocessDocuments = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.DASHBOARD_GREETING).assertTextContains("Welcome back")
        composeRule.onNodeWithTag(UiTestTags.DASHBOARD_FAB).performClick()
        assertTrue(addEmploymentClicked)
    }
}
