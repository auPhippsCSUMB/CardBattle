# ADR 002: Monorepo with Separate Gradle Projects

Date recorded: 2026-10-26  
Status: Implemented

## Context

CardBattle contains a Spring Boot API and an Android application. They share an API contract but have different dependencies, build configurations, and runtime requirements.

## Decision

Keep both applications in one Git repository. Place the backend in `api-backend` and the Android client in `app-frontend`. Each application maintains its own Gradle wrapper and build configuration.

Store shared contracts and design documentation under `docs`.

## Consequences

One pull request can include related backend, Android, and documentation changes. Issues and design artifacts remain together.

Developers must open and synchronize the correct Gradle project. Backend and Android tests require separate commands, and CI must explicitly configure checks for each application.