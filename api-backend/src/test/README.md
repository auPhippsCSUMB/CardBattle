# Backend tests

From `api-backend`, run `bash ./gradlew test` (or `bash ./gradlew check` for coverage checks).
No Railway credentials or Google account are needed. Tests use H2 and an ephemeral local
HTTP key server. `AuthSecurityTests` sends real RSA-signed test JWTs through the application's
security filter and Boot-configured decoder. It checks successful claims, missing/malformed
tokens, expiration, wrong audience, wrong issuer, wrong signature, and public health access.
These prove our validation behavior; they do not prove Google Console setup or Railway availability.

## Deck creation, lookup, and deletion

Run `bash ./gradlew test --tests '*DeckRoutesTests'` for these three routes, or
`bash ./gradlew verify` for the full backend suite and service coverage checks.
The tests use real controllers, services, repositories, and transactions against H2;
Google token verification is mocked. They do not connect to Railway or Render.

- `POST /api/v1/decks` accepts `{"name":"My First Deck"}` and returns 201 with
  `id`, `userId`, `name`, and `createdAt: null`. Names are trimmed and must contain
  1–150 characters. The owner comes from the verified Google token, not the body.
  Sign in through `POST /api/v1/auth/google` first so that your user exists.
- `GET /api/v1/decks/{deckId}` returns only your own deck's metadata, not its cards.
- `DELETE /api/v1/decks/{deckId}` removes your deck and its `Card_in_deck` entries,
  returning 204 with no body. It does not delete inventory, card definitions, users,
  other decks, or battle history. Deleting an empty deck also works.
- All three routes require a valid token (otherwise 401). Missing decks and other
  users' decks return 404 for lookup/deletion. Invalid JSON/names and noninteger or
  out-of-range IDs return 400. Deleting the same deck twice returns 404 the second time.
- If another foreign key blocks deletion, both deletions roll back and the response
  is 409. Tests explicitly check that membership rows are restored on rollback.
- The deck starts empty; listing decks, listing deck cards, renaming decks, and checking
  battle readiness remain TODOs. No schema migration is needed
  for the checked-in schema. `Deck.id` must already have an identity or sequence default
  in Railway, just like `Card.id`; the SQL below lets you check without changing it.

Extra complexity: a single SQL DELETE removes deck membership rows before the deck
DELETE because the existing deck foreign key does not cascade. A service transaction
makes these two steps atomic, so this works without changing Railway foreign keys.
No inventory or deck-card entity is needed yet. The existing nullable `createdAt`
response field stays null; there is no timestamp column.

### Manual terminal checks

Start your backend locally using your usual environment settings, then use a fresh
Google **ID token** in `CARD_TOKEN`. These requests create and delete real data in
whichever database that backend is configured to use.

```bash
export BASE_URL='http://localhost:8080'
export CARD_TOKEN='PASTE_FRESH_GOOGLE_ID_TOKEN_HERE'

# Register/refresh your backend user first. Expect 200 or 201.
curl -i -X POST "$BASE_URL/api/v1/auth/google" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Create an empty deck. Expect 201. Copy the returned id into DECK_ID below.
curl -i -X POST "$BASE_URL/api/v1/decks" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"My First Deck"}'

export DECK_ID=123

# Expect 200 with your deck metadata.
curl -i "$BASE_URL/api/v1/decks/$DECK_ID" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 400; no deck should be created.
curl -i -X POST "$BASE_URL/api/v1/decks" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"   "}'

# Expect 401 without a token.
curl -i "$BASE_URL/api/v1/decks/$DECK_ID"

# Expect 204 with an empty body.
curl -i -X DELETE "$BASE_URL/api/v1/decks/$DECK_ID" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 404 after deletion.
curl -i "$BASE_URL/api/v1/decks/$DECK_ID" \
  -H "Authorization: Bearer $CARD_TOKEN"
```

For an ownership check, use a second account's token to GET/DELETE the first account's
deck **before** deleting it; both requests should return 404 and leave it unchanged.
The automated suite also tests deleting populated decks and preserving unrelated data.

### Manual SQL checks

Run these against the same database your backend uses. Replace `123` with the actual
deck ID. Check before deletion, then repeat after deletion; the deck row and its
membership rows should be gone. Other decks, inventory, cards, and history should remain.
Compare the table counts before/after when no other clients are writing to the database.

```sql
-- Confirm the expected names and automatic deck ID generation.
SELECT column_name, data_type, is_identity, identity_generation, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'Deck'
ORDER BY ordinal_position;

SELECT id, userid, name FROM public."Deck" WHERE id = 123;
SELECT COUNT(*) AS deck_entries FROM public."Card_in_deck" WHERE deckid = 123;

SELECT COUNT(*) AS decks FROM public."Deck";
SELECT COUNT(*) AS cards FROM public."Card";
SELECT COUNT(*) AS inventory FROM public."User_owns_card";
SELECT COUNT(*) AS battle_results FROM public."Wins";
```

## Adding, updating, and removing deck entries

These routes are also covered by `DeckRoutesTests` and the full `verify` task.
Tests check inventory ownership, deck ownership, entry-to-deck matching, generated
IDs, type-only PATCH, null/invalid input, tokens, deletion preservation, and rollback
when a database constraint blocks a write.

