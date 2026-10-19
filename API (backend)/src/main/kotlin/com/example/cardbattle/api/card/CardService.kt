package com.example.cardbattle.api.card

import com.example.cardbattle.api.dataObjects.CardCreateRequest
import com.example.cardbattle.api.dataObjects.CardResponse
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
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
        val name = request.name.trim()

        if (name.isBlank() || name.length > 150) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Card name must contain 1–150 characters",
            )
        }

        if ((request.imgURL?.length ?: 0) > 500) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Image URL must not exceed 500 characters",
            )
        }

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
    fun getMyCard(cardId: Int, googleSubject: String): CardResponse {
        return toResponse(findMyCard(cardId, googleSubject))
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
