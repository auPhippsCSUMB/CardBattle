package com.example.cardbattle.api

import com.example.cardbattle.api.card.CardEntity
import com.example.cardbattle.api.card.CardRepository
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.deck.DeckEntity
import com.example.cardbattle.api.deck.DeckRepository
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
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Real HTTP handling, SQL, and transactions. H2 and mocked Google tokens keep tests offline.
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:inventory-route-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
    "spring.security.oauth2.resourceserver.jwt.audiences=cardbattle-test-client",
])
@AutoConfigureMockMvc
class InventoryRoutesTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var cards: CardRepository
    @Autowired lateinit var decks: DeckRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var json: ObjectMapper
    @MockBean lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun prepareTest() {
        ResourceDatabasePopulator(ClassPathResource("card-delete-schema.sql"))
            .execute(checkNotNull(jdbc.dataSource))
        jdbc.update("""DELETE FROM public."Wins" """)
        jdbc.update("""DELETE FROM public."Card_in_deck" """)
        jdbc.update("""DELETE FROM public."User_owns_card" """)
        decks.deleteAll()
        cards.deleteAll()
        users.deleteAll()
        `when`(jwtDecoder.decode("valid-token")).thenReturn(
            Jwt.withTokenValue("valid-token").header("alg", "RS256").subject("inventory-owner").build()
        )
        `when`(jwtDecoder.decode("invalid-token")).thenThrow(BadJwtException("Invalid test token"))
    }

    @Test
    fun `create saves inventory for the authenticated user with generated ID and embedded card`() {
        val owner = user()
        val author = user("other-author")
        val card = card(author)
        val result = mvc.perform(create(owner.id, card.id))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(owner.id))
            .andExpect(jsonPath("$.cardId").value(card.id))
            .andExpect(jsonPath("$.value").value(5))
            .andExpect(jsonPath("$.card.id").value(card.id))
            .andExpect(jsonPath("$.card.name").value("Test Card"))
            .andExpect(jsonPath("$.card.type").value("WARRIOR"))
            .andExpect(jsonPath("$.card.authorId").value(author.id))
            .andExpect(jsonPath("$.card.imgURL").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.card.description").value(org.hamcrest.Matchers.nullValue()))
            .andReturn()
        val id = json.readTree(result.response.contentAsString).path("id").asInt()
        assertTrue(id > 0)
        assertEquals(owner.id, jdbc.queryForObject(
            """SELECT userid FROM public."User_owns_card" WHERE id = ?""", Int::class.java, id,
        ))
        assertEquals(1L, cards.count())
        assertEquals(0L, decks.count())
    }

    @Test
    fun `repeated creates give distinct copies and the returned inventory ID works in deck routes`() {
        val owner = user()
        val card = card(owner)
        val first = mvc.perform(create(owner.id, card.id)).andExpect(status().isCreated).andReturn()
        val firstId = json.readTree(first.response.contentAsString).path("id").asInt()
        mvc.perform(create(owner.id, card.id)).andExpect(status().isCreated)
        assertEquals(2, countOwned())
        val deck = decks.save(DeckEntity(name = "Test Deck", user = owner))
        mvc.perform(post("/api/v1/decks/${deck.id}/cards").auth()
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"cardId":$firstId,"userId":${owner.id},"type":"WARRIOR"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.cardId").value(firstId))
            .andExpect(jsonPath("$.card.id").value(card.id))
    }

    @Test
    fun `create cannot grant inventory to a different user`() {
        val owner = user()
        val other = user("other-user")
        val card = card(owner)
        mvc.perform(create(other.id, card.id)).andExpect(status().isBadRequest)
        assertEquals(0, countOwned())
    }

    @Test
    fun `create requires a registered user without automatically registering one`() {
        val card = card(user("other-author"))
        mvc.perform(create(1, card.id)).andExpect(status().isNotFound)
        assertEquals(0, countOwned())
        assertEquals(1L, users.count())
    }

    @Test
    fun `create rejects missing and out of range cards`() {
        val owner = user()
        mvc.perform(create(owner.id, Int.MAX_VALUE)).andExpect(status().isNotFound)
        for (id in listOf(0L, -1L, 2147483648L)) {
            mvc.perform(createBody("""{"userId":${owner.id},"cardId":$id,"value":0}"""))
                .andExpect(status().isBadRequest)
        }
        assertEquals(0, countOwned())
    }

    @Test
    fun `create validates required fields JSON and nonnegative integer value`() {
        val owner = user()
        val card = card(owner)
        val bodies = listOf(
            "{}", "{", "", """{"userId":${owner.id},"cardId":${card.id}}""",
            """{"userId":${owner.id},"cardId":${card.id},"value":null}""",
            """{"userId":${owner.id},"cardId":${card.id},"value":-1}""",
            """{"userId":${owner.id},"cardId":${card.id},"value":2147483648}""",
            """{"userId":${owner.id},"value":0}""",
            """{"cardId":${card.id},"value":0}""",
        )
        for (body in bodies) mvc.perform(createBody(body)).andExpect(status().isBadRequest)
        assertEquals(0, countOwned())
    }

    @Test
    fun `zero and maximum integer values are accepted`() {
        val owner = user()
        val card = card(owner)
        for (value in listOf(0, Int.MAX_VALUE)) {
            mvc.perform(create(owner.id, card.id, value)).andExpect(status().isCreated)
                .andExpect(jsonPath("$.value").value(value))
        }
    }

    @Test
    fun `database constraint failure returns 409 without saving a partial entry`() {
        val owner = user()
        val card = card(owner)
        jdbc.execute("""ALTER TABLE public."User_owns_card" ADD CONSTRAINT inventory_test_value CHECK ("value" <> 5)""")
        try {
            mvc.perform(create(owner.id, card.id)).andExpect(status().isConflict)
            assertEquals(0, countOwned())
        } finally {
            jdbc.execute("""ALTER TABLE public."User_owns_card" DROP CONSTRAINT inventory_test_value""")
        }
    }

    @Test
    fun `list defaults to the caller's inventory with card details and matching totals`() {
        val owner = user()
        val author = user("other-author")
        val card = card(author).apply {
            imgURL = "https://example.com/card.png"
            description = "Inventory card"
        }
        cards.saveAndFlush(card)
        seed(owner, card, 101)
        seed(author, card, 102)
        mvc.perform(get("/api/v1/inventory").auth())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(101))
            .andExpect(jsonPath("$.content[0].userId").value(owner.id))
            .andExpect(jsonPath("$.content[0].cardId").value(card.id))
            .andExpect(jsonPath("$.content[0].card.authorId").value(author.id))
            .andExpect(jsonPath("$.content[0].card.imgURL").value(card.imgURL))
            .andExpect(jsonPath("$.content[0].card.description").value(card.description))
            .andExpect(jsonPath("$.page.number").value(0))
            .andExpect(jsonPath("$.page.size").value(20))
            .andExpect(jsonPath("$.page.totalElements").value(1))
            .andExpect(jsonPath("$.page.totalPages").value(1))
    }

    @Test
    fun `pagination orders copies by inventory ID without collapsing duplicate cards`() {
        val owner = user()
        val card = card(owner)
        for (id in listOf(103, 101, 102)) seed(owner, card, id)
        mvc.perform(get("/api/v1/inventory").param("size", "2").auth())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(101))
            .andExpect(jsonPath("$.content[1].id").value(102))
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$.page.totalPages").value(2))
        mvc.perform(get("/api/v1/inventory").param("page", "1").param("size", "2").auth())
            .andExpect(status().isOk).andExpect(jsonPath("$.content[0].id").value(103))
        for (page in listOf(2, Int.MAX_VALUE)) {
            mvc.perform(get("/api/v1/inventory").param("page", page.toString()).param("size", "2").auth())
                .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
                .andExpect(jsonPath("$.page.totalElements").value(3))
        }
    }

    @Test
    fun `type filter is applied before pagination and counting`() {
        val owner = user()
        val warrior = card(owner)
        val mage = card(owner, CardType.MAGE)
        seed(owner, warrior, 101)
        seed(owner, mage, 102)
        seed(owner, mage, 103)
        seed(user("other-user"), mage, 104)
        mvc.perform(get("/api/v1/inventory").param("type", "MAGE").param("size", "1").param("page", "1").auth())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(103))
            .andExpect(jsonPath("$.content[0].card.type").value("MAGE"))
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.page.totalPages").value(2))
        mvc.perform(get("/api/v1/inventory").param("type", "ROGUE").auth())
            .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
            .andExpect(jsonPath("$.page.totalElements").value(0))
    }

    @Test
    fun `user filter narrows own inventory and never exposes other accounts`() {
        val owner = user()
        val other = user("other-user")
        val card = card(owner)
        seed(owner, card, 101)
        seed(other, card, 102)
        mvc.perform(get("/api/v1/inventory").param("userId", owner.id.toString()).auth())
            .andExpect(status().isOk).andExpect(jsonPath("$.page.totalElements").value(1))
        for (id in listOf(other.id, Int.MAX_VALUE)) {
            mvc.perform(get("/api/v1/inventory").param("userId", id.toString()).auth())
                .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
                .andExpect(jsonPath("$.page.totalElements").value(0))
        }
    }

    @Test
    fun `unregistered caller or caller without inventory receives an empty page`() {
        mvc.perform(get("/api/v1/inventory").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(0))
        user()
        mvc.perform(get("/api/v1/inventory").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isEmpty).andExpect(jsonPath("$.page.totalPages").value(0))
    }

    @Test
    fun `invalid pagination owner IDs and card types return 400`() {
        for ((parameter, value) in listOf(
            "page" to "-1", "page" to "bad", "size" to "0", "size" to "101",
            "userId" to "0", "userId" to "-1", "userId" to "bad", "userId" to "2147483648",
            "type" to "mage", "type" to "INVALID",
        )) {
            mvc.perform(get("/api/v1/inventory").param(parameter, value).auth()).andExpect(status().isBadRequest)
        }
        mvc.perform(get("/api/v1/inventory").param("size", "100").auth()).andExpect(status().isOk)
    }

    @Test
    fun `legacy null values remain null rather than being silently converted to zero`() {
        val owner = user()
        seed(owner, card(owner), 101, null)
        mvc.perform(get("/api/v1/inventory").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].value").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `broken legacy card reference returns 409 without changing inventory`() {
        val owner = user()
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid, "value") VALUES (101, ?, 0)""", owner.id)
        mvc.perform(get("/api/v1/inventory").auth()).andExpect(status().isConflict)
        assertEquals(1, countOwned())
    }

    @Test
    fun `both routes require valid Google tokens`() {
        val owner = user()
        val card = card(owner)
        for (token in listOf(null, "invalid-token")) {
            val requests = listOf(
                get("/api/v1/inventory"),
                post("/api/v1/inventory").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${owner.id},"cardId":${card.id},"value":0}"""),
            )
            for (request in requests) {
                if (token != null) request.header("Authorization", "Bearer $token")
                mvc.perform(request).andExpect(status().isUnauthorized)
            }
        }
        assertEquals(0, countOwned())
    }

    private fun user(subject: String = "inventory-owner") = users.save(UserEntity(
        googleSubject = subject, email = "$subject@example.com", displayName = "Inventory Owner",
    ))

    private fun card(author: UserEntity, type: CardType = CardType.WARRIOR) =
        cards.save(CardEntity(name = "Test Card", type = type, author = author))

    private fun create(userId: Int?, cardId: Int?, value: Int = 5) =
        createBody("""{"userId":$userId,"cardId":$cardId,"value":$value}""")

    private fun createBody(body: String) = post("/api/v1/inventory").auth()
        .contentType(MediaType.APPLICATION_JSON).content(body)

    private fun org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder.auth() =
        header("Authorization", "Bearer valid-token")

    private fun countOwned() = jdbc.queryForObject("""SELECT COUNT(*) FROM public."User_owns_card" """, Int::class.java)

    private fun seed(owner: UserEntity, card: CardEntity, id: Int, value: Int? = 5) {
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid, cardid, "value") VALUES (?, ?, ?, ?)""",
            id, owner.id, card.id, value)
    }
}
