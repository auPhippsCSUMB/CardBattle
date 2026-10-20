package com.example.cardbattle.api

import com.example.cardbattle.api.card.CardRepository
import com.example.cardbattle.api.dataObjects.CardCreateRequest
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
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Real controller, service, and persistence; fake token verification and a local H2 database.
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:create-card-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.security.oauth2.resourceserver.jwt.audiences=cardbattle-test-client",
])
@AutoConfigureMockMvc
@Transactional
class CreateCardTests {
    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var cards: CardRepository

    @Autowired
    lateinit var users: UserRepository

    @Autowired
    lateinit var json: ObjectMapper

    @MockBean
    lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun configureTokens() {
        val token = Jwt.withTokenValue("valid-token")
            .header("alg", "RS256")
            .subject("card-author")
            .claim("email", "author@example.com")
            .build()
        `when`(jwtDecoder.decode("valid-token")).thenReturn(token)
        `when`(jwtDecoder.decode("invalid-token"))
            .thenThrow(BadJwtException("Invalid test token"))
    }

    @Test
    fun `creating a card saves it with the authenticated author and returns 201`() {
        val author = registerAuthor()
        val request = CardCreateRequest(
            name = "  First Warrior  ",
            imgURL = "https://example.com/warrior.png",
            description = "My first card",
            type = CardType.WARRIOR,
        )

        val result = mvc.perform(createRequest(request))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("First Warrior"))
            .andExpect(jsonPath("$.imgURL").value(request.imgURL))
            .andExpect(jsonPath("$.description").value(request.description))
            .andExpect(jsonPath("$.type").value("WARRIOR"))
            .andExpect(jsonPath("$.authorId").value(author.id))
            .andReturn()

        val saved = cards.findAll().single()
        assertTrue(checkNotNull(saved.id) > 0)
        assertEquals(saved.id, json.readTree(result.response.contentAsString).path("id").asInt())
        assertEquals("First Warrior", saved.name)
        assertEquals(request.imgURL, saved.imgURL)
        assertEquals(request.description, saved.description)
        assertEquals(CardType.WARRIOR, saved.type)
        assertEquals(author.id, saved.author.id)
    }

    @Test
    fun `image URL and description may be omitted`() {
        registerAuthor()

        mvc.perform(post("/api/v1/cards")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Simple Mage","type":"MAGE"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("MAGE"))

        val saved = cards.findAll().single()
        assertNull(saved.imgURL)
        assertNull(saved.description)
    }

    @Test
    fun `blank names return 400 without saving a card`() {
        registerAuthor()
        assertBadRequest(CardCreateRequest(name = "   ", type = CardType.WARRIOR))
    }

    @Test
    fun `names longer than 150 characters return 400 without saving a card`() {
        registerAuthor()
        assertBadRequest(CardCreateRequest(name = "x".repeat(151), type = CardType.WARRIOR))
    }

    @Test
    fun `image URLs longer than 500 characters return 400 without saving a card`() {
        registerAuthor()
        assertBadRequest(CardCreateRequest(
            name = "Warrior",
            imgURL = "x".repeat(501),
            type = CardType.WARRIOR,
        ))
    }

    @Test
    fun `invalid card types return 400 without saving a card`() {
        registerAuthor()

        mvc.perform(post("/api/v1/cards")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Warrior","type":"NOT_A_CARD_TYPE"}"""))
            .andExpect(status().isBadRequest)

        assertEquals(0L, cards.count())
    }

    @Test
    fun `missing or invalid tokens return 401 without saving a card`() {
        registerAuthor()
        val body = """{"name":"Warrior","type":"WARRIOR"}"""

        mvc.perform(post("/api/v1/cards")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)

        mvc.perform(post("/api/v1/cards")
            .header("Authorization", "Bearer invalid-token")
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized)

        assertEquals(0L, cards.count())
    }

    @Test
    fun `valid token without a registered user returns 404 without saving a card`() {
        mvc.perform(createRequest(CardCreateRequest(name = "Warrior", type = CardType.WARRIOR)))
            .andExpect(status().isNotFound)

        assertEquals(0L, cards.count())
        assertEquals(0L, users.count())
    }

    private fun registerAuthor(): UserEntity = users.save(UserEntity(
        googleSubject = "card-author",
        email = "author@example.com",
        displayName = "Card Author",
    ))

    private fun createRequest(request: CardCreateRequest) =
        post("/api/v1/cards")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(request))

    private fun assertBadRequest(request: CardCreateRequest) {
        mvc.perform(createRequest(request)).andExpect(status().isBadRequest)
        assertEquals(0L, cards.count())
    }
}
