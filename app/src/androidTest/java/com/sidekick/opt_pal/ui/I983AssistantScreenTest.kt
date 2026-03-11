package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.data.model.I983Assessment
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983EntitlementSource
import com.sidekick.opt_pal.data.model.I983EntitlementState
import com.sidekick.opt_pal.data.model.I983PolicyBundle
import com.sidekick.opt_pal.data.model.I983Readiness
import com.sidekick.opt_pal.feature.i983.I983AssistantScreen
import com.sidekick.opt_pal.feature.i983.I983AssistantUiState
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class I983AssistantScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsLockedPreviewState() {
        composeRule.setContent {
            OPTPalTheme {
                I983AssistantScreen(
                    state = baseState().copy(
                        entitlement = I983EntitlementState(
                            isEnabled = false,
                            source = I983EntitlementSource.LOCKED_PREVIEW,
                            message = "Limited rollout."
                        )
                    ),
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                    onNavigateBack = {},
                    onSelectDraft = {},
                    onSelectWorkflow = {},
                    onSelectEmployment = {},
                    onSelectObligation = {},
                    onStartDraft = {},
                    onSaveDraft = {},
                    onGenerateNarrative = {},
                    onExportPdf = {},
                    onShareExport = {},
                    onOpenExport = {},
                    onUploadSigned = {},
                    onOpenReporting = {},
                    onOpenVisaPathwayPlanner = {},
                    onRefreshPolicy = {},
                    onTextFieldChanged = { _, _ -> },
                    onPickDate = {},
                    onToggleDocument = {},
                    onOpenSource = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.I983_LOCKED_PREVIEW).assertIsDisplayed()
    }

    @Test
    fun showsWorkflowSelectorAndReviewActions() {
        composeRule.setContent {
            OPTPalTheme {
                I983AssistantScreen(
                    state = baseState().copy(
                        draft = I983Draft(id = "draft-1"),
                        assessment = I983Assessment(
                            readiness = I983Readiness.READY_TO_EXPORT,
                            headline = "Ready to export",
                            summary = "The tracked required fields are present."
                        )
                    ),
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
                    onNavigateBack = {},
                    onSelectDraft = {},
                    onSelectWorkflow = {},
                    onSelectEmployment = {},
                    onSelectObligation = {},
                    onStartDraft = {},
                    onSaveDraft = {},
                    onGenerateNarrative = {},
                    onExportPdf = {},
                    onShareExport = {},
                    onOpenExport = {},
                    onUploadSigned = {},
                    onOpenReporting = {},
                    onOpenVisaPathwayPlanner = {},
                    onRefreshPolicy = {},
                    onTextFieldChanged = { _, _ -> },
                    onPickDate = {},
                    onToggleDocument = {},
                    onOpenSource = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.I983_WORKFLOW_SELECTOR).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.I983_REVIEW_EXPORT_SECTION).assertIsDisplayed()
        composeRule.onNodeWithText("Export PDF").assertIsDisplayed()
    }

    private fun baseState(): I983AssistantUiState {
        return I983AssistantUiState(
            isLoading = false,
            entitlement = I983EntitlementState(
                isEnabled = true,
                source = I983EntitlementSource.OPEN_BETA,
                message = "Enabled."
            ),
            policyBundle = I983PolicyBundle(
                version = "test",
                generatedAt = 0L,
                lastReviewedAt = 0L,
                templateVersion = "template",
                templateSha256 = "sha"
            )
        )
    }
}
