package com.example.cardbattle.ui.login

import android.app.Activity
import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.cardbattle.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.security.SecureRandom

@Composable
fun LoginScreen(
    requestIdToken: (suspend () -> String)? = null,
    onTokenReceived: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val credentialManager = remember(context) {
        CredentialManager.create(context)
    }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(Modifier.safeDrawingPadding()) {
        Button(
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
                            onTokenReceived(idToken)
                            message = "Google ID token received. Backend verification is still required."
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
        if (message.isNotEmpty()) Text(message)
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
