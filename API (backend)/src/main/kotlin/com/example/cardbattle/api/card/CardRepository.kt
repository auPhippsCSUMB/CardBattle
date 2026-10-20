package com.example.cardbattle.api.card

import com.example.cardbattle.api.dataObjects.CardType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CardRepository : JpaRepository<CardEntity, Int> {
    fun findByIdAndAuthorGoogleSubject(id: Int, googleSubject: String): CardEntity?

    @Query("""SELECT c FROM CardEntity c
        WHERE c.author.googleSubject = :googleSubject AND (:type IS NULL OR c.type = :type)""")
    fun findMyCards(googleSubject: String, type: CardType?, pageable: Pageable): Page<CardEntity>

    @Query("""SELECT c FROM CardEntity c
        WHERE (:type IS NULL OR c.type = :type) AND (:author IS NULL OR c.author.id = :author)""")
    fun findAllCards(type: CardType?, author: Int?, pageable: Pageable): Page<CardEntity>

    @Query(value = """SELECT * FROM public."Card" ORDER BY RANDOM() LIMIT 1""", nativeQuery = true)
    fun findRandomCard(): CardEntity?

    @Query(value = """SELECT * FROM public."Card" WHERE type = :type ORDER BY RANDOM() LIMIT 1""", nativeQuery = true)
    fun findRandomCardByType(type: String): CardEntity?
}
