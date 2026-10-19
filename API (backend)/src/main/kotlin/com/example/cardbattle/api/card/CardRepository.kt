package com.example.cardbattle.api.card

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CardRepository : JpaRepository<CardEntity, Int> {
    fun findByIdAndAuthorGoogleSubject(id: Int, googleSubject: String): CardEntity?

    @Query(value = """SELECT * FROM public."Card" ORDER BY RANDOM() LIMIT 1""", nativeQuery = true)
    fun findRandomCard(): CardEntity?

    @Query(value = """SELECT * FROM public."Card" WHERE type = :type ORDER BY RANDOM() LIMIT 1""", nativeQuery = true)
    fun findRandomCardByType(type: String): CardEntity?
}
