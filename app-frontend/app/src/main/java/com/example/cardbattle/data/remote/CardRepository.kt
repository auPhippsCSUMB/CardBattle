package com.example.cardbattle.data.remote

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NewCardSaveState {
    internal val mutex = Mutex()

    var cardId: Long? = null
        internal set

    var ownershipId: Long? = null
        internal set
}

class CardRepository(
    private val api: CardBattleClient,
) {

    suspend fun loadAuthoredCards(): List<CardResponse> =
        loadAllPages { page ->
            api.listMyCards(page = page, size = 100)
        }.distinctBy { it.id }
            .sortedByDescending { it.id }


    suspend fun loadInventory(
        userId: Long,
    ): List<ApiInventoryEntry> =
        loadAllPages { page ->
            api.listInventory(
                userId = userId,
                page = page,
                size = 100,
            )
        }.distinctBy { it.id }
            .sortedByDescending { it.id }

    suspend fun loadDecks(
        userId: Long,
    ): List<ApiDeck> =
        loadAllPages { page ->
            api.listDecks(
                userId = userId,
                page = page,
                size = 100,
            )
        }.distinctBy { it.id }
            .sortedByDescending { it.id }


    suspend fun saveNewDrawing(
        saveState: NewCardSaveState,
        userId: Long,
        name: String,
        type: CardType,
        pngBytes: ByteArray,
    ): CardResponse = saveState.mutex.withLock {
        validateDrawing(name, pngBytes)
        require(userId > 0) { "Sign in before saving a card." }

        val existingId = saveState.cardId

        val card = if (existingId == null) {
            api.createCard(name, type).also {
                saveState.cardId = it.id
            }
        } else {
            api.updateCard(existingId, name, type)
        }

        api.uploadArt(card.id, pngBytes)

        if (saveState.ownershipId == null) {
            val ownership = api.createOwnership(
                userId = userId,
                cardId = card.id,
                value = 0,
            )

            saveState.ownershipId = ownership.id
        }

        api.getMyCard(card.id)
    }

    suspend fun saveEditedDrawing(
        cardId: Long,
        name: String,
        type: CardType,
        pngBytes: ByteArray,
    ): CardResponse {
        require(cardId > 0)
        validateDrawing(name, pngBytes)

        api.updateCard(cardId, name, type)
        api.uploadArt(cardId, pngBytes)

        return api.getMyCard(cardId)
    }

    suspend fun loadDrawing(cardId: Long): ByteArray =
        api.downloadArt(cardId)

    private fun validateDrawing(
        name: String,
        pngBytes: ByteArray,
    ) {
        require(name.trim().length in 1..150) {
            "Card names must contain 1–150 characters."
        }

        require(
            pngBytes.isNotEmpty() &&
                    pngBytes.size <= 1_048_576
        ) {
            "The drawing must be no larger than 1 MiB."
        }
    }

    private suspend fun <T> loadAllPages(
        fetch: suspend (Int) -> ApiPage<T>,
    ): List<T> {
        val result = mutableListOf<T>()
        var pageNumber = 0

        while (true) {
            val response = fetch(pageNumber)
            result.addAll(response.content)
            pageNumber++

            if (pageNumber >= response.page.totalPages) {
                return result
            }
        }
    }
}