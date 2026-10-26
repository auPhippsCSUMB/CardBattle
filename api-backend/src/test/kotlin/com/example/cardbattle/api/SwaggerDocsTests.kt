package com.example.cardbattle.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest(
        properties = [
            "spring.datasource.url=jdbc:h2:mem:swagger-tests;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.security.oauth2.resourceserver.jwt.audiences=swagger-test-client",
        ]
)
@AutoConfigureMockMvc
class SwaggerDocsTests {

    @Autowired
    lateinit var mvc: MockMvc

    @MockBean
    lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `Swagger landing page is publicly accessible`() {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection)
    }

    @Test
    fun `Swagger HTML is publicly accessible`() {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk)
                .andExpect(
                        content().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                )
    }

    @Test
    fun `API definition contains routes and bearer authentication`() {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.info.title").value("CardBattle API"))
                .andExpect(jsonPath("$.paths['/api/v1/cards'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cards'].post").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/deck-battles'].post").exists()
                )
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                                .value("bearer")
                )
    }

    @Test
    fun `card endpoints still require authentication`() {
        mvc.perform(get("/api/v1/cards"))
                .andExpect(status().isUnauthorized)
    }
}