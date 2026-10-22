package com.example.cardbattle.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Serializable
data class ApiPage<T>(
    val content: List<T>,
    val page: ApiPageInfo,
)

@Serializable
data class ApiPageInfo(
    val size: Int,
    val number: Int,
    val totalElements: Long,
    val totalPages: Int,
)

@Serializable
data class ApiIdentity(
    val googleSubject: String,
    val email: String? = null,
    val name: String? = null,
)

@Serializable
data class ApiStats(
    val wins: Int,
    val losses: Int,
    val totalBattles: Int,
    val winRate: Double,
)

@Serializable
data class ApiArtResponse(
    val imgURL: String,
)

@Serializable
data class ApiDeck(
    val id: Long,
    val userId: Long,
    val name: String,
    val createdAt: String? = null,
)

@Serializable
data class ApiInventoryEntry(
    val id: Long,
    val userId: Long,
    val cardId: Long,
    val value: Int? = null,
    val card: CardResponse? = null,
)

@Serializable
data class ApiDeckEntry(
    val id: Long,
    val deckId: Long,


    @SerialName("cardId")
    val inventoryId: Long,

    val userId: Long,
    val type: CardType? = null,
    val card: CardResponse? = null,
)

@Serializable
private data class AddDeckEntryRequest(
    @SerialName("cardId")
    val inventoryId: Long,
    val userId: Long,
    val type: CardType,
)

@Serializable
data class ApiBattle(
    val id: Long,
    val winningUser: Long,
    val losingUser: Long,
    val winningCard: Long?,
    val losingCard: Long?,
    val occurredAt: String? = null,
)

@Serializable
data class ApiBattleRequest(
    val winningUser: Long,
    val losingUser: Long,
    val winningCard: Long,
    val losingCard: Long,
)

