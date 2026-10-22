# Backend tests

From `api-backend`, run `bash ./gradlew test` (or `bash ./gradlew check` for coverage checks).
No Railway credentials or Google account are needed. Tests use H2 and an ephemeral local
HTTP key server. `AuthSecurityTests` sends real RSA-signed test JWTs through the application's
security filter and Boot-configured decoder. It checks successful claims, missing/malformed
tokens, expiration, wrong audience, wrong issuer, wrong signature, and public health access.
These prove our validation behavior; they do not prove Google Console setup or Railway availability.

## Inventory routes

Run `bash ./gradlew test --tests '*InventoryRoutesTests'` for these routes or
`bash ./gradlew verify` for all backend tests and service coverage checks.
Tests use real controllers, services, SQL, and transactions with local H2 and mocked
Google verification. They do not connect to Railway or Render.

- `POST /api/v1/inventory` accepts `{"userId":1,"cardId":7,"value":0}` and
  returns 201 with a generated inventory `id` and embedded card details. The user
  must already be registered through Google sign-in. `userId` must match the token's
  user, or the response is 400. A missing registered user/card returns 404.
- Here `cardId` is **Card.id**. The response's `id` is **User_owns_card.id**;
  use that returned inventory ID as `cardId` when adding a deck membership.
- The card may have another author. Repeated POSTs create separate inventory copies.
  Only inventory is created: card authorship, decks, memberships, art, and history
  are not changed. This basic self-acquisition route has no purchase/reward/scarcity
  checks; those would be separate server-side game rules if required later.
- `value` is required and must be an integer from 0 through 2147483647. Missing/null
  is rejected explicitly rather than accidentally becoming zero. The response value
  is nullable because older database rows can legitimately contain null; listing
  preserves those values instead of silently inventing zero.
- `GET /api/v1/inventory` returns `content` plus `page` metadata, with zero-based
  `page` (default 0), `size` (default 20, range 1–100), optional uppercase `type`,
  and optional positive integer `userId`. Ordering is always inventory ID ascending;
  no sort parameter is introduced. Type filtering/counting happen before pagination.
- Only the authenticated user's copies are visible. `userId` can narrow their own
  inventory but cannot expose another account: another user's or an unknown positive
  ID returns an empty page. Unregistered callers and callers with no matches also
  get empty pages. Pages beyond the end retain the matching total with empty content.
- Both routes require valid tokens (401 otherwise). Invalid input/parameters return
  400. Constraint conflicts and broken legacy card/author references return 409;
  writes roll back. Inventory PATCH/DELETE are described below; every InventoryController
  route is now implemented.

Extra complexity: a small JDBC repository uses a joined card query for display,
a matching count query for pagination, and generated-key retrieval for INSERT.
It follows the deck SQL approach without new JPA entities or dependencies. Optional
type filters use fixed SQL fragments and bound values, not interpolated user text.
No schema migration is needed if `User_owns_card.id` already auto-generates, as it
does in the checked-in local schema. The test fixture now models that identity too.

### Manual terminal checks

If using Docker, rebuild from the repository root with
`docker compose up --build -d api`. Use a fresh Google ID token and an existing
card ID. These commands write to whichever database that API connects to.

```bash
export BASE_URL='http://localhost:8080'
export CARD_TOKEN='PASTE_FRESH_GOOGLE_ID_TOKEN'

# Register/refresh your backend user first. Copy its numeric id into USER_ID.
curl -i -X POST "$BASE_URL/api/v1/auth/google" \
  -H "Authorization: Bearer $CARD_TOKEN"

export USER_ID=1
export CARD_ID=7

# Expect 201. This card ID comes from Card, not User_owns_card.
curl -i -X POST "$BASE_URL/api/v1/inventory" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"userId\":$USER_ID,\"cardId\":$CARD_ID,\"value\":0}"

# Expect 200 with owned copies and embedded card details.
curl -i "$BASE_URL/api/v1/inventory?page=0&size=2" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Filter by underlying card type. May return an empty page.
curl -i "$BASE_URL/api/v1/inventory?page=0&size=2&type=MAGE" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 400, then 401.
curl -i "$BASE_URL/api/v1/inventory?page=-1" \
  -H "Authorization: Bearer $CARD_TOKEN"
curl -i "$BASE_URL/api/v1/inventory"
```

