package com.example.cardbattle.ui.login

import android.os.Bundle
import android.util.Base64
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cardbattle.ui.theme.CardBattleTheme
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fake provider results keep routine UI tests independent of Google accounts/consent dialogs. */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val deliveredTokens = mutableListOf<String>()

    private fun show(provider: suspend () -> String) {
        compose.setContent {
            CardBattleTheme {
                LoginScreen(requestIdToken = provider, onTokenReceived = { deliveredTokens.add(it) })
            }
        }
    }

    @Test
    fun successfulGoogleResultDeliversTokenWithoutDisplayingIt() {
        // Syntactically valid JWT only: provider parsing is tested here; Spring tests verify signatures.
        fun encode(value: String) = Base64.encodeToString(
            value.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val header = encode("""{"alg":"RS256","typ":"JWT"}""")
        val payload = encode("""{"sub":"test-subject","email":"player@example.com","aud":"test-client","iss":"https://accounts.google.com","exp":4102444800,"iat":1700000000}""")
        val token = "$header.$payload.${encode("test-signature")}"
        val credential = GoogleIdTokenCredential.Builder()
            .setId("player@example.com").setIdToken(token).build()
        show { googleIdToken(credential) }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Google ID token received. Backend verification is still required.").assertIsDisplayed()
        compose.onNodeWithText(token).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(token), deliveredTokens) }
    }

    @Test
    fun noTokenDoesNotReportSuccess() {
        show { "   " }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("No Google ID token received. Please try again.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(deliveredTokens.isEmpty()) }
    }

    @Test
    fun cancellationAllowsRetry() {
        var attempts = 0
        show {
            attempts++
            if (attempts == 1) throw GetCredentialCancellationException("User cancelled")
            "retry-token"
        }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Sign-in cancelled.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(deliveredTokens.isEmpty()) }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.runOnIdle { assertEquals(listOf("retry-token"), deliveredTokens) }
    }

    @Test
    fun developerConsoleErrorDoesNotAuthenticate() {
        show { throw GetCredentialCustomException("test-config-error", "Developer console is not set up correctly") }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Google sign-in failed. Please try again.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(deliveredTokens.isEmpty()) }
    }

    @Test
    fun unexpectedCredentialTypeIsRejected() {
        show { googleIdToken(CustomCredential("unexpected-type", Bundle())) }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Google sign-in failed. Please try again.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(deliveredTokens.isEmpty()) }
    }

    @Test
    fun malformedGoogleCredentialIsRejected() {
        show { googleIdToken(CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())) }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Google sign-in failed. Please try again.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(deliveredTokens.isEmpty()) }
    }

    @Test
    fun pendingSignInDisablesButtonAndDoesNotAuthenticateEarly() {
        val pending = CompletableDeferred<String>()
        show { pending.await() }
        compose.onNodeWithText("Sign in with Google").performClick()
        compose.onNodeWithText("Signing in…").assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(deliveredTokens.isEmpty())
            pending.complete("completed-token")
        }
        compose.onNodeWithText("Google ID token received. Backend verification is still required.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("completed-token"), deliveredTokens) }
    }
}
