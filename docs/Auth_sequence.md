# Authentication Sequence

This document describes CardBattle's implemented Google sign-in flow:
Android Credential Manager obtains a Google ID token, and the Spring Boot
API validates that token as a bearer credential.

## Sequence

```mermaid
sequenceDiagram
    actor User
    participant App as Android app
    participant CM as Credential Manager
    participant Google
    participant API as Spring Boot API on Render
    participant DB as PostgreSQL on Railway

    User->>App: Tap Sign in with Google
    App->>CM: Request Google credential with nonce
    CM->>Google: Authenticate selected account
    Google-->>CM: Google ID token
    CM-->>App: Credential containing ID token

    App->>API: POST /api/v1/auth/google with Bearer token
    API->>API: Validate signature, issuer, audience and expiry

    alt Invalid or expired token
        API-->>App: 401 Unauthorized
        App-->>User: Show sign-in error
    else Valid token
        API->>DB: Find user by Google subject

        alt User does not exist
            API->>DB: Create user
            API-->>App: 201 Created with user profile
        else User already exists
            API->>DB: Refresh display name
            API-->>App: 200 OK with user profile
        end

        App->>App: Store token and profile in memory
        App-->>User: Show Home
    end

    User->>App: Open protected resource
    App->>API: API request with Bearer token
    API->>API: Validate token

    alt Token invalid or missing
        API-->>App: 401 Unauthorized
    else Token valid
        API->>DB: Check applicable ownership or admin permissions

        alt Access denied
            API-->>App: Authorization error
        else Access allowed
            API->>DB: Read or update resource
            DB-->>API: Result
            API-->>App: API response
        end
    end

    User->>App: Log out
    App->>App: Clear token, profile and drawing drafts
    App-->>User: Show Login
```

## Authentication and authorization

- Google identifies the user through the verified token's subject claim.
- First sign-in creates the application's user record.
- Returning sign-in refreshes the stored display name from Google.
- Protected requests include `Authorization: Bearer <token>`.
- Administrative permissions come from the database's `isadmin` flag.
- Resource ownership is enforced by backend services.
- Unauthorized resource lookups may return 404 to avoid exposing another
  user's private resources.
- Swagger documentation and the health endpoint are publicly accessible.

## Session behavior

The application keeps its token in memory. Logging out clears the local
application session; it does not sign the device out of its Google account.

A missing or expired token requires a new successful sign-in.