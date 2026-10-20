package com.example.cardbattle.api

import com.example.cardbattle.api.card.CardEntity
import com.example.cardbattle.api.card.CardRepository
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.user.UserEntity
import com.example.cardbattle.api.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Real controller, service, and database operations; fake tokens and local H2 only.
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:card-patch-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.security.oauth2.resourceserver.jwt.audiences=cardbattle-test-client",
])
@AutoConfigureMockMvc
@Transactional
class CardPatchTests {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var cards: CardRepository

    @Autowired
    lateinit var users: UserRepository

    @Autowired
    lateinit var json: ObjectMapper

    @Autowired
    lateinit var entities: EntityManager

    @MockBean
    lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun configureTokens() {
        val token = Jwt.withTokenValue("valid-token")
            .header("alg", "RS256").subject("card-author").build()
        `when`(jwtDecoder.decode("valid-token")).thenReturn(token)
        `when`(jwtDecoder.decode("invalid-token")).thenThrow(BadJwtException("Invalid test token"))
    }

    @Test
    fun `name only patch preserves every omitted field`() {
        val card = seedCard()
        mvc.perform(request(card.id, """{"name":"  Ice Mage  "}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Ice Mage"))
            .andExpect(jsonPath("$.id").value(card.id))
            .andExpect(jsonPath("$.authorId").value(card.author.id))

        val saved = reload(card)
        assertEquals("Ice Mage", saved.name)
        assertEquals("https://example.com/original.png", saved.imgURL)
        assertEquals("Original description", saved.description)
        assertEquals(CardType.WARRIOR, saved.type)
    }

    @Test
    fun `type only patch preserves the name and optional fields`() {
        val card = seedCard()
        mvc.perform(request(card.id, """{"type":"MAGE"}""")).andExpect(status().isOk)
        val saved = reload(card)
        assertEquals(CardType.MAGE, saved.type)
        assertEquals("Original card", saved.name)
        assertEquals("Original description", saved.description)
        assertEquals("https://example.com/original.png", saved.imgURL)
    }

    @Test
    fun `all editable fields can be updated together`() {
        val card = seedCard()
        mvc.perform(request(card.id, """{
            "name":"New name", "imgURL":"https://example.com/new.png",
            "description":"New description", "type":"ROGUE"
        }""")).andExpect(status().isOk)

        val saved = reload(card)
        assertEquals("New name", saved.name)
        assertEquals("https://example.com/new.png", saved.imgURL)
        assertEquals("New description", saved.description)
        assertEquals(CardType.ROGUE, saved.type)
        assertEquals(card.author.id, saved.author.id)
    }

    @Test
    fun `null image URL clears it without clearing the omitted description`() {
        val card = seedCard()
        mvc.perform(request(card.id, """{"imgURL":null}""")).andExpect(status().isOk)
        val saved = reload(card)
        assertNull(saved.imgURL)
        assertEquals("Original description", saved.description)
        assertEquals("Original card", saved.name)
    }

    @Test
    fun `null description clears it without clearing the omitted image URL`() {
        val card = seedCard()
        mvc.perform(request(card.id, """{"description":null}""")).andExpect(status().isOk)
        val saved = reload(card)
        assertNull(saved.description)
        assertEquals("https://example.com/original.png", saved.imgURL)
        assertEquals(CardType.WARRIOR, saved.type)
    }

    @Test
    fun `empty object is a successful no-op`() {
        val card = seedCard()
        mvc.perform(request(card.id, "{}")).andExpect(status().isOk)
        assertOriginal(reload(card))
        assertEquals(1L, cards.count())
    }

    @Test
    fun `ID author and internal presence tracking cannot be changed by JSON`() {
        val card = seedCard()
        val authorId = card.author.id
        mvc.perform(request(card.id, """{
            "name":"New name", "id":999999, "authorId":999999,
            "author":999999, "providedFields":["description","type"]
        }"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(card.id))
            .andExpect(jsonPath("$.authorId").value(authorId))
            .andExpect(jsonPath("$.providedFields").doesNotExist())
        val saved = reload(card)
        assertEquals(authorId, saved.author.id)
        assertEquals("Original description", saved.description)
        assertEquals(CardType.WARRIOR, saved.type)
    }

    @Test
    fun `invalid or null required fields return 400 without changing the card`() {
        val card = seedCard()
        for (body in listOf(
            """{"name":null}""", """{"name":"   "}""",
            """{"type":null}""", """{"type":"NOT_A_TYPE"}""",
            """{"description":{}}""",
        )) {
            mvc.perform(request(card.id, body)).andExpect(status().isBadRequest)
            assertOriginal(reload(card))
        }
    }

    @Test
    fun `overlong names and image URLs return 400 without changing the card`() {
        val card = seedCard()
        for (fields in listOf(mapOf("name" to "x".repeat(151)), mapOf("imgURL" to "x".repeat(501)))) {
            mvc.perform(request(card.id, json.writeValueAsString(fields)))
                .andExpect(status().isBadRequest)
            assertOriginal(reload(card))
        }
    }

    @Test
    fun `one invalid field prevents the entire update`() {
        val card = seedCard()
        mvc.perform(request(card.id, """{
            "name":"New name", "description":null, "type":null
        }""")).andExpect(status().isBadRequest)
        assertOriginal(reload(card))
    }

    @Test
    fun `another author's card cannot be patched`() {
        val card = seedCard("other-author")
        mvc.perform(request(card.id, """{"name":"Changed"}"""))
            .andExpect(status().isNotFound)
        assertOriginal(reload(card))
    }

    @Test
    fun `missing card returns 404`() {
        mvc.perform(request(2147483647, """{"name":"Changed"}"""))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `patch requires a valid token`() {
        val card = seedCard()
        mvc.perform(patch("/api/v1/cards/${card.id}")
            .contentType(MediaType.APPLICATION_JSON).content("""{"name":"Changed"}"""))
            .andExpect(status().isUnauthorized)
        mvc.perform(patch("/api/v1/cards/${card.id}")
            .header("Authorization", "Bearer invalid-token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"name":"Changed"}"""))
            .andExpect(status().isUnauthorized)
        assertOriginal(reload(card))
    }

    @Test
    fun `noninteger and out of range IDs return 400`() {
        for (id in listOf("not-an-id", "2147483648")) {
            mvc.perform(request(id, "{}")).andExpect(status().isBadRequest)
        }
    }

    private fun request(id: Any?, body: String) =
        patch("/api/v1/cards/$id")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun seedCard(subject: String = "card-author"): CardEntity {
        val author = users.save(UserEntity(
            googleSubject = subject, email = "$subject@example.com", displayName = "Author",
        ))
        return cards.save(CardEntity(
            name = "Original card", imgURL = "https://example.com/original.png",
            description = "Original description", type = CardType.WARRIOR, author = author,
        ))
    }

    private fun reload(card: CardEntity): CardEntity {
        // Verify stored values rather than merely inspecting the already-loaded entity.
        entities.flush()
        entities.clear()
        return cards.findById(checkNotNull(card.id)).orElseThrow()
    }

    private fun assertOriginal(card: CardEntity) {
        assertEquals("Original card", card.name)
        assertEquals("https://example.com/original.png", card.imgURL)
        assertEquals("Original description", card.description)
        assertEquals(CardType.WARRIOR, card.type)
    }
}
