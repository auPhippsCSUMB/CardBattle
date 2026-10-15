package com.example.cardbattle.api.dataObjects

data class UserResponse(
    val id: Long,
    val email: String,
    val displayName: String,
    val role: Role,
    val isAdmin: Boolean
)

data class UserCreateRequest(
    val email: String,
    val displayName: String,
    val password: String
)

data class UserProfilePatchRequest(
    val displayName: String? = null,
    val password: String? = null
)

data class UserAdminPatchRequest(
    val role: Role? = null,
    val isAdmin: Boolean? = null
)
