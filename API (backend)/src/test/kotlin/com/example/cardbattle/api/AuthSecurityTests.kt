package com.example.cardbattle.api

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date
import com.example.cardbattle.api.user.UserEntity
import com.example.cardbattle.api.user.UserRepository
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import kotlin.test.assertEquals


/** Exercises the real filter chain and Boot-configured JWT decoder with local signing keys. */
@SpringBootTest(properties = [
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://accounts.google.com",
    "spring.security.oauth2.resourceserver.jwt.audiences=cardbattle-test-client",
    "spring.datasource.url=jdbc:h2:mem:auth-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
@AutoConfigureMockMvc
class AuthSecurityTests {
    @Autowired
    lateinit var mvc: MockMvc
    @Autowired
    lateinit var users: UserRepository

    @BeforeEach
    fun clearUsers() = users.deleteAll()

    @Test
    fun `valid signed token returns its verified user claims`() {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.googleSubject").value("test-google-subject"))
            .andExpect(jsonPath("$.email").value("player@example.com"))
            .andExpect(jsonPath("$.name").value("Test Player"))
    }

    @Test
    fun `missing token returns 401 with a bearer challenge`() {
        mvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(content().string(""))
    }

    @Test
    fun `malformed token is rejected`() = rejected("not-a-jwt")

    @Test
    fun `expired token is rejected`() = rejected(token(expires = Instant.now().minusSeconds(300)))

    @Test
    fun `token for a different client is rejected`() = rejected(token(audience = "another-client"))

    @Test
    fun `token from a different issuer is rejected`() = rejected(token(issuer = "https://untrusted.example"))

    @Test
    fun `token signed with an untrusted key is rejected`() = rejected(token(key = otherKey))

    @Test
    fun `health remains public`() {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `signed in user can change their display name`() {
        users.save(
            UserEntity(
                googleSubject = "test-google-subject",
                email = "player@example.com",
                displayName = "Old Name"
            )
        )

        mvc.perform(
            put("/api/v1/users/me/display-name")
                .header("Authorization", "Bearer ${token()}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName":"  Captain  "}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Captain"))

        assertEquals(
            "Captain",
            users.findByGoogleSubject("test-google-subject")?.displayName
        )
    }

    @Test
    fun `first Google sign in registers user from verified token`() {
        mvc.perform(post("/api/v1/auth/google").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value("player@example.com"))
            .andExpect(jsonPath("$.displayName").value("Test Player"))
            .andExpect(jsonPath("$.isAdmin").value(false))

        assertEquals(1L, users.count())
        assertEquals("player@example.com", users.findByGoogleSubject("test-google-subject")?.email)
    }

    @Test
    fun `repeat Google sign in keeps the user's edited name and does not duplicate them`() {
        mvc.perform(post("/api/v1/auth/google").header("Authorization", "Bearer ${token()}"))
            .andExpect(status().isCreated)
        val user = users.findByGoogleSubject("test-google-subject")!!
        user.displayName = "Captain"
        users.save(user)

        mvc.perform(post("/api/v1/auth/google").header("Authorization", "Bearer ${token(name = "New Google Name")}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.displayName").value("Captain"))
            .andExpect(jsonPath("$.id").value(user.id))

        assertEquals(1L, users.count())
        assertEquals("Captain", users.findByGoogleSubject("test-google-subject")?.displayName)
    }

    @Test
    fun `Google sign in needs a valid token before it can create a user`() {
        mvc.perform(post("/api/v1/auth/google"))
            .andExpect(status().isUnauthorized)
        mvc.perform(post("/api/v1/auth/google")
            .header("Authorization", "Bearer ${token(audience = "another-client")}"))
            .andExpect(status().isUnauthorized)

        assertEquals(0L, users.count())
    }

    @Test
    fun `display name update needs a valid token`() {
        users.save(UserEntity(googleSubject = "test-google-subject", email = "player@example.com", displayName = "Old Name"))
        val request = put("/api/v1/users/me/display-name")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"displayName":"Attacker"}""")

        mvc.perform(request).andExpect(status().isUnauthorized)
        mvc.perform(put("/api/v1/users/me/display-name")
            .header("Authorization", "Bearer ${token(expires = Instant.now().minusSeconds(300))}")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"displayName":"Attacker"}"""))
            .andExpect(status().isUnauthorized)

        assertEquals("Old Name", users.findByGoogleSubject("test-google-subject")?.displayName)
    }

    @Test
    fun `one user's token cannot edit another user's display name`() {
        users.save(UserEntity(googleSubject = "test-google-subject", email = "player@example.com", displayName = "Old Name"))

        mvc.perform(put("/api/v1/users/me/display-name")
            .header("Authorization", "Bearer ${token(subject = "different-google-subject")}")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"displayName":"Attacker"}"""))
            .andExpect(status().isNotFound)

        assertEquals("Old Name", users.findByGoogleSubject("test-google-subject")?.displayName)
    }

    @Test
    fun `blank display name is rejected without changing the saved user`() {
        users.save(UserEntity(googleSubject = "test-google-subject", email = "player@example.com", displayName = "Old Name"))

        mvc.perform(put("/api/v1/users/me/display-name")
            .header("Authorization", "Bearer ${token()}")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"displayName":"   "}"""))
            .andExpect(status().isBadRequest)

        assertEquals("Old Name", users.findByGoogleSubject("test-google-subject")?.displayName)
    }

    private fun rejected(value: String) {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $value"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.googleSubject").doesNotExist())
    }

    private fun token(
        issuer: String = "https://accounts.google.com",
        audience: String = "cardbattle-test-client",
        expires: Instant = Instant.now().plusSeconds(300),
        key: RSAKey = signingKey,
        subject: String = "test-google-subject",
        name: String = "Test Player",
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer).audience(audience).subject(subject)
            .issueTime(Date.from(Instant.now().minusSeconds(600)))
            .expirationTime(Date.from(expires))
            .claim("email", "player@example.com").claim("name", name).build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims)
            .apply { sign(RSASSASigner(key)) }.serialize()
    }

    companion object {
        private val signingKey = RSAKeyGenerator(2048).keyID("test-key").generate()
        private val otherKey = RSAKeyGenerator(2048).keyID("test-key").generate()
        private val keyServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/jwks") { exchange ->
                val body = JWKSet(signingKey.toPublicJWK()).toString().toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun keys(registry: DynamicPropertyRegistry) {
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://127.0.0.1:${keyServer.address.port}/jwks"
            }
        }

        @JvmStatic
        @AfterAll
        fun stopKeyServer() = keyServer.stop(0)
    }
}
