package com.example.cardbattle.api.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UserRepository :JpaRepository<UserEntity, Int> {
    fun findByGoogleSubject(googleSubject: String): UserEntity?

    @Query("SELECT u FROM UserEntity u WHERE LOCATE(LOWER(:displayName), LOWER(u.displayName)) > 0")
    fun findMatching(displayName: String, pageable: Pageable): Page<UserEntity>

    @Query(value = """SELECT COUNT(*) FROM public."Wins" WHERE winninguser = :userId""", nativeQuery = true)
    fun countWins(userId: Int): Int

    @Query(value = """SELECT COUNT(*) FROM public."Wins" WHERE losinguser = :userId""", nativeQuery = true)
    fun countLosses(userId: Int): Int

    @Query(value = """SELECT COUNT(*) FROM public."Wins"
        WHERE winninguser = :userId OR losinguser = :userId""", nativeQuery = true)
    fun countBattles(userId: Int): Int
}
