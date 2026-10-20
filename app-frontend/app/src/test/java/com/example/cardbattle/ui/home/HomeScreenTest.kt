package com.example.cardbattle.ui.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeScreenTest {
    @Test
    fun `calculateWinRate returns expected percentage`() {
        assertEquals("72%", calculateWinRate(18, 7))
        assertEquals("0%", calculateWinRate(0, 0))
    }
}
