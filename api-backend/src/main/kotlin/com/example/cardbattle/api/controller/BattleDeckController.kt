package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.DeckCardResponse
import com.example.cardbattle.api.dataObjects.DeckResponse
import com.example.cardbattle.api.deck.DeckCardRepository
import com.example.cardbattle.api.deck.DeckRepository
import com.example.cardbattle.api.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class BattleDeckPreviewResponse(
        val deck: DeckResponse,
        val cards: List<DeckCardResponse>,
)

@RestController
class BattleDeckController(
        private val service: BattleDeckPreviewService,
) {
    @GetMapping("/api/v1/battle-decks/{deckId}")
    fun previewDeck(
            @PathVariable deckId: Int,
            authentication: JwtAuthenticationToken,
    ): BattleDeckPreviewResponse =
            service.preview(deckId, authentication.token.subject)
}

@Service
class BattleDeckPreviewService(
        private val decks: DeckRepository,
        private val memberships: DeckCardRepository,
        private val users: UserRepository,
) {
    @Transactional(readOnly = true)
    fun preview(
            deckId: Int,
            googleSubject: String,
    ): BattleDeckPreviewResponse {
        if (deckId <= 0) {
            throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Deck ID must be positive",
            )
        }

        if (users.findByGoogleSubject(googleSubject) == null) {
            throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Sign in through Google before battling",
            )
        }

        val deck = decks.findById(deckId).orElseThrow {
            ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Deck not found",
            )
        }

        val ownerId = checkNotNull(deck.user.id)
        val entries = memberships.findAllInDeck(deckId)

        if (entries.size != 3) {
            throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A battle deck must contain exactly three cards",
            )
        }

        val cards = entries.map { entry ->
            val inventoryId = entry.inventoryId
                    ?: throw ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "A deck entry is missing its inventory copy",
                    )

            if (!memberships.ownsInventoryCopy(inventoryId, ownerId)) {
                throw ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A deck entry does not belong to the deck owner",
                )
            }

            val card = entry.card
                    ?: throw ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "A deck entry is missing its card",
                    )

            DeckCardResponse(
                    id = entry.id.toLong(),
                    deckId = deckId.toLong(),
                    cardId = inventoryId.toLong(),
                    userId = ownerId.toLong(),
                    type = card.type,
                    card = card,
            )
        }

        if (cards.map { it.cardId }.distinct().size != 3) {
            throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A battle deck must use three different owned copies",
            )
        }

        return BattleDeckPreviewResponse(
                deck = DeckResponse(
                        id = deckId.toLong(),
                        userId = ownerId.toLong(),
                        name = deck.name,
                ),
                cards = cards,
        )
    }
}