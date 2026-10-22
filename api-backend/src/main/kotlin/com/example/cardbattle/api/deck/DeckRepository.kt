package com.example.cardbattle.api.deck

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface DeckRepository : JpaRepository<DeckEntity, Int> {
    fun findByIdAndUserGoogleSubject(id: Int, googleSubject: String): DeckEntity?

    @Query("""SELECT d FROM DeckEntity d
        WHERE d.user.googleSubject = :googleSubject AND (:userId IS NULL OR d.user.id = :userId)""")
    fun findMyDecks(googleSubject: String, userId: Int?, pageable: Pageable): Page<DeckEntity>

    // Remove membership rows only, not the underlying cards or inventory copies.
    @Modifying
    @Query(value = """DELETE FROM public."Card_in_deck" WHERE deckid = :deckId""", nativeQuery = true)
    fun deleteDeckEntries(@Param("deckId") deckId: Int): Int
}