For an ownership check, try a second user's `userId` with your token: POST should
return 400; GET with that filter should return an empty page, not their inventory.

### Manual SQL checks

Use the same database and replace user ID `1` with your ID. Verify automatic ID
generation first: `is_identity` should be YES or column_default should contain an
appropriate sequence default. If both are absent, apply the ID-generation correction
before POSTing; do not recreate or clear your table.

```sql
SELECT column_name, is_identity, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'User_owns_card' AND column_name = 'id';

SELECT owned.id AS inventory_id, owned.userid, owned.cardid, owned.value,
       card.name, card.type, card.author
FROM public."User_owns_card" owned
LEFT JOIN public."Card" card ON card.id = owned.cardid
WHERE owned.userid = 1
ORDER BY owned.id;

SELECT COUNT(*) AS total_inventory
FROM public."User_owns_card" WHERE userid = 1;

SELECT COUNT(*) AS matching_mages
FROM public."User_owns_card" owned
JOIN public."Card" card ON card.id = owned.cardid
WHERE owned.userid = 1 AND card.type = 'MAGE';
```

### Updating and deleting inventory copies

Both routes use `User_owns_card.id` as `ownershipId`, not the underlying Card ID.
They require a valid token and ownership of the copy. Missing copies, other users'
copies, and unregistered callers return 404; missing/invalid tokens return 401.
Malformed or out-of-range path IDs return 400.

- `PATCH /api/v1/inventory/{ownershipId}` accepts `{"value":12}`. Only value is
  editable. It must be an integer from 0 through 2147483647. Explicit null, negative,
  malformed, or oversized values return 400 without changing the row. `{}` or an
  unknown-only object is a no-op; existing null values remain null until replaced
  with a valid value. Ownership, card reference, and shared card details are immutable.
  Successful PATCH returns 200 with the same embedded-card format as create/list.
- `DELETE /api/v1/inventory/{ownershipId}` deletes only that copy and returns an
  empty 204. Its Card_in_deck memberships cascade away; the Deck rows remain. Wins
  rows remain, but winningcard/losingcard references to the removed copy become null.
  Other copies, the shared Card (and its drawing), and users remain untouched.
  Repeating DELETE returns 404. Broken legacy copies can still be deleted.
- A blocking database constraint returns 409. The service transaction rolls back
  changes, including cascaded deck-entry deletions and battle-reference updates.
  A PATCH unable to resolve its legacy card reference also returns 409 and rolls back.

Extra complexity stays small: PATCH tracks omitted vs null value, following the
existing PATCH pattern and sharing create's nonnegative validation. SQL UPDATE/DELETE
include both inventory ID and authenticated owner ID. Deletion relies on the existing
CASCADE/SET NULL foreign keys; no new cleanup algorithm or schema migration was added.
Tests cover multiple decks, multiple copies, both battle-card columns, and rollback
with a restrictive test-only foreign key. No frontend deletion behavior was changed.

```bash
# Use your actual inventory-copy ID from createOwnership/listInventory.
export OWNERSHIP_ID=101

# Expect 200 with value 12 and embedded card details.
curl -i -X PATCH "$BASE_URL/api/v1/inventory/$OWNERSHIP_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' -d '{"value":12}'

# Expect 200 with no changes.
curl -i -X PATCH "$BASE_URL/api/v1/inventory/$OWNERSHIP_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' -d '{}'

# Expect 400 without changes.
curl -i -X PATCH "$BASE_URL/api/v1/inventory/$OWNERSHIP_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' -d '{"value":null}'

# Expect 204. This deletes real inventory and its memberships in the configured DB.
curl -i -X DELETE "$BASE_URL/api/v1/inventory/$OWNERSHIP_ID" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 404 when repeated.
curl -i -X DELETE "$BASE_URL/api/v1/inventory/$OWNERSHIP_ID" \
  -H "Authorization: Bearer $CARD_TOKEN"
```

Check before/after using your real IDs. After PATCH, only value changes. After DELETE,
the copy and its deck entries disappear, but decks, the shared card, and battle rows
remain. Record relevant battle IDs and their references before deleting, then query
those same IDs afterward to confirm only the selected references became null.

