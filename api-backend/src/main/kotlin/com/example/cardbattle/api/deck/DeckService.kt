package com.example.cardbattle.api.deck

import com.example.cardbattle.api.dataObjects.DeckCreateRequest
import com.example.cardbattle.api.dataObjects.DeckResponse
import com.example.cardbattle.api.dataObjects.DeckCardCreateRequest
import com.example.cardbattle.api.dataObjects.DeckCardPatchRequest
import com.example.cardbattle.api.dataObjects.DeckCardResponse
import com.example.cardbattle.api.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class DeckService(
    private val decks: DeckRepository,
    private val users: UserRepository,
    private val deckCards: DeckCardRepository,
) {
    @Transactional
    fun createDeck(request: DeckCreateRequest, googleSubject: String): DeckResponse {
        val name = request.name.trim()
        if (name.isBlank() || name.length > 150) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Deck name must contain 1–150 characters")
        }
        val owner = users.findByGoogleSubject(googleSubject)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Sign in through Google before creating a deck")

        return toResponse(decks.save(DeckEntity(name = name, user = owner)))
    }

    @Transactional(readOnly = true)
    fun getDeck(deckId: Int, googleSubject: String): DeckResponse {
        return toResponse(findMyDeck(deckId, googleSubject))
    }

    @Transactional
    fun deleteDeck(deckId: Int, googleSubject: String) {
        val deck = findMyDeck(deckId, googleSubject)
        try {
            // Both deletes commit together, or both roll back if another constraint blocks deletion.
            decks.deleteDeckEntries(deckId)
            decks.delete(deck)
            decks.flush()
        } catch (error: DataIntegrityViolationException) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Deck cannot be deleted while other records reference it",
                error,
            )
        }
    }

    @Transactional
    fun addCardToDeck(deckId: Int, request: DeckCardCreateRequest, googleSubject: String): DeckCardResponse {
        val deck = findMyDeck(deckId, googleSubject)
        val ownerId = checkNotNull(deck.user.id)
        if (request.userId != ownerId.toLong()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID must match the signed-in deck owner")
        }
        if (request.cardId !in 1..Int.MAX_VALUE.toLong()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card ID must be a positive 32-bit inventory ID")
        }
        val inventoryId = request.cardId.toInt()
        if (!deckCards.ownsInventoryCopy(inventoryId, ownerId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Owned inventory card not found")
        }
        try {
            return toCardResponse(deckCards.create(deckId, inventoryId, ownerId, request.type), ownerId)
        } catch (error: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A database constraint prevents adding this deck entry", error)
        }
    }

    @Transactional
    fun updateDeckCard(
        deckId: Int, entryId: Int, request: DeckCardPatchRequest, googleSubject: String,
    ): DeckCardResponse {
        val deck = findMyDeck(deckId, googleSubject)
        val entry = deckCards.findInDeck(entryId, deckId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Deck entry not found")
        if (!request.typeProvided) return toCardResponse(entry, checkNotNull(deck.user.id))
        val type = request.type
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Deck entry type cannot be null")
        try {
            if (deckCards.updateType(entryId, deckId, type) == 0) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Deck entry not found")
            }
        } catch (error: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A database constraint prevents updating this deck entry", error)
        }
        return toCardResponse(entry.copy(type = type), checkNotNull(deck.user.id))
    }

    @Transactional
    fun removeCardFromDeck(deckId: Int, entryId: Int, googleSubject: String) {
        findMyDeck(deckId, googleSubject)
        try {
            if (deckCards.delete(entryId, deckId) == 0) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Deck entry not found")
            }
        } catch (error: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Other records still reference this deck entry", error)
        }
    }

    private fun toCardResponse(entry: DeckCardEntry, ownerId: Int): DeckCardResponse {
        val inventoryId = entry.inventoryId
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Deck entry no longer references an inventory card")
        return DeckCardResponse(
            id = entry.id.toLong(), deckId = entry.deckId.toLong(), cardId = inventoryId.toLong(),
            userId = ownerId.toLong(), type = entry.type,
        )
    }

    private fun findMyDeck(deckId: Int, googleSubject: String): DeckEntity {
        return decks.findByIdAndUserGoogleSubject(deckId, googleSubject)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found")
    }

    private fun toResponse(deck: DeckEntity): DeckResponse {
        return DeckResponse(
            id = checkNotNull(deck.id).toLong(),
            userId = checkNotNull(deck.user.id).toLong(),
            name = deck.name,
        )
    }
}
