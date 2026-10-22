package com.example.cardbattle.api.battle

import com.example.cardbattle.api.dataObjects.BattleCreateRequest
import com.example.cardbattle.api.dataObjects.BattleResponse
import com.example.cardbattle.api.dataObjects.PageMeta
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class BattleService(private val battles: BattleRepository, private val users: UserRepository) {
    @Transactional(readOnly = true)
    fun listBattles(page: Int, size: Int, userId: Long?): PageResponse<BattleResponse> {
        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size must be 1–100")
        }
        val filter = userId?.let { positiveId(it) }
        val total = battles.countMatching(filter)
        return PageResponse(
            battles.findPage(filter, size, page.toLong() * size).map(::response),
            PageMeta(size, page, total, ((total + size - 1) / size).toInt()),
        )
    }

    @Transactional(readOnly = true)
    fun getBattle(id: Long): BattleResponse = response(battles.findById(positiveId(id))
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Battle not found"))

    @Transactional
    fun createBattle(request: BattleCreateRequest, subject: String): BattleResponse {
        val caller = users.findByGoogleSubject(subject)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Sign in through Google before recording a battle")
        val winner = positiveId(request.winningUser)
        val loser = positiveId(request.losingUser)
        val winningCard = positiveId(request.winningCard)
        val losingCard = positiveId(request.losingCard)
        if (winner == loser) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Players must be different users")
        if (caller.id != winner && caller.id != loser) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "You must be a participant to record this battle")
        }
        if (!users.existsById(winner) || !users.existsById(loser)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")
        }
        if (!battles.ownsCard(winner, winningCard) || !battles.ownsCard(loser, losingCard)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory copy not found for the stated player")
        }
        try {
            val id = battles.create(winner, loser, winningCard, losingCard)
            return response(checkNotNull(battles.findById(id)))
        } catch (error: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A database constraint prevents recording this battle", error)
        }
    }

    private fun positiveId(id: Long): Int {
        if (id !in 1..Int.MAX_VALUE.toLong()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "IDs must be positive 32-bit integers")
        }
        return id.toInt()
    }

    private fun response(entry: BattleEntry): BattleResponse {
        val winner = entry.winningUser
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Legacy battle has no winning user")
        val loser = entry.losingUser
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Legacy battle has no losing user")
        return BattleResponse(entry.id.toLong(), winner.toLong(), loser.toLong(),
            entry.winningCard?.toLong(), entry.losingCard?.toLong())
    }
}
