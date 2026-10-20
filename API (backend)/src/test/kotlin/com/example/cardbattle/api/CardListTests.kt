package com.example.cardbattle.api

import com.example.cardbattle.api.card.CardEntity
import com.example.cardbattle.api.card.CardRepository
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.user.UserEntity
import com.example.cardbattle.api.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Local database and fake token verification; no Google or Railway connection.
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:card-list-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.security.oauth2.resourceserver.jwt.audiences=cardbattle-test-client",
])
@AutoConfigureMockMvc
@Transactional
class CardListTests {
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
            .subject("list-author")
            .build()
        `when`(jwtDecoder.decode("valid-token")).thenReturn(token)
        `when`(jwtDecoder.decode("invalid-token")).thenThrow(BadJwtException("Invalid test token"))
    }

    @Test
    fun `my cards returns only the authenticated author's cards with default paging`() {
        val author = seedCards()

        val result = mvc.perform(get("/api/v1/cards")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.content[0].name").value("Alpha"))
            .andExpect(jsonPath("$.content[2].name").value("Charlie"))
            .andExpect(jsonPath("$.page.number").value(0))
            .andExpect(jsonPath("$.page.size").value(20))
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$.page.totalPages").value(1))
            .andReturn()

        val content = json.readTree(result.response.contentAsString).path("content")
        assertTrue(content.all { it.path("authorId").asInt() == author.id })
    }

    @Test
    fun `all cards includes multiple authors`() {
        seedCards()

        val result = mvc.perform(get("/api/v1/allCards")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(4))
            .andExpect(jsonPath("$.content[0].name").value("Alpha"))
            .andExpect(jsonPath("$.content[3].name").value("Delta"))
            .andExpect(jsonPath("$.page.totalElements").value(4))
            .andReturn()

        val content = json.readTree(result.response.contentAsString).path("content")
        assertEquals(2, content.map { it.path("authorId").asInt() }.toSet().size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/v1/cards", "/api/v1/allCards"])
    fun `pages limit results and keep accurate totals including beyond the last page`(path: String) {
        seedCards()
        val total = if (path == "/api/v1/cards") 3 else 4

        mvc.perform(get(path).param("page", "0").param("size", "2")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].name").value("Alpha"))
            .andExpect(jsonPath("$.page.totalElements").value(total))
            .andExpect(jsonPath("$.page.totalPages").value(2))

        mvc.perform(get(path).param("page", "1").param("size", "2")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(total - 2))
            .andExpect(jsonPath("$.content[0].name").value("Charlie"))
            .andExpect(jsonPath("$.page.number").value(1))
            .andExpect(jsonPath("$.page.size").value(2))
            .andExpect(jsonPath("$.page.totalElements").value(total))
            .andExpect(jsonPath("$.page.totalPages").value(2))

        mvc.perform(get(path).param("page", "99").param("size", "2")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.page.totalElements").value(total))
            .andExpect(jsonPath("$.page.totalPages").value(2))
    }

    @Test
    fun `my card type filter cannot include another author's matching cards`() {
        seedCards()

        mvc.perform(get("/api/v1/cards").param("type", "MAGE").param("size", "1")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Bravo"))
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.page.totalPages").value(2))
    }

    @Test
    fun `all cards supports type author and combined filters`() {
        val author = seedCards()

        mvc.perform(get("/api/v1/allCards").param("type", "MAGE")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(3))

        mvc.perform(get("/api/v1/allCards").param("author", author.id.toString())
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(3))

        mvc.perform(get("/api/v1/allCards")
            .param("type", "MAGE").param("author", author.id.toString()).param("size", "1")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].name").value("Bravo"))
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.page.totalPages").value(2))
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/v1/cards", "/api/v1/allCards"])
    fun `sorting supports name id and type in both directions`(path: String) {
        seedCards()
        for (field in listOf("name", "id", "type")) {
            for (direction in listOf("asc", "desc")) {
                val result = mvc.perform(get(path).param("sort", "$field,$direction")
                    .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk).andReturn()
                val content = json.readTree(result.response.contentAsString).path("content")
                if (field == "id") {
                    val values = content.map { it.path(field).asInt() }
                    assertEquals(if (direction == "asc") values.sorted() else values.sortedDescending(), values)
                } else {
                    val values = content.map { it.path(field).asText() }
                    assertEquals(if (direction == "asc") values.sorted() else values.sortedDescending(), values)
                }
            }
        }
    }

    @Test
    fun `duplicate names use ID order so adjacent pages do not repeat cards`() {
        val author = registerUser("list-author")
        val first = cards.save(CardEntity(name = "Same name", type = CardType.MAGE, author = author))
        val second = cards.save(CardEntity(name = "Same name", type = CardType.MAGE, author = author))
        for (path in listOf("/api/v1/cards", "/api/v1/allCards")) {
            for ((page, card) in listOf(first, second).withIndex()) {
                mvc.perform(get(path).param("page", page.toString()).param("size", "1")
                    .header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.content[0].id").value(card.id))
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/v1/cards", "/api/v1/allCards"])
    fun `empty lists return 200 and zero totals`(path: String) {
        mvc.perform(get(path).header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.page.totalElements").value(0))
            .andExpect(jsonPath("$.page.totalPages").value(0))
    }

    @Test
    fun `filters with no matches return an empty page`() {
        seedCards()
        for (path in listOf("/api/v1/cards", "/api/v1/allCards")) {
            mvc.perform(get(path).param("type", "ROGUE")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0))
        }
        mvc.perform(get("/api/v1/allCards").param("author", "2147483647")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(0))
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/v1/cards", "/api/v1/allCards"])
    fun `invalid paging sorting and type return 400`(path: String) {
        val invalidParams = listOf(
            "page" to "-1", "page" to "text",
            "size" to "0", "size" to "-1", "size" to "101",
            "sort" to "name", "sort" to "name,sideways",
            "sort" to "createdAt,asc", "sort" to "name,asc,id",
            "type" to "NOT_A_TYPE",
        )
        for ((key, value) in invalidParams) {
            mvc.perform(get(path).param(key, value)
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `invalid author IDs return 400`() {
        for (author in listOf("0", "-1", "text", "2147483648")) {
            mvc.perform(get("/api/v1/allCards").param("author", author)
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api/v1/cards", "/api/v1/allCards"])
    fun `both list routes require valid authentication`(path: String) {
        mvc.perform(get(path)).andExpect(status().isUnauthorized)
        mvc.perform(get(path).header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized)
    }

    private fun seedCards(): UserEntity {
        val author = registerUser("list-author")
        val otherAuthor = registerUser("other-author")
        // Insert out of alphabetical order to exercise the default name sorting.
        cards.save(CardEntity(name = "Charlie", type = CardType.MAGE, author = author))
        cards.save(CardEntity(name = "Alpha", type = CardType.WARRIOR, author = author))
        cards.save(CardEntity(name = "Bravo", type = CardType.MAGE, author = author))
        cards.save(CardEntity(name = "Delta", type = CardType.MAGE, author = otherAuthor))
        return author
    }

    private fun registerUser(subject: String): UserEntity = users.save(UserEntity(
        googleSubject = subject,
        email = "$subject@example.com",
        displayName = "List Author",
    ))
}
