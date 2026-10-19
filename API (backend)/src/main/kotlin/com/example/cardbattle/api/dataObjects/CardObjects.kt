package com.example.cardbattle.api.dataObjects

data class CardResponse(
    val id: Long,
    val name: String,
    val imgURL: String?,
    val description: String?,
    val type: CardType,
    val authorId: Long,
)

data class CardCreateRequest(
    val name: String,
    val imgURL: String? = null,
    val description: String? = null,
    val type: CardType
)

data class CardPatchRequest(
    val name: String? = null,
    val imgURL: String? = null,
    val description: String? = null,
    val type: CardType? = null
)
