# Backend tests

From `api-backend`, run `bash ./gradlew test` (or `bash ./gradlew check` for coverage checks).
No Railway credentials or Google account are needed. Tests use H2 and an ephemeral local
HTTP key server. `AuthSecurityTests` sends real RSA-signed test JWTs through the application's
security filter and Boot-configured decoder. It checks successful claims, missing/malformed
tokens, expiration, wrong audience, wrong issuer, wrong signature, and public health access.
These prove our validation behavior; they do not prove Google Console setup or Railway availability.

## Card creation

Run `bash ./gradlew test --tests '*CreateCardTests'` to test `POST /api/v1/cards`.
These tests use the real controller, service, and repository with a local H2 database
and mocked token verification. Each test rolls back its database changes.
They cover saving a card with the authenticated author, optional fields, invalid input,
missing/invalid tokens, and a token whose user has not registered.
No Railway connection or real Google token is needed.

## Paginated card lists

Run `bash ./gradlew test --tests '*CardListTests'` for the two list routes.
These tests use real queries against a local H2 database and mocked token verification.

- `GET /api/v1/cards` lists only cards authored by the signed-in Google user, not inventory.
- `GET /api/v1/allCards` lists all authors' cards and accepts an optional integer
  `author` user ID. Both routes require a valid bearer token.
- Both accept zero-based `page` (default 0), `size` (default 20, range 1–100),
  optional uppercase `type` (for example `MAGE`), and `sort=field,direction`.
  Sort fields are `name`, `id`, or `type`; directions are `asc` or `desc`.
  The default is `name,asc`. Equal names/types use ascending ID as a tie-breaker.
- Filtering happens before pagination and counting. The response contains `content`
  and `page` with `size`, `number`, `totalElements`, and `totalPages`.
  Empty matches and pages beyond the end return 200 with an empty list; a page
  beyond the end still reports the matching total.
- Negative pages, sizes outside 1–100, invalid sorts/types, and nonpositive or
  invalid author IDs return 400. An unknown positive author ID gives no matches.

Example requests while the backend is running (`CARD_TOKEN` contains a fresh Google token):

```bash
curl -i 'http://localhost:8080/api/v1/cards?page=0&size=2&sort=name,asc' -H "Authorization: Bearer $CARD_TOKEN"
curl -i 'http://localhost:8080/api/v1/cards?page=1&size=2&sort=name,asc' -H "Authorization: Bearer $CARD_TOKEN"
curl -i 'http://localhost:8080/api/v1/allCards?page=0&size=2&type=MAGE&sort=id,desc' -H "Authorization: Bearer $CARD_TOKEN"
# Replace 7 with an existing user ID to try the author filter.
curl -i 'http://localhost:8080/api/v1/allCards?author=7' -H "Authorization: Bearer $CARD_TOKEN"
```

No database migration or new dependencies are needed. Spring Data's `PageRequest`
performs database pagination and its `Page` result supplies the matching totals.

## Get and delete cards

Run `bash ./gradlew test --tests '*CardRoutesTests'` for the random-card, get-my-card,
get-public-card, and delete-card routes. The tests use a separate local H2 database and fake token
verification. Test-only inventory, deck, and battle tables model Railway's foreign keys.

- `GET /api/v1/randomCard` returns any author's card; optional `?type=MAGE` filters it.
  No cards or no matching type returns 404; invalid type names return 400.
- `GET /api/v1/cards/{cardId}` returns a card only to its author.
- `GET /api/v1/allCards/{cardId}` returns a card from any author. "Public" means
  catalog access for signed-in users; it still requires a valid Google bearer token.
  A missing/deleted card returns 404, and a noninteger or out-of-range ID returns 400.
- `DELETE /api/v1/cards/{cardId}` deletes a card only for its author and returns 204.
  Inventory entries and their deck entries are deleted automatically. Decks and battle
  results remain, with deleted battle-card references set to null. An unrelated
  restrictive foreign key still returns 409.
