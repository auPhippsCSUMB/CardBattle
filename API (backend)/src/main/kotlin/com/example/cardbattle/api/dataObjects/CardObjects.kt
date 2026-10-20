package com.example.cardbattle.api.dataObjects

import com.fasterxml.jackson.annotation.JsonIgnore

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

class CardPatchRequest {
    // Jackson calls a setter only when the JSON property is present, even if its value is null.
    // This internal set is not a JSON field callers can supply.
    @get:JsonIgnore
    val providedFields = mutableSetOf<String>()

    var name: String? = null
        set(value) {
            providedFields.add("name")
            field = value
        }

    var imgURL: String? = null
        set(value) {
            providedFields.add("imgURL")
            field = value
        }

    var description: String? = null
        set(value) {
            providedFields.add("description")
            field = value
        }

    var type: CardType? = null
        set(value) {
            providedFields.add("type")
            field = value
        }
}
