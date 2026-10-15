package com.example.cardbattle.data.remote

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class CardBattleApiTest {
    private lateinit var server: HttpServer

    private val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
    }

    @AfterEach
    fun stopServer() = server.stop(0)

    @Test
    fun `first sign in sends bearer token and reads created user`() {
        val request = AtomicReference<CapturedRequest>()
        server.createContext("/api/v1/auth/google") { exchange ->
            request.set(exchange.capture())
            exchange.respond(201, profileJson("New Player"))
        }

        val user = runBlocking { signInAndGetUser("test-id-token", baseUrl) }

        assertEquals("POST", request.get().method)
        assertEquals("Bearer test-id-token", request.get().authorization)
        assertEquals("New Player", user.displayName)
        assertEquals(7, user.id)
    }

    @Test
    fun `repeat sign in accepts existing user response`() {
        server.createContext("/api/v1/auth/google") { exchange ->
            exchange.respond(200, profileJson("Chosen Name"))
        }

        val user = runBlocking { signInAndGetUser("test-id-token", baseUrl) }

        assertEquals("Chosen Name", user.displayName)
    }

    @Test
    fun `rejected sign in does not return a user`() {
        server.createContext("/api/v1/auth/google") { exchange -> exchange.respond(401, "") }

        val error = assertThrows(IOException::class.java) {
            runBlocking { signInAndGetUser("bad-token", baseUrl) }
        }

        assertEquals("Backend sign-in returned HTTP 401", error.message)
    }

    @Test
    fun `display name update sends token and JSON and reads saved user`() {
        val request = AtomicReference<CapturedRequest>()
        server.createContext("/api/v1/users/me/display-name") { exchange ->
            request.set(exchange.capture())
            exchange.respond(200, profileJson("Captain"))
        }

        val user = runBlocking { updateDisplayName("test-id-token", "Captain", baseUrl) }

        assertEquals("PUT", request.get().method)
        assertEquals("Bearer test-id-token", request.get().authorization)
        assertEquals("application/json; charset=UTF-8", request.get().contentType)
        assertEquals("""{"displayName":"Captain"}""", request.get().body)
        assertEquals("Captain", user.displayName)
    }

    @Test
    fun `expired token status is available to the edit flow`() {
        server.createContext("/api/v1/users/me/display-name") { exchange -> exchange.respond(401, "") }

        val error = assertThrows(BackendHttpException::class.java) {
            runBlocking { updateDisplayName("expired-token", "Captain", baseUrl) }
        }

        assertEquals(401, error.statusCode)
    }

    private fun profileJson(displayName: String) =
        """{"id":7,"email":"player@example.com","displayName":"$displayName","isAdmin":false}"""

    private data class CapturedRequest(
        val method: String,
        val authorization: String?,
        val contentType: String?,
        val body: String,
    )

    private fun HttpExchange.capture() = CapturedRequest(
        method = requestMethod,
        authorization = requestHeaders.getFirst("Authorization"),
        contentType = requestHeaders.getFirst("Content-Type"),
        body = requestBody.bufferedReader().use { it.readText() },
    )

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) } else close()
    }
}
