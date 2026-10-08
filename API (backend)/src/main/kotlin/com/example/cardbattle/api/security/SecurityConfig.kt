package com.example.cardbattle.api.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
                .csrf { csrf -> csrf.disable() }
                .authorizeHttpRequests { auth ->
                    auth.requestMatchers("/actuator/health")
                            .permitAll()
                            .anyRequest()
                            .authenticated()
                }
                .oauth2ResourceServer { oauth2 -> oauth2.jwt {} }

        return http.build()
    }
}
