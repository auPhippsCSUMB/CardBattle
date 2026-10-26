# ADR 001: Hosted PostgreSQL

Date recorded: 2026-10-26  
Status: Implemented

## Context

CardBattle stores users, card designs, owned copies, decks, memberships, artwork, and battle results. These resources have relationships that require consistent IDs and referential integrity.

## Decision

Use PostgreSQL hosted on Railway as the shared persistent database. The Spring Boot API runs separately on Render and connects using environment-provided database credentials.

Use Spring Data JPA for entity operations and JDBC for operations requiring explicit SQL. Provide a separate PostgreSQL instance through Docker Compose for local development.

## Consequences

Foreign keys and transactions support consistent updates between related records. Database contents persist when the API container is replaced.

The backend requires database connectivity and private configuration. Schema changes must be applied consistently across environments. PostgreSQL-specific queries need testing against PostgreSQL.