- `POST /api/v1/decks/{deckId}/cards` accepts `cardId`, `userId`, and `type`;
  returns 201. **cardId means `User_owns_card.id`, not `Card.id`.** The inventory
  copy must belong to the signed-in user, who must also own the deck. `userId` is
  still required for compatibility with the existing DTO, but must match that user
  or the request returns 400. Missing/other users' inventory returns 404.
- `PATCH /api/v1/decks/{deckId}/cards/{cardInDeckId}` changes only membership
  `type`. It does not change the underlying card type. `{}` is a no-op; explicit
  null/invalid enum values return 400. Other fields are ignored, like card PATCH.
- `DELETE /api/v1/decks/{deckId}/cards/{cardInDeckId}` deletes only the membership
  and returns 204 with no body. Inventory, cards, decks, other entries, and history remain.
- `cardInDeckId` means `Card_in_deck.id`, which is returned by the POST. A missing
  entry or an entry from a different deck returns 404. All routes require valid
  tokens and deck ownership. Constraint conflicts return 409 and roll back.
- The response contains membership IDs and metadata; `card` stays null (no extra
  card fetch). The `type` response is now nullable because older database rows may
  already have null metadata. New entries must supply a non-null enum.
- This batch does not impose a three-card maximum, unique type slots, or duplicate
  inventory-copy restrictions. Repeated POSTs create separate membership rows unless
  your database has additional constraints. These are editing routes, not battle-readiness checks.

Extra complexity is limited to one small JDBC repository for the two existing link
tables; no full inventory entity or inventory API implementation is introduced.
Spring's service transaction covers both the existing JPA deck lookup and these SQL
operations. PATCH tracks whether `type` was supplied, following the card PATCH pattern.
Legacy entries without an inventory reference can still be removed; PATCH returns 409
and rolls back any update rather than returning a fake inventory ID.

### Check automatic membership IDs first

Run this against the database your backend uses:

```sql
SELECT column_name, is_identity, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'Card_in_deck' AND column_name = 'id';
```

If `is_identity` is `NO` and `column_default` is null, manually run the saved script
`db/migrations/20261020_card_in_deck_identity.sql`. It adds identity generation and
starts above existing IDs. It preserves rows and leaves existing identity/sequence
defaults untouched. It is **not run automatically**. Fresh local databases already
have identity generation from `db/init/001_schema.sql`.

### Manual terminal checks

Use the same `BASE_URL`, fresh `CARD_TOKEN`, and an existing `DECK_ID` you own.
Find your numeric user ID in the Google sign-in response and an inventory ID with SQL:

```sql
-- Replace 1 with your user ID. This SELECT does not create inventory.
SELECT owned.id AS inventory_id, owned.userid, owned.cardid AS underlying_card_id,
       card.name, card.type
FROM public."User_owns_card" owned
JOIN public."Card" card ON card.id = owned.cardid
WHERE owned.userid = 1;
```

Creating a card does not automatically give you an inventory copy. If this SELECT
returns nothing, add a deliberate test inventory row first (replace user/card IDs
with existing ones you own/intend to use). This writes real test data; the routes
do not grant inventory automatically:

```sql
-- Check User_owns_card.id has identity/sequence generation before using this INSERT.
INSERT INTO public."User_owns_card" (userid, cardid, value)
VALUES (1, 7, 0)
RETURNING id, userid, cardid;
```

```bash
# Replace all three example IDs with your actual values.
export USER_ID=1
export DECK_ID=123
export INVENTORY_ID=11

# Expect 201. Copy its id (the membership ID) into ENTRY_ID below.
curl -i -X POST "$BASE_URL/api/v1/decks/$DECK_ID/cards" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"cardId\":$INVENTORY_ID,\"userId\":$USER_ID,\"type\":\"WARRIOR\"}"

export ENTRY_ID=41

# Expect 200 with type MAGE; underlying Card.type remains unchanged.
curl -i -X PATCH "$BASE_URL/api/v1/decks/$DECK_ID/cards/$ENTRY_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' -d '{"type":"MAGE"}'

# Expect 200 with unchanged metadata.
curl -i -X PATCH "$BASE_URL/api/v1/decks/$DECK_ID/cards/$ENTRY_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' -d '{}'

# Expect 400; no change.
curl -i -X PATCH "$BASE_URL/api/v1/decks/$DECK_ID/cards/$ENTRY_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' -d '{"type":null}'

# Expect 401 without a token.
curl -i -X DELETE "$BASE_URL/api/v1/decks/$DECK_ID/cards/$ENTRY_ID"

# Expect 204. Repeating this request should return 404.
curl -i -X DELETE "$BASE_URL/api/v1/decks/$DECK_ID/cards/$ENTRY_ID" \
  -H "Authorization: Bearer $CARD_TOKEN"
```

### Manual SQL checks

Replace IDs with your actual values. After add, the membership row should exist.
After PATCH, only its type changes. After DELETE, that row is gone while its
inventory copy, underlying card, and deck remain:

```sql
SELECT entry.id, entry.deckid, entry.cardid AS inventory_id, entry.userid,
       entry.type AS membership_type, card.id AS underlying_card_id, card.type AS card_type
FROM public."Card_in_deck" entry
JOIN public."User_owns_card" owned ON owned.id = entry.cardid
JOIN public."Card" card ON card.id = owned.cardid
WHERE entry.id = 41;

SELECT * FROM public."User_owns_card" WHERE id = 11;
SELECT id, userid, name FROM public."Deck" WHERE id = 123;
SELECT COUNT(*) AS battle_results FROM public."Wins";
```

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
