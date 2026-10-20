package com.example.cardbattle.api.user

import com.example.cardbattle.api.dataObjects.UserResponse
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val users: UserRepository) {

    @PutMapping("/me/display-name")
    fun updateDisplayName(
        @RequestBody request: UpdateDisplayNameRequest,
        authentication: JwtAuthenticationToken
    ): UserResponse {
        val newName = request.displayName.trim()
        if (newName.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name cannot be blank")
        }

        val user = users.findByGoogleSubject(authentication.token.subject)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")

        user.displayName = newName
        val saved = users.save(user)

        return UserResponse(saved.id, saved.email, saved.displayName, saved.isAdmin)
    }
}

data class UpdateDisplayNameRequest(val displayName: String)
