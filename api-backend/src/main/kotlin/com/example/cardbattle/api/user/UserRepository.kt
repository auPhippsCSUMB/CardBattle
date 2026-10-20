package com.example.cardbattle.api.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository :JpaRepository<UserEntity, Int> {
    fun findByGoogleSubject(googleSubject: String): UserEntity?
}