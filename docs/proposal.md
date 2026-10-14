# <API name> Proposal

## 1. The pitch (one paragraph)
What the API does, who uses it, and why a client app would need it.
Our API allows users to create their own user, edit their own user, and delete their own user. Users
can create their own cards, edit their own cards, and delete their own cards. Users can also GET other
cards, either from a specific parameter, all cards, or random card. A client app would need this
API if they plan on using our app to create, trade, and battle cards as their own user.

## 2. Resources
| Resource       | Key fields                                                           | Relationships                                      |
| -------------- | -------------------------------------------------------------------- | -------------------------------------------------- |
| User           | id, email, displayName, role, password, isAdmin                      | Each user owns 0 to many cards, decks, and wins    |
| Card           | id, name, imgURL, description, type, author(fk)                      | Each card is authored by one user                  |
| User_owns_card | id, userId(fk), cardId(fk), value                                    | Each card is owned by one to many users            |
| Deck           | id, userId(fk), name                                                 | Each deck is created by one user                   |
| Card_in_deck   | id, cardId(fk), deckId(fk), userId(fk), type(fk)                     | Each card belongs to one to many decks             |
| Wins           | id, winningUser(fk), losingUser(fk), winningCard(fk), losingCard(fk) | Each user and card has one to many wins and losses |


