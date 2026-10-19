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
import com.example.cardbattle.data.remote.UserProfile
import com.example.cardbattle.ui.theme.CardBattleTheme
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Fake provider and backend results keep UI tests independent of Google and Railway. */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val deliveredTokens = mutableListOf<String>()
    private val deliveredUsers = mutableListOf<UserProfile>()
    private val user = UserProfile(7, "player@example.com", "Test Player", false)

    private fun show(
        backend: suspend (String) -> UserProfile = { user },
        provider: suspend () -> String,
    ) {
        compose.setContent {
            CardBattleTheme {
                LoginScreen(
                    requestIdToken = provider,
                    authenticateWithBackend = backend,
                    onSignedIn = { token, profile ->
                        deliveredTokens.add(token)
                        deliveredUsers.add(profile)
                    },
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
        compose.waitUntil(5_000) { deliveredTokens.size == 1 }
        compose.onNodeWithText(token).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(token), deliveredTokens) }
        compose.runOnIdle { assertEquals(listOf(user), deliveredUsers) }
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
        compose.runOnIdle { assertTrue(deliveredTokens.isEmpty()) }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.waitUntil(5_000) { deliveredTokens.size == 1 }
        compose.runOnIdle { assertEquals(listOf("retry-token"), deliveredTokens) }
    }

    @Test
    fun developerConsoleErrorDoesNotAuthenticate() {
        show { throw GetCredentialCustomException("test-config-error", "Developer console is not set up correctly") }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Google sign-in failed. Please try again.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(deliveredTokens.isEmpty()) }
    }

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
        compose.waitUntil(5_000) { deliveredTokens.size == 1 }
        compose.runOnIdle { assertEquals(listOf("completed-token"), deliveredTokens) }
    }

    @Test
    fun backendRejectionDoesNotDeliverTokenOrUser() {
        show(provider = { "test-token" }, backend = { throw IOException("HTTP 401") })

        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Google sign-in failed. Please try again.").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(deliveredTokens.isEmpty())
            assertTrue(deliveredUsers.isEmpty())
        }
    }

    @Test
    fun pendingBackendVerificationDoesNotAuthenticateEarly() {
        val pending = CompletableDeferred<UserProfile>()
        show(provider = { "test-token" }, backend = { pending.await() })

        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Signing in…").assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(deliveredTokens.isEmpty())
            pending.complete(user)
        }

        compose.waitUntil(5_000) { deliveredTokens.size == 1 }
        compose.runOnIdle {
            assertEquals(listOf("test-token"), deliveredTokens)
            assertEquals(listOf(user), deliveredUsers)
        }
    }
}
