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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Real controller, service, transactions and H2 persistence; only Google verification is mocked.
// No test-wide transaction: failed deletes must prove the service rolls back its own changes.
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:deck-route-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
    "spring.security.oauth2.resourceserver.jwt.audiences=cardbattle-test-client",
])
@AutoConfigureMockMvc
class DeckRoutesTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var decks: DeckRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var cards: CardRepository
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate
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
            Jwt.withTokenValue("valid-token").header("alg", "RS256").subject("deck-owner").build()
        )
        `when`(jwtDecoder.decode("invalid-token")).thenThrow(BadJwtException("Invalid test token"))
    }

    @Test
    fun `create saves an empty deck with a generated ID and authenticated owner`() {
        val owner = registerUser()
        val other = registerUser("other-user")
        val result = mvc.perform(post("/api/v1/decks")
            .header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            // A supplied userId cannot override the authenticated owner.
            .content("""{"name":"  My First Deck  ","userId":${other.id}}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("My First Deck"))
            .andExpect(jsonPath("$.userId").value(owner.id))
            .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.nullValue()))
            .andReturn()

        val saved = decks.findAll().single()
        assertTrue(checkNotNull(saved.id) > 0)
        assertEquals(saved.id, json.readTree(result.response.contentAsString).path("id").asInt())
        assertEquals("My First Deck", saved.name)
        assertEquals(owner.id, saved.user.id)
        assertEquals(0, countRows("Card_in_deck"))
    }

    @Test
    fun `a trimmed name of 150 characters is accepted`() {
        registerUser()
        mvc.perform(createRequest("""{"name":"  ${"x".repeat(150)}  "}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("x".repeat(150)))
    }

    @Test
    fun `repeated create requests generate separate decks`() {
        registerUser()
        repeat(2) {
            mvc.perform(createRequest("""{"name":"Same name"}"""))
                .andExpect(status().isCreated)
        }
        assertEquals(2L, decks.count())
        assertEquals(2, decks.findAll().map { it.id }.toSet().size)
    }

    @Test
    fun `blank and overlong names return 400 without saving`() {
        registerUser()
        for (name in listOf("", "   ", "x".repeat(151))) {
            mvc.perform(createRequest(json.writeValueAsString(mapOf("name" to name))))
                .andExpect(status().isBadRequest)
        }
        assertEquals(0L, decks.count())
    }

    @Test
    fun `missing null and malformed names return 400 without saving`() {
        registerUser()
        for (body in listOf("{}", """{"name":null}""", "{", "")) {
            mvc.perform(createRequest(body)).andExpect(status().isBadRequest)
        }
        assertEquals(0L, decks.count())
    }

    @Test
    fun `create requires a registered user and does not register one automatically`() {
        mvc.perform(createRequest("""{"name":"My Deck"}"""))
            .andExpect(status().isNotFound)
        assertEquals(0L, decks.count())
        assertEquals(0L, users.count())
    }

    @Test
    fun `get returns the owner's deck metadata without changing it`() {
        val owner = registerUser()
        val deck = decks.save(DeckEntity(name = "My Deck", user = owner))
        mvc.perform(get("/api/v1/decks/${deck.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(deck.id))
            .andExpect(jsonPath("$.name").value("My Deck"))
            .andExpect(jsonPath("$.userId").value(owner.id))
        assertEquals(1L, decks.count())
    }

    @Test
    fun `get and delete return 404 for a missing deck`() {
        mvc.perform(get("/api/v1/decks/2147483647")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
        mvc.perform(delete("/api/v1/decks/2147483647")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `get and delete cannot access another user's deck or remove its entries`() {
        registerUser()
        val deck = seedPopulatedDeck(registerUser("other-user"))
        mvc.perform(get("/api/v1/decks/${deck.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
        mvc.perform(delete("/api/v1/decks/${deck.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
        assertTrue(decks.existsById(checkNotNull(deck.id)))
        assertEquals(1, countRows("Card_in_deck"))
        assertEquals(1, countRows("User_owns_card"))
    }

    @Test
    fun `get and delete return 404 for an unregistered caller`() {
        val deck = decks.save(DeckEntity(name = "Other Deck", user = registerUser("other-user")))
        mvc.perform(get("/api/v1/decks/${deck.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
        mvc.perform(delete("/api/v1/decks/${deck.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
        assertTrue(decks.existsById(checkNotNull(deck.id)))
    }

    @Test
    fun `get and delete reject noninteger and out of range IDs with 400`() {
        for (id in listOf("not-an-id", "2147483648")) {
            mvc.perform(get("/api/v1/decks/$id").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest)
            mvc.perform(delete("/api/v1/decks/$id").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `delete removes an empty deck and returns an empty 204 response`() {
        val deck = decks.save(DeckEntity(name = "Empty Deck", user = registerUser()))
        mvc.perform(delete("/api/v1/decks/${deck.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
        assertFalse(decks.existsById(checkNotNull(deck.id)))
        mvc.perform(get("/api/v1/decks/${deck.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
        mvc.perform(delete("/api/v1/decks/${deck.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete removes only that deck and its entries preserving cards inventory history and other decks`() {
        val owner = registerUser()
        val deck = seedPopulatedDeck(owner)
        val otherDeck = decks.save(DeckEntity(name = "Keep Deck", user = registerUser("other-user")))
        jdbc.update("""INSERT INTO public."Card_in_deck" (id, cardid, deckid)
            VALUES (42, 11, ?)""", otherDeck.id)
        jdbc.update("""INSERT INTO public."Wins" (id, winninguser, losinguser, winningcard, losingcard)
            VALUES (51, ?, ?, 11, 11)""", owner.id, owner.id)

        mvc.perform(delete("/api/v1/decks/${deck.id}")
            .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isNoContent)

        assertFalse(decks.existsById(checkNotNull(deck.id)))
        assertTrue(decks.existsById(checkNotNull(otherDeck.id)))
        assertEquals(1L, cards.count())
        assertEquals(2L, users.count())
        assertEquals(1, countRows("User_owns_card"))
        assertEquals(1, countRows("Card_in_deck"))
        assertEquals(otherDeck.id, jdbc.queryForObject(
            """SELECT deckid FROM public."Card_in_deck" WHERE id = 42""", Int::class.java,
        ))
        assertEquals(1, countRows("Wins"))
        assertEquals(11, jdbc.queryForObject(
            """SELECT winningcard FROM public."Wins" WHERE id = 51""", Int::class.java,
        ))
        assertEquals(11, jdbc.queryForObject(
            """SELECT losingcard FROM public."Wins" WHERE id = 51""", Int::class.java,
        ))
    }

    @Test
    fun `a blocking foreign key returns 409 and rolls back deleted membership rows`() {
        val deck = seedPopulatedDeck(registerUser())
        jdbc.execute("CREATE TABLE deck_test_reference (deck_id INTEGER REFERENCES public.\"Deck\"(id))")
        try {
            jdbc.update("INSERT INTO deck_test_reference (deck_id) VALUES (?)", deck.id)
            mvc.perform(delete("/api/v1/decks/${deck.id}")
                .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isConflict)
            assertTrue(decks.existsById(checkNotNull(deck.id)))
            assertEquals(1, countRows("Card_in_deck"))
            assertEquals(1, countRows("User_owns_card"))
        } finally {
            jdbc.execute("DROP TABLE deck_test_reference")
        }
    }

    @Test
    fun `all three routes require a valid token without modifying the deck`() {
        val deck = seedPopulatedDeck(registerUser())
        for (token in listOf(null, "invalid-token")) {
            val requests = listOf(
                post("/api/v1/decks").contentType(MediaType.APPLICATION_JSON).content("""{"name":"New Deck"}"""),
                get("/api/v1/decks/${deck.id}"),
                delete("/api/v1/decks/${deck.id}"),
            )
            for (request in requests) {
                if (token != null) request.header("Authorization", "Bearer $token")
                mvc.perform(request).andExpect(status().isUnauthorized)
            }
        }
        assertEquals(1L, decks.count())
        assertEquals(1, countRows("Card_in_deck"))
    }

    @Test
    fun `add links an owned inventory copy and returns a generated membership ID`() {
        val owner = registerUser()
        val deck = seedPopulatedDeck(owner)
        // Inventory 11 is intentionally different from the underlying Card.id.
        val result = mvc.perform(addRequest(deck, checkNotNull(owner.id)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.deckId").value(deck.id))
            .andExpect(jsonPath("$.cardId").value(11))
            .andExpect(jsonPath("$.userId").value(owner.id))
            .andExpect(jsonPath("$.type").value("WARRIOR"))
            .andExpect(jsonPath("$.card").value(org.hamcrest.Matchers.nullValue()))
            .andReturn()
        val entryId = json.readTree(result.response.contentAsString).path("id").asInt()
        assertTrue(entryId > 0)
        assertEquals(11, jdbc.queryForObject(
            """SELECT cardid FROM public."Card_in_deck" WHERE id = ?""", Int::class.java, entryId,
        ))
        assertEquals(owner.id, jdbc.queryForObject(
            """SELECT userid FROM public."Card_in_deck" WHERE id = ?""", Int::class.java, entryId,
        ))
        assertEquals(1, countRows("User_owns_card"))
        assertEquals(1L, cards.count())
    }

    @Test
    fun `add rejects a spoofed user ID and missing or someone else's inventory`() {
        val owner = registerUser()
        val other = registerUser("other-user")
        val deck = seedPopulatedDeck(owner)
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid, cardid)
            VALUES (12, ?, ?)""", other.id, cards.findAll().single().id)
        mvc.perform(addRequest(deck, checkNotNull(other.id))).andExpect(status().isBadRequest)
        for (id in listOf(12L, 2147483647L)) {
            mvc.perform(addRequest(deck, checkNotNull(owner.id), id)).andExpect(status().isNotFound)
        }
        assertEquals(1, countRows("Card_in_deck"))
    }

    @Test
    fun `add validates required fields enums JSON and positive inventory IDs`() {
        val owner = registerUser()
        val deck = seedPopulatedDeck(owner)
        val bodies = listOf(
            "{}", "{", """{"cardId":11,"userId":${owner.id}}""",
            """{"cardId":11,"type":"MAGE"}""",
            """{"userId":${owner.id},"type":"MAGE"}""",
            """{"cardId":11,"userId":${owner.id},"type":null}""",
            """{"cardId":11,"userId":${owner.id},"type":"INVALID"}""",
        )
        for (body in bodies) {
            mvc.perform(post("/api/v1/decks/${deck.id}/cards")
                .header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
        }
        for (id in listOf(0L, -1L, 2147483648L)) {
            mvc.perform(addRequest(deck, checkNotNull(owner.id), id)).andExpect(status().isBadRequest)
        }
        assertEquals(1, countRows("Card_in_deck"))
    }

    @Test
    fun `membership routes cannot access another owner's deck`() {
        val caller = registerUser()
        val deck = seedPopulatedDeck(registerUser("other-user"))
        mvc.perform(addRequest(deck, checkNotNull(caller.id))).andExpect(status().isNotFound)
        mvc.perform(patchEntry(deck, 41, """{"type":"MAGE"}""")).andExpect(status().isNotFound)
        mvc.perform(deleteEntry(deck, 41)).andExpect(status().isNotFound)
        assertEquals(1, countRows("Card_in_deck"))
    }

    @Test
    fun `membership routes return 404 for a missing deck`() {
        val owner = registerUser()
        val missing = DeckEntity(id = Int.MAX_VALUE, name = "Missing", user = owner)
        mvc.perform(addRequest(missing, checkNotNull(owner.id))).andExpect(status().isNotFound)
        mvc.perform(patchEntry(missing, 41, "{}")).andExpect(status().isNotFound)
        mvc.perform(deleteEntry(missing, 41)).andExpect(status().isNotFound)
    }

    @Test
    fun `patch changes only membership type not the card inventory or immutable IDs`() {
        val owner = registerUser()
        val deck = seedPopulatedDeck(owner)
        mvc.perform(patchEntry(deck, 41,
            """{"type":"MAGE","deckId":999,"cardId":999,"userId":999,"id":999}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(41))
            .andExpect(jsonPath("$.deckId").value(deck.id))
            .andExpect(jsonPath("$.cardId").value(11))
            .andExpect(jsonPath("$.userId").value(owner.id))
            .andExpect(jsonPath("$.type").value("MAGE"))
        assertEquals("MAGE", jdbc.queryForObject(
            """SELECT type FROM public."Card_in_deck" WHERE id = 41""", String::class.java,
        ))
        assertEquals(CardType.WARRIOR, cards.findAll().single().type)
        assertEquals(1, countRows("User_owns_card"))
    }

    @Test
    fun `empty patch keeps existing type including legacy null metadata`() {
        val deck = seedPopulatedDeck(registerUser())
        mvc.perform(patchEntry(deck, 41, "{}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.nullValue()))
        mvc.perform(patchEntry(deck, 41, """{"type":"RANGER"}""")).andExpect(status().isOk)
        mvc.perform(patchEntry(deck, 41, "{}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.type").value("RANGER"))
    }

    @Test
    fun `patch rejects null invalid enum and malformed JSON without changes`() {
        val deck = seedPopulatedDeck(registerUser())
        jdbc.update("""UPDATE public."Card_in_deck" SET type = 'WARRIOR' WHERE id = 41""")
        for (body in listOf("""{"type":null}""", """{"type":null,"typeProvided":false}""",
            """{"type":"INVALID"}""", "{", "")) {
            mvc.perform(patchEntry(deck, 41, body)).andExpect(status().isBadRequest)
        }
        assertEquals("WARRIOR", jdbc.queryForObject(
            """SELECT type FROM public."Card_in_deck" WHERE id = 41""", String::class.java,
        ))
    }

    @Test
    fun `patch and removal require membership to belong to the deck in the path`() {
        val owner = registerUser()
        val deck = seedPopulatedDeck(owner)
        val differentDeck = decks.save(DeckEntity(name = "Other Deck", user = owner))
        for (entryId in listOf(41, Int.MAX_VALUE)) {
            mvc.perform(patchEntry(differentDeck, entryId, """{"type":"MAGE"}"""))
                .andExpect(status().isNotFound)
            mvc.perform(deleteEntry(differentDeck, entryId)).andExpect(status().isNotFound)
        }
        assertEquals(1, countRows("Card_in_deck"))
    }

    @Test
    fun `remove deletes only membership preserving inventory cards decks and history`() {
        val owner = registerUser()
        val deck = seedPopulatedDeck(owner)
        val otherDeck = decks.save(DeckEntity(name = "Keep", user = owner))
        jdbc.update("""INSERT INTO public."Card_in_deck" (id, cardid, deckid) VALUES (42, 11, ?)""", otherDeck.id)
        jdbc.update("""INSERT INTO public."Wins" (id, winninguser, losinguser, winningcard, losingcard)
            VALUES (51, ?, ?, 11, 11)""", owner.id, owner.id)
        mvc.perform(deleteEntry(deck, 41)).andExpect(status().isNoContent).andExpect(content().string(""))
        mvc.perform(deleteEntry(deck, 41)).andExpect(status().isNotFound)
        assertEquals(2L, decks.count())
        assertEquals(1L, cards.count())
        assertEquals(1, countRows("User_owns_card"))
        assertEquals(1, countRows("Card_in_deck"))
        assertEquals(1, countRows("Wins"))
        assertEquals(11, jdbc.queryForObject(
            """SELECT winningcard FROM public."Wins" WHERE id = 51""", Int::class.java,
        ))
    }

    @Test
    fun `a blocking reference returns 409 without removing the entry`() {
        val deck = seedPopulatedDeck(registerUser())
        jdbc.execute("CREATE TABLE deck_entry_test_reference (entry_id INTEGER REFERENCES public.\"Card_in_deck\"(id))")
        try {
            jdbc.update("INSERT INTO deck_entry_test_reference (entry_id) VALUES (41)")
            mvc.perform(deleteEntry(deck, 41)).andExpect(status().isConflict)
            assertEquals(1, countRows("Card_in_deck"))
        } finally {
            jdbc.execute("DROP TABLE deck_entry_test_reference")
        }
    }

    @Test
    fun `database constraints on add and patch return 409 without partial changes`() {
        val owner = registerUser()
        val deck = seedPopulatedDeck(owner)
        jdbc.execute("""ALTER TABLE public."Card_in_deck" ADD CONSTRAINT deck_test_type CHECK (type <> 'MAGE')""")
        try {
            mvc.perform(post("/api/v1/decks/${deck.id}/cards")
                .header("Authorization", "Bearer valid-token").contentType(MediaType.APPLICATION_JSON)
                .content("""{"cardId":11,"userId":${owner.id},"type":"MAGE"}"""))
                .andExpect(status().isConflict)
            mvc.perform(patchEntry(deck, 41, """{"type":"MAGE"}""")).andExpect(status().isConflict)
            assertEquals(1, countRows("Card_in_deck"))
        } finally {
            jdbc.execute("""ALTER TABLE public."Card_in_deck" DROP CONSTRAINT deck_test_type""")
        }
    }

    @Test
    fun `membership routes require valid tokens`() {
        val owner = registerUser()
        val deck = seedPopulatedDeck(owner)
        for (token in listOf(null, "invalid-token")) {
            val requests = listOf(
                post("/api/v1/decks/${deck.id}/cards").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"cardId":11,"userId":${owner.id},"type":"MAGE"}"""),
                patch("/api/v1/decks/${deck.id}/cards/41").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/v1/decks/${deck.id}/cards/41"),
            )
            for (request in requests) {
                if (token != null) request.header("Authorization", "Bearer $token")
                mvc.perform(request).andExpect(status().isUnauthorized)
            }
        }
        assertEquals(1, countRows("Card_in_deck"))
    }

    @Test
    fun `membership routes reject malformed and out of range path IDs`() {
        for (id in listOf("bad-id", "2147483648")) {
            for (path in listOf("$id/cards/41", "1/cards/$id")) {
                mvc.perform(patch("/api/v1/decks/$path").header("Authorization", "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest)
                mvc.perform(delete("/api/v1/decks/$path").header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isBadRequest)
            }
            mvc.perform(post("/api/v1/decks/$id/cards").header("Authorization", "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON).content("""{"cardId":11,"userId":1,"type":"MAGE"}"""))
                .andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `patch on a legacy entry without inventory returns 409 and rolls back but removal works`() {
        val deck = seedPopulatedDeck(registerUser())
        jdbc.update("""UPDATE public."Card_in_deck" SET cardid = NULL WHERE id = 41""")
        mvc.perform(patchEntry(deck, 41, "{}")).andExpect(status().isConflict)
        mvc.perform(patchEntry(deck, 41, """{"type":"MAGE"}""")).andExpect(status().isConflict)
        assertEquals(null, jdbc.queryForObject(
            """SELECT type FROM public."Card_in_deck" WHERE id = 41""", String::class.java,
        ))
        mvc.perform(deleteEntry(deck, 41)).andExpect(status().isNoContent)
        assertEquals(1, countRows("User_owns_card"))
    }

    @Test
    fun `editing routes allow repeated inventory copies and more than three entries`() {
        val owner = registerUser()
        val deck = seedPopulatedDeck(owner)
        repeat(4) {
            mvc.perform(addRequest(deck, checkNotNull(owner.id))).andExpect(status().isCreated)
        }
        assertEquals(5, countRows("Card_in_deck"))
        assertEquals(1, countRows("User_owns_card"))
    }

    private fun addRequest(deck: DeckEntity, userId: Int, inventoryId: Long = 11) =
        post("/api/v1/decks/${deck.id}/cards").header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"cardId":$inventoryId,"userId":$userId,"type":"WARRIOR"}""")

    private fun patchEntry(deck: DeckEntity, entryId: Int, body: String) =
        patch("/api/v1/decks/${deck.id}/cards/$entryId").header("Authorization", "Bearer valid-token")
            .contentType(MediaType.APPLICATION_JSON).content(body)

    private fun deleteEntry(deck: DeckEntity, entryId: Int) =
        delete("/api/v1/decks/${deck.id}/cards/$entryId").header("Authorization", "Bearer valid-token")

    private fun createRequest(body: String) = post("/api/v1/decks")
        .header("Authorization", "Bearer valid-token")
        .contentType(MediaType.APPLICATION_JSON).content(body)

    private fun registerUser(subject: String = "deck-owner"): UserEntity = users.save(UserEntity(
        googleSubject = subject, email = "$subject@example.com", displayName = "Deck Owner",
    ))

    private fun seedPopulatedDeck(owner: UserEntity): DeckEntity {
        val deck = decks.save(DeckEntity(name = "Populated Deck", user = owner))
        val card = cards.save(CardEntity(name = "Warrior", type = CardType.WARRIOR, author = owner))
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid, cardid)
            VALUES (11, ?, ?)""", owner.id, card.id)
        jdbc.update("""INSERT INTO public."Card_in_deck" (id, cardid, deckid)
            VALUES (41, 11, ?)""", deck.id)
        return deck
    }

    private fun countRows(table: String): Int = checkNotNull(jdbc.queryForObject(
        """SELECT COUNT(*) FROM public."$table" """, Int::class.java,
    ))
}
