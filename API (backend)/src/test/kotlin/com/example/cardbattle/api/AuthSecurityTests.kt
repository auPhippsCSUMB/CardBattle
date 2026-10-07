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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date

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

    private fun rejected(value: String) {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer $value"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.googleSubject").doesNotExist())
    }

    private fun token(
        issuer: String = "https://accounts.google.com",
        audience: String = "cardbattle-test-client",
        expires: Instant = Instant.now().plusSeconds(300),
        key: RSAKey = signingKey
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer).audience(audience).subject("test-google-subject")
            .issueTime(Date.from(Instant.now().minusSeconds(600)))
            .expirationTime(Date.from(expires))
            .claim("email", "player@example.com").claim("name", "Test Player").build()
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
