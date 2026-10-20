package com.example.cardbattle.api.deck

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeckRepository : JpaRepository<DeckEntity, Int> {
    fun findByIdAndUserGoogleSubject(id: Int, googleSubject: String): DeckEntity?

    // Remove membership rows only, not the underlying cards or inventory copies.
    @Modifying
    @Query(value = """DELETE FROM public."Card_in_deck" WHERE deckid = :deckId""", nativeQuery = true)
    fun deleteDeckEntries(@Param("deckId") deckId: Int): Int
}
