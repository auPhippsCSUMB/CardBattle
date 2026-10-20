package com.example.cardbattle.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private const val DEFAULT_BACKEND_URL = "https://cardbattle-e5vf.onrender.com"
//private const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8080"

suspend fun verifyWithBackend(idToken: String): Boolean =
    withContext(Dispatchers.IO) {
        val connection = URL("$DEFAULT_BACKEND_URL/api/v1/auth/google")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty(
                "Authorization",
                "Bearer $idToken"
            )
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val status = connection.responseCode
            Log.d("CardBattleApi", "Sign-in HTTP status: $status")
            status == 200 || status == 201
        } finally {
            connection.disconnect()
        }
    }

@Serializable
data class UserProfile(
    val id: Int?,
    val email: String,
    val displayName: String,
    val isAdmin: Boolean
)

suspend fun signInAndGetUser(
    idToken: String,
    backendUrl: String = DEFAULT_BACKEND_URL,
): UserProfile =
    withContext(Dispatchers.IO) {
        val connection = URL("$backendUrl/api/v1/auth/google")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val status = connection.responseCode
            if (status != 200 && status != 201) {
                throw IOException("Backend sign-in returned HTTP $status")
            }

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            Json.decodeFromString<UserProfile>(json)
        } finally {
            connection.disconnect()
        }
    }
@Serializable
private data class UpdateDisplayNameRequest(val displayName: String)

class BackendHttpException(val statusCode: Int) :
    IOException("Backend returned HTTP $statusCode")

suspend fun updateDisplayName(
    idToken: String,
    newName: String,
    backendUrl: String = DEFAULT_BACKEND_URL,
): UserProfile =
    withContext(Dispatchers.IO) {
        val connection = URL("$backendUrl/api/v1/users/me/display-name")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val body = Json.encodeToString(UpdateDisplayNameRequest(newName))
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            if (status != 200) throw BackendHttpException(status)

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            Json.decodeFromString<UserProfile>(json)
        } finally {
            connection.disconnect()
        }
    }
