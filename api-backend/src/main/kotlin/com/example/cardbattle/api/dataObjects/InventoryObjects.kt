package com.example.cardbattle.api.dataObjects

import com.fasterxml.jackson.annotation.JsonIgnore

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

class OwnershipPatchRequest {
    @get:JsonIgnore
    var valueProvided: Boolean = false
        private set

    var value: Int? = null
        set(newValue) {
            valueProvided = true
            field = newValue
        }
}
