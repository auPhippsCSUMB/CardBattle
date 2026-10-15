package com.example.cardbattle.api.dataObjects

data class DeckResponse(
    val id: Long,
    val userId: Long,
    val name: String,
    val createdAt: String? = null
)

data class DeckCreateRequest(
    val name: String
)

data class DeckPatchRequest(
    val name: String? = null
)

data class DeckCardResponse(
    val id: Long,
    val deckId: Long,
    val cardId: Long,
    val userId: Long,
    val type: CardType,
    val card: CardResponse? = null
)

data class DeckCardCreateRequest(
    val cardId: Long,
    val userId: Long,
    val type: CardType
)

data class DeckCardPatchRequest(
    val type: CardType? = null
)