```sql
-- Replace inventory ID 101, underlying Card ID 7, and affected Deck ID 123.
SELECT id, userid, cardid, value FROM public."User_owns_card" WHERE id = 101;
SELECT id, deckid, cardid FROM public."Card_in_deck" WHERE cardid = 101;
SELECT id, winningcard, losingcard FROM public."Wins"
WHERE winningcard = 101 OR losingcard = 101;

-- These rows should still exist after deletion.
SELECT id, name, author FROM public."Card" WHERE id = 7;
SELECT id, userid, name FROM public."Deck" WHERE id = 123;

-- Replace 301 with a battle ID recorded above; the row should remain after deletion.
SELECT id, winningcard, losingcard FROM public."Wins" WHERE id = 301;
```

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
- The deck starts empty. Listing decks, listing entries, and renaming decks are
  implemented below; battle-readiness rules are outside these routes. No schema migration is needed
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
- The response contains membership IDs and metadata plus an embedded `card` with
  its underlying ID, name, image URL, description, type, and author ID. The outer
  `cardId` is still the inventory ID; `card.id` is the actual Card ID. The outer
  `type` response is nullable because older database rows may
  already have null metadata. New entries must supply a non-null enum.
- This batch does not impose a three-card maximum, unique type slots, or duplicate
  inventory-copy restrictions. Repeated POSTs create separate membership rows unless
  your database has additional constraints. These are editing routes, not battle-readiness checks.

Extra complexity is limited to one small JDBC repository for existing link tables
and a join to Card; the deck operations need no full inventory entity.
Spring's service transaction covers both the existing JPA deck lookup and these SQL
operations. PATCH tracks whether `type` was supplied, following the card PATCH pattern.
Legacy entries without a usable inventory/card/author reference can still be removed;
listing/add/PATCH returns 409 and rolls back any write rather than returning fake details.

### Embedded card details

GET-membership-list, POST-membership, and PATCH-membership all populate the same
existing `card` response field. The repository reads `Card_in_deck` -> `User_owns_card`
-> `Card` in one joined SELECT for the list (not one extra query per entry). POST
reads its inserted entry through that same query; PATCH reuses the joined lookup.
LEFT JOIN retains broken legacy entries so they can produce 409 rather than silently
disappearing. Existing ownership checks, membership ordering, and all ID meanings remain.

The nested `card.type` and outer `type` are different fields: changing membership
metadata does not edit the shared card. The nested author ID may differ from the
deck owner. Tests cover multiple distinct cards, current names/image URLs/descriptions,
another author's card in the caller's inventory, null optional fields, and rollback
on broken card links. No schema change or drawing-byte query was added; `imgURL`
is the existing URL, not the PNG stored in `card_art`.

Example response (IDs are illustrative):

```json
{
  "id": 41,
  "deckId": 123,
  "cardId": 11,
  "userId": 1,
  "type": "WARRIOR",
  "card": {
    "id": 7,
    "name": "My Mage",
    "imgURL": null,
    "description": "A shared card definition",
    "type": "MAGE",
    "authorId": 2
  }
}
```

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
returns nothing, create a copy through the inventory API first (replace user/card
IDs with your actual values). This writes a real inventory row:

```bash
curl -i -X POST "$BASE_URL/api/v1/inventory" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"cardId":7,"value":0}'
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

To verify the nested card response directly, compare this SELECT with
`GET /api/v1/decks/123/cards` (replace the deck ID):

```sql
SELECT entry.id AS membership_id, entry.cardid AS inventory_id,
       entry.type AS membership_type, card.id AS underlying_card_id,
       card.name, card.imgurl, card.description, card.type AS card_type, card.author
