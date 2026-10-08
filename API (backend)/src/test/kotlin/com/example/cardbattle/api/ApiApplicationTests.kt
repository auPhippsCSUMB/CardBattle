package com.example.cardbattle.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.oauth2.jwt.JwtDecoder

// Test properties outrank exported Railway datasource variables in the developer's shell.
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:context-tests;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
class ApiApplicationTests {

    @MockBean
    lateinit var jwtDecoder: JwtDecoder

    @Test
    fun contextLoads() {
    }
}
