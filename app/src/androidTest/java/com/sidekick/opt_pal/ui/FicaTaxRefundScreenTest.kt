package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.data.model.FicaEligibilityClassification
import com.sidekick.opt_pal.data.model.FicaEligibilityResult
import com.sidekick.opt_pal.data.model.W2ExtractionDraft
import com.sidekick.opt_pal.feature.tax.FicaTaxRefundScreen
import com.sidekick.opt_pal.feature.tax.FicaTaxRefundUiState
import com.sidekick.opt_pal.feature.tax.TaxRefundStep
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FicaTaxRefundScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersSourceAndEligibilityStates() {
        composeRule.setContent {
            OPTPalTheme {
                FicaTaxRefundScreen(
                    state = FicaTaxRefundUiState(
                        isLoading = false,
                        step = TaxRefundStep.ELIGIBILITY,
                        selectedCaseId = "case-1",
                        selectedW2DocumentId = "doc-1",
                        availableW2Documents = listOf(
                            W2ExtractionDraft(
                                documentId = "doc-1",
                                fileName = "w2.pdf",
                                displayName = "2025 W-2",
                                taxYear = 2025,
                                employerName = "Acme Corp",
                                employeeName = "Student",
                                employeeSsnLast4 = "6789",
                                employerEinMasked = "XX-XXX6789",
                                socialSecurityTaxBox4 = 620.0,
                                medicareTaxBox6 = 145.0
                            )
                        ),
                        latestEligibilityResult = FicaEligibilityResult(
                            classification = FicaEligibilityClassification.ELIGIBLE.wireValue,
                            refundAmount = 765.0
                        )
                    ),
                    onNavigateBack = {},
                    onPickW2 = {},
                    onSelectExistingCase = {},
                    onBeginNewCaseSelection = {},
                    onSelectW2Document = {},
                    onUseSelectedW2 = {},
                    onFirstUsStudentTaxYearChanged = {},
                    onAuthorizedEmploymentConfirmedChanged = {},
                    onMaintainedStudentStatusChanged = {},
                    onNoResidencyStatusChangeChanged = {},
                    onEvaluateEligibility = {},
                    onGenerateEmployerPacket = {},
                    onEmployerOutcomeSelected = {},
                    onFullSsnChanged = {},
                    onFullEmployerEinChanged = {},
                    onMailingAddressChanged = {},
                    onGenerateIrsPacket = {},
                    onOpenPacket = {},
                    onArchiveCase = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.TAX_REFUND_ELIGIBILITY_STEP).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.TAX_REFUND_FIRST_YEAR_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.TAX_REFUND_EVALUATE_BUTTON).assertIsDisplayed()
    }
}
