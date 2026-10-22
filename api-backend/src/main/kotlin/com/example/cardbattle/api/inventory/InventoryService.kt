package com.example.cardbattle.api.inventory

import com.example.cardbattle.api.card.CardRepository
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.dataObjects.OwnershipCreateRequest
import com.example.cardbattle.api.dataObjects.OwnershipResponse
import com.example.cardbattle.api.dataObjects.PageMeta
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class InventoryService(
    private val inventory: InventoryRepository,
    private val users: UserRepository,
    private val cards: CardRepository,
) {
    @Transactional
    fun createOwnership(request: OwnershipCreateRequest, googleSubject: String): OwnershipResponse {
        val value = request.value
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is required")
        if (value < 0 || request.cardId !in 1..Int.MAX_VALUE.toLong()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Value must be nonnegative and card ID must be a positive 32-bit integer")
        }
        val owner = users.findByGoogleSubject(googleSubject)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Sign in through Google before adding inventory")
        val ownerId = checkNotNull(owner.id)
        if (request.userId != ownerId.toLong()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID must match the signed-in user")
        }
        val cardId = request.cardId.toInt()
        if (!cards.existsById(cardId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found")
        }
        try {
            val id = inventory.create(ownerId, cardId, value)
            return toResponse(checkNotNull(inventory.findByIdAndUserId(id, ownerId)))
        } catch (error: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A database constraint prevents creating this inventory entry", error)
        }
    }

    @Transactional(readOnly = true)
    fun listInventory(
        googleSubject: String, page: Int, size: Int, userId: Int?, type: CardType?,
    ): PageResponse<OwnershipResponse> {
        if (page < 0 || size !in 1..100 || (userId != null && userId <= 0)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Page must be zero or greater, size must be 1–100, and user ID must be positive")
        }
        val owner = users.findByGoogleSubject(googleSubject)
        // userId narrows the caller's inventory; it never grants access to another account.
        if (owner == null || (userId != null && userId != owner.id)) {
            return PageResponse(emptyList(), PageMeta(size, page, 0, 0))
        }
        val ownerId = checkNotNull(owner.id)
        val total = inventory.countMatching(ownerId, type)
        val entries = inventory.findPage(ownerId, type, size, page.toLong() * size)
        return PageResponse(
            content = entries.map { toResponse(it) },
            page = PageMeta(size, page, total, ((total + size - 1) / size).toInt()),
        )
    }

    private fun toResponse(entry: InventoryEntry): OwnershipResponse {
        val cardId = entry.cardId
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Inventory entry has no card reference")
        val card = entry.card
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Inventory entry cannot resolve its card and author")
        return OwnershipResponse(
            id = entry.id.toLong(), userId = entry.userId.toLong(),
            cardId = cardId.toLong(), value = entry.value, card = card,
        )
    }
}
