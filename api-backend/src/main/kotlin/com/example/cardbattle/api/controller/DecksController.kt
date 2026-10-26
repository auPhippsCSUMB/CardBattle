package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.*
import com.example.cardbattle.api.deck.DeckService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.*

/**
 * Manages the signed-in user's decks and their inventory-copy memberships.
 */
@Tag(
        name = "Decks",
        description = "Manage your decks and the owned card copies inside them.",
)
@ApiResponses(
        value = [
            ApiResponse(
                    responseCode = "401",
                    description = "Missing, invalid, or expired bearer token",
                    content = [Content()],
            ),
        ]
)
@RestController
@RequestMapping("/api/v1/decks")
class DecksController(
        private val deckService: DeckService,
) {

    @Operation(
            summary = "List your decks",
            description = "Returns a paginated list of the signed-in user's decks. " +
                    "The userId filter cannot expose another user's decks.",
    )
    @ApiResponse(
            responseCode = "200",
            description = "Page of decks returned",
    )
    @GetMapping
    fun listDecks(
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0")
            page: Int,

            @Parameter(description = "Page size, from 1 through 100")
            @RequestParam(defaultValue = "20")
            size: Int,

            @RequestParam(required = false)
            userId: Int?,

            @Parameter(
                    description = "Sort by name or id, followed by asc or desc",
                    example = "name,asc",
            )
            @RequestParam(required = false, defaultValue = "name,asc")
            sort: String,

            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): PageResponse<DeckResponse> =
            deckService.listDecks(
                    authentication.token.subject,
                    page,
                    size,
                    userId,
                    sort,
            )

    @Operation(
            summary = "Create a deck",
            description = "Creates an empty deck with a generated ID. " +
                    "The trimmed name must contain 1–150 characters. " +
                    "Cards are added through separate membership requests.",
    )
    @ApiResponse(
            responseCode = "201",
            description = "Deck created",
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDeck(
            @RequestBody request: DeckCreateRequest,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): DeckResponse =
            deckService.createDeck(
                    request,
                    authentication.token.subject,
            )

    @Operation(
            summary = "Get one of your decks",
            description = "Returns 404 if the deck is missing or owned by someone else.",
    )
    @ApiResponse(
            responseCode = "200",
            description = "Deck returned",
    )
    @GetMapping("/{deckId}")
    fun getDeck(
            @PathVariable deckId: Int,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): DeckResponse =
            deckService.getDeck(
                    deckId,
                    authentication.token.subject,
            )

    @Operation(
            summary = "Rename a deck",
            description = "Updates the name of your deck. " +
                    "An omitted name leaves it unchanged; null is rejected.",
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated deck returned",
    )
    @PatchMapping("/{deckId}")
    fun updateDeck(
            @PathVariable deckId: Int,
            @RequestBody request: DeckPatchRequest,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): DeckResponse =
            deckService.updateDeck(
                    deckId,
                    request,
                    authentication.token.subject,
            )

    @Operation(
            summary = "Delete a deck",
            description = "Deletes your deck and its membership entries. " +
                    "The cards remain in your inventory.",
    )
    @ApiResponse(
            responseCode = "204",
            description = "Deck deleted",
            content = [Content()],
    )
    @DeleteMapping("/{deckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDeck(
            @PathVariable deckId: Int,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ) {
        deckService.deleteDeck(
                deckId,
                authentication.token.subject,
        )
    }

    @Operation(
            summary = "List cards in your deck",
            description = "Returns membership entries in membership-ID order. " +
                    "Each entry includes the underlying card details. " +
                    "The response's cardId is an inventory-copy ID.",
    )
    @ApiResponse(
            responseCode = "200",
            description = "Deck memberships returned",
    )
    @GetMapping("/{deckId}/cards")
    fun listDeckCards(
            @PathVariable deckId: Int,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): List<DeckCardResponse> =
            deckService.listDeckCards(
                    deckId,
                    authentication.token.subject,
            )

    @Operation(
            summary = "Add an owned card copy to a deck",
            description = "request.cardId must be User_owns_card.id, " +
                    "not the underlying Card.id. The copy must belong to you, " +
                    "and request.userId must match your user ID. " +
                    "The returned id identifies the new deck membership.",
    )
    @ApiResponse(
            responseCode = "201",
            description = "Deck membership created",
    )
    @PostMapping("/{deckId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    fun addCardToDeck(
            @PathVariable deckId: Int,
            @RequestBody request: DeckCardCreateRequest,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): DeckCardResponse =
            deckService.addCardToDeck(
                    deckId,
                    request,
                    authentication.token.subject,
            )

    @Operation(
            summary = "Update a deck membership's class",
            description = "cardInDeckId is Card_in_deck.id. " +
                    "This changes the membership's type field; " +
                    "it does not replace its inventory copy or edit the card design.",
    )
    @ApiResponse(
            responseCode = "200",
            description = "Updated membership returned",
    )
    @PatchMapping("/{deckId}/cards/{cardInDeckId}")
    fun updateDeckCard(
            @PathVariable deckId: Int,
            @PathVariable cardInDeckId: Int,
            @RequestBody request: DeckCardPatchRequest,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ): DeckCardResponse =
            deckService.updateDeckCard(
                    deckId,
                    cardInDeckId,
                    request,
                    authentication.token.subject,
            )

    @Operation(
            summary = "Remove a card from a deck",
            description = "cardInDeckId is the membership ID. " +
                    "The owned copy remains in your inventory.",
    )
    @ApiResponse(
            responseCode = "204",
            description = "Membership removed",
            content = [Content()],
    )
    @DeleteMapping("/{deckId}/cards/{cardInDeckId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeCardFromDeck(
            @PathVariable deckId: Int,
            @PathVariable cardInDeckId: Int,
            @Parameter(hidden = true)
            authentication: JwtAuthenticationToken,
    ) {
        deckService.removeCardFromDeck(
                deckId,
                cardInDeckId,
                authentication.token.subject,
        )
    }
}