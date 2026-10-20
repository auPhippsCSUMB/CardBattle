package com.example.cardbattle.api

import com.example.cardbattle.api.card.CardEntity
import com.example.cardbattle.api.card.CardRepository
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.dataObjects.BattleResponse
import com.example.cardbattle.api.user.UserEntity
import com.example.cardbattle.api.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Real HTTP handling and persistence, with fake tokens and a separate local H2 database.
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:card-route-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // Match production's quoted table names for the native random-card queries.
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
    "spring.security.oauth2.resourceserver.jwt.audiences=cardbattle-test-client",
])
@AutoConfigureMockMvc
class CardRoutesTests {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var cards: CardRepository

    @Autowired
    lateinit var users: UserRepository

    @Autowired
    lateinit var json: ObjectMapper

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @MockBean
    lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun prepareTest() {
        ResourceDatabasePopulator(ClassPathResource("card-delete-schema.sql"))
            .execute(checkNotNull(jdbc.dataSource))
        // Remove dependent test rows before their cards and users.
        jdbc.update("""DELETE FROM public."Wins" """)
        jdbc.update("""DELETE FROM public."Card_in_deck" """)
        jdbc.update("""DELETE FROM public."User_owns_card" """)
        jdbc.update("""DELETE FROM public."Deck" """)
        cards.deleteAll()
        users.deleteAll()
        val token = Jwt.withTokenValue("valid-token")
            .header("alg", "RS256")
            .subject("card-author")
            .build()
        `when`(jwtDecoder.decode("valid-token")).thenReturn(token)
        `when`(jwtDecoder.decode("invalid-token"))
            .thenThrow(BadJwtException("Invalid test token"))
    }

