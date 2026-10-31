# Build From Source

Requirements and commands for building CatSmoker locally.

## Prerequisites

- **Android Studio** (stable).
- **JDK 17**.
- **Android SDK** with the platform/NDK versions referenced below.
- **Android NDK** `28.2.13676358` (set in `app/build.gradle.kts`).

## Key versions

| Item        | Value            |
| ----------- | ---------------- |
| AGP         | 9.4.1            |
| Kotlin      | 2.4.21           |
| KSP         | 2.3.10           |
| Compose BOM | 2026.09.00       |
| Min SDK     | 27 (Android 8.1) |
| Target SDK  | 37               |
| Compile SDK | 37               |
| Version     | 2.0.5 (code 11)  |
| NDK         | 28.2.13676358    |

## Variants

One branch builds two distribution flavors (see `FLAVOR_WORKFLOW.md`):
`full` (complete GitHub app) and `playstore` (Play-safe build). Shared code
lives in `app/src/main/`; variant code in `app/src/full/` / `app/src/playstore/`.
Firebase Analytics is shared (same project, `app/google-services.json` is
gitignored/local-only and must be present to build); ads are per-variant
(Start.io on full, AdMob on playstore).

## Build

From the repository root:

```bash
./gradlew :app:assembleFullDebug :app:assemblePlaystoreDebug
./gradlew :app:assembleFullRelease :app:assemblePlaystoreRelease
```

(On Windows: `.\gradlew.bat …`.)

Debug APKs are produced at:

```
app/build/outputs/apk/full/debug/app-full-debug.apk
app/build/outputs/apk/playstore/debug/app-playstore-debug.apk
```

Release builds are minified (`isMinifyEnabled = true`, `isShrinkResources =
true`); without a gitignored `signing.properties` (storeFile, storePassword,
keyAlias, keyPassword) they stay debug-signed so any clone still builds.

App bundles (`:app:bundleFullRelease`, `:app:bundlePlaystoreRelease`) also
work: ABI splits switch off automatically when a bundle task is requested,
because a bundle already carries every ABI in its base module. Gradle names
the universal split APK `app-full-universal-release.apk`; GitHub releases
publish it as `app-full-release.apk`, the name the self-updater expects.

## Running unit tests

```bash
./gradlew :app:testFullDebugUnitTest :app:testPlaystoreDebugUnitTest
```

Tests live in `app/src/test/java/com/catsmoker/app/` (shared, runs against
both variants) and `app/src/testFull/` (full-only: spoof/LSPosed/Magisk) and
cover pure-Kotlin logic: parsers, config templates, the Magisk module builder,
gaming interventions, and related utilities.

## Lint

Lint is configured **not** to abort the build on errors (`abortOnError = false`,
`checkReleaseBuilds = true`) so minor warnings never block a build. You can
still run it explicitly:

```bash
./gradlew lint
```

## Release notes

- `isMinifyEnabled = true` and `isShrinkResources = true` for the release
  build type; without `signing.properties` the release APK stays debug-signed
  (see above).
- **GitHub Release rule**: GitHub Releases must publish ONLY the `full` variant assets: the universal `app-full-release.apk` along with all per-ABI split APK files (`app-full-arm64-v8a-release.apk`, `app-full-armeabi-v7a-release.apk`, `app-full-x86_64-release.apk`). NEVER attach the `playstore` variant (`app-playstore-release.apk` / `.aab`) to GitHub Releases; the Play variant is built exclusively for Google Play Console upload.
- The Start.io ad SDK id can be overridden via `STARTIO_APP_ID` in
  `local.properties` (full variant; defaults to the demo id). AdMob ids for
  the playstore variant come from `ADMOB_APP_ID` / `ADMOB_BANNER_ID` /
  `ADMOB_INTERSTITIAL_ID` in `local.properties`, defaulting to Google's
  sample test ids.
- Nothing's Glyph Developer Kit API key comes from `NOTHING_API_KEY` in
  `local.properties` (both variants; falls back to a dummy that only makes the
  GDK refuse registration). Nothing stopped requiring it from Android 16, so a
  missing key is not a build failure. Nothing publishes no licence text for the
  vendored `app/libs/glyph-matrix-sdk-2.0.aar` — see `GAMING_MODE.md`.

## Troubleshooting

- **NDK not found** — ensure the NDK version above is installed via SDK Manager.
- **Platform/build-tools missing** — let Android Studio sync and install what
  it asks for.
- **Xposed API not resolving** — the `de.robv.android.xposed:api` dependency
  is `fullCompileOnly` (full variant only); it resolves from remote repos and
  only needs to compile, not package.
- **google-services.json missing** — Firebase is shared by both variants, so
  the gitignored `app/google-services.json` must exist locally or configuration
  fails with "File google-services.json is missing". Copy it from a trusted
  checkout (same `com.catsmoker.app` project for both variants).
