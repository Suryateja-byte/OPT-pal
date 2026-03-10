package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.data.model.PolicyAlertAvailability
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertSource
import com.sidekick.opt_pal.data.model.PolicyAlertState
import com.sidekick.opt_pal.feature.policy.PolicyAlertFeedScreen
import com.sidekick.opt_pal.feature.policy.PolicyAlertFeedUiState
import com.sidekick.opt_pal.feature.policy.PolicyAlertFilter
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolicyAlertFeedScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersFeedAndOpensSourceAction() {
        var sourceOpened = false
        composeRule.setContent {
            OPTPalTheme {
                PolicyAlertFeedScreen(
                    state = baseState(),
                    systemNotificationsEnabled = true,
                    onNavigateBack = {},
                    onSelectFilter = {},
                    onSelectAlert = {},
                    onEnableNotifications = {},
                    onOpenSource = { sourceOpened = true },
                    onPrimaryAction = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.POLICY_ALERT_FEED_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.POLICY_ALERT_DETAIL).assertIsDisplayed()
        composeRule.onNodeWithText("Source").performClick()
        assertTrue(sourceOpened)
    }

    @Test
    fun rendersNotificationCtaWhenDisabled() {
        composeRule.setContent {
            OPTPalTheme {
                PolicyAlertFeedScreen(
                    state = baseState().copy(policyNotificationsEnabled = false),
                    systemNotificationsEnabled = false,
                    onNavigateBack = {},
                    onSelectFilter = {},
                    onSelectAlert = {},
                    onEnableNotifications = {},
                    onOpenSource = {},
                    onPrimaryAction = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.POLICY_ALERT_NOTIFICATIONS_BUTTON).assertIsDisplayed()
    }

    @Test
    fun rendersUnreadCriticalAlertSummary() {
        composeRule.setContent {
            OPTPalTheme {
                PolicyAlertFeedScreen(
                    state = baseState().copy(
                        alertStates = listOf(
                            PolicyAlertState(alertId = "alert-2", openedAt = 1L, lastSeenAt = 1L)
                        ),
                        selectedFilter = PolicyAlertFilter.CRITICAL
                    ),
                    systemNotificationsEnabled = true,
                    onNavigateBack = {},
                    onSelectFilter = {},
                    onSelectAlert = {},
                    onEnableNotifications = {},
                    onOpenSource = {},
                    onPrimaryAction = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.POLICY_ALERT_DETAIL).assertTextContains("Travel warning")
    }

    private fun baseState(): PolicyAlertFeedUiState {
        return PolicyAlertFeedUiState(
            isLoading = false,
            availability = PolicyAlertAvailability(isEnabled = true, message = "Enabled"),
            alerts = listOf(
                PolicyAlertCard(
                    id = "alert-1",
                    title = "Travel warning",
                    whatChanged = "Travel rules changed.",
                    whoIsAffected = "Students on OPT.",
                    whyItMatters = "This affects travel timing.",
                    recommendedAction = "Review travel guidance.",
                    source = PolicyAlertSource(
                        label = "DOS U.S. Visas News",
                        url = "https://travel.state.gov/content/travel/en/News/visas-news.html",
                        publishedAt = 1L
                    ),
                    severity = "critical",
                    confidence = "high",
                    finality = "guidance",
                    topics = listOf("travel"),
                    publishedAt = 1L,
                    callToActionLabel = "Open Travel Advisor"
                ),
                PolicyAlertCard(
                    id = "alert-2",
                    title = "Reporting update",
                    whatChanged = "SEVIS reporting changed.",
                    whoIsAffected = "Students on OPT.",
                    whyItMatters = "This affects reporting timing.",
                    recommendedAction = "Review reporting guidance.",
                    source = PolicyAlertSource(
                        label = "ICE SEVIS What's New",
                        url = "https://www.ice.gov/sevis/whats-new",
                        publishedAt = 1L
                    ),
                    severity = "medium",
                    confidence = "high",
                    finality = "operations",
                    topics = listOf("reporting"),
                    publishedAt = 2L
                )
            ),
            selectedAlertId = "alert-1",
            policyNotificationsEnabled = true
        )
    }
}
