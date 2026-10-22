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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Real services, HTTP handling, and database queries; only Google token decoding is mocked.
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:user-route-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
    "spring.security.oauth2.resourceserver.jwt.audiences=cardbattle-test-client",
])
@AutoConfigureMockMvc
class UserRoutesTests {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var cards: CardRepository
    @Autowired lateinit var decks: DeckRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @MockBean lateinit var jwtDecoder: JwtDecoder

    @BeforeEach
    fun prepare() {
        ResourceDatabasePopulator(ClassPathResource("card-delete-schema.sql")).execute(checkNotNull(jdbc.dataSource))
        jdbc.update("""DELETE FROM public."Wins" """)
        jdbc.update("""DELETE FROM public."Card_in_deck" """)
        jdbc.update("""DELETE FROM public."User_owns_card" """)
        decks.deleteAll()
        cards.deleteAll()
        users.deleteAll()
        for (subject in listOf("admin", "member", "other", "unknown")) {
            `when`(jwtDecoder.decode(subject)).thenReturn(Jwt.withTokenValue(subject).header("alg", "RS256")
                .subject(subject).claim("email", "$subject@example.com").claim("name", "Google Name").build())
        }
        `when`(jwtDecoder.decode("invalid")).thenThrow(BadJwtException("Invalid test token"))
    }

