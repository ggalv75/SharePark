# Security

## Reporting a vulnerability

Open a [private security advisory](https://github.com/security/advisories) on this repository
rather than a public issue. Please allow a reasonable window for a fix before disclosure.

## API key handling

SharePark needs one credential: a Google Maps Platform key used by the Maps SDK and the
Geocoding API. It is treated as follows.

**It is never in the repository.** The key lives in `local.properties`, which is git-ignored.
`app/build.gradle.kts` reads it at configuration time and injects it as a manifest placeholder
and a `BuildConfig` field, so no source file contains the literal.

**Three layers keep it that way:**

1. `.gitignore` excludes `local.properties`, `*.jks`, `*.keystore`, and `keystore.properties`.
2. A pre-commit hook (`.githooks/pre-commit`) rejects any commit that stages `local.properties`,
   signing material, or a `AIzaSy…` literal. Enable it once per clone:

   ```bash
   git config core.hooksPath .githooks
   ```

3. The `secret-scan` CI job fails the build if a key literal or a tracked `local.properties`
   ever reaches `main`.

**In CI**, the key comes from the repository secret `MAPS_API_KEY` and is written to
`local.properties` at build time. Set it under *Settings → Secrets and variables → Actions*.
Forks and pull requests build without the secret — the app compiles and the map renders blank.

## Restricting the key at the provider

A `.gitignore` protects against leaking the key; it does not protect against a key that leaks
some other way. Restrict it in the [Google Cloud Console](https://console.cloud.google.com/google/maps-apis/credentials):

- **Application restriction** → *Android apps*, with package name `com.sharepark` and the
  SHA-1 of each signing certificate (debug and release). This makes the key useless to anyone
  who extracts it from the APK.
- **API restriction** → *Maps SDK for Android* and *Geocoding API* only.
- Set a **quota cap** on the Geocoding API so an unexpected spike cannot run up a bill.

Note that the Geocoding API is a web-service API and cannot be restricted by Android package
name — if you need both, use two separate keys: an Android-restricted key for the map and a
separate, quota-capped key for geocoding.

## On-device data

Parking history is stored in a local Room database in the app's private storage. There is no
backend, no account, and no analytics. The only outbound request is reverse geocoding. Records
older than 30 days are deleted automatically by a WorkManager job.

## The accessibility service

`WhatsAppAutoSendService` is an `AccessibilityService` that presses send in WhatsApp. It only
acts on a message the user configured, only inside WhatsApp, and only after the user enables it
by hand in Android's Accessibility settings. It reads no other app's content and transmits
nothing off-device.
