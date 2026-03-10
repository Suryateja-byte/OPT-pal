package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.data.model.UscisCaseSummary
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UscisTrackerAvailability
import com.sidekick.opt_pal.feature.casestatus.UscisCaseStatusScreen
import com.sidekick.opt_pal.feature.casestatus.UscisCaseStatusUiState
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UscisCaseStatusScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersReceiptFieldAndDetailSection() {
        composeRule.setContent {
            OPTPalTheme {
                UscisCaseStatusScreen(
                    state = UscisCaseStatusUiState(
                        isLoading = false,
                        availability = UscisTrackerAvailability(mode = "sandbox"),
                        cases = listOf(
                            UscisCaseTracker(
                                id = "MSC1234567890",
                                receiptNumber = "MSC1234567890",
                                normalizedStage = "CARD_PRODUCED",
                                officialStatusText = "Card Is Being Produced",
                                plainEnglishSummary = "USCIS is producing your EAD card.",
                                recommendedAction = "Watch for mailing and delivery updates.",
                                lastCheckedAt = 1_000L
                            )
                        ),
                        selectedCaseId = "MSC1234567890"
                    ),
                    notificationsEnabled = false,
                    onNavigateBack = {},
                    onReceiptNumberChanged = {},
                    onAddCase = {},
                    onSelectCase = {},
                    onRefreshCase = {},
                    onRefreshSelectedCase = {},
                    onArchiveSelectedCase = {},
                    onRemoveSelectedCase = {},
                    onDismissMessage = {},
                    onEnableNotifications = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.CASE_STATUS_RECEIPT_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.CASE_STATUS_DETAIL).assertTextContains("Card Is Being Produced")
        composeRule.onNodeWithTag(UiTestTags.CASE_STATUS_NOTIFICATIONS_BUTTON).assertIsDisplayed()
    }
}
