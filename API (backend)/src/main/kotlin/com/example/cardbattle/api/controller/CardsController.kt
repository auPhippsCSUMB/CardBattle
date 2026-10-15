package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.CardCreateRequest
import com.example.cardbattle.api.dataObjects.CardPatchRequest
import com.example.cardbattle.api.dataObjects.CardResponse
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.dataObjects.StatsResponse
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
class CardsController {

    @GetMapping("/api/v1/cards")
    fun listMyCards(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) type: CardType?,
        @RequestParam(required = false, defaultValue = "createdAt,desc") sort: String
    ): PageResponse<CardResponse> {
        val card = StubDataFactory.sampleCard().copy(
            type = type ?: StubDataFactory.sampleCard().type,
            name = if (sort.startsWith("name")) "Arcane Sentinel" else StubDataFactory.sampleCard().name
        )
        return StubDataFactory.page(listOf(card), page, size)
    }

    @PostMapping("/api/v1/cards")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCard(@RequestBody request: CardCreateRequest): CardResponse = CardResponse(
        id = 42L,
        name = request.name,
        imgURL = request.imgURL,
        description = request.description,
        type = request.type,
        authorId = 7L,
        createdAt = "2026-10-01T12:00:00Z"
    )

    @GetMapping("/api/v1/cards/{cardId}")
    fun getMyCard(@PathVariable cardId: Long): CardResponse = StubDataFactory.sampleCard(cardId)

    @PatchMapping("/api/v1/cards/{cardId}")
    fun updateCard(
        @PathVariable cardId: Long,
        @RequestBody request: CardPatchRequest
    ): CardResponse {
        val sample = StubDataFactory.sampleCard(cardId)
        return sample.copy(
            name = request.name ?: sample.name,
            imgURL = request.imgURL ?: sample.imgURL,
            description = request.description ?: sample.description,
            type = request.type ?: sample.type
        )
    }

    @DeleteMapping("/api/v1/cards/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCard(@PathVariable cardId: Long) {
    }

    @GetMapping("/api/v1/allCards")
    fun listAllCards(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) type: CardType?,
        @RequestParam(required = false) author: Long?,
        @RequestParam(required = false, defaultValue = "name,asc") sort: String
    ): PageResponse<CardResponse> {
        val card = StubDataFactory.sampleCard(authorId = author ?: 7L).copy(
            type = type ?: StubDataFactory.sampleCard().type,
            name = if (sort.startsWith("name")) "Battle Archivist" else StubDataFactory.sampleCard().name
        )
        return StubDataFactory.page(listOf(card), page, size)
    }

    @GetMapping("/api/v1/allCards/{cardId}")
    fun getPublicCard(@PathVariable cardId: Long): CardResponse = StubDataFactory.sampleCard(cardId)

    @GetMapping("/api/v1/randomCard")
    fun getRandomCard(@RequestParam(required = false) type: CardType?): CardResponse =
        StubDataFactory.sampleCard().copy(type = type ?: CardType.MAGE)

    @GetMapping("/api/v1/cards/{cardId}/stats")
    fun getCardStats(@PathVariable cardId: Long): StatsResponse = StubDataFactory.sampleStats()
}
