package com.example.cardbattle.api.dataObjects

data class OwnershipResponse(
    val id: Long,
    val userId: Long,
    val cardId: Long,
    val value: Int?,
    val card: CardResponse? = null
)

data class OwnershipCreateRequest(
    val userId: Long,
    val cardId: Long,
    val value: Int?
)

data class OwnershipPatchRequest(
    val value: Int? = null
)