## 3. ER sketch
Tables, primary and foreign keys, and cardinality. Edit this Mermaid diagram (it renders on GitHub;
try changes at https://mermaid.live):

```mermaid
erDiagram
    User {
        INT id PK
        VARCHAR(255) email
        VARCHAR(100) displayName
        VARCHAR(50) role
        VARCHAR(255) password
        BOOLEAN isAdmin
    }

    Card {
        INT id PK
        VARCHAR(150) name
        VARCHAR(500) imgURL
        TEXT description
        VARCHAR(50) type
        INT author FK
    }

    Attributes {
        INT id PK
        INT cardId FK
        VARCHAR(100) name
        VARCHAR(255) value
    }

    User_owns_card {
        INT id PK
        INT userId FK
        INT cardId FK
        INT value
    }

    Deck {
        INT id PK
        INT userId FK
        VARCHAR(150) name
    }

    Card_in_deck {
        INT id PK
        INT cardId FK
        INT deckId FK
        INT userId FK
        VARCHAR(50) type
    }

    Wins {
        INT id PK
        INT winningUser FK
        INT losingUser FK
        INT winningCard FK
        INT losingCard FK
    }

    User ||--o{ Card : authors
    User ||--o{ User_owns_card : owns
    Card ||--o{ User_owns_card : owned_as
    User ||--o{ Deck : creates
    Deck ||--o{ Card_in_deck : contains
    User ||--o{ Card_in_deck : uses
    User_owns_card ||--o{ Card_in_deck : selected_card
    User ||--o{ Wins : winning_user
    User ||--o{ Wins : losing_user
    User_owns_card ||--o{ Wins : winning_card
    User_owns_card ||--o{ Wins : losing_card
```

## 4. Endpoints

| Verb   | Path                                                               | Auth   | Purpose                                                                 |
| ------ | ------------------------------------------------------------------ | ------ | ----------------------------------------------------------------------- |
| POST   | /api/v1/users                                                      | public | create a user                                                            |
| GET    | /api/v1/users/me                                                   | user   | get my profile                                                           |
| PATCH  | /api/v1/users/me                                                   | user   | edit my profile                                                          |
| DELETE | /api/v1/users/me                                                   | user   | delete my user                                                           |
| GET    | /api/v1/users?page=0&size=20&displayName=gideon&sort=displayName,asc | admin  | list all users (paginated, filter by displayName, sort by displayName)   |
| GET    | /api/v1/users/{userId}                                             | admin  | get one user                                                             |
| PATCH  | /api/v1/users/{userId}                                             | admin  | update a user's role/admin state                                         |
| DELETE | /api/v1/users/{userId}                                             | admin  | delete a user                                                            |
| GET    | /api/v1/cards?page=0&size=20&type=mage&sort=name,asc               | user   | list my authored cards (paginated, filter by type, sort by name)         |
| POST   | /api/v1/cards                                                      | user   | create a card                                                            |
| GET    | /api/v1/cards/{cardId}                                             | user   | get one card I authored                                                  |
| PATCH  | /api/v1/cards/{cardId}                                             | user   | edit a card I authored                                                   |
| DELETE | /api/v1/cards/{cardId}                                             | user   | delete a card I authored                                                 |
| GET    | /api/v1/allCards?page=0&size=20&type=mage&author=12&sort=name,asc  | public | list all public cards (paginated, filter by type/author, sort by name)   |
| GET    | /api/v1/allCards/{cardId}                                          | public | get one public card                                                      |
| GET    | /api/v1/randomCard?type=mage                                       | public | get a random card, optionally filtered by type                           |
| GET    | /api/v1/inventory?page=0&size=20&userId=7&type=mage                | user   | list cards a user owns (paginated, filter by owner/type)                 |
| POST   | /api/v1/inventory                                                  | user   | add an owned card entry to a user's collection                           |
| PATCH  | /api/v1/inventory/{ownershipId}                                    | user   | update owned card quantity/value                                         |
| DELETE | /api/v1/inventory/{ownershipId}                                    | user   | remove an owned card entry                                               |
| GET    | /api/v1/decks?page=0&size=20&userId=7&sort=name,asc                | user   | list decks (paginated, filter by owner, sort by name)                    |
| POST   | /api/v1/decks                                                      | user   | create a deck                                                            |
| GET    | /api/v1/decks/{deckId}                                             | user   | get one deck                                                             |
| PATCH  | /api/v1/decks/{deckId}                                             | user   | rename or edit a deck                                                    |
| DELETE | /api/v1/decks/{deckId}                                             | user   | delete a deck                                                            |
| GET    | /api/v1/decks/{deckId}/cards                                       | user   | list cards in a deck                                                     |
| POST   | /api/v1/decks/{deckId}/cards                                       | user   | add a card to a deck                                                     |
| PATCH  | /api/v1/decks/{deckId}/cards/{cardInDeckId}                        | user   | edit deck-card metadata such as slot/type                                |
| DELETE | /api/v1/decks/{deckId}/cards/{cardInDeckId}                        | user   | remove a card from a deck                                                |
| GET    | /api/v1/battles?page=0&size=20&userId=7                            | user   | list battle results/history (paginated, filter by player)                |
| POST   | /api/v1/battles                                                    | user   | record a battle result                                                   |
| GET    | /api/v1/battles/{battleId}                                         | user   | get one battle result                                                    |
| GET    | /api/v1/users/{userId}/stats                                       | public | get win/loss stats for a user                                            |
| GET    | /api/v1/cards/{cardId}/stats                                       | public | get win/loss stats for a card                                            |
    
Mark each endpoint `public`, `user`, or `admin`. Mark which collection paginates and which
filters or sorts.

## 5. Technical choices
- **Database host:** (Neon, Supabase, Railway, Atlas, ...) and why
We are using Railway because of familiarity and easy Postgresql compatibality.
- **OAuth2 provider:** (Google, GitHub, Auth0) and confirmation that it supports Authorization Code + PKCE from a native app
We are going to be using Google for Oauth for simplicity and familiarity
- **Repo layout:** monorepo or split, and why
We are using a monorepo because we think it makes more sense to keep the scope of the project under one repo
These become your ADRs later.

## 6. Risks
The two things most likely to go wrong, and what you will do first to find out.
- We think it is likely for our gradle to have issues because it always does, but especially because we are trying to use the same
gradle for both API and Android builds. We will try to run both skeleton builds with the same gradle on all machines to find out first.
- We think the monorepo might end up causing issues because of the checks and rules being separate for each folder and unfamiliarity with
application of rules per folder instead of per-repo.

## 7. Team and Sprint 1
Who owns what in Sprint 1. Link your Project board and Sprint 1 milestone. (FEEL FREE TO REPEAT ALREADY OWNED THINGS __DELETE WHEN READ)
- What Austin owns:
    - API endroutes and openapi.yaml
- What Michael owns:
    - ER Diagram and database
- What Fernando owns:
    - HomeScreen.kt and error model
- What Gideon owns:
    - Spring Boot + Android Studio Starter code and openapi.yaml


- [Project Board](https://github.com/users/auPhippsCSUMB/projects/1)
- [Sprint 1](https://github.com/auPhippsCSUMB/CardBattle/milestone/22)
