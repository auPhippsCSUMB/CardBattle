package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.BattleResponse
import com.example.cardbattle.api.dataObjects.CardResponse
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.dataObjects.DeckCardResponse
import com.example.cardbattle.api.dataObjects.DeckResponse
import com.example.cardbattle.api.dataObjects.OwnershipResponse
import com.example.cardbattle.api.dataObjects.PageMeta
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.dataObjects.Role
import com.example.cardbattle.api.dataObjects.StatsResponse
import com.example.cardbattle.api.dataObjects.UserResponse

object StubDataFactory {
    fun sampleUser(id: Long = 7L): UserResponse = UserResponse(
        id = id,
        email = "player$id@example.com",
        displayName = "CardDuelist$id",
        role = Role.USER,
        isAdmin = false
    )

    fun sampleCard(id: Long = 42L, authorId: Long = 7L): CardResponse = CardResponse(
        id = id,
        name = "Ironclad Vanguard",
        imgURL = "https://example.com/cards/$id.png",
        description = "Defensive powerhouse equipped with tower shield",
        type = CardType.MAGE,
        authorId = authorId,
        createdAt = "2026-10-01T12:00:00Z"
    )

    fun sampleOwnership(id: Long = 19L, userId: Long = 7L, cardId: Long = 42L): OwnershipResponse = OwnershipResponse(
        id = id,
        userId = userId,
        cardId = cardId,
        value = 2,
        card = sampleCard(cardId, userId)
    )

    fun sampleDeck(id: Long = 3L, userId: Long = 7L): DeckResponse = DeckResponse(
        id = id,
        userId = userId,
        name = "Starter Mage Deck",
        createdAt = "2026-10-03T09:30:00Z"
    )

    fun sampleDeckCard(id: Long = 12L, deckId: Long = 3L, userId: Long = 7L, cardId: Long = 42L): DeckCardResponse =
        DeckCardResponse(
            id = id,
            deckId = deckId,
            cardId = cardId,
            userId = userId,
            type = CardType.MAGE,
            card = sampleCard(cardId, userId)
        )

    fun sampleBattle(id: Long = 25L): BattleResponse = BattleResponse(
        id = id,
        winningUser = 7L,
        losingUser = 9L,
        winningCard = 19L,
        losingCard = 22L,
        occurredAt = "2026-10-06T18:45:00Z"
    )

    fun sampleStats(): StatsResponse = StatsResponse(
        wins = 18,
        losses = 7,
        totalBattles = 25,
        winRate = 72.0
    )

    fun <T> page(content: List<T>, page: Int, size: Int): PageResponse<T> = PageResponse(
        content = content,
        page = PageMeta(
            size = size,
            number = page,
            totalElements = content.size.toLong(),
            totalPages = if (content.isEmpty()) 0 else 1
        )
    )
}