FROM public."Card_in_deck" entry
LEFT JOIN public."User_owns_card" owned ON owned.id = entry.cardid
LEFT JOIN public."Card" card ON card.id = owned.cardid
WHERE entry.deckid = 123
ORDER BY entry.id;
```

## Paginated deck lists, membership lists, and renaming

Run `bash ./gradlew test --tests '*DeckListAndPatchTests'` for this batch or
`bash ./gradlew verify` for the full backend suite. These tests use H2 and mocked
Google verification, with real controllers, services, repositories, and SQL.
No Railway/Render connection or real Google account is needed for automated tests.

- `GET /api/v1/decks` returns only the authenticated user's decks, wrapped in
  `content` and `page` just like the card-list routes. Page numbers start at zero;
  defaults are `page=0`, `size=20`, and `sort=name,asc`. Size must be 1–100.
- Supported sorts are `name,asc`, `name,desc`, `id,asc`, and `id,desc`. Equal names
  use ascending ID as a tie-breaker. Filtering/counting happen before pagination.
- The existing optional `userId` parameter only narrows your own decks. Your own
  ID returns matches; another user's ID or an unknown positive ID returns an empty
  page, never another user's decks. Nonpositive/malformed/out-of-range IDs return 400.
- Empty matches, an unregistered caller, and pages beyond the end return 200 with
  empty content. Pages beyond the end still report the matching total.
- `GET /api/v1/decks/{deckId}/cards` returns a plain array (not a page), ordered
  by membership ID ascending. Empty owned decks return `[]`. Fields match the add/
  PATCH membership response: `id` is the membership ID, `cardId` is the inventory
  ID, `userId` is the deck owner, and `card` embeds the underlying CardResponse.
  Legacy null membership `type` values remain null. Broken inventory/card/author
  references return 409 without modifying anything,
  consistent with membership PATCH; the existing remove route can remove that row.
- `PATCH /api/v1/decks/{deckId}` accepts `{"name":"New name"}`. Name is trimmed
  and must contain 1–150 characters. `{}` is a no-op; explicit null, blank, or an
  overlong name returns 400. Unknown fields are ignored. Ownership, IDs, membership,
  and the existing null `createdAt` are not editable.
- All three routes require a valid token (401 otherwise). Missing/other users'
  decks return 404 for membership listing and renaming. Malformed or out-of-range
  path IDs return 400. Every DecksController route is now implemented.

Extra complexity: pagination uses the same Spring Data PageRequest and stable-sort
approach as cards. Rename tracks whether name was supplied, following the existing
PATCH pattern, and shares createDeck's name validation. Entry listing reuses the
existing joined SQL row mapper and owner checks; no extra entities, timestamp
columns, schema migrations, or inventory grants were added.

### Manual terminal checks

Rebuild your local API if using Docker (`docker compose up --build -d api` from
the repository root). Use a fresh Google ID token belonging to an already registered
user. Requests use whichever database that API is configured to connect to.

```bash
export BASE_URL='http://localhost:8080'
export CARD_TOKEN='PASTE_FRESH_GOOGLE_ID_TOKEN'

# Expect 200 with content and page metadata.
curl -i "$BASE_URL/api/v1/decks?page=0&size=2&sort=name,asc" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Page 1 skips the first two matches; it may be empty.
curl -i "$BASE_URL/api/v1/decks?page=1&size=2&sort=name,asc" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Copy an owned deck ID from the list above.
export DECK_ID=123

# Expect 200 with membership metadata and nested card details, or [] for an empty deck.
curl -i "$BASE_URL/api/v1/decks/$DECK_ID/cards" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 200 with the trimmed new name.
curl -i -X PATCH "$BASE_URL/api/v1/decks/$DECK_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"  Renamed Deck  "}'

# Expect 200 with the same name (no-op).
curl -i -X PATCH "$BASE_URL/api/v1/decks/$DECK_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' -d '{}'

# Expect 400; the name stays unchanged.
curl -i -X PATCH "$BASE_URL/api/v1/decks/$DECK_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":null}'

# Expect 400 for invalid pagination and 401 without a token.
curl -i "$BASE_URL/api/v1/decks?page=-1" -H "Authorization: Bearer $CARD_TOKEN"
curl -i "$BASE_URL/api/v1/decks"
```

For ownership checks, use a second account's token: it should not see your deck in
its list, and its GET-membership/PATCH-name requests for your deck should return 404.

### Manual SQL checks

Replace user ID `1` and deck ID `123` with actual IDs. The count should match
`page.totalElements`. After renaming, only the deck name should have changed;
membership rows remain unchanged. List responses use inventory IDs, not Card IDs.

```sql
SELECT id, userid, name FROM public."Deck" WHERE userid = 1 ORDER BY name, id;
SELECT COUNT(*) AS matching_decks FROM public."Deck" WHERE userid = 1;
SELECT id, userid, name FROM public."Deck" WHERE id = 123;
SELECT id, deckid, cardid AS inventory_id, userid, type
FROM public."Card_in_deck" WHERE deckid = 123 ORDER BY id;
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

