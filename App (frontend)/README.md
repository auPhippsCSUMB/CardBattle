# Frontend sign-in tests

From `App (frontend)`, with an Android emulator/device running (API 34 or higher):

```bash
bash ./gradlew :app:connectedDebugAndroidTest
```

`LoginScreenTest` checks successful token delivery, blank tokens, cancellation and retry,
Google configuration errors, unexpected/malformed credentials, and the disabled button while
sign-in is pending. It supplies fake Credential Manager results, so these tests need no
Google account or Railway connection. Tokens are neither displayed nor logged.

To compile tests without a device: `bash ./gradlew :app:assembleDebugAndroidTest`.
The existing JVM tests run with `bash ./gradlew :app:testDebugUnitTest`.
Device test reports appear in `app/build/reports/androidTests/connected/`.

For real Google sign-in, run the app on a Google Play device/emulator, tap Sign in with Google,
and complete account selection. Set a debugger breakpoint on `onTokenReceived(idToken)` to
inspect the token in memory. A received token still needs backend verification; the UI does
not treat it as a verified CardBattle session. Follow the live Google test instructions in
`API (backend)/README.md` to verify it against the running API.

Railway credentials belong only in the backend. The frontend will communicate with Spring
over HTTP, not connect to PostgreSQL. Automatic Android-to-backend token submission is not
implemented yet, so the current tests cover token acquisition and backend validation separately.
