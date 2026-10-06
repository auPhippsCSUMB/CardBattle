package com.example.cardbattle.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.DriverManager
import java.time.Duration
import java.util.Properties

/** No Spring context: these checks must never run Hibernate schema updates on a remote DB. */
class LiveConnectionTests {
    @Test
    @Tag("railway")
    fun `Railway PostgreSQL accepts a read only query`() {
        val url = required("SPRING_DATASOURCE_URL")
        require(url.startsWith("jdbc:postgresql://")) { "Use a PostgreSQL JDBC URL (or a Railway SSH tunnel)." }
        val properties = Properties().apply {
            setProperty("user", required("SPRING_DATASOURCE_USERNAME"))
            setProperty("password", required("SPRING_DATASOURCE_PASSWORD"))
            setProperty("connectTimeout", "10")
            setProperty("socketTimeout", "10")
            setProperty("readOnly", "true")
        }
        DriverManager.getConnection(url, properties).use { connection ->
            connection.isReadOnly = true
            assertEquals("PostgreSQL", connection.metaData.databaseProductName)
            connection.createStatement().use { statement ->
                statement.queryTimeout = 10
                statement.executeQuery("SELECT 1").use { result ->
                    assertTrue(result.next())
                    assertEquals(1, result.getInt(1))
                }
            }
        }
    }

    @Test
    @Tag("google-live")
    fun `running backend accepts real Google identity and rejects missing or invalid credentials`() {
        val baseUrl = required("CARDBATTLE_API_URL").trimEnd('/')
        val uri = URI.create("$baseUrl/api/v1/auth/me")
        require(uri.scheme == "https" ||
            (uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "::1", "[::1]"))) {
            "Use HTTPS except when testing a loopback backend."
        }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        val token = required("GOOGLE_ID_TOKEN")
        val expectedSubject = required("GOOGLE_EXPECTED_SUBJECT")
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        fun request(bearer: String?): HttpResponse<String> {
            val builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET()
            if (bearer != null) builder.header("Authorization", "Bearer $bearer")
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }
        assertEquals(401, request(null).statusCode(), "Missing token must be rejected")
        assertEquals(401, request("not-a-jwt").statusCode(), "Invalid token must be rejected")
        val response = request(token)
        assertEquals(200, response.statusCode(), "Fresh Google ID token must be accepted")
        val body = jacksonObjectMapper().readTree(response.body())
        // Avoid printing identity or bearer-token values in assertion failures.
        assertTrue(body.path("googleSubject").asText() == expectedSubject, "Verified subject must match the test account")
    }

    private fun required(name: String): String = System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: error("Set $name before running this opt-in live test. See API (backend)/README.md.")
}
