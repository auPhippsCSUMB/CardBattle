package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.dataObjects.StatsResponse
import com.example.cardbattle.api.dataObjects.UserAdminPatchRequest
import com.example.cardbattle.api.dataObjects.UserResponse
import com.example.cardbattle.api.user.UserService
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UsersController(private val users: UserService) {

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMyProfile(authentication: JwtAuthenticationToken) = users.deleteMyProfile(authentication.token.subject)

    @GetMapping
    fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) displayName: String?,
        @RequestParam(required = false, defaultValue = "displayName,asc") sort: String,
        authentication: JwtAuthenticationToken,
    ): PageResponse<UserResponse> = users.listUsers(authentication.token.subject, page, size, displayName, sort)

    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: Int, authentication: JwtAuthenticationToken): UserResponse =
        users.getUser(authentication.token.subject, userId)

    @PatchMapping("/{userId}")
    fun updateUser(
        @PathVariable userId: Int,
        @RequestBody request: UserAdminPatchRequest,
        authentication: JwtAuthenticationToken,
    ): UserResponse = users.updateUser(authentication.token.subject, userId, request)

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable userId: Int, authentication: JwtAuthenticationToken) =
        users.deleteUser(authentication.token.subject, userId)

    @GetMapping("/{userId}/stats")
    fun getUserStats(@PathVariable userId: Int): StatsResponse = users.getUserStats(userId)
}
