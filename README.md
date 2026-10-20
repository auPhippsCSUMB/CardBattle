![CI](https://github.com/auPhippsCSUMB/CardBattle/actions/workflows/ci.yml/badge.svg)

# CardBattle

- `api-backend/`: Spring Boot API, Java 21, and its own Gradle wrapper.
- `app-frontend/`: Android app and its own Gradle wrapper.
- `docs/openapi.yaml`: the implemented card, Google authentication, and profile API contract.

Open the renamed directories in your IDE. Ignored files do not follow Git renames:
keep backend settings in `api-backend/.env` and Android's `sdk.dir` in
`app-frontend/local.properties`. Old directories may still contain local settings
and build outputs; they are not the active projects.

## Docker with Railway (default)

The default Compose file starts only the API and uses the existing Railway database.
Create a private repository-root `.env` using `.env.example` as the key reference,
or copy your existing backend settings there. Fill in the real public Railway
JDBC URL, username, password, and Google Web client ID. Never commit these files.
Compose reads the root `.env`; it does not automatically read `api-backend/.env`.

```bash
docker compose up --build
```

The API is available at `http://localhost:8080` by default. No database schema
changes are automatically applied to Railway.

```bash
docker compose down
```

The container always listens on 8080. `APP_PORT` changes only the host port.
Keep `APP_PORT=8080` for the Android emulator's existing
`http://10.0.2.2:8080` backend URL. If you choose another host port, update
the app's backend URL in `data/remote/CardBattleApi.kt` to match.

## Optional local PostgreSQL

This starts a separate database and overrides only the API's database connection.
The Google Web client ID is still read from the root `.env`. Its value must match
`app-frontend/app/src/main/res/values/strings.xml`.

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build
```

PostgreSQL is available on `localhost:5432` by default. Configure its credentials
and host port with the `POSTGRES_*` values in `.env.example`. The API waits for
the database health check before starting.

On a fresh volume, `api-backend/db/init/001_schema.sql` creates all six tables,
generated integer IDs, Google user identity, and cascade/null deletion rules.
PostgreSQL runs initialization scripts only for an empty volume. An existing
database volume needs an explicit reviewed schema update; restarting does not
reapply the script. The separate cascade migration records the earlier Railway
change and is not needed on a newly initialized local database.

Local and Railway databases contain different users/cards. Sign in again to
register your Google user in whichever database the API currently uses.

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml down
```

## Run the API directly

From a Bash terminal with Java 21 installed:

```bash
cd api-backend
set -a
source .env
set +a
bash ./gradlew bootRun
```

Use a public Railway host/port in this host-run configuration. The Docker-only
hostname `db` is not available to a process running directly on your computer.
Do not start both the Docker API and host API on the same host port.

## Android

Open `app-frontend` in Android Studio. Let the IDE configure the Android SDK,
or set `sdk.dir=/your/Android/Sdk` in its ignored `local.properties`.
Gradle's frontend daemon configuration requests Java 25; the backend uses Java 21.
The wrappers select the Gradle versions, so no global Gradle installation is needed.

Use a Google Play emulator for real Google sign-in. A physical device requires a
backend address reachable from that device instead of `10.0.2.2`.
See [frontend testing and sign-in](app-frontend/README.md).

## Verification

```bash
cd api-backend
bash ./gradlew verify
```

Backend tests use local H2 databases and fake/local signing keys; they do not
modify Railway. Coverage enforcement includes the actual `CardService` class.
See [backend tests and route examples](api-backend/src/test/README.md).

The API contract intentionally omits controller stubs that still contain TODOs.
Errors are documented by status code; a uniform Problem Details response body
is not currently promised by the implementation.
