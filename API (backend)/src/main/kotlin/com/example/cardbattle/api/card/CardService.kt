package com.example.cardbattle.api.card

import com.example.cardbattle.api.dataObjects.CardCreateRequest
import com.example.cardbattle.api.dataObjects.CardResponse
import com.example.cardbattle.api.user.UserRepository
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

        return CardResponse(
            id = checkNotNull(saved.id).toLong(),
            name = saved.name,
            imgURL = saved.imgURL,
            description = saved.description,
            type = saved.type,
            authorId = checkNotNull(author.id).toLong(),
        )
    }
}