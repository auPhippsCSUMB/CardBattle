package com.example.cardbattle.api.inventory

import com.example.cardbattle.api.dataObjects.CardResponse
import com.example.cardbattle.api.dataObjects.CardType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class InventoryRepository(private val jdbc: JdbcTemplate) {
    private val selection = """SELECT owned.id, owned.userid, owned.cardid, owned."value",
        card.id AS underlying_card_id, card.name, card.imgurl, card.description, card.type, card.author
        FROM public."User_owns_card" owned
        LEFT JOIN public."Card" card ON card.id = owned.cardid"""

    private val mapper = RowMapper { row, _ ->
        val cardId = row.getObject("underlying_card_id", Int::class.javaObjectType)
        val authorId = row.getObject("author", Int::class.javaObjectType)
        InventoryEntry(
            id = row.getInt("id"),
            userId = row.getInt("userid"),
            cardId = row.getObject("cardid", Int::class.javaObjectType),
            value = row.getObject("value", Int::class.javaObjectType),
            card = if (cardId != null && authorId != null) CardResponse(
                id = cardId.toLong(), name = row.getString("name"),
                imgURL = row.getString("imgurl"), description = row.getString("description"),
                type = CardType.valueOf(row.getString("type")), authorId = authorId.toLong(),
            ) else null,
        )
    }

    fun create(userId: Int, cardId: Int, value: Int): Int {
        val keys = GeneratedKeyHolder()
        jdbc.update({ connection ->
            connection.prepareStatement(
                """INSERT INTO public."User_owns_card" (userid, cardid, "value") VALUES (?, ?, ?)""",
                arrayOf("id"),
            ).apply {
                setInt(1, userId)
                setInt(2, cardId)
                setInt(3, value)
            }
        }, keys)
        return checkNotNull(keys.key).toInt()
    }

    fun findByIdAndUserId(id: Int, userId: Int): InventoryEntry? = jdbc.query(
        "$selection WHERE owned.id = ? AND owned.userid = ?", mapper, id, userId,
    ).firstOrNull()

    fun findPage(userId: Int, type: CardType?, size: Int, offset: Long): List<InventoryEntry> {
        val typeFilter = if (type == null) "" else " AND card.type = ?"
        val parameters = mutableListOf<Any>(userId)
        if (type != null) parameters.add(type.name)
        parameters.add(size)
        parameters.add(offset)
        return jdbc.query(
            "$selection WHERE owned.userid = ?$typeFilter ORDER BY owned.id ASC LIMIT ? OFFSET ?",
            mapper, *parameters.toTypedArray(),
        )
    }

    fun countMatching(userId: Int, type: CardType?): Long {
        val typeFilter = if (type == null) "" else " AND card.type = ?"
        val parameters = if (type == null) arrayOf<Any>(userId) else arrayOf(userId, type.name)
        return checkNotNull(jdbc.queryForObject(
            """SELECT COUNT(*) FROM public."User_owns_card" owned
                LEFT JOIN public."Card" card ON card.id = owned.cardid
                WHERE owned.userid = ?$typeFilter""", Long::class.javaObjectType, *parameters,
        ))
    }
}

data class InventoryEntry(
    val id: Int,
    val userId: Int,
    val cardId: Int?,
    val value: Int?,
    val card: CardResponse?,
)
