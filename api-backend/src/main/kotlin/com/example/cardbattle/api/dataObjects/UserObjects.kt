package com.example.cardbattle.api.dataObjects

data class UserResponse(
    val id: Int?,
    val email: String,
    val displayName: String,
    val isAdmin: Boolean
)

class UserAdminPatchRequest {
    @get:com.fasterxml.jackson.annotation.JsonIgnore
    var adminProvided: Boolean = false
        private set

    @get:com.fasterxml.jackson.annotation.JsonProperty("isAdmin")
    @set:com.fasterxml.jackson.annotation.JsonProperty("isAdmin")
    var isAdmin: Boolean? = null
        set(value) {
            adminProvided = true
            field = value
        }
}
