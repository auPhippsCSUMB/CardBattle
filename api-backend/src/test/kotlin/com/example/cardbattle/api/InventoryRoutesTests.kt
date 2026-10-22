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

    @Test
    fun `patch edits only the owned copy's value and returns embedded card details`() {
        val owner = user()
        val author = user("other-author")
        val card = card(author)
        seed(owner, card, 101)
        mvc.perform(patchValue(101,
            """{"value":12,"id":999,"userId":999,"cardId":999,"card":{"name":"Fake"}}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.userId").value(owner.id))
            .andExpect(jsonPath("$.cardId").value(card.id))
            .andExpect(jsonPath("$.value").value(12))
            .andExpect(jsonPath("$.card.id").value(card.id))
            .andExpect(jsonPath("$.card.authorId").value(author.id))
            .andExpect(jsonPath("$.card.name").value("Test Card"))
        assertEquals(12, storedValue(101))
        assertEquals("Test Card", cards.findById(checkNotNull(card.id)).orElseThrow().name)
        assertEquals(1, countOwned())
    }

    @Test
    fun `empty patch preserves values including legacy null and null can be replaced with a valid value`() {
        val owner = user()
        seed(owner, card(owner), 101)
        mvc.perform(patchValue(101, "{}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.value").value(5))
        jdbc.update("""UPDATE public."User_owns_card" SET "value" = NULL WHERE id = 101""")
        mvc.perform(patchValue(101, "{}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.value").value(org.hamcrest.Matchers.nullValue()))
        mvc.perform(patchValue(101, """{"value":10}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.value").value(10))
    }

    @Test
    fun `patch rejects invalid values and JSON without changing the entry`() {
        val owner = user()
        seed(owner, card(owner), 101)
        for (body in listOf("{", "", """{"value":null}""", """{"value":-1}""",
            """{"value":2147483648}""", """{"value":null,"valueProvided":false}""")) {
            mvc.perform(patchValue(101, body)).andExpect(status().isBadRequest)
            assertEquals(5, storedValue(101))
        }
    }

    @Test
    fun `patch accepts zero and the maximum integer and ignores unknown only patches`() {
        val owner = user()
        seed(owner, card(owner), 101)
        for (value in listOf(0, Int.MAX_VALUE)) {
            mvc.perform(patchValue(101, """{"value":$value}"""))
                .andExpect(status().isOk).andExpect(jsonPath("$.value").value(value))
        }
        mvc.perform(patchValue(101, """{"cardId":999,"userId":999}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.value").value(Int.MAX_VALUE))
    }

    @Test
    fun `patch and delete cannot access another user's copy`() {
        user()
        val other = user("other-user")
        seed(other, card(other), 101)
        mvc.perform(patchValue(101, """{"value":12}""")).andExpect(status().isNotFound)
        mvc.perform(deleteCopy(101)).andExpect(status().isNotFound)
        assertEquals(1, countOwned())
        assertEquals(5, storedValue(101))
    }

    @Test
    fun `patch and delete return 404 for missing entries and unregistered callers`() {
        val other = user("other-user")
        seed(other, card(other), 101)
        // The token's subject has not registered yet.
        mvc.perform(patchValue(101, "{}")).andExpect(status().isNotFound)
        mvc.perform(deleteCopy(101)).andExpect(status().isNotFound)
        user()
        mvc.perform(patchValue(Int.MAX_VALUE, "{}")).andExpect(status().isNotFound)
        mvc.perform(deleteCopy(Int.MAX_VALUE)).andExpect(status().isNotFound)
        assertEquals(1, countOwned())
    }

    @Test
    fun `patch and delete reject malformed or out of range path IDs`() {
        for (id in listOf("not-an-id", "2147483648")) {
            mvc.perform(patch("/api/v1/inventory/$id").auth()
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest)
            mvc.perform(delete("/api/v1/inventory/$id").auth()).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `patch and delete require valid tokens and leave inventory untouched`() {
        val owner = user()
        seed(owner, card(owner), 101)
        for (token in listOf(null, "invalid-token")) {
            val requests = listOf(
                patch("/api/v1/inventory/101").contentType(MediaType.APPLICATION_JSON).content("""{"value":12}"""),
                delete("/api/v1/inventory/101"),
            )
            for (request in requests) {
                if (token != null) request.header("Authorization", "Bearer $token")
                mvc.perform(request).andExpect(status().isUnauthorized)
            }
        }
        assertEquals(1, countOwned())
        assertEquals(5, storedValue(101))
    }

    @Test
    fun `a database constraint blocking value updates returns 409 without changing the value`() {
        val owner = user()
        seed(owner, card(owner), 101)
        jdbc.execute("""ALTER TABLE public."User_owns_card" ADD CONSTRAINT inventory_test_patch CHECK ("value" <> 12)""")
        try {
            mvc.perform(patchValue(101, """{"value":12}""")).andExpect(status().isConflict)
            assertEquals(5, storedValue(101))
        } finally {
            jdbc.execute("""ALTER TABLE public."User_owns_card" DROP CONSTRAINT inventory_test_patch""")
        }
    }

    @Test
    fun `delete returns an empty 204 and removes inventory but preserves its card and user`() {
        val owner = user()
        val card = card(owner)
        seed(owner, card, 101)
        mvc.perform(deleteCopy(101)).andExpect(status().isNoContent).andExpect(content().string(""))
        assertEquals(0, countOwned())
        assertEquals(1L, users.count())
        assertTrue(cards.existsById(checkNotNull(card.id)))
        mvc.perform(deleteCopy(101)).andExpect(status().isNotFound)
        mvc.perform(patchValue(101, "{}")).andExpect(status().isNotFound)
        mvc.perform(get("/api/v1/inventory").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isEmpty)
    }

    @Test
    fun `delete cascades only this copy's memberships and nulls its battle references preserving decks and other copies`() {
        val owner = user()
        val opponent = user("opponent")
        val card = card(owner)
        seed(owner, card, 101)
        seed(owner, card, 102)
        seed(opponent, card, 103)
        val firstDeck = decks.save(DeckEntity(name = "First Deck", user = owner))
        val secondDeck = decks.save(DeckEntity(name = "Second Deck", user = owner))
        jdbc.update("""INSERT INTO public."Card_in_deck" (id, cardid, deckid)
            VALUES (201, 101, ?), (202, 102, ?), (203, 101, ?), (204, 103, ?)""",
            firstDeck.id, firstDeck.id, secondDeck.id, secondDeck.id)
        jdbc.update("""INSERT INTO public."Wins" (id, winninguser, losinguser, winningcard, losingcard)
            VALUES (301, ?, ?, 101, 103), (302, ?, ?, 102, 101), (303, ?, ?, 101, 101)""",
            owner.id, opponent.id, owner.id, opponent.id, owner.id, opponent.id)

        mvc.perform(deleteCopy(101)).andExpect(status().isNoContent)
        assertEquals(2, countOwned())
        assertEquals(1L, cards.count())
        assertEquals(2L, users.count())
        assertEquals(2L, decks.count())
        assertEquals(2, jdbc.queryForObject("""SELECT COUNT(*) FROM public."Card_in_deck" """, Int::class.java))
        assertEquals(0, jdbc.queryForObject("""SELECT COUNT(*) FROM public."Card_in_deck" WHERE cardid = 101""", Int::class.java))
        assertEquals(3, jdbc.queryForObject("""SELECT COUNT(*) FROM public."Wins" """, Int::class.java))
        assertEquals(null, battleReference(301, "winningcard"))
        assertEquals(103, battleReference(301, "losingcard"))
        assertEquals(102, battleReference(302, "winningcard"))
        assertEquals(null, battleReference(302, "losingcard"))
        assertEquals(null, battleReference(303, "winningcard"))
        assertEquals(null, battleReference(303, "losingcard"))
    }

    @Test
    fun `a restrictive reference returns 409 and rolls back cascades and history changes`() {
        val owner = user()
        val card = card(owner)
        seed(owner, card, 101)
        seed(owner, card, 102)
        val deck = decks.save(DeckEntity(name = "Keep Deck", user = owner))
        jdbc.update("""INSERT INTO public."Card_in_deck" (id, cardid, deckid) VALUES (201, 101, ?)""", deck.id)
        jdbc.update("""INSERT INTO public."Wins" (id, winninguser, losinguser, winningcard, losingcard)
            VALUES (301, ?, ?, 101, 102)""", owner.id, owner.id)
        jdbc.execute("CREATE TABLE inventory_test_reference (owned_id INTEGER REFERENCES public.\"User_owns_card\"(id))")
        try {
            jdbc.update("INSERT INTO inventory_test_reference (owned_id) VALUES (101)")
            mvc.perform(deleteCopy(101)).andExpect(status().isConflict)
            assertEquals(2, countOwned())
            assertEquals(1, jdbc.queryForObject("""SELECT COUNT(*) FROM public."Card_in_deck" WHERE cardid = 101""", Int::class.java))
            assertEquals(101, battleReference(301, "winningcard"))
            assertEquals(102, battleReference(301, "losingcard"))
        } finally {
            jdbc.execute("DROP TABLE inventory_test_reference")
        }
    }

    @Test
    fun `broken legacy copies can be deleted while patch rolls back if it cannot return card details`() {
        val owner = user()
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid, "value") VALUES (101, ?, 5)""", owner.id)
        mvc.perform(patchValue(101, """{"value":12}""")).andExpect(status().isConflict)
        assertEquals(5, storedValue(101))
        mvc.perform(patchValue(101, "{}")).andExpect(status().isConflict)
        mvc.perform(deleteCopy(101)).andExpect(status().isNoContent)
        assertEquals(0, countOwned())
    }

    private fun patchValue(id: Int, body: String) = patch("/api/v1/inventory/$id").auth()
        .contentType(MediaType.APPLICATION_JSON).content(body)

    private fun deleteCopy(id: Int) = delete("/api/v1/inventory/$id").auth()

    private fun storedValue(id: Int): Int? = jdbc.queryForObject(
        """SELECT "value" FROM public."User_owns_card" WHERE id = ?""", Int::class.java, id,
    )

    // Called only with the two hardcoded column names in the tests above.
    private fun battleReference(id: Int, column: String): Int? = jdbc.queryForObject(
        """SELECT $column FROM public."Wins" WHERE id = ?""", Int::class.java, id,
    )

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
