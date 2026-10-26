package com.example.cardbattle.api.controller

import com.example.cardbattle.api.card.CardService
import com.example.cardbattle.api.dataObjects.CardCreateRequest
import com.example.cardbattle.api.dataObjects.CardPatchRequest
import com.example.cardbattle.api.dataObjects.CardResponse
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.dataObjects.StatsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Provides authored-card management, catalog discovery, and card statistics.
 */
@Tag(
        name = "Cards",
        description = "Manage card designs, browse the catalog, and view statistics.",
)
@ApiResponses(
        value = [
            ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body or parameters",
                    content = [
                        Content(
                                mediaType = "application/json",
                                schema = Schema(type = "object"),
                        ),
                    ],
            ),
            ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired bearer token",
                    content = [Content()],
            ),
            ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error",
                    content = [
                        Content(
                                mediaType = "application/json",
                                schema = Schema(type = "object"),
                        ),
                    ],
            ),
        ]
)
@RestController
class CardsController(
        private val cardService: CardService,
) {

    @Operation(
            summary = "List your created cards",
            description = "Returns a paginated list of cards authored by " +
                    "the signed-in user. An empty collection returns an empty page.",
    )
    @ApiResponse(
            responseCode = "200",
            description = "Page of authored cards returned",
    )
    @GetMapping("/api/v1/cards")
    fun listMyCards(
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0")
            page: Int,

            @Parameter(description = "Page size, from 1 through 100")
            @RequestParam(defaultValue = "20")
            size: Int,

            @Parameter(description = "Optional card-class filter")
            @RequestParam(required = false)
            type: CardType?,

            @Parameter(
                    description = "Sort by name, id, or type, followed by asc or desc",
                    example = "id,desc",
            )
            @RequestParam(required = false, defaultValue = "name,asc")
            sort: String,

            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): PageResponse<CardResponse> =
            cardService.listMyCards(
                    authentication.token.subject,
                    page,
                    size,
                    type,
                    sort,
            )

    @Operation(
            summary = "Create a card design",
            description = "Creates a design with a generated card ID. " +
                    "Uploading artwork and adding an owned inventory copy " +
                    "are separate operations.",
    )
    @ApiResponses(
            value = [
                ApiResponse(
                        responseCode = "201",
                        description = "Card design created",
                ),
                ApiResponse(
                        responseCode = "404",
                        description = "The signed-in user has not registered through Google sign-in",
                        content = [
                            Content(
                                    mediaType = "application/json",
                                    schema = Schema(type = "object"),
                            ),
                        ],
                ),
            ]
    )
    @PostMapping("/api/v1/cards")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCard(
            @RequestBody request: CardCreateRequest,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): CardResponse =
            cardService.createCard(
                    request = request,
                    googleSubject = authentication.token.subject,
            )

    @Operation(
            summary = "Get one of your created cards",
            description = "Returns a card authored by the signed-in user.",
    )
    @ApiResponses(
            value = [
                ApiResponse(
                        responseCode = "200",
                        description = "Card returned",
                ),
                ApiResponse(
                        responseCode = "404",
                        description = "Card does not exist or belongs to another author",
                        content = [
                            Content(
                                    mediaType = "application/json",
                                    schema = Schema(type = "object"),
                            ),
                        ],
                ),
            ]
    )
    @GetMapping("/api/v1/cards/{cardId}")
    fun getMyCard(
            @PathVariable cardId: Int,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): CardResponse =
            cardService.getMyCard(
                    cardId,
                    authentication.token.subject,
            )

    @Operation(
            summary = "Edit one of your created cards",
            description = "Updates only supplied fields. " +
                    "Only the card's author can edit its design.",
    )
    @ApiResponses(
            value = [
                ApiResponse(
                        responseCode = "200",
                        description = "Updated card returned",
                ),
                ApiResponse(
                        responseCode = "404",
                        description = "Card does not exist or belongs to another author",
                        content = [
                            Content(
                                    mediaType = "application/json",
                                    schema = Schema(type = "object"),
                            ),
                        ],
                ),
            ]
    )
    @PatchMapping("/api/v1/cards/{cardId}")
    fun updateCard(
            @PathVariable cardId: Int,
            @RequestBody request: CardPatchRequest,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): CardResponse =
            cardService.updateCard(
                    cardId,
                    request,
                    authentication.token.subject,
            )

    @Operation(
            summary = "Delete one of your created cards",
            description = "Deletes the underlying card design. " +
                    "This differs from removing an owned inventory copy.",
    )
    @ApiResponses(
            value = [
                ApiResponse(
                        responseCode = "204",
                        description = "Card deleted",
                        content = [Content()],
                ),
                ApiResponse(
                        responseCode = "404",
                        description = "Card does not exist or belongs to another author",
                        content = [
                            Content(
                                    mediaType = "application/json",
                                    schema = Schema(type = "object"),
                            ),
                        ],
                ),
                ApiResponse(
                        responseCode = "409",
                        description = "A database relationship prevents deletion",
                        content = [
                            Content(
                                    mediaType = "application/json",
                                    schema = Schema(type = "object"),
                            ),
                        ],
                ),
            ]
    )
    @DeleteMapping("/api/v1/cards/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCard(
            @PathVariable cardId: Int,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ) {
        cardService.deleteCard(
                cardId,
                authentication.token.subject,
        )
    }

    @Operation(
            summary = "Browse the cards",
            description = "Returns a full list of card across all creators. " +
                    "Supports class and author filters.",
    )
    @ApiResponse(
            responseCode = "200",
            description = "Page of catalog cards returned",
    )
    @GetMapping("/api/v1/allCards")
    fun listAllCards(
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0")
            page: Int,

            @Parameter(description = "Page size, from 1 through 100")
            @RequestParam(defaultValue = "20")
            size: Int,

            @RequestParam(required = false)
            type: CardType?,

            @Parameter(description = "Optional author user ID")
            @RequestParam(required = false)
            author: Int?,

            @Parameter(
                    description = "Sort by name, id, or type, followed by asc or desc",
                    example = "name,asc",
            )
            @RequestParam(required = false, defaultValue = "name,asc")
            sort: String,
    ): PageResponse<CardResponse> =
            cardService.listAllCards(
                    page,
                    size,
                    type,
                    author,
                    sort,
            )

    @Operation(
            summary = "Get a card from the catalog",
            description = "Returns a card design regardless of its author. " +
                    "(Authentication is still required).",
    )
    @ApiResponses(
            value = [
                ApiResponse(
                        responseCode = "200",
                        description = "Catalog card returned",
                ),
                ApiResponse(
                        responseCode = "404",
                        description = "Card does not exist",
                        content = [
                            Content(
                                    mediaType = "application/json",
                                    schema = Schema(type = "object"),
                            ),
                        ],
                ),
            ]
    )
    @GetMapping("/api/v1/allCards/{cardId}")
    fun getPublicCard(
            @PathVariable cardId: Int,
    ): CardResponse =
            cardService.getPublicCard(cardId)

    @Operation(
            summary = "Get a random catalog card",
            description = "Selects from the entire catalog, optionally filtered " +
                    "by class. This does not add the card to your inventory.",
    )
    @ApiResponses(
            value = [
                ApiResponse(
                        responseCode = "200",
                        description = "Random card returned",
                ),
                ApiResponse(
                        responseCode = "404",
                        description = "No card matches the requested filter",
                        content = [
                            Content(
                                    mediaType = "application/json",
                                    schema = Schema(type = "object"),
                            ),
                        ],
                ),
            ]
    )
    @GetMapping("/api/v1/randomCard")
    fun getRandomCard(
            @RequestParam(required = false)
            type: CardType?,
    ): CardResponse =
            cardService.getRandomCard(type)

    @Operation(
            summary = "Get card statistics",
            description = "Returns recorded wins, losses, battle count, " +
                    "and win rate for the card. Win rate is a ratio from 0 to 1.",
    )
    @ApiResponses(
            value = [
                ApiResponse(
                        responseCode = "200",
                        description = "Card statistics returned",
                ),
                ApiResponse(
                        responseCode = "404",
                        description = "Card does not exist",
                        content = [
                            Content(
                                    mediaType = "application/json",
                                    schema = Schema(type = "object"),
                            ),
                        ],
                ),
            ]
    )
    @GetMapping("/api/v1/cards/{cardId}/stats")
    fun getCardStats(
            @PathVariable cardId: Int,
    ): StatsResponse =
            cardService.getCardStats(cardId)
}