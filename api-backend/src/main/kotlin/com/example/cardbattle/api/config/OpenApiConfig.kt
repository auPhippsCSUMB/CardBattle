package com.example.cardbattle.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun cardBattleOpenApi(): OpenAPI =
            OpenAPI()
                    .info(
                            Info()
                                    .title("CardBattle API")
                                    .version("v1")
                                    .description(
                                            "API for Google sign-in, user profiles, cards, " +
                                                    "drawing uploads, inventory, decks, and battles. " +
                                                    "Protected operations require a valid Google ID token."
                                    )
                    )
                    .servers(
                            listOf(
                                    Server()
                                            .url("/")
                                            .description("Current API server")
                            )
                    )
                    .components(
                            Components()
                                    .addSecuritySchemes(
                                            "bearerAuth",
                                            SecurityScheme()
                                                    .type(SecurityScheme.Type.HTTP)
                                                    .scheme("bearer")
                                                    .bearerFormat("JWT")
                                                    .description(
                                                            "Paste a fresh Google ID token without " +
                                                                    "the Bearer prefix."
                                                    )
                                    )
                    )
                    .addSecurityItem(
                            SecurityRequirement().addList("bearerAuth")
                    )
}