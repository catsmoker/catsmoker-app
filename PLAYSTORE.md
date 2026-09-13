# PLAYSTORE.md — `playstore` branch record

> Dedicated record for preparing and maintaining CatSmoker (`com.catsmoker.app`)
> for Google Play Store publication.
>
> - Branch: `playstore` (existing branch; this file lives on that branch).
> - Rule: keep **all** Play Store-specific changes, configurations, fixes,
>   compliance work, release preparation, and publishing decisions on
>   `playstore`. Keep unrelated experimental/development work on the
>   appropriate development branches (e.g. `main`).
> - Update this file whenever a Play Store-related change or decision is made.
> - Do not invent information here: document only what is known from the
>   repository, project configuration, or existing work. Unknown items are
>   marked as unknown / pending.

## 1. Purpose

The `playstore` branch is the single place where the app is shaped into a
Play-policy-compliant listing:

- a build variant/configuration that can pass Play review,
- the permission / declaration / Data safety story,
- removal or gating of Play-incompatible capabilities,
- release configuration, versioning, and signing readiness,
- testing evidence and publishing status.

`main` (and feature branches) remain the development line. `playstore`
receives only cherry-picked `[PLAY-SAFE]` commits from `main` (see
`docs/BRANCH_WORKFLOW.md`) — never a merge, never a rebase, and never the
other way around.

## 2. Relationship to other branches

| Branch | Role |
| --- | --- |
| `main` | Development line. Full feature set including root / LSPosed / Magisk flows distributed via GitHub Releases. |
| `playstore` | Play publication line. Only Play-compatible changes plus cherry-picked `[PLAY-SAFE]` commits from `main` (see `docs/BRANCH_WORKFLOW.md`). |
| Feature / fix branches | Short-lived work; merge into `main` first with a `[PLAY-SAFE]` / `[MAIN-ONLY]` prefix, then cherry-pick into `playstore` only if `[PLAY-SAFE]` (see `docs/BRANCH_WORKFLOW.md`). |

Observed state (verified via `git rev-parse` / `git diff`):

