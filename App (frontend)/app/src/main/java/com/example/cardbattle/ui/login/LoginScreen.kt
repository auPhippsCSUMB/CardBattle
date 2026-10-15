package com.example.cardbattle.ui.login

import android.app.Activity
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.cardbattle.R
import com.example.cardbattle.data.LocalAuthManager
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    onNavigateToSignUp: () -> Unit = {},
    requestIdToken: (suspend () -> String)? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val authManager = remember { LocalAuthManager(context) }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Welcome",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    message = ""
                },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    message = ""
                },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (message.isNotEmpty()) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Normal login button
            Button(
                onClick = {
                    when {
                        username.isBlank() || password.isBlank() -> {
                            message = "Please enter username and password"
                        }
                        else -> {
                            val result = authManager.login(username.trim(), password)
                            result.onSuccess {
                                onLoginSuccess()
                            }.onFailure { err ->
                                message = err.message ?: "Login failed"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
            }

            TextButton(onClick = onNavigateToSignUp) {
                Text("Don't have an account? Create Account")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Google Sign In button
            OutlinedButton(
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
                                // Extract the first name directly from the idToken payload
                                val googleName = extractNameFromIdToken(idToken)
                                authManager.setActiveUser(googleName)
                                onLoginSuccess()
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
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (loading) "Signing in…" else "Sign in with Google")
            }
        }
    }
}
private fun extractNameFromIdToken(idToken: String): String {
    return try {
        val parts = idToken.split(".")
        if (parts.size >= 2) {
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING)
            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
            when {
                json.has("given_name") -> json.getString("given_name")
                json.has("name") -> json.getString("name")
                json.has("email") -> json.getString("email").substringBefore("@")
                else -> "Player"
            }
        } else {
            "Player"
        }
    } catch (e: Exception) {
        "Player"
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