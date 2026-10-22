package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.DeckCardCreateRequest
import com.example.cardbattle.api.dataObjects.DeckCardPatchRequest
import com.example.cardbattle.api.dataObjects.DeckCardResponse
import com.example.cardbattle.api.dataObjects.DeckCreateRequest
import com.example.cardbattle.api.dataObjects.DeckPatchRequest
import com.example.cardbattle.api.dataObjects.DeckResponse
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.deck.DeckService
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
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
class DecksController(
    private val deckService: DeckService,
) {

    @GetMapping
    fun listDecks(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) userId: Int?,
        @RequestParam(required = false, defaultValue = "name,asc") sort: String,
        authentication: JwtAuthenticationToken,
    ): PageResponse<DeckResponse> = deckService.listDecks(authentication.token.subject, page, size, userId, sort)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDeck(
        @RequestBody request: DeckCreateRequest,
        authentication: JwtAuthenticationToken,
    ): DeckResponse = deckService.createDeck(request, authentication.token.subject)

    @GetMapping("/{deckId}")
    fun getDeck(
        @PathVariable deckId: Int,
        authentication: JwtAuthenticationToken,
    ): DeckResponse = deckService.getDeck(deckId, authentication.token.subject)

    @PatchMapping("/{deckId}")
    fun updateDeck(
        @PathVariable deckId: Int,
        @RequestBody request: DeckPatchRequest,
        authentication: JwtAuthenticationToken,
    ): DeckResponse = deckService.updateDeck(deckId, request, authentication.token.subject)

    @DeleteMapping("/{deckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDeck(
        @PathVariable deckId: Int,
        authentication: JwtAuthenticationToken,
    ) {
        deckService.deleteDeck(deckId, authentication.token.subject)
    }

    @GetMapping("/{deckId}/cards")
    fun listDeckCards(
        @PathVariable deckId: Int,
        authentication: JwtAuthenticationToken,
    ): List<DeckCardResponse> = deckService.listDeckCards(deckId, authentication.token.subject)

    @PostMapping("/{deckId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    fun addCardToDeck(
        @PathVariable deckId: Int,
        @RequestBody request: DeckCardCreateRequest,
        authentication: JwtAuthenticationToken,
    ): DeckCardResponse = deckService.addCardToDeck(deckId, request, authentication.token.subject)

    @PatchMapping("/{deckId}/cards/{cardInDeckId}")
    fun updateDeckCard(
        @PathVariable deckId: Int,
        @PathVariable cardInDeckId: Int,
        @RequestBody request: DeckCardPatchRequest,
        authentication: JwtAuthenticationToken,
    ): DeckCardResponse = deckService.updateDeckCard(deckId, cardInDeckId, request, authentication.token.subject)

    @DeleteMapping("/{deckId}/cards/{cardInDeckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeCardFromDeck(
        @PathVariable deckId: Int,
        @PathVariable cardInDeckId: Int,
        authentication: JwtAuthenticationToken,
    ) {
        deckService.removeCardFromDeck(deckId, cardInDeckId, authentication.token.subject)
    }
}
