package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.feature.employment.AddEmploymentScreen
import com.sidekick.opt_pal.feature.employment.AddEmploymentUiState
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddEmploymentScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsErrorAndNotifiesOnSave() {
        var saved = false
        composeRule.setContent {
            val scrollState = rememberScrollState()
            OPTPalTheme {
                AddEmploymentScreen(
                    state = AddEmploymentUiState(
                        employerName = "Acme",
                        jobTitle = "Engineer",
                        hoursPerWeek = "40",
                        startDate = System.currentTimeMillis(),
                        errorMessage = "Error"
                    ),
                    onEmployerNameChange = {},
                    onJobTitleChange = {},
                    onHoursPerWeekChange = {},
                    onIsCurrentJobChange = {},
                    onRequestStartDate = {},
                    onRequestEndDate = {},
                    onSave = { saved = true },
                    onNavigateBack = {},
                    scrollState = scrollState
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.ADD_ERROR_TEXT).assertTextContains("Error")
        composeRule.onNodeWithTag(UiTestTags.ADD_SAVE_BUTTON).performClick()
        assertTrue(saved)
    }
}
