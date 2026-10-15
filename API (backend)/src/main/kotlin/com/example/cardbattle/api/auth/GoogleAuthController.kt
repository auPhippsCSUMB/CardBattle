package com.example.cardbattle.api.auth

import com.example.cardbattle.api.user.UserEntity
import com.example.cardbattle.api.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

//This route is what is called when oauth is used to login
//a new user will be rerouted to findByGoogleSubject to check
//if we need to create a new user or use the existing one
@RestController
@RequestMapping("/api/v1/auth")
class GoogleAuthController(private val users: UserRepository) {
    @PostMapping("/google")
    fun signIn(authentication: JwtAuthenticationToken): ResponseEntity<UserResponse> {
        val token = authentication.token
        val googleSubject = token.subject
        val email = token.getClaimAsString("email") ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Token has no email")
        val name = token.getClaimAsString("name") ?: email
        val existing = users.findByGoogleSubject(googleSubject)
        val user = existing ?: users.save(
            UserEntity(
                googleSubject = googleSubject,
                email = email,
                displayName = name
            )
        )

        val status = if (existing == null) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(
            UserResponse(user.id, user.email, user.displayName, user.isAdmin)
        )
    }
}

data class UserResponse(
    val id: Int?,
    val email: String,
    val displayName: String,
    val isAdmin: Boolean
)