## Battle routes: record results, list history, and get one result

Implemented all three routes with the same controller → service → repository structure:

| Route | Expected success |
| --- | --- |
| `POST /api/v1/battles` | 201 with the generated result ID. |
| `GET /api/v1/battles?page=0&size=20&userId=1` | 200 with filtered content and page totals. |
| `GET /api/v1/battles/{battleId}` | 200 with one result. |

All three require a valid Google ID token. History is readable by any authenticated caller,
consistent with the existing shared game-statistics routes; it does not expose email addresses.
Creation additionally requires a registered caller who is either the winning or losing user.
Other callers receive 403, including unrelated admins. Unregistered creators receive 404.

Extra complexity and intentional limits:

- Both users must exist and be different. Each inventory copy must belong to its stated
  player and reference an existing card. Invalid IDs/self-matches return 400; nonexistent
  users or missing/mismatched inventory copies return 404. No deck is required.
- `winningCard` and `losingCard` are **User_owns_card ownership IDs**, not shared Card IDs.
  Both players can use separate copies of the same shared card.
- This is **participant-reported result storage**, not a battle engine. The server cannot
  prove the submitted winner actually won. Do not treat it as anti-cheat-protected ranking
  data. Server-run combat or independently verified match results would be a separate feature.
- Repeated POSTs create separate results, including repeated statistics. There is no match ID
  or duplicate-submission protection in the existing schema. Avoid retrying successful POSTs.
- Listing defaults to page 0, size 20 (maximum 100), ordered by result ID ascending.
  `userId` matches either player before counting/pagination. Missing filter users and pages
  beyond the end return empty content. Count plus page queries use bound SQL parameters.
- No timestamp column is added; `occurredAt` remains null. Existing inventory/card deletion
  rules preserve the battle with null card references. User stats still count its players.
  A legacy result missing either user returns 409 if fetched or included in the requested page,
  rather than silently reporting that missing user as ID 0. No legacy data is changed.
- Inserts are transactional; a database integrity constraint returns 409 with no partial result.
  New code uses existing JDBC/JPA support; no new dependency or OAuth configuration is needed.

### Check ID generation first

The new-empty-local schema already makes `Wins.id` an identity column. Existing Railway or
older local tables may still need the same fix used for cards and inventory. Inspect first:

```sql
SELECT column_name, data_type, is_identity, identity_generation, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'Wins' AND column_name = 'id';
```

If `is_identity` is `NO` **and** `column_default` is null, manually run
`api-backend/db/migrations/20261022_wins_identity.sql` in your database SQL client.
The complete script can be pasted into the query console; it preserves existing rows/IDs,
starts the new sequence after existing results, and skips columns already using identity or
a sequence default. This script is **not automatically applied** by the backend and was not
run against Railway during implementation. It briefly locks Wins while updating its schema.
If identity is already `YES`, or an existing sequence default is present, no change is needed
for normal generated IDs. Re-run the inspection afterward to confirm.

### Offline automated checks

From the repository root:

```bash
cd api-backend
bash ./gradlew test --tests '*BattleRoutesTests' --tests '*OpenApiContractTests'
bash ./gradlew verify --console=plain
```

Tests run real HTTP/service/repository/transaction behavior against H2 with mocked token
decoding. Existing security tests still cover real JWT validation. Battle tests cover both
participants posting, third-party rejection, user/card ownership, missing/null/invalid IDs,
pagination/filtering, generated result IDs, constraint rollback, existing card/user stats,
and history with deleted inventory/card references. The shared test fixture now gives Wins
an identity column, matching the existing new-local PostgreSQL schema.

### Manual curl checks

