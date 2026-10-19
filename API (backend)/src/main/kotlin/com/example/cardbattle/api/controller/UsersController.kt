package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.dataObjects.StatsResponse
import com.example.cardbattle.api.dataObjects.UserAdminPatchRequest
import com.example.cardbattle.api.dataObjects.UserResponse
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
class UsersController {

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMyProfile(): Unit = TODO("Implement DELETE /api/v1/users/me")

    @GetMapping
    fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) displayName: String?,
        @RequestParam(required = false, defaultValue = "displayName,asc") sort: String
    ): PageResponse<UserResponse> = TODO("Implement GET /api/v1/users")

    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: Int): UserResponse = TODO("Implement GET /api/v1/users/{userId}")

    @PatchMapping("/{userId}")
    fun updateUser(
        @PathVariable userId: Int,
        @RequestBody request: UserAdminPatchRequest
    ): UserResponse = TODO("Implement PATCH /api/v1/users/{userId}")

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable userId: Int): Unit = TODO("Implement DELETE /api/v1/users/{userId}")

    @GetMapping("/{userId}/stats")
    fun getUserStats(@PathVariable userId: Int): StatsResponse = TODO("Implement GET /api/v1/users/{userId}/stats")
}