    @Test
    fun `get my card returns the authored card`() {
        val author = registerUser()
        val card = saveCard(author)

        mvc.perform(get("/api/v1/cards/${card.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(card.id))
            .andExpect(jsonPath("$.name").value("Test Warrior"))
            .andExpect(jsonPath("$.type").value("WARRIOR"))
            .andExpect(jsonPath("$.authorId").value(author.id))
    }

    @Test
    fun `get my card returns 404 for a missing card`() {
        mvc.perform(get("/api/v1/cards/2147483647")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `get my card does not expose another author's card`() {
        registerUser()
        val card = saveCard(registerUser("other-author"))

        mvc.perform(get("/api/v1/cards/${card.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)

        assertTrue(cards.existsById(checkNotNull(card.id)))
    }

    @Test
    fun `delete card removes the authored card and returns 204`() {
        val card = saveCard(registerUser())

        mvc.perform(delete("/api/v1/cards/${card.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))

        assertFalse(cards.existsById(checkNotNull(card.id)))
        mvc.perform(get("/api/v1/cards/${card.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete card returns 404 for a missing card`() {
        mvc.perform(delete("/api/v1/cards/2147483647")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete card cannot delete another author's card`() {
        val caller = registerUser()
        val otherAuthor = registerUser("other-author")
        val card = saveCard(otherAuthor)
        val otherCard = saveCard(caller)
        seedRelatedRecords(card, otherCard, caller, otherAuthor)

        mvc.perform(delete("/api/v1/cards/${card.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)

        assertTrue(cards.existsById(checkNotNull(card.id)))
        assertEquals(4, rowCount("User_owns_card"))
        assertEquals(4, rowCount("Card_in_deck"))
        assertEquals(2, rowCount("Deck"))
        assertEquals(3, rowCount("Wins"))
        assertEquals(11, jdbc.queryForObject(
            """SELECT winningcard FROM public."Wins" WHERE id = 51""", Int::class.java,
        ))
    }

    @Test
    fun `delete cascades through inventory and deck entries but preserves decks and history`() {
        val author = registerUser()
        val owner = registerUser("owner")
        val opponent = registerUser("opponent")
        val card = saveCard(author)
        val otherCard = saveCard(author, CardType.MAGE)
        seedRelatedRecords(card, otherCard, owner, opponent)

        mvc.perform(delete("/api/v1/cards/${card.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNoContent)

        assertFalse(cards.existsById(checkNotNull(card.id)))
        assertTrue(cards.existsById(checkNotNull(otherCard.id)))
        assertEquals(3L, users.count())
        assertEquals(2, rowCount("User_owns_card"))
        assertEquals(0, jdbc.queryForObject(
            """SELECT COUNT(*) FROM public."User_owns_card" WHERE cardid = ?""",
            Int::class.java, card.id,
        ))
        assertEquals(2, rowCount("Card_in_deck"))
        assertEquals(2, rowCount("Deck"))
        // The three-card deck becomes incomplete; the other deck survives even when empty.
        assertEquals(2, jdbc.queryForObject(
            """SELECT COUNT(*) FROM public."Card_in_deck" WHERE deckid = 31""", Int::class.java,
        ))
        assertEquals(0, jdbc.queryForObject(
            """SELECT COUNT(*) FROM public."Card_in_deck" WHERE deckid = 32""", Int::class.java,
        ))
        assertEquals(3, rowCount("Wins"))
        assertNull(jdbc.queryForObject(
            """SELECT winningcard FROM public."Wins" WHERE id = 51""", Int::class.java,
        ))
        assertEquals(21, jdbc.queryForObject(
            """SELECT losingcard FROM public."Wins" WHERE id = 51""", Int::class.java,
        ))
        assertEquals(22, jdbc.queryForObject(
            """SELECT winningcard FROM public."Wins" WHERE id = 52""", Int::class.java,
        ))
        assertNull(jdbc.queryForObject(
            """SELECT losingcard FROM public."Wins" WHERE id = 52""", Int::class.java,
        ))
        assertEquals(21, jdbc.queryForObject(
            """SELECT winningcard FROM public."Wins" WHERE id = 53""", Int::class.java,
        ))
        assertEquals(22, jdbc.queryForObject(
            """SELECT losingcard FROM public."Wins" WHERE id = 53""", Int::class.java,
        ))
    }

    @Test
    fun `battle responses can represent deleted card references`() {
        val battle = BattleResponse(
            id = 51, winningUser = 1, losingUser = 2,
            winningCard = null, losingCard = null,
        )
        val response = json.writeValueAsString(battle)
        assertTrue(json.readTree(response).path("winningCard").isNull)
        assertTrue(json.readTree(response).path("losingCard").isNull)
        assertEquals(battle, json.readValue(response, BattleResponse::class.java))
    }

    @Test
    fun `an unrelated restrictive foreign key still returns 409 without deleting the card`() {
        val card = saveCard(registerUser())
        // Fallback for a future relationship that has not enabled cascading deletion.
        jdbc.execute("""CREATE TABLE card_test_reference (
            card_id INTEGER REFERENCES public."Card"(id)
        )""")
        try {
            jdbc.update("INSERT INTO card_test_reference (card_id) VALUES (?)", card.id)

            mvc.perform(delete("/api/v1/cards/${card.id}")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isConflict)

            assertTrue(cards.existsById(checkNotNull(card.id)))
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM card_test_reference", Int::class.java))
        } finally {
            jdbc.execute("DROP TABLE card_test_reference")
        }
    }

    @Test
    fun `random card returns an existing card from any author`() {
        val author = registerUser()
        val first = saveCard(author)
        val second = saveCard(registerUser("other-author"), CardType.MAGE)

        val result = mvc.perform(get("/api/v1/randomCard")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andReturn()

        val returnedId = json.readTree(result.response.contentAsString).path("id").asInt()
        assertTrue(returnedId in setOf(first.id, second.id))
        assertEquals(2L, cards.count())
    }

    @Test
    fun `random card respects the optional type filter`() {
        val author = registerUser()
        saveCard(author)
        val mage = saveCard(author, CardType.MAGE)

        mvc.perform(get("/api/v1/randomCard").param("type", "MAGE")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(mage.id))
            .andExpect(jsonPath("$.type").value("MAGE"))
    }

    @Test
    fun `random card returns 404 when the database is empty`() {
        mvc.perform(get("/api/v1/randomCard")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `random card returns 404 when no card matches the type`() {
        saveCard(registerUser(), CardType.WARRIOR)

        mvc.perform(get("/api/v1/randomCard").param("type", "MAGE")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `random card rejects an invalid type with 400`() {
        mvc.perform(get("/api/v1/randomCard").param("type", "NOT_A_TYPE")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `all three routes require a valid token`() {
        val card = saveCard(registerUser())
        for (token in listOf(null, "invalid-token")) {
            val requests = listOf(
                get("/api/v1/cards/${card.id}"),
                get("/api/v1/randomCard"),
                delete("/api/v1/cards/${card.id}"),
            )
            for (request in requests) {
                if (token != null) request.header("Authorization", "Bearer $token")
                mvc.perform(request).andExpect(status().isUnauthorized)
            }
        }
        assertTrue(cards.existsById(checkNotNull(card.id)))
    }

    private fun registerUser(subject: String = "card-author"): UserEntity =
        users.save(UserEntity(
            googleSubject = subject,
            email = "$subject@example.com",
            displayName = "Test Author",
        ))

    private fun saveCard(author: UserEntity, type: CardType = CardType.WARRIOR): CardEntity =
        cards.save(CardEntity(name = "Test Warrior", type = type, author = author))

    private fun rowCount(table: String): Int =
        checkNotNull(jdbc.queryForObject("""SELECT COUNT(*) FROM public."$table" """, Int::class.java))

    private fun seedRelatedRecords(
        card: CardEntity,
        otherCard: CardEntity,
        owner: UserEntity,
        opponent: UserEntity,
    ) {
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid, cardid)
            VALUES (11, ?, ?), (12, ?, ?), (21, ?, ?), (22, ?, ?)""",
            owner.id, card.id, opponent.id, card.id,
            owner.id, otherCard.id, owner.id, otherCard.id,
        )
        jdbc.update("""INSERT INTO public."Deck" (id, userid, name)
            VALUES (31, ?, 'Three-card deck'), (32, ?, 'Single-card deck')""",
            owner.id, opponent.id,
        )
        jdbc.update("""INSERT INTO public."Card_in_deck" (id, cardid, deckid)
            VALUES (41, 11, 31), (42, 21, 31), (43, 22, 31), (44, 12, 32)""")
        jdbc.update("""INSERT INTO public."Wins" (id, winninguser, losinguser, winningcard, losingcard)
            VALUES (51, ?, ?, 11, 21), (52, ?, ?, 22, 12), (53, ?, ?, 21, 22)""",
            owner.id, opponent.id, owner.id, opponent.id, owner.id, opponent.id,
        )
    }
}
