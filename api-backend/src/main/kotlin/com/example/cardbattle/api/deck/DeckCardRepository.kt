package com.example.cardbattle.api.deck

import com.example.cardbattle.api.dataObjects.CardType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

// These small SQL operations avoid introducing full inventory/card-in-deck entity models yet.
@Repository
class DeckCardRepository(private val jdbc: JdbcTemplate) {
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
        return DeckCardEntry(checkNotNull(keys.key).toInt(), deckId, inventoryId, type)
    }

    fun findInDeck(entryId: Int, deckId: Int): DeckCardEntry? = jdbc.query(
        """SELECT id, deckid, cardid, type FROM public."Card_in_deck" WHERE id = ? AND deckid = ?""",
        { row, _ -> DeckCardEntry(
            id = row.getInt("id"),
            deckId = row.getInt("deckid"),
            // Older rows may have null references after manual database edits.
            inventoryId = row.getObject("cardid", Int::class.javaObjectType),
            type = row.getString("type")?.let { CardType.valueOf(it) },
        ) }, entryId, deckId,
    ).firstOrNull()

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
)