class CardBattleClient(
    private val tokenProvider: () -> String,
    private val backendUrl: String =
        "https://cardbattle-e5vf.onrender.com",
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    companion object {
        private val JSON =
            "application/json; charset=utf-8".toMediaType()

        private val PNG = "image/png".toMediaType()

        private val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .build()
    }


    suspend fun signIn(): UserProfile =
        signInAndGetUser(tokenProvider(), backendUrl)

    suspend fun identity(): ApiIdentity =
        read("GET", "/api/v1/auth/me")

    suspend fun changeDisplayName(name: String): UserProfile =
        updateDisplayName(tokenProvider(), name, backendUrl)


    suspend fun listMyCards(
        page: Int = 0,
        size: Int = 20,
        type: CardType? = null,
    ): ApiPage<CardResponse> = read(
        "GET",
        query(
            "/api/v1/cards",
            "page" to page,
            "size" to size,
            "type" to type?.name,
            "sort" to "id,desc",
        ),
    )

    suspend fun createCard(
        name: String,
        type: CardType,
    ): CardResponse {
        require(name.trim().length in 1..150)

        return read(
            "POST",
            "/api/v1/cards",
            body = json.encodeToString(
                CardCreateRequest(
                    name = name.trim(),
                    type = type,
                )
            ),
            expected = 201,
        )
    }

    suspend fun getMyCard(cardId: Long): CardResponse =
        read("GET", "/api/v1/cards/$cardId")

    suspend fun updateCard(
        cardId: Long,
        name: String,
        type: CardType,
    ): CardResponse {
        require(name.trim().length in 1..150)

        return read(
            "PATCH",
            "/api/v1/cards/$cardId",
            body = buildJsonObject {
                put("name", name.trim())
                put("type", type.name)
            }.toString(),
        )
    }

    suspend fun deleteCard(cardId: Long) {
        send(
            method = "DELETE",
            path = "/api/v1/cards/$cardId",
            expected = 204,
        )
    }

    suspend fun listCatalog(
        page: Int = 0,
        size: Int = 20,
        type: CardType? = null,
        authorId: Long? = null,
    ): ApiPage<CardResponse> = read(
        "GET",
        query(
            "/api/v1/allCards",
            "page" to page,
            "size" to size,
            "type" to type?.name,
            "author" to authorId,
            "sort" to "id,desc",
        ),
    )

    suspend fun getCatalogCard(cardId: Long): CardResponse =
        read("GET", "/api/v1/allCards/$cardId")

    suspend fun randomCard(type: CardType? = null): CardResponse =
        read(
            "GET",
            query(
                "/api/v1/randomCard",
                "type" to type?.name,
            ),
        )

    suspend fun cardStats(cardId: Long): ApiStats =
        read("GET", "/api/v1/cards/$cardId/stats")


    suspend fun uploadArt(
        cardId: Long,
        pngBytes: ByteArray,
    ): ApiArtResponse {
        require(
            pngBytes.isNotEmpty() &&
                    pngBytes.size <= 1_048_576
        ) {
            "Drawing must contain between 1 byte and 1 MiB."
        }

        val response = send(
            method = "PUT",
            path = "/api/v1/cards/$cardId/art",
            body = pngBytes.toRequestBody(PNG),
        )

        return json.decodeFromString(response.decodeToString())
    }

    suspend fun downloadArt(cardId: Long): ByteArray =
        send(
            method = "GET",
            path = "/api/v1/cards/$cardId/art",
            accept = "image/png",
        )


    // BACKEND TODO: listDecks
    suspend fun listDecks(
        userId: Long,
        page: Int = 0,
        size: Int = 20,
    ): ApiPage<ApiDeck> = read(
        "GET",
        query(
            "/api/v1/decks",
            "userId" to userId,
            "page" to page,
            "size" to size,
        ),
    )

    suspend fun createDeck(name: String): ApiDeck =
        read(
            "POST",
            "/api/v1/decks",
            body = buildJsonObject {
                put("name", name.trim())
            }.toString(),
            expected = 201,
        )

    suspend fun getDeck(deckId: Long): ApiDeck =
        read("GET", "/api/v1/decks/$deckId")

    // BACKEND TODO: updateDeck
    suspend fun renameDeck(
        deckId: Long,
        name: String,
    ): ApiDeck = read(
        "PATCH",
        "/api/v1/decks/$deckId",
        body = buildJsonObject {
            put("name", name.trim())
        }.toString(),
    )

    suspend fun deleteDeck(deckId: Long) {
        send(
            method = "DELETE",
            path = "/api/v1/decks/$deckId",
            expected = 204,
        )
    }

    // BACKEND TODO: listDeckCards
    suspend fun deckEntries(deckId: Long): List<ApiDeckEntry> =
        read("GET", "/api/v1/decks/$deckId/cards")

    suspend fun addDeckEntry(
        deckId: Long,
        inventoryId: Long,
        userId: Long,
        type: CardType,
    ): ApiDeckEntry = read(
        "POST",
        "/api/v1/decks/$deckId/cards",
        body = json.encodeToString(
            AddDeckEntryRequest(
                inventoryId = inventoryId,
                userId = userId,
                type = type,
            )
        ),
        expected = 201,
    )


    suspend fun updateDeckEntryClass(
        deckId: Long,
        membershipId: Long,
        type: CardType,
    ): ApiDeckEntry = read(
        "PATCH",
        "/api/v1/decks/$deckId/cards/$membershipId",
        body = buildJsonObject {
            put("type", type.name)
        }.toString(),
    )

    suspend fun removeDeckEntry(
        deckId: Long,
        membershipId: Long,
    ) {
        send(
            method = "DELETE",
            path = "/api/v1/decks/$deckId/cards/$membershipId",
            expected = 204,
        )
    }


    suspend fun listInventory(
        userId: Long,
        page: Int = 0,
        size: Int = 20,
        type: CardType? = null,
    ): ApiPage<ApiInventoryEntry> = read(
        "GET",
        query(
            "/api/v1/inventory",
            "userId" to userId,
            "page" to page,
            "size" to size,
            "type" to type?.name,
        ),
    )

    suspend fun createOwnership(
        userId: Long,
        cardId: Long,
        value: Int,
    ): ApiInventoryEntry = read(
        "POST",
        "/api/v1/inventory",
        body = buildJsonObject {
            put("userId", userId)
            put("cardId", cardId)
            put("value", value)
        }.toString(),
        expected = 201,
    )

    suspend fun updateOwnership(
        ownershipId: Long,
        value: Int,
    ): ApiInventoryEntry = read(
        "PATCH",
        "/api/v1/inventory/$ownershipId",
        body = buildJsonObject {
            put("value", value)
        }.toString(),
    )

    suspend fun deleteOwnership(ownershipId: Long) {
        send(
            method = "DELETE",
            path = "/api/v1/inventory/$ownershipId",
            expected = 204,
        )
    }



    suspend fun listBattles(
        userId: Long,
        page: Int = 0,
        size: Int = 20,
    ): ApiPage<ApiBattle> = read(
        "GET",
        query(
            "/api/v1/battles",
            "userId" to userId,
            "page" to page,
            "size" to size,
        ),
    )

    suspend fun recordBattle(
        result: ApiBattleRequest,
    ): ApiBattle = read(
        "POST",
        "/api/v1/battles",
        body = json.encodeToString(result),
        expected = 201,
    )

    suspend fun getBattle(battleId: Long): ApiBattle =
        read("GET", "/api/v1/battles/$battleId")


    suspend fun deleteMyProfile() {
        send(
            method = "DELETE",
            path = "/api/v1/users/me",
            expected = 204,
        )
    }

    suspend fun listUsers(
        page: Int = 0,
        size: Int = 20,
        displayName: String? = null,
    ): ApiPage<UserProfile> = read(
        "GET",
        query(
            "/api/v1/users",
            "page" to page,
            "size" to size,
            "displayName" to displayName,
        ),
    )

    suspend fun getUser(userId: Long): UserProfile =
        read("GET", "/api/v1/users/$userId")

    suspend fun updateUserAdminStatus(
        userId: Long,
        isAdmin: Boolean,
    ): UserProfile = read(
        "PATCH",
        "/api/v1/users/$userId",
        body = buildJsonObject {
            put("isAdmin", isAdmin)
        }.toString(),
    )

    suspend fun deleteUser(userId: Long) {
        send(
            method = "DELETE",
            path = "/api/v1/users/$userId",
            expected = 204,
        )
    }

    suspend fun userStats(userId: Long): ApiStats =
        read("GET", "/api/v1/users/$userId/stats")



    private suspend inline fun <reified T> read(
        method: String,
        path: String,
        body: String? = null,
        expected: Int = 200,
    ): T {
        val bytes = send(
            method = method,
            path = path,
            body = body?.toRequestBody(JSON),
            expected = expected,
        )

        return json.decodeFromString(bytes.decodeToString())
    }

    private suspend fun send(
        method: String,
        path: String,
        body: RequestBody? = null,
        expected: Int = 200,
        accept: String = "application/json",
    ): ByteArray {
        val token = tokenProvider()
        check(token.isNotBlank()) { "Sign in first." }

        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(backendUrl.trimEnd('/') + path)
                .header("Authorization", "Bearer $token")
                .header("Accept", accept)
                .method(method, body)
                .build()

            http.newCall(request).execute().use { response ->
                if (response.code != expected) {
                    throw BackendHttpException(response.code)
                }

                response.body?.bytes() ?: byteArrayOf()
            }
        }
    }

    private fun query(
        path: String,
        vararg parameters: Pair<String, Any?>,
    ): String {
        val encoded = parameters
            .filter { it.second != null }
            .joinToString("&") { (key, value) ->
                URLEncoder.encode(key, "UTF-8") + "=" +
                        URLEncoder.encode(value.toString(), "UTF-8")
            }

        return if (encoded.isEmpty()) path else "$path?$encoded"
    }
}