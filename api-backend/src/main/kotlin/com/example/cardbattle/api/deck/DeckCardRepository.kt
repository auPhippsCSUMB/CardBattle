package com.example.cardbattle.api.deck

import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.dataObjects.CardResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

// These small SQL operations avoid introducing full inventory/card-in-deck entity models yet.
@Repository
class DeckCardRepository(private val jdbc: JdbcTemplate) {
    // One joined read fetches all card details, rather than a separate query per entry.
    // LEFT JOIN keeps broken legacy references visible so the service can return 409.
    private val entrySelect = """SELECT entry.id, entry.deckid, entry.cardid, entry.type,
        card.id AS underlying_card_id, card.name AS card_name, card.imgurl,
        card.description, card.type AS card_type, card.author
        FROM public."Card_in_deck" entry
        LEFT JOIN public."User_owns_card" owned ON owned.id = entry.cardid
        LEFT JOIN public."Card" card ON card.id = owned.cardid"""

    private val entryMapper = RowMapper { row, _ ->
        val cardId = row.getObject("underlying_card_id", Int::class.javaObjectType)
        val authorId = row.getObject("author", Int::class.javaObjectType)
        DeckCardEntry(
            id = row.getInt("id"),
            deckId = row.getInt("deckid"),
            inventoryId = row.getObject("cardid", Int::class.javaObjectType),
            type = row.getString("type")?.let { CardType.valueOf(it) },
            card = if (cardId != null && authorId != null) CardResponse(
                id = cardId.toLong(),
                name = row.getString("card_name"),
                imgURL = row.getString("imgurl"),
                description = row.getString("description"),
                type = CardType.valueOf(row.getString("card_type")),
                authorId = authorId.toLong(),
            ) else null,
        )
    }

    fun ownsInventoryCopy(inventoryId: Int, userId: Int): Boolean =
        jdbc.queryForObject(
            """SELECT COUNT(*) FROM public."User_owns_card" WHERE id = ? AND userid = ?""",
            Int::class.java, inventoryId, userId,
        ) == 1

    fun create(deckId: Int, inventoryId: Int, userId: Int, type: CardType): DeckCardEntry {
        val keys = GeneratedKeyHolder()
        jdbc.update({ connection ->
            connection.prepareStatement(
                """INSERT INTO public."Card_in_deck" (deckid, cardid, userid, type) VALUES (?, ?, ?, ?)""",
                arrayOf("id"),
            ).apply {
                setInt(1, deckId)
                setInt(2, inventoryId)
                setInt(3, userId)
                setString(4, type.name)
            }
        }, keys)
        return checkNotNull(findInDeck(checkNotNull(keys.key).toInt(), deckId))
    }

    fun findInDeck(entryId: Int, deckId: Int): DeckCardEntry? = jdbc.query(
        "$entrySelect WHERE entry.id = ? AND entry.deckid = ?",
        entryMapper, entryId, deckId,
    ).firstOrNull()

    fun findAllInDeck(deckId: Int): List<DeckCardEntry> = jdbc.query(
        "$entrySelect WHERE entry.deckid = ? ORDER BY entry.id ASC",
        entryMapper, deckId,
    )

    fun updateType(entryId: Int, deckId: Int, type: CardType): Int = jdbc.update(
        """UPDATE public."Card_in_deck" SET type = ? WHERE id = ? AND deckid = ?""",
        type.name, entryId, deckId,
    )

    fun delete(entryId: Int, deckId: Int): Int = jdbc.update(
        """DELETE FROM public."Card_in_deck" WHERE id = ? AND deckid = ?""", entryId, deckId,
    )
}

data class DeckCardEntry(
    val id: Int,
    val deckId: Int,
    val inventoryId: Int?,
    val type: CardType?,
    val card: CardResponse?,
)
