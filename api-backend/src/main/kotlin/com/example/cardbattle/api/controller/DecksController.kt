package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.DeckCardCreateRequest
import com.example.cardbattle.api.dataObjects.DeckCardPatchRequest
import com.example.cardbattle.api.dataObjects.DeckCardResponse
import com.example.cardbattle.api.dataObjects.DeckCreateRequest
import com.example.cardbattle.api.dataObjects.DeckPatchRequest
import com.example.cardbattle.api.dataObjects.DeckResponse
import com.example.cardbattle.api.dataObjects.PageResponse
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
@RequestMapping("/api/v1/decks")
class DecksController {

    @GetMapping
    fun listDecks(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false, defaultValue = "name,asc") sort: String
    ): PageResponse<DeckResponse> = TODO("Implement GET /api/v1/decks")

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDeck(@RequestBody request: DeckCreateRequest): DeckResponse = TODO("Implement POST /api/v1/decks")

    @GetMapping("/{deckId}")
    fun getDeck(@PathVariable deckId: Long): DeckResponse = TODO("Implement GET /api/v1/decks/{deckId}")

    @PatchMapping("/{deckId}")
    fun updateDeck(
        @PathVariable deckId: Long,
        @RequestBody request: DeckPatchRequest
    ): DeckResponse = TODO("Implement PATCH /api/v1/decks/{deckId}")

    @DeleteMapping("/{deckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDeck(@PathVariable deckId: Long): Unit = TODO("Implement DELETE /api/v1/decks/{deckId}")

    @GetMapping("/{deckId}/cards")
    fun listDeckCards(@PathVariable deckId: Long): List<DeckCardResponse> = TODO("Implement GET /api/v1/decks/{deckId}/cards")

    @PostMapping("/{deckId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    fun addCardToDeck(
        @PathVariable deckId: Long,
        @RequestBody request: DeckCardCreateRequest
    ): DeckCardResponse = TODO("Implement POST /api/v1/decks/{deckId}/cards")

    @PatchMapping("/{deckId}/cards/{cardInDeckId}")
    fun updateDeckCard(
        @PathVariable deckId: Long,
        @PathVariable cardInDeckId: Long,
        @RequestBody request: DeckCardPatchRequest
    ): DeckCardResponse = TODO("Implement PATCH /api/v1/decks/{deckId}/cards/{cardInDeckId}")

    @DeleteMapping("/{deckId}/cards/{cardInDeckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeCardFromDeck(
        @PathVariable deckId: Long,
        @PathVariable cardInDeckId: Long
    ): Unit = TODO("Implement DELETE /api/v1/decks/{deckId}/cards/{cardInDeckId}")
}