Rebuild/start the backend with your usual Docker configuration. Get a fresh Google ID token
in `CARD_TOKEN`. Use **two existing registered users** and an inventory entry owned by each.
The token must belong to one of those users; a single user cannot battle themselves.
Your coworker can create their inventory entry using their token and share its ownership ID.
No need to create additional cards if those entries already exist.

Find the database IDs using read-only SQL:

```sql
SELECT id, displayname FROM public."User" ORDER BY id;
SELECT owned.id AS ownership_id, owned.userid, owned.cardid AS shared_card_id, card.name
FROM public."User_owns_card" owned
JOIN public."Card" card ON card.id = owned.cardid
ORDER BY owned.userid, owned.id;
```

Replace every example ID below with actual IDs from your database. Do not confuse ownership
IDs with shared card IDs or Google subject strings. Use HTTPS if testing Render.

```bash
export API_URL='http://localhost:8080'
export WINNER_USER_ID=1
export LOSER_USER_ID=2
export WINNER_OWNERSHIP_ID=101
export LOSER_OWNERSHIP_ID=102

# MUTATES STATS: expect 201. Run once, then copy the returned id into BATTLE_ID.
# Your token may belong to either stated player; do not invent a real competitive result.
curl -i -w '\n\n' -X POST "$API_URL/api/v1/battles" \
  -H "Authorization: Bearer $CARD_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"winningUser\":$WINNER_USER_ID,\"losingUser\":$LOSER_USER_ID,\"winningCard\":$WINNER_OWNERSHIP_ID,\"losingCard\":$LOSER_OWNERSHIP_ID}"

export BATTLE_ID=1 # replace with the result id returned above

# Expect 200 with the saved result. occurredAt is null.
curl -i -w '\n\n' "$API_URL/api/v1/battles/$BATTLE_ID" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 200 with the first page. Page 1 is the SECOND page.
curl -i -w '\n\n' "$API_URL/api/v1/battles?page=0&size=2" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 200, filtered to results where this user won OR lost.
curl -i -w '\n\n' "$API_URL/api/v1/battles?userId=$WINNER_USER_ID&page=0&size=2" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 200 with empty content if this page is past the end.
curl -i -w '\n\n' "$API_URL/api/v1/battles?page=100000&size=20" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 400 for invalid pagination.
curl -i -w '\n\n' "$API_URL/api/v1/battles?page=-1" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 401 without a token.
curl -i -w '\n\n' "$API_URL/api/v1/battles"

# Expect 200; the player's stats include the newly recorded result.
curl -i -w '\n\n' "$API_URL/api/v1/users/$WINNER_USER_ID/stats" \
  -H "Authorization: Bearer $CARD_TOKEN"

# Expect 400: a user cannot be both players. No result should be inserted.
curl -i -w '\n\n' -X POST "$API_URL/api/v1/battles" \
  -H "Authorization: Bearer $CARD_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"winningUser\":$WINNER_USER_ID,\"losingUser\":$WINNER_USER_ID,\"winningCard\":$WINNER_OWNERSHIP_ID,\"losingCard\":$WINNER_OWNERSHIP_ID}"
```

To check a 403, submit the same valid result body using a **third user's** token; there must
be no inserted result. To check a 404, use a participant's token but use a nonexistent
ownership ID or one belonging to the wrong player. A missing positive battle ID returns 404;
negative, nonnumeric, or IDs above 2147483647 return 400.

### Read-only database checks

```sql
-- Replace 1 with the returned battle ID.
SELECT id, winninguser, losinguser, winningcard, losingcard FROM public."Wins" WHERE id = 1;

-- Confirm each still-existing inventory copy belongs to the stated player.
-- LEFT JOIN preserves history even after inventory deletion.
SELECT w.id, w.winninguser, winner.userid AS winning_copy_owner,
       w.losinguser, loser.userid AS losing_copy_owner,
       w.winningcard, w.losingcard
FROM public."Wins" w
LEFT JOIN public."User_owns_card" winner ON winner.id = w.winningcard
LEFT JOIN public."User_owns_card" loser ON loser.id = w.losingcard
ORDER BY w.id;

-- Compare with the userId-filtered API total, replacing 1 with the player ID.
SELECT COUNT(*) FROM public."Wins" WHERE winninguser = 1 OR losinguser = 1;
SELECT COUNT(*) AS wins FROM public."Wins" WHERE winninguser = 1;
SELECT COUNT(*) AS losses FROM public."Wins" WHERE losinguser = 1;
```

