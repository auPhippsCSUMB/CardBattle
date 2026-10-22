package com.example.cardbattle.api

import com.example.cardbattle.api.card.CardEntity
import com.example.cardbattle.api.card.CardRepository
import com.example.cardbattle.api.dataObjects.CardType
import com.example.cardbattle.api.deck.DeckEntity
import com.example.cardbattle.api.deck.DeckRepository
import com.example.cardbattle.api.user.UserEntity
import com.example.cardbattle.api.user.UserRepository
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

// Real routes and database queries; H2 and fake Google verification keep tests offline.
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:deck-list-patch-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
    "spring.security.oauth2.resourceserver.jwt.audiences=cardbattle-test-client",
])
@AutoConfigureMockMvc
class DeckListAndPatchTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var decks: DeckRepository
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var cards: CardRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @MockBean lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun prepareTest() {
        ResourceDatabasePopulator(ClassPathResource("card-delete-schema.sql"))
            .execute(checkNotNull(jdbc.dataSource))
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
    fun `deck pages contain only the caller's decks with matching totals and default name order`() {
        val owner = user()
        val last = deck(owner, "Zulu")
        val first = deck(owner, "Alpha")
        deck(user("someone-else"), "Another user's deck")

        mvc.perform(get("/api/v1/decks").param("size", "1").auth())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(first.id))
            .andExpect(jsonPath("$.content[0].userId").value(owner.id))
            .andExpect(jsonPath("$.page.number").value(0))
            .andExpect(jsonPath("$.page.size").value(1))
            .andExpect(jsonPath("$.page.totalElements").value(2))
            .andExpect(jsonPath("$.page.totalPages").value(2))
        mvc.perform(get("/api/v1/decks").param("page", "1").param("size", "1").auth())
            .andExpect(status().isOk).andExpect(jsonPath("$.content[0].id").value(last.id))
        mvc.perform(get("/api/v1/decks").param("page", "2").param("size", "1").auth())
            .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
            .andExpect(jsonPath("$.page.totalElements").value(2))
    }

    @Test
    fun `user filter cannot expose other users' decks`() {
        val owner = user()
        deck(owner)
        val other = user("someone-else")
        deck(other)
        mvc.perform(get("/api/v1/decks").param("userId", owner.id.toString()).auth())
            .andExpect(status().isOk).andExpect(jsonPath("$.page.totalElements").value(1))
        for (id in listOf(other.id, Int.MAX_VALUE)) {
            mvc.perform(get("/api/v1/decks").param("userId", id.toString()).auth())
                .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
                .andExpect(jsonPath("$.page.totalElements").value(0))
        }
    }

    @Test
    fun `unregistered caller and owner without decks receive empty pages`() {
        mvc.perform(get("/api/v1/decks").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isEmpty).andExpect(jsonPath("$.page.totalPages").value(0))
        user()
        mvc.perform(get("/api/v1/decks").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.page.size").value(20))
            .andExpect(jsonPath("$.page.totalElements").value(0))
    }

    @Test
    fun `deck sorting supports both directions and ID tie breaking for equal names`() {
        val owner = user()
        val first = deck(owner, "Same")
        val second = deck(owner, "Same")
        val zulu = deck(owner, "Zulu")
        for (sort in listOf("name,asc", "name,desc")) {
            mvc.perform(get("/api/v1/decks").param("sort", sort).auth())
                .andExpect(status().isOk)
                .andExpect(jsonPath(if (sort.endsWith("desc")) "$.content[1].id" else "$.content[0].id").value(first.id))
                .andExpect(jsonPath(if (sort.endsWith("desc")) "$.content[2].id" else "$.content[1].id").value(second.id))
        }
        for ((sort, expected) in listOf("id,asc" to first.id, "id,desc" to zulu.id)) {
            mvc.perform(get("/api/v1/decks").param("sort", sort).auth())
                .andExpect(status().isOk).andExpect(jsonPath("$.content[0].id").value(expected))
        }
    }

    @Test
    fun `invalid page size owner filter and sort return 400`() {
        val invalid = listOf(
            "page" to "-1", "page" to "bad", "size" to "0", "size" to "101",
            "userId" to "0", "userId" to "-1", "userId" to "bad", "userId" to "2147483648",
            "sort" to "type,asc", "sort" to "name,sideways", "sort" to "name",
            "sort" to "name,asc,id", "sort" to "user.id,asc",
        )
        for ((parameter, value) in invalid) {
            mvc.perform(get("/api/v1/decks").param(parameter, value).auth()).andExpect(status().isBadRequest)
        }
        mvc.perform(get("/api/v1/decks").param("size", "100").auth()).andExpect(status().isOk)
    }

    @Test
    fun `listing cards returns stable membership IDs inventory IDs and legacy null types`() {
        val owner = user()
        val firstDeck = deck(owner)
        val secondDeck = deck(owner, "Other")
        seedEntry(firstDeck)
        val firstCard = cards.findAll().single()
        val secondCard = cards.save(CardEntity(name = "Mage", type = CardType.MAGE, author = owner))
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid, cardid) VALUES (12, ?, ?)""", owner.id, secondCard.id)
        jdbc.update("""INSERT INTO public."Card_in_deck" (id, cardid, deckid, type)
            VALUES (42, 12, ?, NULL), (43, 11, ?, 'ROGUE')""", firstDeck.id, secondDeck.id)
        mvc.perform(get("/api/v1/decks/${firstDeck.id}/cards").auth())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(41))
            .andExpect(jsonPath("$[0].deckId").value(firstDeck.id))
            .andExpect(jsonPath("$[0].cardId").value(11))
            .andExpect(jsonPath("$[0].userId").value(owner.id))
            .andExpect(jsonPath("$[0].type").value("WARRIOR"))
            .andExpect(jsonPath("$[0].card.id").value(firstCard.id))
            .andExpect(jsonPath("$[0].card.name").value("Warrior"))
            .andExpect(jsonPath("$[0].card.authorId").value(owner.id))
            .andExpect(jsonPath("$[1].id").value(42))
            .andExpect(jsonPath("$[1].type").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[1].cardId").value(12))
            .andExpect(jsonPath("$[1].card.id").value(secondCard.id))
            .andExpect(jsonPath("$[1].card.name").value("Mage"))
        assertEquals(3, jdbc.queryForObject("""SELECT COUNT(*) FROM public."Card_in_deck" """, Int::class.java))
    }

    @Test
    fun `an empty owned deck returns an empty array`() {
        val deck = deck(user())
        mvc.perform(get("/api/v1/decks/${deck.id}/cards").auth())
            .andExpect(status().isOk).andExpect(content().json("[]"))
    }

    @Test
    fun `listing an orphan legacy entry returns 409 without modifying it`() {
        val deck = deck(user())
        jdbc.update("""INSERT INTO public."Card_in_deck" (id, deckid) VALUES (41, ?)""", deck.id)
        mvc.perform(get("/api/v1/decks/${deck.id}/cards").auth()).andExpect(status().isConflict)
        assertEquals(1, jdbc.queryForObject("""SELECT COUNT(*) FROM public."Card_in_deck" """, Int::class.java))
    }

    @Test
    fun `rename trims the name and leaves ownership memberships and other decks unchanged`() {
        val owner = user()
        val deck = deck(owner)
        val other = deck(owner, "Keep")
        seedEntry(deck)
        mvc.perform(rename(deck.id, """{"name":"  Renamed Deck  ","userId":999,"id":999}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(deck.id))
            .andExpect(jsonPath("$.userId").value(owner.id))
            .andExpect(jsonPath("$.name").value("Renamed Deck"))
            .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.nullValue()))
        assertEquals("Renamed Deck", decks.findById(checkNotNull(deck.id)).orElseThrow().name)
        assertEquals("Keep", decks.findById(checkNotNull(other.id)).orElseThrow().name)
        assertEquals(1, jdbc.queryForObject("""SELECT COUNT(*) FROM public."Card_in_deck" WHERE deckid = ?""", Int::class.java, deck.id))
    }

    @Test
    fun `empty and unknown-only rename patches are no ops`() {
        val deck = deck(user())
        for (body in listOf("{}", """{"userId":999,"createdAt":"fake"}""")) {
            mvc.perform(rename(deck.id, body)).andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("Original"))
        }
    }

    @Test
    fun `invalid rename input returns 400 without changes`() {
        val deck = deck(user())
        for (body in listOf("""{"name":null}""", """{"name":null,"nameProvided":false}""",
            """{"name":"   "}""", """{"name":"${"x".repeat(151)}"}""", "{", "")) {
            mvc.perform(rename(deck.id, body)).andExpect(status().isBadRequest)
            assertEquals("Original", decks.findById(checkNotNull(deck.id)).orElseThrow().name)
        }
    }

    @Test
    fun `a trimmed rename of 150 characters is accepted`() {
        val deck = deck(user())
        mvc.perform(rename(deck.id, """{"name":"  ${"x".repeat(150)}  "}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.name").value("x".repeat(150)))
    }

    @Test
    fun `rename and membership listing hide missing and other users' decks`() {
        user()
        val otherDeck = deck(user("someone-else"))
        for (id in listOf(otherDeck.id, Int.MAX_VALUE)) {
            mvc.perform(get("/api/v1/decks/$id/cards").auth()).andExpect(status().isNotFound)
            mvc.perform(rename(id, """{"name":"Not allowed"}""")).andExpect(status().isNotFound)
        }
        assertEquals("Original", decks.findById(checkNotNull(otherDeck.id)).orElseThrow().name)
    }

    @Test
    fun `unregistered callers cannot rename or list an existing deck's entries`() {
        val deck = deck(user("someone-else"))
        mvc.perform(get("/api/v1/decks/${deck.id}/cards").auth()).andExpect(status().isNotFound)
        mvc.perform(rename(deck.id, "{}")).andExpect(status().isNotFound)
    }

    @Test
    fun `rename and membership listing reject malformed and oversized IDs`() {
        for (id in listOf("bad", "2147483648")) {
            mvc.perform(get("/api/v1/decks/$id/cards").auth()).andExpect(status().isBadRequest)
            mvc.perform(patch("/api/v1/decks/$id").auth().contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `all three routes require valid tokens`() {
        val deck = deck(user())
        for (token in listOf(null, "invalid-token")) {
            val requests = listOf(get("/api/v1/decks"), get("/api/v1/decks/${deck.id}/cards"),
                patch("/api/v1/decks/${deck.id}").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Changed"}"""))
            for (request in requests) {
                if (token != null) request.header("Authorization", "Bearer $token")
                mvc.perform(request).andExpect(status().isUnauthorized)
            }
        }
        assertEquals("Original", decks.findById(checkNotNull(deck.id)).orElseThrow().name)
    }

    @Test
    fun `embedded cards expose current card details and distinguish author owner and both types`() {
        val owner = user()
        val author = user("card-author")
        val deck = deck(owner)
        seedEntry(deck)
        val actual = cards.save(CardEntity(
            name = "Other Author's Mage", type = CardType.MAGE, author = author,
            imgURL = "https://example.com/mage.png", description = "Shared card definition",
        ))
        jdbc.update("""UPDATE public."User_owns_card" SET cardid = ? WHERE id = 11""", actual.id)

        mvc.perform(get("/api/v1/decks/${deck.id}/cards").auth())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].cardId").value(11))
            .andExpect(jsonPath("$[0].userId").value(owner.id))
            .andExpect(jsonPath("$[0].type").value("WARRIOR"))
            .andExpect(jsonPath("$[0].card.id").value(actual.id))
            .andExpect(jsonPath("$[0].card.authorId").value(author.id))
            .andExpect(jsonPath("$[0].card.name").value(actual.name))
            .andExpect(jsonPath("$[0].card.type").value("MAGE"))
            .andExpect(jsonPath("$[0].card.imgURL").value(actual.imgURL))
            .andExpect(jsonPath("$[0].card.description").value(actual.description))

        actual.name = "Updated Mage"
        cards.saveAndFlush(actual)
        mvc.perform(get("/api/v1/decks/${deck.id}/cards").auth())
            .andExpect(status().isOk).andExpect(jsonPath("$[0].card.name").value("Updated Mage"))
    }

    @Test
    fun `missing underlying card returns 409 rather than silently omitting membership`() {
        val deck = deck(user())
        seedEntry(deck)
        jdbc.update("""UPDATE public."User_owns_card" SET cardid = NULL WHERE id = 11""")
        mvc.perform(get("/api/v1/decks/${deck.id}/cards").auth()).andExpect(status().isConflict)
        assertEquals(1, jdbc.queryForObject("""SELECT COUNT(*) FROM public."Card_in_deck" """, Int::class.java))
    }

    private fun user(subject: String = "deck-owner") = users.save(UserEntity(
        googleSubject = subject, email = "$subject@example.com", displayName = "Deck Owner",
    ))

    private fun deck(owner: UserEntity, name: String = "Original") = decks.save(DeckEntity(name = name, user = owner))

    private fun rename(id: Int?, body: String) = patch("/api/v1/decks/$id").auth()
        .contentType(MediaType.APPLICATION_JSON).content(body)

    private fun org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder.auth() =
        header("Authorization", "Bearer valid-token")

    private fun seedEntry(deck: DeckEntity) {
        val card = cards.save(CardEntity(name = "Warrior", type = CardType.WARRIOR, author = deck.user))
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid, cardid) VALUES (11, ?, ?)""", deck.user.id, card.id)
        jdbc.update("""INSERT INTO public."Card_in_deck" (id, cardid, deckid, type)
            VALUES (41, 11, ?, 'WARRIOR')""", deck.id)
    }
}
