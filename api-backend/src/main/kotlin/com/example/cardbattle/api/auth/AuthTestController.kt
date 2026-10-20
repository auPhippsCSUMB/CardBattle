package com.example.cardbattle.api.auth

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthTestController {

    @GetMapping("/me")
    fun currentUser(authentication: JwtAuthenticationToken): Map<String, Any?> {
        val token = authentication.token

        return mapOf(
                "googleSubject" to token.subject,
                "email" to token.getClaimAsString("email"),
                "name" to token.getClaimAsString("name")
        )
    }
}
