package com.example.cardbattle.api

import com.example.cardbattle.api.card.CardEntity
import com.example.cardbattle.api.card.CardRepository
import com.example.cardbattle.api.dataObjects.CardType
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
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        // Cards reference users, so delete cards first.
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
        registerUser()
        val card = saveCard(registerUser("other-author"))

        mvc.perform(delete("/api/v1/cards/${card.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)

        assertTrue(cards.existsById(checkNotNull(card.id)))
    }

    @Test
    fun `referenced card returns 409 and stays in the database`() {
        val card = saveCard(registerUser())
        // Simulate the existing Railway inventory foreign key without adding inventory features.
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
}
