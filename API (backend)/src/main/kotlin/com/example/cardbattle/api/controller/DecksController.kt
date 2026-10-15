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
    ): PageResponse<DeckResponse> {
        val deck = StubDataFactory.sampleDeck(userId = userId ?: 7L).copy(
            name = if (sort.startsWith("name")) "Starter Mage Deck" else StubDataFactory.sampleDeck().name
        )
        return StubDataFactory.page(listOf(deck), page, size)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDeck(@RequestBody request: DeckCreateRequest): DeckResponse =
        StubDataFactory.sampleDeck().copy(name = request.name)

    @GetMapping("/{deckId}")
    fun getDeck(@PathVariable deckId: Long): DeckResponse = StubDataFactory.sampleDeck(deckId)

    @PatchMapping("/{deckId}")
    fun updateDeck(
        @PathVariable deckId: Long,
        @RequestBody request: DeckPatchRequest
    ): DeckResponse {
        val sample = StubDataFactory.sampleDeck(deckId)
        return sample.copy(name = request.name ?: sample.name)
    }

    @DeleteMapping("/{deckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDeck(@PathVariable deckId: Long) {
    }

    @GetMapping("/{deckId}/cards")
    fun listDeckCards(@PathVariable deckId: Long): List<DeckCardResponse> =
        listOf(StubDataFactory.sampleDeckCard(deckId = deckId))

    @PostMapping("/{deckId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    fun addCardToDeck(
        @PathVariable deckId: Long,
        @RequestBody request: DeckCardCreateRequest
    ): DeckCardResponse = StubDataFactory.sampleDeckCard(
        deckId = deckId,
        userId = request.userId,
        cardId = request.cardId
    ).copy(type = request.type)

    @PatchMapping("/{deckId}/cards/{cardInDeckId}")
    fun updateDeckCard(
        @PathVariable deckId: Long,
        @PathVariable cardInDeckId: Long,
        @RequestBody request: DeckCardPatchRequest
    ): DeckCardResponse {
        val sample = StubDataFactory.sampleDeckCard(id = cardInDeckId, deckId = deckId)
        return sample.copy(type = request.type ?: sample.type)
    }

    @DeleteMapping("/{deckId}/cards/{cardInDeckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeCardFromDeck(
        @PathVariable deckId: Long,
        @PathVariable cardInDeckId: Long
    ) {
    }
}
