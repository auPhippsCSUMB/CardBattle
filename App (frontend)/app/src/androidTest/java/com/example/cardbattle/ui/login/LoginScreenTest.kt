package com.example.cardbattle.ui.login

import android.os.Bundle
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cardbattle.ui.theme.CardBattleTheme
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var loginSuccessCalled = false
    private var navigateToSignUpCalled = false

    private fun show(provider: (suspend () -> String)? = null) {
        loginSuccessCalled = false
        navigateToSignUpCalled = false
        compose.setContent {
            CardBattleTheme {
                LoginScreen(
                    onLoginSuccess = { loginSuccessCalled = true },
                    onNavigateToSignUp = { navigateToSignUpCalled = true },
                    requestIdToken = provider
                )
            }
        }
    }

    // --- UI Layout & Form Tests ---

    @Test
    fun loginScreen_displaysAllCoreElements() {
        show()
        compose.onNodeWithText("Welcome").assertIsDisplayed()
        compose.onNodeWithText("Username").assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
        compose.onNodeWithText("Login").assertIsDisplayed()
        compose.onNodeWithText("Don't have an account? Create Account").assertIsDisplayed()
        compose.onNodeWithText("Sign in with Google").assertIsDisplayed()
    }

    @Test
    fun login_withEmptyFields_showsValidationError() {
        show()
        compose.onNodeWithText("Login").performClick()
        compose.onNodeWithText("Please enter username and password").assertIsDisplayed()
        assertTrue(!loginSuccessCalled)
    }

    @Test
    fun clickCreateAccount_navigatesToSignUp() {
        show()
        compose.onNodeWithText("Don't have an account? Create Account").performClick()
        assertTrue("Expected navigation to SignUp screen", navigateToSignUpCalled)
    }

    // --- Google Sign-In Provider Tests ---

    @Test
    fun successfulGoogleResult_authenticatesUser() {
        fun encode(value: String) = Base64.encodeToString(
            value.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val header = encode("""{"alg":"RS256","typ":"JWT"}""")
        val payload = encode("""{"sub":"test-subject","email":"player@example.com","name":"PlayerOne","given_name":"Player"}""")
        val token = "$header.$payload.${encode("test-signature")}"

        val credential = GoogleIdTokenCredential.Builder()
            .setId("player@example.com")
            .setIdToken(token)
            .build()

        show { googleIdToken(credential) }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.runOnIdle { assertTrue(loginSuccessCalled) }
    }

    @Test
    fun noTokenDoesNotReportSuccess() {
        show { "   " }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("No Google ID token received. Please try again.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(!loginSuccessCalled) }
    }

    @Test
    fun cancellationAllowsRetry() {
        var attempts = 0
        show {
            attempts++
            if (attempts == 1) throw GetCredentialCancellationException("User cancelled")
            // Valid minimal JWT for retry
            val part = Base64.encodeToString("{}".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            "$part.$part.$part"
        }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Sign-in cancelled.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(!loginSuccessCalled) }

        compose.onNodeWithText("Sign in with Google").performClick()
        compose.runOnIdle { assertTrue(loginSuccessCalled) }
    }

    @Test
    fun developerConsoleErrorDoesNotAuthenticate() {
        show { throw GetCredentialCustomException("test-config-error", "Developer console error") }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Google sign-in failed. Please try again.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(!loginSuccessCalled) }
    }

    @Test
    fun pendingSignInDisablesButtonAndDoesNotAuthenticateEarly() {
        val pending = CompletableDeferred<String>()
        show { pending.await() }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Signing in…").assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(!loginSuccessCalled)
            val part = Base64.encodeToString("{}".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            pending.complete("$part.$part.$part")
        }
        compose.runOnIdle { assertTrue(loginSuccessCalled) }
    }
}