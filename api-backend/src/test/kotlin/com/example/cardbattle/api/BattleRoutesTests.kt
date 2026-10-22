package com.example.cardbattle.api

import com.example.cardbattle.api.card.CardEntity
import com.example.cardbattle.api.card.CardRepository
import com.example.cardbattle.api.dataObjects.CardType
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Real routes, services, SQL, and transactions. Token decoding alone is mocked for offline tests.
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:battle-route-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
    "spring.security.oauth2.resourceserver.jwt.audiences=cardbattle-test-client",
])
@AutoConfigureMockMvc
class BattleRoutesTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var cards: CardRepository
    @Autowired lateinit var decks: DeckRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var json: ObjectMapper
    @MockBean lateinit var jwtDecoder: JwtDecoder
    lateinit var winner: UserEntity
    lateinit var loser: UserEntity
    lateinit var card: CardEntity

    @BeforeEach
    fun prepare() {
        ResourceDatabasePopulator(ClassPathResource("card-delete-schema.sql")).execute(checkNotNull(jdbc.dataSource))
        jdbc.update("""DELETE FROM public."Wins" """)
        jdbc.update("""DELETE FROM public."Card_in_deck" """)
        jdbc.update("""DELETE FROM public."User_owns_card" """)
        decks.deleteAll()
        cards.deleteAll()
        users.deleteAll()
        winner = user("winner")
        loser = user("loser")
        user("bystander")
        card = cards.save(CardEntity(name = "Battle Card", type = CardType.WARRIOR, author = winner))
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid, cardid, "value") VALUES
            (101, ?, ?, 5), (102, ?, ?, 6), (103, ?, ?, 7)""",
            winner.id, card.id, loser.id, card.id, winner.id, card.id)
        for (subject in listOf("winner", "loser", "bystander", "unknown")) {
            `when`(jwtDecoder.decode(subject)).thenReturn(Jwt.withTokenValue(subject).header("alg", "RS256").subject(subject).build())
        }
        `when`(jwtDecoder.decode("invalid")).thenThrow(BadJwtException("Invalid test token"))
    }

    @Test
    fun `create returns a generated id saves inventory ids and can be fetched by another signed in user`() {
        val result = mvc.perform(create()).andExpect(status().isCreated)
            .andExpect(jsonPath("$.winningUser").value(winner.id)).andExpect(jsonPath("$.losingUser").value(loser.id))
            .andExpect(jsonPath("$.winningCard").value(101)).andExpect(jsonPath("$.losingCard").value(102))
            .andExpect(jsonPath("$.occurredAt").value(org.hamcrest.Matchers.nullValue())).andReturn()
        val id = json.readTree(result.response.contentAsString).path("id").asInt()
        assertTrue(id > 0)
        assertEquals(1, countBattles())
        assertEquals(101, jdbc.queryForObject("""SELECT winningcard FROM public."Wins" WHERE id = ?""", Int::class.java, id))
        mvc.perform(get("/api/v1/battles/$id").auth("bystander")).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.winningCard").value(101))
        assertEquals(3, jdbc.queryForObject("""SELECT COUNT(*) FROM public."User_owns_card" """, Int::class.java))
        assertEquals(1, cards.count().toInt())
    }

    @Test
    fun `losing participant may record a result and repeated posts create separate results`() {
        for (attempt in 1..2) mvc.perform(create(subject = "loser")).andExpect(status().isCreated)
        assertEquals(2, countBattles())
    }

    @Test
    fun `third parties including admins cannot record other players results`() {
        val outsider = users.findByGoogleSubject("bystander")!!
        outsider.isAdmin = true
        users.saveAndFlush(outsider)
        mvc.perform(create(subject = "bystander")).andExpect(status().isForbidden)
        mvc.perform(create(subject = "unknown")).andExpect(status().isNotFound)
        assertEquals(0, countBattles())
    }

    @Test
    fun `creation rejects invalid players missing users and mismatched inventory owners`() {
        val bodies = listOf(
            body(winningUser = loser.id!!.toLong()), // self-match
            body(winningUser = -1), body(losingUser = 0), body(winningCard = 0), body(losingCard = -1),
            body(winningUser = 2147483648L), body(losingUser = 2147483648L),
            body(winningCard = 2147483648L), body(losingCard = 2147483648L),
        )
        for (body in bodies) mvc.perform(create(body)).andExpect(status().isBadRequest)
        for (body in listOf(body(losingUser = Int.MAX_VALUE.toLong()), body(winningCard = Int.MAX_VALUE.toLong()),
            body(losingCard = Int.MAX_VALUE.toLong()), body(winningCard = 102), body(losingCard = 103))) {
            mvc.perform(create(body)).andExpect(status().isNotFound)
        }
        assertEquals(0, countBattles())
    }

    @Test
    fun `null missing malformed and overflowing request fields return 400`() {
        for (body in listOf("", "{", "{}", """{"winningUser":null}""",
            """{"winningUser":"abc"}""", body().replace("101", "9223372036854775808"))) {
            mvc.perform(create(body)).andExpect(status().isBadRequest)
        }
        assertEquals(0, countBattles())
    }

    @Test
    fun `creation rejects legacy inventory entries without an underlying card`() {
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid) VALUES (104, ?)""", winner.id)
        mvc.perform(create(body(winningCard = 104))).andExpect(status().isNotFound)
        assertEquals(0, countBattles())
    }

    @Test
    fun `database constraints return 409 and roll back creation`() {
        jdbc.execute("""ALTER TABLE public."Wins" ADD CONSTRAINT battle_test_create CHECK (winningcard <> 101)""")
        try {
            mvc.perform(create()).andExpect(status().isConflict)
            assertEquals(0, countBattles())
        } finally {
            jdbc.execute("""ALTER TABLE public."Wins" DROP CONSTRAINT battle_test_create""")
        }
    }

    @Test
    fun `all routes require valid tokens and unauthorized requests do not write`() {
        for (token in listOf(null, "invalid")) {
            for (request in listOf(get("/api/v1/battles"), get("/api/v1/battles/1"),
                post("/api/v1/battles").contentType(MediaType.APPLICATION_JSON).content(body()))) {
                if (token != null) request.auth(token)
                mvc.perform(request).andExpect(status().isUnauthorized)
            }
        }
        assertEquals(0, countBattles())
    }

    @Test
    fun `list filters both winner and loser before pagination with stable ids and matching totals`() {
        seed(201)
        seed(202, loser.id, winner.id)
        val outsider = users.findByGoogleSubject("bystander")!!
        seed(203, outsider.id, outsider.id)
        mvc.perform(get("/api/v1/battles?userId=${winner.id}&page=0&size=1").auth())
            .andExpect(status().isOk).andExpect(jsonPath("$.content[0].id").value(201))
            .andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$.page.totalPages").value(2))
        mvc.perform(get("/api/v1/battles?userId=${winner.id}&page=1&size=1").auth())
            .andExpect(status().isOk).andExpect(jsonPath("$.content[0].id").value(202))
        mvc.perform(get("/api/v1/battles").auth("unknown")).andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(3))
            .andExpect(jsonPath("$.content[2].id").value(203))
        mvc.perform(get("/api/v1/battles?userId=${outsider.id}").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.page.totalElements").value(1))
    }

    @Test
    fun `empty nonexistent filter and far beyond end pages return 200 with accurate totals`() {
        mvc.perform(get("/api/v1/battles").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isEmpty).andExpect(jsonPath("$.page.totalPages").value(0))
        seed(201)
        for (query in listOf("page=1&size=1", "page=2147483647&size=100", "userId=2147483647")) {
            mvc.perform(get("/api/v1/battles?$query").auth()).andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isEmpty)
        }
    }

    @Test
    fun `invalid list parameters and battle ids return 400 and missing battle returns 404`() {
        for (query in listOf("page=-1", "size=0", "size=101", "page=abc", "userId=0", "userId=-1", "userId=2147483648")) {
            mvc.perform(get("/api/v1/battles?$query").auth()).andExpect(status().isBadRequest)
        }
        for (id in listOf("0", "-1", "abc", "2147483648", "9223372036854775808")) {
            mvc.perform(get("/api/v1/battles/$id").auth()).andExpect(status().isBadRequest)
        }
        mvc.perform(get("/api/v1/battles/2147483647").auth()).andExpect(status().isNotFound)
    }

    @Test
    fun `deleting inventory preserves history and player stats while returning null card references`() {
        val result = mvc.perform(create()).andExpect(status().isCreated).andReturn()
        val id = json.readTree(result.response.contentAsString).path("id").asInt()
        jdbc.update("""DELETE FROM public."User_owns_card" WHERE id = 101""")
        mvc.perform(get("/api/v1/battles/$id").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.winningCard").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.losingCard").value(102))
        mvc.perform(get("/api/v1/battles").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].winningCard").value(org.hamcrest.Matchers.nullValue()))
        mvc.perform(get("/api/v1/users/${winner.id}/stats").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.wins").value(1)).andExpect(jsonPath("$.winRate").value(1.0))
        mvc.perform(get("/api/v1/users/${loser.id}/stats").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.losses").value(1)).andExpect(jsonPath("$.winRate").value(0.0))
        assertEquals(1, countBattles())
    }

    @Test
    fun `recording a result immediately contributes to both user and card stats`() {
        mvc.perform(create()).andExpect(status().isCreated)
        mvc.perform(get("/api/v1/cards/${card.id}/stats").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.wins").value(1)).andExpect(jsonPath("$.losses").value(1))
            .andExpect(jsonPath("$.totalBattles").value(1)).andExpect(jsonPath("$.winRate").value(0.5))
    }

    @Test
    fun `deleting the underlying card preserves the result with both inventory references null`() {
        val result = mvc.perform(create()).andExpect(status().isCreated).andReturn()
        val id = json.readTree(result.response.contentAsString).path("id").asInt()
        cards.deleteById(checkNotNull(card.id))
        mvc.perform(get("/api/v1/battles/$id").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.winningUser").value(winner.id))
            .andExpect(jsonPath("$.losingUser").value(loser.id))
            .andExpect(jsonPath("$.winningCard").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.losingCard").value(org.hamcrest.Matchers.nullValue()))
        assertEquals(1, countBattles())
    }

    @Test
    fun `caller supplied result id timestamp and other fields cannot override stored identity`() {
        val request = body().dropLast(1) + ""","id":-99,"occurredAt":"fake","isAdmin":true}"""
        val result = mvc.perform(create(request)).andExpect(status().isCreated)
            .andExpect(jsonPath("$.occurredAt").value(org.hamcrest.Matchers.nullValue())).andReturn()
        assertTrue(json.readTree(result.response.contentAsString).path("id").asInt() > 0)
    }

    @Test
    fun `legacy null user references return 409 rather than fabricated user zero`() {
        for ((winning, losing) in listOf(null to loser.id, winner.id to null)) {
            jdbc.update("""DELETE FROM public."Wins" """)
            seed(201, winning, losing)
            mvc.perform(get("/api/v1/battles/201").auth()).andExpect(status().isConflict)
            mvc.perform(get("/api/v1/battles").auth()).andExpect(status().isConflict)
            assertEquals(1, countBattles())
        }
    }

    private fun user(subject: String) = users.save(UserEntity(
        googleSubject = subject, email = "$subject@example.com", displayName = subject,
    ))
    private fun body(winningUser: Long = winner.id!!.toLong(), losingUser: Long = loser.id!!.toLong(),
        winningCard: Long = 101, losingCard: Long = 102) =
        """{"winningUser":$winningUser,"losingUser":$losingUser,"winningCard":$winningCard,"losingCard":$losingCard}"""
    private fun create(body: String = body(), subject: String = "winner") = post("/api/v1/battles").auth(subject)
        .contentType(MediaType.APPLICATION_JSON).content(body)
    private fun MockHttpServletRequestBuilder.auth(subject: String = "winner") = header("Authorization", "Bearer $subject")
    private fun countBattles() = jdbc.queryForObject("""SELECT COUNT(*) FROM public."Wins" """, Int::class.java)
    private fun seed(id: Int, winningUser: Int? = winner.id, losingUser: Int? = loser.id) {
        jdbc.update("""INSERT INTO public."Wins" (id, winninguser, losinguser) VALUES (?, ?, ?)""", id, winningUser, losingUser)
    }
}