Creating a result will make that user's profile deletion return 409 while the result still
references them. There is intentionally no new battle delete route. Do manual result creation
against development data; automated tests do not connect to Railway or modify real results.

## User routes: profiles, administration, stats, and safe deletion

All six former `UsersController` TODOs are implemented. The existing Google sign-in and
display-name endpoints remain unchanged. No new dependency, database migration, Google
configuration, or frontend change is needed.

Permissions (a valid Google ID token is required for every route):

| Route | Who can use it? |
| --- | --- |
| `GET /api/v1/users` | Registered database admins only; includes email and admin status. |
| `GET /api/v1/users/{userId}` | That user or a registered database admin. |
| `PATCH /api/v1/users/{userId}` | Registered database admins only; edits only `isAdmin`. |
| `DELETE /api/v1/users/{userId}` | Registered database admins only. |
| `DELETE /api/v1/users/me` | The registered caller; identity comes from the verified token's subject. |
| `GET /api/v1/users/{userId}/stats` | Any valid token; only game stats are returned. |

Missing/invalid tokens return 401. Registered callers without permission get 403;
unregistered callers get 404 for operations requiring a stored account. Missing target users
return 404 after authorization. Malformed/overflowing IDs return 400; authorized requests
with nonpositive IDs also return 400.

Extra complexity is limited to database-backed permission checks, paginated filtering,
PATCH field-presence tracking, and safe transaction handling. User deletion deliberately
does **not** introduce a new cascade policy: existing cards, inventory, decks, and battle
history may block it with 409, leaving everything intact. No schema changes are needed.
It does not delete a Google account, revoke tokens, or prevent signing in again: the normal
Google sign-in endpoint can recreate a deleted user with `isAdmin=false`.

Admin status is read from the database for each operation, never accepted from token
claims or sign-in request data. Google sign-in preserves the stored flag. An admin can
demote themselves, including the last admin; restoring access then requires a trusted
database operator. There is intentionally no public "make me admin" endpoint.

The first admin must be set by a trusted database operator. Inspect accounts first:

```sql
SELECT id, email, displayname, isadmin FROM public."User" ORDER BY id;
-- Optional bootstrap: replace 123 with the inspected account you intend to make admin.
UPDATE public."User" SET isadmin = TRUE WHERE id = 123;
```

Run offline tests from the repository root:

```bash
cd api-backend
bash ./gradlew test --tests '*UserRoutesTests' --tests '*OpenApiContractTests'
bash ./gradlew verify --console=plain
```

`UserRoutesTests` uses H2 with the real HTTP/service/repository layers. Only token decoding
is mocked; existing `AuthSecurityTests` separately exercise real JWT verification. Tests cover
all six routes, self/admin authorization, promotion attempts, immediate demotion, Google
sign-in compatibility, pagination/filtering/sort, PATCH omission/null, stats, and deletion
blocked independently by cards, inventory, decks, and either side of battle history.

Manual checks: start/rebuild the backend with your usual Docker configuration. Use a fresh
Google ID token in `CARD_TOKEN` and the database user ID returned by `POST auth/google`.
Replace the example IDs; do not use `/auth/me`'s Google subject as a database user ID.
Use HTTPS when pointing these commands at Render.

