package com.example.cardbattle.api.controller

import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class DeckBattleRequest(
        val requestId: String,
        val playerDeckId: Long,
        val opponentDeckId: Long,
)

data class DeckBattleResponse(
        val requestId: String,
        val player: BattleDeckPreviewResponse,
        val opponent: BattleDeckPreviewResponse,
        val playerScore: Int,
        val opponentScore: Int,
        val outcome: String,
)

private data class StoredDeckBattle(
        val playerUserId: Int,
        val playerDeckId: Int,
        val opponentDeckId: Int,
        val responseJson: String?,
)

@RestController
class DeckMatchController(
        private val service: DeckBattleService,
) {
    @PostMapping("/api/v1/deck-battles")
    @ResponseStatus(HttpStatus.CREATED)
    fun play(
            @RequestBody request: DeckBattleRequest,
            authentication:  JwtAuthenticationToken,
    ): DeckBattleResponse =
            service.play(request, authentication.token.subject)
}

@Service
class DeckBattleService(
        private val previews: BattleDeckPreviewService,
        private val users: UserRepository,
        private val jdbc: JdbcTemplate,
        private val mapper: ObjectMapper,
) {
    @Transactional
    fun play(
            request: DeckBattleRequest,
            subject: String,
    ): DeckBattleResponse {
        val requestId = try {
            UUID.fromString(request.requestId)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid request ID",
            )
        }

        val playerDeckId = positiveId(request.playerDeckId)
        val opponentDeckId = positiveId(request.opponentDeckId)

        val caller = users.findByGoogleSubject(subject)
                ?: throw ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Sign in through Google before battling",
                )

        val callerId = checkNotNull(caller.id)

        // A repeated request waits for the first transaction, then
        // retrieves its result instead of recording another match.
        jdbc.update(
                """
            INSERT INTO public.deck_battle (
                request_id,
                player_user_id,
                player_deck_id,
                opponent_deck_id
            )
            VALUES (?, ?, ?, ?)
            ON CONFLICT (request_id) DO NOTHING
            """.trimIndent(),
                requestId,
                callerId,
                playerDeckId,
                opponentDeckId,
        )

        val stored = jdbc.query(
                """
            SELECT player_user_id, player_deck_id,
                   opponent_deck_id, response_json
            FROM public.deck_battle
            WHERE request_id = ?
            FOR UPDATE
            """.trimIndent(),
                { row, _ ->
                    StoredDeckBattle(
                            playerUserId = row.getInt("player_user_id"),
                            playerDeckId = row.getInt("player_deck_id"),
                            opponentDeckId = row.getInt("opponent_deck_id"),
                            responseJson = row.getString("response_json"),
                    )
                },
                requestId,
        ).single()

        if (
                stored.playerUserId != callerId ||
                stored.playerDeckId != playerDeckId ||
                stored.opponentDeckId != opponentDeckId
        ) {
            throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This request ID belongs to another battle",
            )
        }

        stored.responseJson?.let {
            return mapper.readValue(
                    it,
                    DeckBattleResponse::class.java,
            )
        }

        val playerPreview = previews.preview(playerDeckId, subject)
        val opponentPreview = previews.preview(opponentDeckId, subject)

        if (playerPreview.deck.userId != callerId.toLong()) {
            throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Select one of your own decks",
            )
        }

        if (opponentPreview.deck.userId == callerId.toLong()) {
            throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose another player's deck",
            )
        }

        val player = playerPreview.copy(
                cards = playerPreview.cards.shuffled(),
        )

        val opponent = opponentPreview.copy(
                cards = opponentPreview.cards.shuffled(),
        )

        var playerScore = 0
        var opponentScore = 0

        repeat(3) { index ->
            val left = checkNotNull(player.cards[index].card).type
            val right = checkNotNull(opponent.cards[index].card).type

            when {
                left == right -> {
                    playerScore++
                    opponentScore++
                }

                right in advantages.getValue(left) -> {
                    playerScore++
                }

                else -> {
                    opponentScore++
                }
            }
        }

        val opponentUserId = opponent.deck.userId.toInt()

        val outcome = when {
            playerScore > opponentScore -> {
                recordWin(callerId, opponentUserId)
                "WIN"
            }

            playerScore < opponentScore -> {
                recordWin(opponentUserId, callerId)
                "LOSS"
            }

            else -> "TIE"
        }

        val response = DeckBattleResponse(
                requestId = requestId.toString(),
                player = player,
                opponent = opponent,
                playerScore = playerScore,
                opponentScore = opponentScore,
                outcome = outcome,
        )

        jdbc.update(
                """
            UPDATE public.deck_battle
            SET opponent_user_id = ?, response_json = ?
            WHERE request_id = ?
            """.trimIndent(),
                opponentUserId,
                mapper.writeValueAsString(response),
                requestId,
        )

        return response
    }

    private fun recordWin(winner: Int, loser: Int) {
        jdbc.update(
                """
            INSERT INTO public."Wins" (
                winninguser, losinguser, winningcard, losingcard
            )
            VALUES (?, ?, NULL, NULL)
            """.trimIndent(),
                winner,
                loser,
        )
    }

    private fun positiveId(value: Long): Int {
        if (value !in 1..Int.MAX_VALUE.toLong()) {
            throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Deck IDs must be positive integers",
            )
        }

        return value.toInt()
    }

    companion object {
        private val advantages = mapOf(
                CardType.WARRIOR to setOf(CardType.BRAWLER, CardType.RANGER),
                CardType.BRAWLER to setOf(CardType.RANGER, CardType.MAGE),
                CardType.RANGER to setOf(CardType.MAGE, CardType.ROGUE),
                CardType.MAGE to setOf(CardType.ROGUE, CardType.WARRIOR),
                CardType.ROGUE to setOf(CardType.WARRIOR, CardType.BRAWLER),
        )
    }
}