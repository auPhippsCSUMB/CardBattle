package com.example.cardbattle.api.user

import com.example.cardbattle.api.dataObjects.PageMeta
import com.example.cardbattle.api.dataObjects.PageResponse
import com.example.cardbattle.api.dataObjects.StatsResponse
import com.example.cardbattle.api.dataObjects.UserAdminPatchRequest
import com.example.cardbattle.api.dataObjects.UserResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class UserService(private val users: UserRepository) {
    @Transactional(readOnly = true)
    fun listUsers(subject: String, page: Int, size: Int, displayName: String?, sort: String): PageResponse<UserResponse> {
        requireAdmin(subject)
        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size must be 1–100")
        }
        val parts = sort.split(',')
        if (parts.size != 2 || parts[0] !in setOf("id", "displayName") || parts[1] !in setOf("asc", "desc")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort must be id or displayName, followed by asc or desc")
        }
        var ordering = Sort.by(Sort.Direction.fromString(parts[1]), parts[0])
        if (parts[0] != "id") ordering = ordering.and(Sort.by("id"))
        val result = users.findMatching(displayName?.trim().orEmpty(), PageRequest.of(page, size, ordering))
        return PageResponse(result.content.map(::response), PageMeta(size, page, result.totalElements, result.totalPages))
    }

    @Transactional(readOnly = true)
    fun getUser(subject: String, id: Int): UserResponse {
        val caller = currentUser(subject)
        if (caller.id != id && !caller.isAdmin) forbidden()
        return response(findUser(id))
    }

    @Transactional
    fun updateUser(subject: String, id: Int, request: UserAdminPatchRequest): UserResponse {
        requireAdmin(subject)
        val target = findUser(id)
        if (!request.adminProvided) return response(target)
        val admin = request.isAdmin
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "isAdmin cannot be null")
        // Only this field is editable; a request cannot change identity, email, or Google subject.
        target.isAdmin = admin
        try {
            return response(users.saveAndFlush(target))
        } catch (error: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "A database constraint prevents updating this user", error)
        }
    }

    @Transactional
    fun deleteMyProfile(subject: String) = delete(currentUser(subject))

    @Transactional
    fun deleteUser(subject: String, id: Int) {
        requireAdmin(subject)
        delete(findUser(id))
    }

    @Transactional(readOnly = true)
    fun getUserStats(id: Int): StatsResponse {
        findUser(id)
        val wins = users.countWins(id)
        val losses = users.countLosses(id)
        val appearances = wins.toDouble() + losses
        return StatsResponse(wins, losses, users.countBattles(id), if (appearances == 0.0) 0.0 else wins / appearances)
    }

    private fun delete(user: UserEntity) {
        try {
            // Do not erase related game data or battle history. Existing foreign keys protect them.
            users.delete(user)
            users.flush()
        } catch (error: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cards, inventory, decks, or battle history still reference this user", error)
        }
    }

    private fun requireAdmin(subject: String) {
        if (!currentUser(subject).isAdmin) forbidden()
    }

    private fun currentUser(subject: String): UserEntity = users.findByGoogleSubject(subject)
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Sign in through Google first")

    private fun findUser(id: Int): UserEntity {
        if (id <= 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID must be positive")
        return users.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
    }

    private fun forbidden(): Nothing = throw ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to access this user operation")

    private fun response(user: UserEntity) = UserResponse(user.id, user.email, user.displayName, user.isAdmin)
}