- `main` and `playstore` both point at `bd1e870` ("standardized notification
  and action icons across services using new custom assets").
- `git diff --stat main..playstore` is empty — i.e. at the time this file was
  created, the Play Store work had **not yet diverged** from `main`. All
  removals/reviews below are therefore **planned, not yet implemented**.

Workflow:

1. Do Play work only while on `playstore` (`git rev-parse --abbrev-ref HEAD`
   must print `playstore`).
2. To pick up development: cherry-pick `[PLAY-SAFE]` commits from `main`
   only (`git switch playstore && git cherry-pick <commit>`). **Never
   `git merge main`, never rebase onto `main`** — `main` carries
   `main`-only functionality (spoofing, LSPosed, Magisk) that must never
   reach this branch. On conflict: stop, assess per `docs/BRANCH_WORKFLOW.md`,
   and record the outcome in §12 — never silently drop a Play restriction
   to make the pick succeed.
3. Never commit secrets (keystores, passwords, `local.properties` values).
   `local.properties` is git-ignored (see `.gitignore`) and carries
   `sdk.dir` plus the `ADMOB_*` IDs on this branch.

## 3. Release configuration (observed — sources cited)

Sources: `app/build.gradle.kts`, `gradle.properties`, `docs/BUILD.md`,
`app/src/main/AndroidManifest.xml`.

| Item | Observed value |
| --- | --- |
| `namespace` / `applicationId` | `com.catsmoker.app` (`app/build.gradle.kts:11,15`) |
| `minSdk` | 27 (Android 8.1) |
| `targetSdk` | 36 |
| `compileSdk` | 37 |
| `versionCode` | 7 (comment notes the bump forces Shizuku to restart the AIDL helper; do not bump casually — `app/build.gradle.kts:18-21`, `AGENTS.md`) |
| `versionName` | `2.0.0` |
| AGP / Kotlin / KSP / Compose BOM / NDK | 9.4.0 / 2.4.20 / 2.3.10 / 2026.08.00 / 27.0.12077973 (per `docs/BUILD.md`; Kotlin bumped on this branch in `57bccfd`) |
| `release` build type | `isMinifyEnabled = true`, `isShrinkResources = true`, `isDebuggable = false`, ProGuard `proguard-android-optimize.txt` + `proguard-rules.pro` — but `signingConfig = signingConfigs.getByName("debug")` (`app/build.gradle.kts:37-43`) |
| `debug` build type | no minify, debuggable, debug signing |
| Lint | `abortOnError = false`, `checkReleaseBuilds = true` (`app/build.gradle.kts`; non-blocking, surfaces warnings on release builds) |
| Gradle flags | `android.builtInKotlin=true` — do **not** add the `kotlin.android` plugin; four plugins only (`AGENTS.md`, `gradle/libs.versions.toml`) |
| Local overrides | `ADMOB_APP_ID` / `ADMOB_BANNER_ID` / `ADMOB_INTERSTITIAL_ID` from `local.properties`, fallback to Google's documented sample (test) IDs (`app/build.gradle.kts`); `sdk.dir` also in `local.properties` |
| Ads provider | AdMob (`com.google.android.gms:play-services-ads:25.4.0`) — this branch only; `main` serves Start.io |
| Packaging excludes | `META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,LICENSE*,NOTICE*,ASL2.0}` |

Play implication (not yet done):

- The release block **must get a real signing config before upload**; today a
  "release" APK is still debug-signed (`docs/BUILD.md` says the same).
- `versionCode` bumps have a side effect (Shizuku helper restart), so each
  Play upload bump must be intentional.

## 4. Signing / upload-key information (no secrets stored)

- Current state: **no Play upload key configured in the repo.** Release uses
  the debug keystore (`app/build.gradle.kts:42`). No keystore file, alias,
  or password is checked in, and none may be added.
- Required before first upload (pending):
  1. Create the upload key **outside the repo** (Play App Signing recommended).
  2. Wire it via environment / CI secrets or a local `signing.properties`
      that stays git-ignored — never commit the `.jks`/`.keystore`.
  3. Record here only: key owner, creation date, SHA-1/SHA-256 fingerprints
     of the **public certificate**, and which Play track it was uploaded to.
     Fingerprints are currently **unknown** (no key created yet).
- Rotation/recovery plan: unknown — to be documented when Play App Signing is
  enrolled.

## 5. Feature compliance matrix (author decisions)

These are the author's binding decisions for the `playstore` line. They are
recorded verbatim; §6 maps each row to the code it affects.

| CatSmoker feature | Decision |
| --- | --- |
| GFX/FPS configuration editing | 🟢 **KEEP** |
| Game-file editing | 🟢 **KEEP** |
| SAF | 🟢 **KEEP** |
| Shizuku | 🟢 **KEEP** |
| User-requested force-stop | 🟢 **KEEP / review** |
| App freeze/suspend | 🟢 **KEEP / review** |
| Game backups | 🟢 **KEEP** |
| Gaming overlay | 🟢 **KEEP** |
| Audio enhancement | 🟢 **KEEP** |
| DND | 🟢 **KEEP / review** |
| VPN firewall | 🟢 **KEEP + declaration** |
| `MANAGE_EXTERNAL_STORAGE` | 🟡 **REVIEW** |
| `PACKAGE_USAGE_STATS` | 🟡 **REVIEW** |
| Dexopt | 🟡 **REVIEW** |
| `device_config` | 🟡 **REVIEW** |
| `setprop` | 🟡 **REVIEW exact commands** |
| `wm size/density` | 🟡 **REVIEW** |
| Notification listener | 🟡 **REVIEW exact purpose** |
| LSPosed hooks | 🔴 **REMOVE** |
| ID spoofing | 🔴 **REMOVE** |
| Fake `getprop` | 🔴 **REMOVE** |
| Magisk spoof module | 🔴 **REMOVE** |
| External APK updater | 🔴 **REMOVE** |

Legend: 🟢 keep (review = keep but verify Play-safe wording/flow) ·
🟡 review (needs per-permission/per-command justification or gating) ·
🔴 remove (must not ship on `playstore`).

## 6. What each row maps to in this repo (observed, not invented)

KEEP rows (retain, adapt wording where it references removed flows):

- GFX/FPS configuration editing — `features/gamingtools/tools/graphics/`,
  `features/gamingtools/tools/interventions/GameInterventions.kt`
  (`device_config put game_overlay …`), per-game editors under
  `features/editgamefiles/{genshin,grid,hsr,pubg,wuwa}/`.
- Game-file editing — `features/editgamefiles/` (backup-first via
  `ConfigBackupStore`, read-back verify; Genshin text-substitution only,
  GRID targeted `<value>`, PUBG `PubgSavePatcher` byte-patch).
- SAF — Storage Access Framework path in file engineering (no shell,
  no force-stop, no hash sync) — the Play-safe file path.
- Shizuku — `moe.shizuku` API + `FileService` user service
  (`system/shell/ShellRunner`, Shizuku provider in manifest). Keep.
- User-requested force-stop — `am force-stop` flows (e.g.
  `strings_gamefiles.xml: gf_hs_stopped`, `GamingEngine.manualBoostRam`).
  KEEP / review: keep strictly user-initiated, confirm wording.
- App freeze/suspend — Gaming Mode `pm suspend --user 0` with `hardWhitelist`
  + snapshot revert (`docs/GAMING_MODE.md`, `GamingEngine`). KEEP / review:
  verify suspend is session-scoped and reverted.
- Game backups — `ConfigBackupStore` device-bytes-first flow. KEEP.
- Gaming overlay — `PerformanceOverlayService` (ID 104),
  `CrosshairOverlayService` (shares ID 102), `SYSTEM_ALERT_WINDOW`,
  `specialUse` FGS + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. KEEP.
- Audio enhancement — `MODIFY_AUDIO_SETTINGS` (+ `RECORD_AUDIO` declared in
  manifest) audio-boost path. KEEP (verify `RECORD_AUDIO` justification if
  retained).
- DND — Focus Mode via notification-policy access
  (`ACCESS_NOTIFICATION_POLICY`, `docs/GAMING_MODE.md`). KEEP / review:
  gate on explicit grant, restore original value.
- VPN firewall — `features/gamingtools/tools/firewall/VpnFirewallService`
  (`BIND_VPN_SERVICE`, ID 4301, user VPN consent dialog). KEEP + declaration:
  Play requires the VPN disclosure + Data safety entry; in-app copy already
  states it is a local loopback block, one VPN at a time
  (`strings_gaming.xml: gt_net_vpn_*`).

REVIEW rows (ship only with justification/gating/declaration):

- `MANAGE_EXTERNAL_STORAGE` — declared in manifest (`AndroidManifest.xml:58`
  with `tools:ignore="AllFilesAccessPolicy"`). `docs/SECURITY.md` justifies it
  for file engineering on older Android. Play restricts this to core-use
  cases — review whether SAF + media/storage intents suffice on the Play
  build, else prepare the All-files-access declaration.
- `PACKAGE_USAGE_STATS` — declared (`AndroidManifest.xml:63`); used for
  app/game presence heuristics (`AutoForceStopService`,
  `docs/SECURITY.md`). Needs Settings-grant flow + Data safety + listing
  disclosure review.
- Dexopt — `GamingEngine.runArtOptimization` (`cmd package compile`) +
  `DexoptSweepWorker` scheduled `speed` (never `-f`) sweep via unique
  periodic WorkManager work. Review battery/background justifications,
  `FOREGROUND_SERVICE_DATA_SYNC` + `specialUse` subtype "Scheduled dexopt
  sweep", and the Stop-button semantics.
- `device_config` — Game Interventions
  (`device_config put game_overlay <pkg> mode=…`, both rows, restore from
  snapshot). Review exact keys and revert behaviour on the Play build.
- `setprop` — exact commands under review, e.g.
  `GameDeveloperOptions.setGameDefaultFrameRateDisabled` writes
  `setprop <PROP_GAME_FRAME_RATE_DISABLED> true|false`
  (`GameDeveloperOptions.kt:435-444`); `GamingEngine` `setprop` helpers with
  read-back verify (`GamingEngine.kt:1778+`). Inventory every key before
  shipping; drop anything that alters global device identity.
- `wm size/density` — Resolution Changer (`GamingToolsViewModel` `wm size` /
  `wm density` parsing/override, `DisplayMetricsProvider` as one of the two
  display-fact owners). Review safe-range gating and revert.
- Notification listener — `GamingNotificationListener`
  (`BIND_NOTIFICATION_LISTENER_SERVICE`, second-layer suppression, cancels
  only / never posts). REVIEW exact purpose text for the listing +
  in-app disclosure.

REMOVE rows (must not ship on `playstore`):

- LSPosed hooks — `features/spoofdevice/root/LSPosedModule.kt` (+
  `GetPropInterceptor`), Xposed `compileOnly` API
  (`gradle/libs.versions.toml: api`), `xposedmodule`/`xposeddescription`/
  `xposedminversion`/`xposedscope` meta-data + `@array/scope` (62 pkgs, only
  array in `strings.xml`), `-keep` rules in `proguard-rules.pro:39-43`,
  `assets/xposed_init`, `xposedsharedprefs` (`lsposed_prefs`
  `MODE_WORLD_READABLE`). Remove code + manifest meta-data + assets + scope
  gating on this branch.
- ID spoofing — `features/spoofdevice/` UI + `SpoofRepository` presets +
  `SpoofConfigProvider` (exported, UID-scoped) + `Settings.Global` profile
  keys (`catsmoker_lsposed_*`, `LSPosedConfig`). Remove on this branch
  (presets are author-curated and must otherwise stay exact — but on
  `playstore` the feature itself goes).
- Fake `getprop` — `GetPropInterceptor` (`SystemProperties` + `getprop`
  overlay). Remove together with the hooks (partial spoof is worse than
  none — never ship half).
- Magisk spoof module — `MagiskModuleBuilder` (generates `system.prop` =
  `MODEL_KEYS` + `KEY_PIXELPROPS_GAME`, `service.sh`, `module.prop`),
  `MagiskModuleBuilderTest`, `.gitattributes` LF pinning for shipped module
  assets. Remove generation/export UI on this branch.
- External APK updater — `SettingsViewModel` GitHub-Releases checker:
  `https://api.github.com/repos/catsmoker/com.catsmoker.app/releases`
  (`SettingsViewModel.kt:172`), `.apk` asset download to
  `getExternalFilesDir()/update.apk` + `FileProvider` install intent
  (`SettingsViewModel.kt:205-249`). Incompatible with Play (self-update
  outside Play). Remove on this branch; Play updates come from Play.

## 7. Play policy touchpoints found in the repo

Observed in `AndroidManifest.xml` / `docs/SECURITY.md` / code. Each needs a
listing + Data safety + (where applicable) declaration entry before upload.
Status is **pending** unless §9 says otherwise.

- Sensitive permissions: `MANAGE_EXTERNAL_STORAGE`, `PACKAGE_USAGE_STATS`
  (appop via Settings), `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`,
  `WRITE_SECURE_SETTINGS` (signature|privileged — declared only so
  `adb pm grant` can work; not grantable at install), `POST_NOTIFICATIONS`,
  `ACCESS_NOTIFICATION_POLICY`, `READ/WRITE_EXTERNAL_STORAGE` (+
  `requestLegacyExternalStorage`), `RECORD_AUDIO`, `AD_ID`.
- Foreground services: all `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`
  (booster 101, gaming/crosshair 102, dexopt fg/skipped 103/105, perf overlay
  104, force-stop 4201, VPN 4301) plus the re-declared
  `SystemForegroundService` for WorkManager. Review each subtype string.
- VPN: `VpnFirewallService` (`BIND_VPN_SERVICE`, consent dialog). Needs VPN
  disclosure, Data safety "VPN" handling statement, and one-VPN-at-a-time
  copy (already in-app).
- Notification listener: `GamingNotificationListener`
  (`BIND_NOTIFICATION_LISTENER_SERVICE`, user grant in Settings, inert until
  granted). Needs purpose disclosure; until granted the layer reports "not
  applicable".
- Ads: AdMob SDK (`system/ads/AdManager.kt`, `AdMobBanner`,
  `com.google.android.gms:play-services-ads:25.4.0`, `MobileAds.initialize`
  in `CatsmokerApp.initDeferredTasks`, App ID via `ADMOB_APP_ID` manifest
  placeholder, unit IDs via `BuildConfig`). Needs Ads declaration + Data
  safety (ad SDK data collection) + `AD_ID` justification. Unset keys serve
  Google's sample (test) IDs — replace with production IDs via
  `local.properties` (never commit them) before upload. UMP consent flow for
  the EEA is still **pending**.
- Package visibility: `<queries>` lists Shizuku / ZArchiver / Magisk /
  KernelSU / APatch + named game packages + MAIN/GAME/VIEW intents. The
  root-manager entries must go with the Magisk removal on this branch.
- No `QUERY_ALL_PACKAGES` declared (good — do not add).
- Content provider: exported `SpoofConfigProvider` exists for the LSPosed
  path — goes away with the spoof removal.
- License: CC BY-NC-SA 4.0 (`LICENSE`, README badges). NonCommercial +
  ShareAlike has Play implications for paid listings — listing must stay
  free; confirm Play's requirements for CC-licensed apps before monetising.
- Privacy/Data safety inputs: stated position is "no unnecessary data
  collection; all modifications performed locally" (README), with network
  limited to ads (AdMob on this branch), DNS optimisation, and an `8.8.8.8` latency ping
  (`docs/SECURITY.md`). No privacy-policy URL or Data safety form content was
  found in the repo — both are **pending**.
- Target API: 36 (meets the current Play target-API expectation at time of
  writing; re-check at upload since Play moves the floor).

## 8. Required changes (checklist for `playstore`)

REMOVE (🔴 — must land before any Play upload):

- [x] Strip LSPosed entrypoints (`LSPosedModule`, `GetPropInterceptor`,
      `LSPosedConfig` hook-side), Xposed `compileOnly` dep, `xposed_init`
      asset, `xposedscope`/`xposed*` manifest meta-data, scope array usage,
      `SpoofConfigProvider` (or restrict if kept for non-spoof use — default
      is remove), Magisk `MagiskModuleBuilder` + export UI + tests, related
      ProGuard keeps, and all spoof UI/routes (`spoof_profiles`,
      `spoof_editor`, `spoof_apps`, `spoof_safe_mode` + shared
      `SpoofDeviceViewModel` scoping).
      — **Done 2026-09-13** (see §11). Deleted `features/spoofdevice/`
      (7 screens + ViewModel + `root/` + `tools/MagiskModuleBuilder`),
      `SpoofConfigProvider`, `LSPosedConfig`, `SpoofRepository`,
      `DeviceProfile`/`DevicePreset`, `RandomGenerator` (spoof-only),
      `xposed_init`, `strings_spoof.xml` ×4 locales, 5 spoof/Magisk/ID
      tests; removed `SPOOF_*` routes + NavHost entries + dashboard card,
      manifest meta-data/providers/magisk `<queries>`, ProGuard keeps,
      Xposed dep + repo, `.gitattributes` pinning, `dash_spoof_*` strings
      (×4) + `scope` array.
- [x] Remove external updater (`SettingsViewModel` GitHub check/download/
      install flow + `update.apk` + related strings/permissions usage).
      — **Done 2026-09-13** (see §11). ViewModel reduced to
      appearance + ads; Settings screen updater section dropped;
      `FileProvider` + `file_paths.xml` removed (updater-only);
      13 updater `sys_*` keys removed ×4 locales (`sys_later` kept —
      still used by theme/language/support dialogs).
- [x] Scrub copy: README/install guides referencing Magisk/LSPosed flashing,
      in-app strings pointing at root-hook flows, scope arrays, and any
      "sideload update" prompts. Listing copy must describe only the Play
      build's capabilities.
      — **Done 2026-09-13** (see §11). Both READMEs rewritten to the
      Play build (Shizuku/SAF only, Play distribution, no spoofing);
      3 stale KDoc cross-references updated. `docs/` left untouched
      (main-line architecture reference, not user-facing copy).

REVIEW / GATE (🟡 — justify, narrow, or gate; record outcome per item):

- [ ] `MANAGE_EXTERNAL_STORAGE` — decide keep-with-declaration vs drop for
      SAF-only on Play.
- [ ] `PACKAGE_USAGE_STATS` — confirm grant flow + disclosure text.
- [ ] Dexopt (`runArtOptimization` + `DexoptSweepWorker`) — confirm
      user-initiated + cancellable + scheduled-`speed`-only story.
- [ ] `device_config` / `setprop` / `wm size|density` — publish the exact
      allow-listed commands + keys and their snapshot-revert pairing; drop
      the rest on this branch.
- [ ] Notification listener — finalise the "exact purpose" sentence for the
      listing and in-app disclosure.
- [ ] VPN firewall — add the Play VPN disclosure + Data safety entry (KEEP +
      declaration).
- [ ] Force-stop / freeze-suspend / DND — confirm user-initiated, scoped,
      and reverted; adjust copy so nothing implies silent control of other
      apps.

HYGIENE (required regardless):

- [ ] Real signing config for `release` (no debug signing on upload).
- [ ] Decide `versionCode`/`versionName` for the first Play upload (note the
      Shizuku AIDL-restart side effect of any `versionCode` bump).
- [ ] Privacy policy URL + Data safety form (ads, VPN, DNS, usage-stats,
      file access) — none found in repo yet.
- [ ] Play listing assets (icon, feature graphic, screenshots, category,
      content rating, contact email) — none found in repo yet.
- [ ] `lintRelease` / `assembleRelease` green on `playstore`; fix or
      document new warnings (lint currently non-blocking).
- [ ] Full unit-test pass (`testDebugUnitTest`) after removals; delete or
      rewrite spoof/Magisk/updater tests that cover removed code.

## 9. Status

- Completed: `playstore` branch designated and pushed (`origin/playstore`
  exists); this `PLAYSTORE.md` record created; decision matrix (§5) adopted;
  AdMob switch landed (uncommitted at time of writing); **all §8 REMOVE
  rows landed 2026-09-13** (spoof/LSPosed/Magisk/updater stripped, copy
  scrubbed — see §11). REVIEW rows (§8 🟡) and hygiene items are untouched.
- Pending: §8 REVIEW + HYGIENE rows. Order suggested: REVIEW rows next
  (narrow + document), then hygiene (signing, policy, listing, tests).
- Verification status (honest): `compileDebugKotlin` is green on
  `playstore` after the 2026-09-13 `[PLAY-SAFE]` pick (`ed02f8a`).
  `assembleRelease`, `testDebugUnitTest`, `lintDebug`, and the on-device
  `verify` pass are all still pending before any upload.
- Known issues / risks:
  - Spoof/Magisk/updater code is deeply referenced (strings in 4 locales,
    ProGuard, manifest, tests) — removal must touch all split `strings_*`
    files **and** the `ar`/`es`/`zh-rCN` mirrors key-for-key (repo
    localisation rule), or drop the keys everywhere at once.
  - `versionCode` bump restarts Shizuku's helper — coordinate with release
    notes.
  - Release is minified (`proguard-rules.pro` keeps Shizuku
    reflection; LSPosed keeps to be deleted with that feature) — re-verify
    R8 output after deletions.
  - `README.md` / `README.zh-CN.md` must stay in sync (badges/shields/Star
    History verbatim); Play-variant notes added on this branch must be
    mirrored.
- Testing requirements (before any upload):
  - `./gradlew :app:assembleRelease` + install on min (API 27) and current
    devices; walk every REVIEW-gated flow and confirm grant → act → revert.
  - `./gradlew :app:testDebugUnitTest` green; `lintDebug` report read
    (`app/build/reports/lint-results-debug.html`).
  - On-device `verify` skill pass (build → adb install → drive Compose UI)
    for the Play build, including locale `en` + one mirrored locale.
- Publishing status: **not published.** No Play track, no upload key, no
  Data safety submission, no content rating — all pending. Current
  `versionCode 7 / versionName 2.0.0` has **not** been uploaded to Play.

## 10. Important decisions

- 2026-09-13: `playstore` adopted as the dedicated Play publication branch;
  Play-specific work stays here, development stays on `main`/feature
  branches (this file).
- 2026-09-13: Feature matrix in §5 adopted (KEEP / REVIEW / REMOVE). In
  particular: Shizuku + SAF stay (Play-safe privilege paths); LSPosed hooks,
  ID spoofing, fake `getprop`, Magisk module, and the GitHub-APK
  self-updater are excluded from the Play build.
- Release signing stays out of the repo (no secrets in git); first upload
  needs a separately-created upload key + Play App Signing enrolment.
- 2026-09-13: `playstore` switches ads to **AdMob**
  (`com.google.android.gms:play-services-ads:25.4.0`, banner +
  interstitial); `main` keeps Start.io. IDs come from `local.properties`
  (`ADMOB_APP_ID` / `ADMOB_BANNER_ID` / `ADMOB_INTERSTITIAL_ID`) with
  Google's sample (test) IDs as fallback. Production IDs + UMP consent flow
  + Ads declaration / Data safety entry are pending before upload.

## 11. Changelog (newest first)

- 2026-09-13: Cherry-picked `[PLAY-SAFE] 643cd0c` from `main`
  (`ed02f8a`, zero conflicts; `libs.versions.toml` hunk empty —
  Kotlin 2.4.20 already present via `57bccfd`). Manifest drops unused
  `BLUETOOTH`, `ACCESS_WIFI_STATE`, `FOREGROUND_SERVICE_DATA_SYNC`;
  `resConfigs` strips transitive locales; legacy launcher PNGs removed
  (adaptive-only, minSdk 27); lint `checkReleaseBuilds` on (still
  non-blocking). About gains a Still-in-development card (Logs +
  GitHub-issues links, 4 locales) — bug-report link only, no
  self-update behaviour, so the §8 updater removal stands. Logs dumps
  the full logcat buffer with fixed terminal contrast; game-icon
  bitmap caching; Wuwa locale-aware history time. `compileDebugKotlin`
  green on `playstore` after the pick; `assembleRelease` / unit tests
  / lint / on-device `verify` still pending.

- 2026-09-13: Adopted one-way sync policy (`docs/BRANCH_WORKFLOW.md`):
  `[PLAY-SAFE]` cherry-picks only, no merges/rebases from `main`, nothing
  ever flows back. Rewrote §§1–2 (which had prescribed
  `git merge origin/main`) to match.

- 2026-09-13: Landed all §8 REMOVE rows. Deleted `features/spoofdevice/`
  (Spoof screens, `SpoofDeviceViewModel`, `root/LSPosedModule` +
  `GetPropInterceptor`, `tools/MagiskModuleBuilder`), `SpoofConfigProvider`,
  `LSPosedConfig`, `SpoofRepository`, `DeviceProfile`/`DevicePreset`,
  spoof-only `RandomGenerator`, `assets/xposed_init`,
  `strings_spoof.xml` (all 4 locales), and 5 tests
  (`MagiskModuleBuilderTest`, `LSPosedConfigTest`,
  `ScreenRefreshRateConfigTest`, `SpoofRateCandidatesTest`,
  `RandomGeneratorTest`). Removed `SPOOF_*` routes + NavHost entries +
  dashboard spoof card, `xposed*` manifest meta-data, `SpoofConfigProvider`
  + `FileProvider` providers, Magisk/KernelSU/APatch `<queries>`,
  LSPosed ProGuard keeps, Xposed `compileOnly` dep + `api.xposed.info`
  repo, `.gitattributes` `xposed_init` pinning, `dash_spoof_*` (×4) +
  `scope` array. Stripped the GitHub self-updater (`SettingsViewModel`
  → appearance + ads only; updater Settings section, `file_paths.xml`,
  13 `sys_*` updater keys ×4 locales dropped). Scrubbed both READMEs to
  the Play build (Shizuku/SAF only, Play distribution pending, no
  spoofing); updated 3 stale KDoc cross-references. Static check only:
  no remaining references to removed symbols in `app/src/main`; **no
  build/test/lint run yet** (compile aborted for duration) — required
  before upload.

- 2026-09-13: Switched `playstore` ads from Start.io to AdMob (legacy GMA
  SDK 25.4.0, banner + interstitial, test-ID fallbacks): replaced
  `startio-sdk` with `play-services-ads` (`libs.versions.toml`,
  `app/build.gradle.kts`), swapped the manifest provider block for the
  `APPLICATION_ID` meta-data, rewrote `AdManager` + `StartAppBanner` as
  `AdManager` (AdMob) + `AdMobBanner`, moved init to `MobileAds.initialize`,
  dropped StartApp ProGuard rules, updated `.gitignore`/`docs/BUILD.md`/
  `docs/SECURITY.md`/`docs/ARCHITECTURE.md`.

- 2026-09-13: Created `PLAYSTORE.md` on `playstore`; recorded branch role,
  observed release config (`v2.0.0` / code 7, target 36 / min 27), signing
  gap (debug-signed release), full KEEP/REVIEW/REMOVE matrix, and the
  pre-work state (`playstore` == `main` @ `bd1e870`). Added Play-variant
  notes to `README.md` (+ `README.zh-CN.md` mirror) and the branch rule to
  `AGENTS.md`. No code removals yet.

## 12. How to update this file

- Append a dated entry to §11 for every Play-related merge, removal, review
  outcome, version bump, signing change (fingerprints only), test pass, or
  Play-console submission.
- Move items between §8 → §9 as they land; never delete the decision rows in
  §5 — supersede them with a dated note in §10 instead.
