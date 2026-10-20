package com.example.cardbattle.data.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AuthSessionTest {
    @Test
    fun `token is kept until session is cleared`() {
        val session = AuthSession()
        assertNull(session.idToken)

        session.save("test-id-token")
        assertEquals("test-id-token", session.idToken)

        session.clear()
        assertNull(session.idToken)
    }
}
