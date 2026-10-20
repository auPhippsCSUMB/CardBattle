package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.BattleResponse
import com.example.cardbattle.api.dataObjects.CardResponse
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.dataObjects.DeckCardResponse
import com.example.cardbattle.api.dataObjects.DeckResponse
import com.example.cardbattle.api.dataObjects.OwnershipResponse
import com.example.cardbattle.api.dataObjects.PageMeta
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.dataObjects.StatsResponse
import com.example.cardbattle.api.dataObjects.UserResponse

object StubDataFactory {
    fun sampleUser(id: Int = 7): UserResponse = TODO("Replace with real user stub data for /api/v1/users")

    fun sampleCard(id: Long = 42L, authorId: Long = 7L): CardResponse = TODO("Replace with real card stub data for /api/v1/cards")

    fun sampleOwnership(id: Long = 19L, userId: Long = 7L, cardId: Long = 42L): OwnershipResponse = TODO("Replace with real inventory stub data for /api/v1/inventory")

    fun sampleDeck(id: Long = 3L, userId: Long = 7L): DeckResponse = TODO("Replace with real deck stub data for /api/v1/decks")

    fun sampleDeckCard(id: Long = 12L, deckId: Long = 3L, userId: Long = 7L, cardId: Long = 42L): DeckCardResponse = TODO("Replace with real deck-card stub data for /api/v1/decks/{deckId}/cards")

    fun sampleBattle(id: Long = 25L): BattleResponse = TODO("Replace with real battle stub data for /api/v1/battles")

    fun sampleStats(): StatsResponse = TODO("Replace with real stats stub data for user/card stats endpoints")

    fun <T> page(content: List<T>, page: Int, size: Int): PageResponse<T> = TODO("Replace with real paginated response stub data")
}
