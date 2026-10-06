# Backend tests

From `API (backend)`, run `bash ./gradlew test` (or `bash ./gradlew check` for coverage checks).
No Railway credentials or Google account are needed. Tests use H2 and an ephemeral local
HTTP key server. `AuthSecurityTests` sends real RSA-signed test JWTs through the application's
security filter and Boot-configured decoder. It checks successful claims, missing/malformed
tokens, expiration, wrong audience, wrong issuer, wrong signature, and public health access.
These prove our validation behavior; they do not prove Google Console setup or Railway availability.

## Live Railway connection

Run Railway commands from `API (backend)`, where this project's Railway link is configured.
Following the private SSH setup in the root README, start the tunnel in one terminal:

```bash
cd "API (backend)" # omit if already in this directory
railway connect Postgres --tunnel-only --port 42863
```

Use the localhost port from your own `SPRING_DATASOURCE_URL` if it differs from `42863`.
Keep that terminal open; a public database endpoint is not required.
Then in a separate Bash terminal:

```bash
cd "API (backend)" # omit if already in this directory
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
Studio debugger breakpoint on `onTokenReceived(idToken)` in `LoginScreen.kt` to inspect the
local `idToken` variable; do not add token logging. This step requires an actual Google account
and consent UI. Get the account's expected `sub` from the locally inspected token payload
(decoding a payload does not verify its signature) and compare it with the API's verified result.

In a separate **Bash** terminal in `API (backend)`:

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
The Android app currently obtains tokens; it does not yet call this API automatically.

The two live tasks are excluded from normal `test`/`build`, always rerun when requested,
and fail with an explanatory message if their required environment variables are absent.
Reports: `build/reports/tests/test/index.html`, `build/reports/tests/railwayTest/index.html`,
and `build/reports/tests/googleSignInTest/index.html`.
