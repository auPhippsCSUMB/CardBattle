package com.example.cardbattle.api.card

import com.example.cardbattle.api.dataObjects.CardCreateRequest
import com.example.cardbattle.api.dataObjects.CardPatchRequest
import com.example.cardbattle.api.dataObjects.CardResponse
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.dataObjects.PageMeta
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.dataObjects.StatsResponse
import com.example.cardbattle.api.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class CardService(
    private val cards: CardRepository,
    private val users: UserRepository,
) {
    @Transactional
    fun createCard(
        request: CardCreateRequest,
        googleSubject: String,
    ): CardResponse {
        val name = validateName(request.name)
        validateImageUrl(request.imgURL)

        val author = users.findByGoogleSubject(googleSubject)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Sign in through Google before creating a card",
            )

        val saved = cards.save(
            CardEntity(
                name = name,
                imgURL = request.imgURL,
                description = request.description,
                type = request.type,
                author = author,
            )
        )

        return toResponse(saved)
    }

    @Transactional(readOnly = true)
    fun listMyCards(
        googleSubject: String,
        page: Int,
        size: Int,
        type: CardType?,
        sort: String,
    ): PageResponse<CardResponse> {
        val results = cards.findMyCards(googleSubject, type, pageRequest(page, size, sort))
        return toPageResponse(results)
    }

    @Transactional(readOnly = true)
    fun listAllCards(
        page: Int,
        size: Int,
        type: CardType?,
        author: Int?,
        sort: String,
    ): PageResponse<CardResponse> {
        if (author != null && author <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Author ID must be positive")
        }
        val results = cards.findAllCards(type, author, pageRequest(page, size, sort))
        return toPageResponse(results)
    }

    @Transactional(readOnly = true)
    fun getMyCard(cardId: Int, googleSubject: String): CardResponse {
        return toResponse(findMyCard(cardId, googleSubject))
    }

    @Transactional(readOnly = true)
    fun getPublicCard(cardId: Int): CardResponse {
        val card = cards.findById(cardId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found")
        }
        return toResponse(card)
    }

    @Transactional(readOnly = true)
    fun getCardStats(cardId: Int): StatsResponse {
        if (!cards.existsById(cardId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found")
        }

        val wins = cards.countWinsForCard(cardId)
        val losses = cards.countLossesForCard(cardId)
        val appearances = wins.toDouble() + losses

        return StatsResponse(
            wins = wins,
            losses = losses,
            // A battle between two copies of this card is still just one battle.
            totalBattles = cards.countBattlesForCard(cardId),
            winRate = if (appearances == 0.0) 0.0 else wins / appearances,
        )
    }

    @Transactional(readOnly = true)
    fun getRandomCard(type: CardType?): CardResponse {
        val card = if (type == null) {
            cards.findRandomCard()
        } else {
            cards.findRandomCardByType(type.name)
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No matching cards found")

        return toResponse(card)
    }

    @Transactional
    fun updateCard(cardId: Int, request: CardPatchRequest, googleSubject: String): CardResponse {
        val card = findMyCard(cardId, googleSubject)
        val fields = request.providedFields

        // Validate before changing the entity, so a bad field cannot partially update it.
        val name = if ("name" in fields) {
            validateName(request.name ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Card name cannot be null",
            ))
        } else card.name
        val type = if ("type" in fields) {
            request.type ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Card type cannot be null",
            )
        } else card.type
        if ("imgURL" in fields) validateImageUrl(request.imgURL)

        card.name = name
        card.type = type
        if ("imgURL" in fields) card.imgURL = request.imgURL
        if ("description" in fields) card.description = request.description

        return toResponse(cards.save(card))
    }

    @Transactional
    fun deleteCard(cardId: Int, googleSubject: String) {
        val card = findMyCard(cardId, googleSubject)
        try {
            cards.delete(card)
            // Execute the DELETE now so a foreign-key failure can become a useful HTTP response.
            cards.flush()
        } catch (error: DataIntegrityViolationException) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Card cannot be deleted while other records reference it",
                error,
            )
        }
    }

    private fun findMyCard(cardId: Int, googleSubject: String): CardEntity {
        return cards.findByIdAndAuthorGoogleSubject(cardId, googleSubject)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found")
    }

    private fun validateName(value: String): String {
        val name = value.trim()
        if (name.isBlank() || name.length > 150) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Card name must contain 1–150 characters",
            )
        }
        return name
    }

    private fun validateImageUrl(value: String?) {
        if ((value?.length ?: 0) > 500) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Image URL must not exceed 500 characters",
            )
        }
    }

    private fun pageRequest(page: Int, size: Int, sort: String): PageRequest {
        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Page must be zero or greater and size must be between 1 and 100",
            )
        }

        val parts = sort.split(",").map { it.trim() }
        if (parts.size != 2 || parts[0] !in setOf("name", "id", "type") ||
            parts[1].lowercase() !in setOf("asc", "desc")) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Sort must be name,asc; id,asc; or type,asc (desc is also supported)",
            )
        }

        var ordering = Sort.by(Sort.Direction.fromString(parts[1]), parts[0])
        // Equal names/types need a consistent order when fetching separate pages.
        if (parts[0] != "id") ordering = ordering.and(Sort.by("id"))
        return PageRequest.of(page, size, ordering)
    }

    private fun toPageResponse(results: Page<CardEntity>): PageResponse<CardResponse> {
        return PageResponse(
            content = results.content.map { toResponse(it) },
            page = PageMeta(
                size = results.size,
                number = results.number,
                totalElements = results.totalElements,
                totalPages = results.totalPages,
            ),
        )
    }

    private fun toResponse(card: CardEntity): CardResponse {
        return CardResponse(
            id = checkNotNull(card.id).toLong(),
            name = card.name,
            imgURL = card.imgURL,
            description = card.description,
            type = card.type,
            authorId = checkNotNull(card.author.id).toLong(),
        )
    }
}