```bash
export API_URL='http://localhost:8080'
export USER_ID=123 # replace with your actual database user ID

# Register/refresh your backend account first. Expect 200 or 201.
curl -i -X POST "$API_URL/api/v1/auth/google" -H "Authorization: Bearer $CARD_TOKEN"

# Your own profile: expect 200. Does not expose google_subject.
curl -i "$API_URL/api/v1/users/$USER_ID" -H "Authorization: Bearer $CARD_TOKEN"

# Your stats: expect 200. With no battle results, all fields are zero.
curl -i "$API_URL/api/v1/users/$USER_ID/stats" -H "Authorization: Bearer $CARD_TOKEN"

# Admin-only: expect 403 as a normal registered user, 200 after trusted admin bootstrap.
curl -i "$API_URL/api/v1/users?page=0&size=2&sort=displayName,asc" \
  -H "Authorization: Bearer $CARD_TOKEN"

# As an admin, filter the matching total before pagination. May return an empty page.
curl -i --get "$API_URL/api/v1/users" --data-urlencode 'displayName=alice' \
  --data-urlencode 'size=2' --data-urlencode 'sort=id,desc' \
  -H "Authorization: Bearer $CARD_TOKEN"

# As an admin: invalid pagination returns 400.
curl -i "$API_URL/api/v1/users?page=-1" -H "Authorization: Bearer $CARD_TOKEN"

# No token: expect 401.
curl -i "$API_URL/api/v1/users/$USER_ID/stats"
```

To test admin PATCH, use a separate, consenting test account as the target. Do not
demote your only admin or delete an account you need. Supply the administrator's token
as `CARD_TOKEN`, and set `TEST_USER_ID` to the other account's database ID.

```bash
export TEST_USER_ID=456 # replace with the test account's ID

# As admin: expect 200; no change.
curl -i -X PATCH "$API_URL/api/v1/users/$TEST_USER_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" -H 'Content-Type: application/json' -d '{}'

# As admin: expect 400; no change.
curl -i -X PATCH "$API_URL/api/v1/users/$TEST_USER_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" -H 'Content-Type: application/json' -d '{"isAdmin":null}'

# As admin: promote the consenting test account. Expect 200 with isAdmin true.
curl -i -X PATCH "$API_URL/api/v1/users/$TEST_USER_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" -H 'Content-Type: application/json' -d '{"isAdmin":true}'

# Restore that account to ordinary user status. Expect 200 with isAdmin false.
curl -i -X PATCH "$API_URL/api/v1/users/$TEST_USER_ID" \
  -H "Authorization: Bearer $CARD_TOKEN" -H 'Content-Type: application/json' -d '{"isAdmin":false}'

# DESTRUCTIVE: only run for a disposable test account.
# As admin: expect 204 if unlinked; 409 if linked game data blocks deletion.
# After successful deletion, repeating returns 404.
curl -i -X DELETE "$API_URL/api/v1/users/$TEST_USER_ID" -H "Authorization: Bearer $CARD_TOKEN"

# DESTRUCTIVE ALTERNATIVE: use the disposable account's own token, not your admin token.
# Expect 204 if unlinked, or 409 if linked. Do not run against your main account.
# curl -i -X DELETE "$API_URL/api/v1/users/me" -H "Authorization: Bearer $DISPOSABLE_TOKEN"
```

Verify the stored profile/admin flag and stats in Railway (replace 123 with the target ID):

```sql
SELECT id, email, displayname, isadmin FROM public."User" WHERE id = 123;

SELECT
    COUNT(*) FILTER (WHERE winninguser = 123) AS wins,
    COUNT(*) FILTER (WHERE losinguser = 123) AS losses,
    COUNT(*) AS total_battles,
    COALESCE(
        (COUNT(*) FILTER (WHERE winninguser = 123))::double precision /
        NULLIF((COUNT(*) FILTER (WHERE winninguser = 123)) +
               (COUNT(*) FILTER (WHERE losinguser = 123)), 0),
        0
    ) AS win_rate
FROM public."Wins"
WHERE winninguser = 123 OR losinguser = 123;

-- Investigate a blocked deletion without deleting related data:
SELECT 'cards' AS relation_name, COUNT(*) AS linked_rows FROM public."Card" WHERE author = 123
UNION ALL SELECT 'inventory', COUNT(*) FROM public."User_owns_card" WHERE userid = 123
UNION ALL SELECT 'decks', COUNT(*) FROM public."Deck" WHERE userid = 123
UNION ALL SELECT 'battles', COUNT(*) FROM public."Wins" WHERE winninguser = 123 OR losinguser = 123;
```

Stats use user references directly, not card/inventory joins, so null card references do
not lose user results. `winRate` is a fraction (0.5 means 50%). A self-match contributes
one win and one loss but only one `totalBattles`, matching the card-stats convention.
Profile name edits still refresh from Google's name on the next Google sign-in.

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
