package com.example.cardbattle.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.oauth2.jwt.JwtDecoder

@SpringBootTest
class ApiApplicationTests {

    @MockBean
    lateinit var jwtDecoder: JwtDecoder

    @Test
    fun contextLoads() {
    }
}