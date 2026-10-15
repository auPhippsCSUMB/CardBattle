package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.dataObjects.StatsResponse
import com.example.cardbattle.api.dataObjects.UserAdminPatchRequest
import com.example.cardbattle.api.dataObjects.UserCreateRequest
import com.example.cardbattle.api.dataObjects.UserProfilePatchRequest
import com.example.cardbattle.api.dataObjects.UserResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UsersController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@RequestBody request: UserCreateRequest): UserResponse = UserResponse(
        id = 7L,
        email = request.email,
        displayName = request.displayName,
        role = StubDataFactory.sampleUser().role,
        isAdmin = false
    )

    @GetMapping("/me")
    fun getMyProfile(): UserResponse = StubDataFactory.sampleUser()

    @PatchMapping("/me")
    fun updateMyProfile(@RequestBody request: UserProfilePatchRequest): UserResponse {
        val sample = StubDataFactory.sampleUser()
        return sample.copy(displayName = request.displayName ?: sample.displayName)
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMyProfile() {
    }

    @GetMapping
    fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) displayName: String?,
        @RequestParam(required = false, defaultValue = "displayName,asc") sort: String
    ): PageResponse<UserResponse> {
        val user = StubDataFactory.sampleUser().copy(
            displayName = displayName ?: StubDataFactory.sampleUser().displayName
        )
        return StubDataFactory.page(listOf(user.copy(email = "${sort.substringBefore(',')}@example.com")), page, size)
    }

    @GetMapping("/{userId}")
    fun getUser(@PathVariable userId: Long): UserResponse = StubDataFactory.sampleUser(userId)

    @PatchMapping("/{userId}")
    fun updateUser(
        @PathVariable userId: Long,
        @RequestBody request: UserAdminPatchRequest
    ): UserResponse {
        val sample = StubDataFactory.sampleUser(userId)
        return sample.copy(
            role = request.role ?: sample.role,
            isAdmin = request.isAdmin ?: sample.isAdmin
        )
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable userId: Long) {
    }

    @GetMapping("/{userId}/stats")
    fun getUserStats(@PathVariable userId: Long): StatsResponse = StubDataFactory.sampleStats()
}