- Missing cards and cards authored by someone else return 404 for get-my-card and delete.
  All four routes require a valid Google bearer token (otherwise 401).

With the backend running, replace 123 below with an existing card ID:

```bash
curl -i 'http://localhost:8080/api/v1/allCards/123' -H "Authorization: Bearer $CARD_TOKEN"
```

Expect 200 even if someone else authored the card. Compare with the read-only SQL
`SELECT id, name, imgurl, description, type, author FROM public."Card" WHERE id = 123;`.
The read does not create an inventory entry or modify the card.

The random query uses `ORDER BY RANDOM() LIMIT 1`, suitable for the small project
database. The read routes return data only; requesting a random card does not grant
inventory ownership.

The manual PostgreSQL migration is `db/migrations/20261019_card_delete_cascade.sql`.
It records the four foreign-key changes; Spring Boot does not apply it automatically.
If you already ran this SQL in Railway, no additional database action is needed.
The H2 test schema in `src/test/resources/card-delete-schema.sql` models the same
relationships but does not execute or verify the PostgreSQL migration itself.

Tests verify deletion across multiple users' inventories, a three-card deck becoming
incomplete, an empty deck remaining, and winning/losing card references becoming null
without losing battle history or unaffected records. Another author's delete request
must leave the card and related data intact. `BattleResponse` permits null card IDs;
`BattleCreateRequest` still requires both IDs for a new battle.

## Card statistics

`GET /api/v1/cards/{cardId}/stats` returns `wins`, `losses`, `totalBattles`, and
`winRate`. Any signed-in user can view these statistics, across every owner's
inventory copies of the specified card. A missing/deleted card returns 404,
invalid integer IDs return 400, and missing/invalid authentication returns 401.
An existing card without results returns 200 with all four values zero.

The three short repository count queries join `Wins.winningcard/losingcard` to
`User_owns_card.id`, then filter by `User_owns_card.cardid`.
`totalBattles` counts distinct battle rows: a match between two copies of the same
card contributes one win, one loss, but just one battle. `winRate` is
`wins / (wins + losses)`, a fraction from 0 to 1, or 0 when there are no results.
It is deliberately not divided by `totalBattles` because of same-card matches.

Left joins preserve the remaining side's stats when the opponent reference is null.
A null card reference cannot be attributed to its original card, so these statistics
only include results whose inventory links still exist. No schema migration or new
inventory/battle entities are needed.

Run `bash ./gradlew test --tests '*CardRoutesTests'` for statistics and other card
route tests. With the backend running and a fresh token:

```bash
# Replace 123 with an existing card ID.
curl -i 'http://localhost:8080/api/v1/cards/123/stats' -H "Authorization: Bearer $CARD_TOKEN"
```

Compare the counts with this read-only PostgreSQL query (replace 123 throughout):

```sql
SELECT
    COUNT(*) FILTER (WHERE winner.cardid = 123) AS wins,
    COUNT(*) FILTER (WHERE loser.cardid = 123) AS losses,
    COUNT(*) AS total_battles
FROM public."Wins" w
LEFT JOIN public."User_owns_card" winner ON winner.id = w.winningcard
LEFT JOIN public."User_owns_card" loser ON loser.id = w.losingcard
WHERE winner.cardid = 123 OR loser.cardid = 123;
```

## Partial card updates

`PATCH /api/v1/cards/{cardId}` updates only a card authored by the signed-in user
and returns 200 with the updated `CardResponse`.

- Omitted fields stay unchanged. An empty `{}` is a successful no-op.
- `name` is trimmed and must have 1–150 characters. `type` must be a valid enum
  value. Neither accepts explicit null.
- `imgURL` accepts at most 500 characters or null. `description` accepts text or
  null. Explicit null clears either optional field.
- ID and author are not editable. Unknown properties are ignored by the existing
  Jackson configuration, so sending those fields cannot change them.
- Invalid input returns 400 without partially applying the other fields. Missing
  cards and another author's cards return 404. Missing/invalid tokens return 401.

