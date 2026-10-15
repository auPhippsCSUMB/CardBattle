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
    ): PageResponse<CardResponse> = TODO("Implement GET /api/v1/cards")

    @PostMapping("/api/v1/cards")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCard(@RequestBody request: CardCreateRequest): CardResponse = TODO("Implement POST /api/v1/cards")

    @GetMapping("/api/v1/cards/{cardId}")
    fun getMyCard(@PathVariable cardId: Long): CardResponse = TODO("Implement GET /api/v1/cards/{cardId}")

    @PatchMapping("/api/v1/cards/{cardId}")
    fun updateCard(
        @PathVariable cardId: Long,
        @RequestBody request: CardPatchRequest
    ): CardResponse = TODO("Implement PATCH /api/v1/cards/{cardId}")

    @DeleteMapping("/api/v1/cards/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCard(@PathVariable cardId: Long) = TODO("Implement DELETE /api/v1/cards/{cardId}")

    @GetMapping("/api/v1/allCards")
    fun listAllCards(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) type: CardType?,
        @RequestParam(required = false) author: Long?,
        @RequestParam(required = false, defaultValue = "name,asc") sort: String
    ): PageResponse<CardResponse> = TODO("Implement GET /api/v1/allCards")

    @GetMapping("/api/v1/allCards/{cardId}")
    fun getPublicCard(@PathVariable cardId: Long): CardResponse = TODO("Implement GET /api/v1/allCards/{cardId}")

    @GetMapping("/api/v1/randomCard")
    fun getRandomCard(@RequestParam(required = false) type: CardType?): CardResponse = TODO("Implement GET /api/v1/randomCard")

    @GetMapping("/api/v1/cards/{cardId}/stats")
    fun getCardStats(@PathVariable cardId: Long): StatsResponse = TODO("Implement GET /api/v1/cards/{cardId}/stats")
}
