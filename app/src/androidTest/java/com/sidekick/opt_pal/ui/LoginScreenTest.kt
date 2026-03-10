package com.sidekick.opt_pal.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.opt_pal.feature.auth.LoginScreen
import com.sidekick.opt_pal.feature.auth.LoginUiState
import com.sidekick.opt_pal.ui.theme.OPTPalTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsErrorAndPropagatesClicks() {
        var loginClicked = false
        composeRule.setContent {
            OPTPalTheme {
                LoginScreen(
                    state = LoginUiState(
                        email = "user@example.com",
                        password = "password123",
                        errorMessage = "Invalid credentials",
                        isLoading = false
                    ),
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onLogin = { loginClicked = true },
                    onRegister = {}
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.LOGIN_EMAIL_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.LOGIN_ERROR_TEXT).assertTextContains("Invalid")
        composeRule.onNodeWithTag(UiTestTags.LOGIN_BUTTON).performClick()
        assertTrue(loginClicked)
    }
}
