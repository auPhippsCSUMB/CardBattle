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

//private const val DEFAULT_BACKEND_URL = "https://cardbattle-e5vf.onrender.com"
private const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8080"

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
@Serializable
enum class CardType(val label: String) {
    WARRIOR("Warrior"),
    BRAWLER("Brawler"),
    RANGER("Ranger"),
    MAGE("Mage"),
    ROGUE("Rogue"),
}

@Serializable
data class CardCreateRequest(
    val name: String,
    val type: CardType,
    val imgURL: String? = null,
    val description: String? = null,
)

@Serializable
data class CardResponse(
    val id: Long,
    val name: String,
    val imgURL: String?,
    val description: String?,
    val type: CardType,
    val authorId: Long,
)

suspend fun createCard(
    idToken: String,
    request: CardCreateRequest,
    backendUrl: String = DEFAULT_BACKEND_URL,
): CardResponse {
    val cleanRequest = request.copy(name = request.name.trim())

    require(cleanRequest.name.length in 1..150) {
        "Card name must contain 1–150 characters."
    }
    require((cleanRequest.imgURL?.length ?: 0) <= 500) {
        "Image URL must not exceed 500 characters."
    }

    return withContext(Dispatchers.IO) {
        val connection = URL("$backendUrl/api/v1/cards")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty(
                "Authorization",
                "Bearer $idToken",
            )
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=UTF-8",
            )

            val body = Json.encodeToString(cleanRequest)
            connection.outputStream.use {
                it.write(body.toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode != 201) {
                throw BackendHttpException(connection.responseCode)
            }

            val response = connection.inputStream
                .bufferedReader()
                .use { it.readText() }

            Json.decodeFromString<CardResponse>(response)
        } finally {
            connection.disconnect()
        }
    }
}