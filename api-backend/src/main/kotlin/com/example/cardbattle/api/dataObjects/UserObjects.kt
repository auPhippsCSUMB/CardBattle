package com.example.cardbattle.api.dataObjects

data class UserResponse(
    val id: Int?,
    val email: String,
    val displayName: String,
    val isAdmin: Boolean
)

data class UserAdminPatchRequest(
    val isAdmin: Boolean? = null
)
