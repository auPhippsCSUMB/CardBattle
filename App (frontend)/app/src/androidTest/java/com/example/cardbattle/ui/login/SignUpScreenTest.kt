package com.example.cardbattle

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.cardbattle.ui.login.SignUpScreen
import com.example.cardbattle.ui.theme.CardBattleTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignUpScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun signUpScreen_displaysAllFields() {
        composeTestRule.setContent {
            CardBattleTheme {
                SignUpScreen()
            }
        }

        composeTestRule.onNodeWithText("Create Account").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign Up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Already have an account? Log In").assertIsDisplayed()
    }

    @Test
    fun signUpScreen_emptyFields_showsRequiredError() {
        composeTestRule.setContent {
            CardBattleTheme {
                SignUpScreen()
            }
        }

        composeTestRule.onNodeWithText("Sign Up").performClick()
        composeTestRule.onNodeWithText("All fields are required").assertIsDisplayed()
    }

    @Test
    fun signUpScreen_mismatchedPasswords_showsMismatchError() {
        composeTestRule.setContent {
            CardBattleTheme {
                SignUpScreen()
            }
        }

        composeTestRule.onNodeWithText("Username").performTextInput("Hero")
        composeTestRule.onNodeWithText("Password").performTextInput("pass123")
        composeTestRule.onNodeWithText("Confirm Password").performTextInput("pass999")

        composeTestRule.onNodeWithText("Sign Up").performClick()
        composeTestRule.onNodeWithText("Passwords do not match").assertIsDisplayed()
    }

    @Test
    fun signUpScreen_shortPassword_showsLengthError() {
        composeTestRule.setContent {
            CardBattleTheme {
                SignUpScreen()
            }
        }

        composeTestRule.onNodeWithText("Username").performTextInput("Hero")
        composeTestRule.onNodeWithText("Password").performTextInput("12")
        composeTestRule.onNodeWithText("Confirm Password").performTextInput("12")

        composeTestRule.onNodeWithText("Sign Up").performClick()
        composeTestRule.onNodeWithText("Password must be at least 4 characters").assertIsDisplayed()
    }

    @Test
    fun signUpScreen_backToLogin_triggersCallback() {
        var navigatedBack = false

        composeTestRule.setContent {
            CardBattleTheme {
                SignUpScreen(
                    onBackToLogin = { navigatedBack = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Already have an account? Log In").performClick()
        assertTrue("Expected back-to-login navigation", navigatedBack)
    }
}