`CardPatchRequest` uses property setters to track supplied fields in an internal,
JSON-ignored set. This is the small extra mechanism needed to distinguish
`{}` from `{"description":null}`. Create and patch share the same name/image URL
validation helpers. No database changes or new dependencies are needed.

Run `bash ./gradlew test --tests '*CardPatchTests'`. Tests use local H2 and mocked
token verification; successful updates are flushed and reloaded to verify stored values.

With the backend running and a fresh token, replace 123 with a card you authored:

```bash
curl -i -X PATCH 'http://localhost:8080/api/v1/cards/123' -H "Authorization: Bearer $CARD_TOKEN" -H 'Content-Type: application/json' -d '{"name":"Ice Mage","type":"MAGE"}'
curl -i -X PATCH 'http://localhost:8080/api/v1/cards/123' -H "Authorization: Bearer $CARD_TOKEN" -H 'Content-Type: application/json' -d '{"description":null}'
# Expect 400 and no changes.
curl -i -X PATCH 'http://localhost:8080/api/v1/cards/123' -H "Authorization: Bearer $CARD_TOKEN" -H 'Content-Type: application/json' -d '{"name":"  "}'
```

Verify with `GET /api/v1/cards/123` or this read-only SQL:

```sql
SELECT id, name, imgurl, description, type, author
FROM public."Card"
WHERE id = 123;
```

## Live Railway connection

Run Railway commands from `api-backend`, where this project's Railway link is configured.
Following the private SSH setup in the root README, start the tunnel in one terminal:

```bash
cd "api-backend" # omit if already in this directory
railway connect Postgres --tunnel-only --port 42863
```

Use the localhost port from your own `SPRING_DATASOURCE_URL` if it differs from `42863`.
Keep that terminal open; a public database endpoint is not required.
Then in a separate Bash terminal:

```bash
cd "api-backend" # omit if already in this directory
set -a
source .env
set +a
bash ./gradlew railwayTest
```

Required variables: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`. This task uses JDBC directly, verifies PostgreSQL, and runs
only `SELECT 1` on a read-only connection. It never runs Hibernate/schema updates.
It checks the configured endpoint: ensure it is your Railway development database/tunnel.
A connection-refused error usually means the tunnel stopped or the local port changed.

## Live Google sign-in and API authentication

Start the backend with its normal Google issuer and Web client ID configuration and a working
database connection. Obtain a fresh ID token by signing into the Android app. Use an Android
Studio debugger breakpoint on `val user = authenticateWithBackend(idToken)` in `LoginScreen.kt` to inspect the
local `idToken` variable; do not add token logging. This step requires an actual Google account
and consent UI. Get the account's expected `sub` from the locally inspected token payload
(decoding a payload does not verify its signature) and compare it with the API's verified result.

In a separate **Bash** terminal in `api-backend`:

```bash
export CARDBATTLE_API_URL='http://localhost:8080'
read -r -s -p 'Paste fresh Google ID token: ' GOOGLE_ID_TOKEN
echo
export GOOGLE_ID_TOKEN
read -r -p 'Expected Google subject (sub): ' GOOGLE_EXPECTED_SUBJECT
export GOOGLE_EXPECTED_SUBJECT
bash ./gradlew googleSignInTest
unset GOOGLE_ID_TOKEN GOOGLE_EXPECTED_SUBJECT
```

Use HTTPS for a remote backend. The task sends missing, malformed, and real tokens to
`/api/v1/auth/me`, expects 401/401/200, and checks the returned subject. It does not print
the bearer token. A fresh token must target the same Web client ID as the running backend.
The Android app submits its token automatically to `POST /api/v1/auth/google`.
The live check above separately exercises `GET /api/v1/auth/me`.

The two live tasks are excluded from normal `test`/`build`, always rerun when requested,
and fail with an explanatory message if their required environment variables are absent.
Reports: `build/reports/tests/test/index.html`, `build/reports/tests/railwayTest/index.html`,
and `build/reports/tests/googleSignInTest/index.html`.
