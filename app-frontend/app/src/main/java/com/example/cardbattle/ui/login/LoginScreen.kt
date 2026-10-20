package com.example.cardbattle.ui.login

import android.app.Activity
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.cardbattle.R
import com.example.cardbattle.data.remote.UserProfile
import com.example.cardbattle.data.remote.signInAndGetUser
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.security.SecureRandom

@Composable
fun LoginScreen(
    requestIdToken: (suspend () -> String)? = null,
    authenticateWithBackend: suspend (String) -> UserProfile = { signInAndGetUser(it) },
    onSignedIn: (String, UserProfile) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val credentialManager = remember(context) {
        CredentialManager.create(context)
    }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Welcome",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text("Sign in or create an account with Google.")

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                onClick = {
                    loading = true
                    message = ""
                    scope.launch {
                        try {
                            val idToken = if (requestIdToken != null) {
                                requestIdToken()
                            } else {
                                requestGoogleIdToken(
                                    checkNotNull(activity) { "Sign-in requires an Activity" },
                                    credentialManager,
                                    context.getString(R.string.server_client_id)
                                )
                            }
                            if (idToken.isBlank()) {
                                message = "No Google ID token received. Please try again."
                            } else {
                                val user = authenticateWithBackend(idToken)
                                onSignedIn(idToken, user)
                            }
                        } catch (exception: GetCredentialCancellationException) {
                            message = "Sign-in cancelled."
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            message = "Google sign-in failed. Please try again."
                        } finally {
                            loading = false
                        }
                    }
                }
            ) {
                Text(text = if (loading) "Signing in…" else "Sign in with Google")
            }
            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private suspend fun requestGoogleIdToken(
    activity: Activity,
    credentialManager: CredentialManager,
    serverClientId: String
): String {
    val option = GetSignInWithGoogleOption.Builder(serverClientId)
        .setNonce(generateNonce()).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val result = credentialManager.getCredential(context = activity, request = request)
    return googleIdToken(result.credential)
}

internal fun googleIdToken(credential: Credential): String {
    require(credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        "Unexpected credential type"
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}

private fun generateNonce(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)

    return Base64.encodeToString(
        bytes,
        Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
    )
}
