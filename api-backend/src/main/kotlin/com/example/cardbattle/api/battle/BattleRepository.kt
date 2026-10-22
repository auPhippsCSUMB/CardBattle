package com.example.cardbattle.api.battle

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class BattleRepository(private val jdbc: JdbcTemplate) {
    private val selection = """SELECT id, winninguser, losinguser, winningcard, losingcard FROM public."Wins" """
    private val mapper = RowMapper { row, _ ->
        BattleEntry(
            id = row.getInt("id"),
            winningUser = row.getObject("winninguser", Int::class.javaObjectType),
            losingUser = row.getObject("losinguser", Int::class.javaObjectType),
            winningCard = row.getObject("winningcard", Int::class.javaObjectType),
            losingCard = row.getObject("losingcard", Int::class.javaObjectType),
        )
    }

    fun findById(id: Int): BattleEntry? = jdbc.query("$selection WHERE id = ?", mapper, id).firstOrNull()

    fun countMatching(userId: Int?): Long {
        val filter = if (userId == null) "" else "WHERE winninguser = ? OR losinguser = ?"
        val parameters = if (userId == null) emptyArray<Any>() else arrayOf<Any>(userId, userId)
        return checkNotNull(jdbc.queryForObject(
            """SELECT COUNT(*) FROM public."Wins" $filter""", Long::class.javaObjectType, *parameters,
        ))
    }

    fun findPage(userId: Int?, size: Int, offset: Long): List<BattleEntry> {
        val filter = if (userId == null) "" else "WHERE winninguser = ? OR losinguser = ?"
        val parameters = mutableListOf<Any>()
        if (userId != null) parameters.addAll(listOf(userId, userId))
        parameters.add(size)
        parameters.add(offset)
        return jdbc.query("$selection $filter ORDER BY id ASC LIMIT ? OFFSET ?", mapper, *parameters.toTypedArray())
    }

    // Check the inventory owner and underlying card without loading art or requiring a deck.
    fun ownsCard(userId: Int, inventoryId: Int): Boolean = checkNotNull(jdbc.queryForObject(
        """SELECT COUNT(*) FROM public."User_owns_card" owned
            JOIN public."Card" card ON card.id = owned.cardid
            WHERE owned.id = ? AND owned.userid = ?""",
        Int::class.javaObjectType, inventoryId, userId,
    )) > 0

    fun create(winner: Int, loser: Int, winningCard: Int, losingCard: Int): Int {
        val keys = GeneratedKeyHolder()
        jdbc.update({ connection ->
            connection.prepareStatement(
                """INSERT INTO public."Wins" (winninguser, losinguser, winningcard, losingcard) VALUES (?, ?, ?, ?)""",
                arrayOf("id"),
            ).apply {
                setInt(1, winner)
                setInt(2, loser)
                setInt(3, winningCard)
                setInt(4, losingCard)
            }
        }, keys)
        return checkNotNull(keys.key).toInt()
    }
}

// User columns are nullable in the existing schema. Never turn a legacy NULL into user ID 0.
data class BattleEntry(
    val id: Int,
    val winningUser: Int?,
    val losingUser: Int?,
    val winningCard: Int?,
    val losingCard: Int?,
)