    @Test
    fun `all six routes require a valid token`() {
        for (token in listOf(null, "invalid")) {
            val requests = listOf(get("/api/v1/users"), get("/api/v1/users/1"),
                get("/api/v1/users/1/stats"), delete("/api/v1/users/me"), delete("/api/v1/users/1"),
                patch("/api/v1/users/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
            for (request in requests) {
                if (token != null) request.auth(token)
                mvc.perform(request).andExpect(status().isUnauthorized)
            }
        }
    }

    @Test
    fun `ordinary users cannot list promote or delete other accounts`() {
        val member = user("member")
        val other = user("other")
        mvc.perform(get("/api/v1/users").auth()).andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/users/${other.id}").auth()).andExpect(status().isForbidden)
        mvc.perform(edit(member.id, """{"isAdmin":true}""").auth()).andExpect(status().isForbidden)
        mvc.perform(edit(other.id, "{}").auth()).andExpect(status().isForbidden)
        mvc.perform(delete("/api/v1/users/${other.id}").auth()).andExpect(status().isForbidden)
        assertEquals(2, users.count().toInt())
        assertEquals(false, users.findById(checkNotNull(member.id)).orElseThrow().isAdmin)
    }

    @Test
    fun `profile is available only to self or a database admin and does not expose google subject`() {
        user("admin", true)
        val member = user("member")
        for (subject in listOf("member", "admin")) {
            mvc.perform(get("/api/v1/users/${member.id}").auth(subject)).andExpect(status().isOk)
                .andExpect(jsonPath("$.email").value("member@example.com"))
                .andExpect(jsonPath("$.isAdmin").value(false))
                .andExpect(jsonPath("$.googleSubject").doesNotExist())
        }
    }

    @Test
    fun `admin list filters before pagination with stable sorting and matching totals`() {
        user("admin", true, "Zed")
        val first = user("member", name = "Alice")
        val second = user("other", name = "Alice")
        mvc.perform(get("/api/v1/users?displayName=ALIC&page=0&size=1").auth("admin"))
            .andExpect(status().isOk).andExpect(jsonPath("$.content[0].id").value(first.id))
            .andExpect(jsonPath("$.page.totalElements").value(2)).andExpect(jsonPath("$.page.totalPages").value(2))
        mvc.perform(get("/api/v1/users?displayName=alic&page=1&size=1").auth("admin"))
            .andExpect(status().isOk).andExpect(jsonPath("$.content[0].id").value(second.id))
        mvc.perform(get("/api/v1/users?sort=id,desc&size=1").auth("admin"))
            .andExpect(status().isOk).andExpect(jsonPath("$.content[0].id").value(second.id))
        mvc.perform(get("/api/v1/users?sort=displayName,desc&size=1").auth("admin"))
            .andExpect(status().isOk).andExpect(jsonPath("$.content[0].displayName").value("Zed"))
        for (query in listOf("page=100", "displayName=missing")) {
            mvc.perform(get("/api/v1/users?$query").auth("admin"))
                .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
        }
        for (literal in listOf("%", "'", "_")) {
            mvc.perform(get("/api/v1/users").param("displayName", literal).auth("admin"))
                .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
        }
        mvc.perform(get("/api/v1/users").param("displayName", "  ").auth("admin"))
            .andExpect(status().isOk).andExpect(jsonPath("$.page.totalElements").value(3))
    }

    @Test
    fun `list rejects invalid page size and sort`() {
        user("admin", true)
        for (query in listOf("page=-1", "size=0", "size=101", "page=no", "sort=email,asc",
            "sort=id,up", "sort=id", "sort=id,asc,desc")) {
            mvc.perform(get("/api/v1/users?$query").auth("admin")).andExpect(status().isBadRequest)
        }
    }

    @Test
    fun `admin patch edits only admin flag and null is not an omitted field`() {
        user("admin", true)
        val member = user("member")
        for (body in listOf("{}", """{"email":"hacker@example.com","googleSubject":"hacker","displayName":"Fake","id":99}""")) {
            mvc.perform(edit(member.id, body).auth("admin")).andExpect(status().isOk)
                .andExpect(jsonPath("$.isAdmin").value(false)).andExpect(jsonPath("$.email").value("member@example.com"))
        }
        for (body in listOf("{", "", """{"isAdmin":null}""", """{"isAdmin":null,"adminProvided":false}""")) {
            mvc.perform(edit(member.id, body).auth("admin")).andExpect(status().isBadRequest)
        }
        for (value in listOf(true, false)) {
            mvc.perform(edit(member.id, """{"isAdmin":$value}""").auth("admin"))
                .andExpect(status().isOk).andExpect(jsonPath("$.isAdmin").value(value))
            assertEquals(value, users.findById(checkNotNull(member.id)).orElseThrow().isAdmin)
        }
        assertEquals("member", users.findById(checkNotNull(member.id)).orElseThrow().googleSubject)
    }

    @Test
    fun `permissions are rechecked from database after demotion and Google sign in preserves them`() {
        val admin = user("admin", true)
        mvc.perform(post("/api/v1/auth/google").auth("admin")).andExpect(status().isOk)
            .andExpect(jsonPath("$.isAdmin").value(true)).andExpect(jsonPath("$.displayName").value("Google Name"))
        mvc.perform(edit(admin.id, """{"isAdmin":false}""").auth("admin")).andExpect(status().isOk)
        mvc.perform(get("/api/v1/users").auth("admin")).andExpect(status().isForbidden)
        mvc.perform(edit(admin.id, """{"isAdmin":true}""").auth("admin")).andExpect(status().isForbidden)
    }

    @Test
    fun `missing and invalid ids and unregistered callers are handled`() {
        user("admin", true)
        for (id in listOf("0", "-1", "abc", "2147483648")) {
            for (request in listOf(get("/api/v1/users/$id"), get("/api/v1/users/$id/stats"),
                delete("/api/v1/users/$id"), edit(id, "{}"))) {
                mvc.perform(request.auth("admin")).andExpect(status().isBadRequest)
            }
        }
        for (request in listOf(get("/api/v1/users/2147483647"), get("/api/v1/users/2147483647/stats"),
            delete("/api/v1/users/2147483647"), edit(Int.MAX_VALUE, "{}"))) {
            mvc.perform(request.auth("admin")).andExpect(status().isNotFound)
        }
        for (request in listOf(get("/api/v1/users"), get("/api/v1/users/1"),
            delete("/api/v1/users/me"), delete("/api/v1/users/1"), edit(1, "{}"))) {
            mvc.perform(request.auth("unknown")).andExpect(status().isNotFound)
        }
    }

    @Test
    fun `stats count user references even when card references are null and mirror battle counts once`() {
        val member = user("member")
        val opponent = user("other")
        user("admin", true)
        mvc.perform(get("/api/v1/users/${member.id}/stats").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.wins").value(0)).andExpect(jsonPath("$.winRate").value(0.0))
        jdbc.update("""INSERT INTO public."Wins" (id, winninguser, losinguser) VALUES
            (1, ?, ?), (2, ?, ?), (3, ?, ?), (4, NULL, ?)""",
            member.id, opponent.id, opponent.id, member.id, member.id, member.id, opponent.id)
        mvc.perform(get("/api/v1/users/${member.id}/stats").auth("other")).andExpect(status().isOk)
            .andExpect(jsonPath("$.wins").value(2)).andExpect(jsonPath("$.losses").value(2))
            .andExpect(jsonPath("$.totalBattles").value(3)).andExpect(jsonPath("$.winRate").value(0.5))
        mvc.perform(get("/api/v1/users/${opponent.id}/stats").auth()).andExpect(status().isOk)
            .andExpect(jsonPath("$.wins").value(1)).andExpect(jsonPath("$.losses").value(2))
            .andExpect(jsonPath("$.totalBattles").value(3))
    }

    @Test
    fun `self deletion removes only the caller and another Google sign in recreates a non admin account`() {
        val member = user("member")
        val other = user("other")
        mvc.perform(delete("/api/v1/users/me").auth()).andExpect(status().isNoContent).andExpect(content().string(""))
        assertTrue(!users.existsById(checkNotNull(member.id)))
        assertTrue(users.existsById(checkNotNull(other.id)))
        mvc.perform(delete("/api/v1/users/me").auth()).andExpect(status().isNotFound)
        mvc.perform(post("/api/v1/auth/google").auth()).andExpect(status().isCreated)
            .andExpect(jsonPath("$.isAdmin").value(false))
    }

    @Test
    fun `admin can delete an unlinked user without deleting others`() {
        user("admin", true)
        val member = user("member")
        mvc.perform(delete("/api/v1/users/${member.id}").auth("admin")).andExpect(status().isNoContent)
        mvc.perform(delete("/api/v1/users/${member.id}").auth("admin")).andExpect(status().isNotFound)
        assertEquals(1, users.count().toInt())
    }

    @Test
    fun `each existing game relationship blocks deletion without losing any data`() {
        user("admin", true)
        val member = user("member")
        val other = user("other")
        val memberId = checkNotNull(member.id)
        // Test each restrictive relationship separately, rather than depending on their order.
        val card = cards.save(CardEntity(name = "Keep Card", type = CardType.WARRIOR, author = member))
        blocked(memberId)
        cards.delete(card)
        val deck = decks.save(DeckEntity(name = "Keep Deck", user = member))
        blocked(memberId)
        decks.delete(deck)
        jdbc.update("""INSERT INTO public."User_owns_card" (id, userid) VALUES (101, ?)""", memberId)
        blocked(memberId)
        jdbc.update("""DELETE FROM public."User_owns_card" WHERE id = 101""")
        for (column in listOf("winninguser", "losinguser")) {
            jdbc.update("""INSERT INTO public."Wins" (id, $column) VALUES (1, ?)""", memberId)
            blocked(memberId)
            assertEquals(1, jdbc.queryForObject("""SELECT COUNT(*) FROM public."Wins" """, Int::class.java))
            jdbc.update("""DELETE FROM public."Wins" """)
        }
        assertTrue(users.existsById(checkNotNull(other.id)))
    }

    private fun blocked(id: Int) {
        for (request in listOf(delete("/api/v1/users/me").auth(), delete("/api/v1/users/$id").auth("admin"))) {
            mvc.perform(request).andExpect(status().isConflict)
            assertTrue(users.existsById(id))
        }
    }

    private fun user(subject: String, admin: Boolean = false, name: String = subject) = users.save(UserEntity(
        googleSubject = subject, email = "$subject@example.com", displayName = name, isAdmin = admin,
    ))

    private fun edit(id: Any?, body: String) = patch("/api/v1/users/$id").contentType(MediaType.APPLICATION_JSON).content(body)
    private fun MockHttpServletRequestBuilder.auth(subject: String = "member") = header("Authorization", "Bearer $subject")
}
