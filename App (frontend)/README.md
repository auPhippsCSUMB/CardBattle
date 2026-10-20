# Frontend sign-in tests

From `App (frontend)`, with an Android emulator/device running (API 34 or higher):

```bash
bash ./gradlew :app:connectedDebugAndroidTest
```

`LoginScreenTest` checks the Google-only login layout, token delivery to the backend,
blank tokens, cancellation and retry, Google configuration errors, unexpected/malformed
credentials, backend rejection, and the disabled button during token acquisition and
backend verification. It supplies fake provider and backend results, so these tests need
no Google account or Railway connection. Fake tokens do not prove live Google validity.
Tokens are neither displayed nor logged.

To compile tests without a device: `bash ./gradlew :app:assembleDebugAndroidTest`.
The existing JVM tests run with `bash ./gradlew :app:testDebugUnitTest`.
Device test reports appear in `app/build/reports/androidTests/connected/`.

For real Google sign-in, start the backend on port 8080 and run the app on a Google Play
emulator. The app uses `http://10.0.2.2:8080` to reach the host machine; a physical device
needs an accessible backend address. Ensure the Android `server_client_id` resource
matches the backend's `GOOGLE_SERVER_CLIENT_ID` audience and that Google OAuth is
configured for the app's package and signing certificate.

Tap Sign in with Google and complete account selection. Credential Manager obtains a
Google-issued ID token, which the app submits as a bearer token to
`POST /api/v1/auth/google`. Only after backend verification succeeds does the app save
the token in its in-memory session and open Home with the returned profile. First sign-in
creates the backend user (HTTP 201); returning users receive their existing profile
(HTTP 200). There is no separate password signup or local account store.

For the live smoke test, confirm Home opens with the Google name and that a display-name
edit works. On the next successful sign-in, the stored display name refreshes from the
verified Google token (falling back to its email when no name claim is present).
A rejected token or unreachable backend must leave the
app on Login with a retryable error. Inspect tokens only in memory with a debugger if
needed; do not copy them into logs, screenshots, or test fixtures.

Railway credentials belong only in the backend. The frontend communicates with Spring
over HTTP and never connects directly to PostgreSQL. Backend authentication/security tests
use local signing keys and an in-memory database; run them from `api-backend` with
`bash ./gradlew test --tests '*AuthSecurityTests'`.
