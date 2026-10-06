package com.example.cardbattle.ui.login

import android.app.Activity
import android.util.Base64
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.cardbattle.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import java.security.SecureRandom
import android.util.Log


private const val TAG = "GoogleAuth"
@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val credentialManager = remember {
        CredentialManager.create(context)
    }

    Button(
        onClick = {
            if (activity == null) return@Button

            scope.launch {
                try {
                    val googleOption = GetSignInWithGoogleOption.Builder(
                        serverClientId = context.getString(R.string.server_client_id)
                    )
                        .setNonce(generateNonce())
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleOption)
                        .build()

                    val result = credentialManager.getCredential(
                        context = activity,
                        request = request
                    )

                    val credential = result.credential

                    if (
                        credential is CustomCredential &&
                        credential.type ==
                        GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        val googleCredential =
                            GoogleIdTokenCredential.createFrom(credential.data)

                        val idToken = googleCredential.idToken

                        Log.d(TAG, "Google ID token received")
//                        Log.d(TAG, "Token preview: ${idToken.take(20)}...")
                        Log.d(TAG, "FULL_TOKEN_FOR_LOCAL_TEST=$idToken")
                    }
                } catch (exception: Exception) {
                    Log.e(TAG, "Google sign-in failed", exception)
                }
            }
        }
    ) {
        Text(text = "Sign in with Google")
    }
}

private fun generateNonce(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)

    return Base64.encodeToString(
        bytes,
        Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
    )
}