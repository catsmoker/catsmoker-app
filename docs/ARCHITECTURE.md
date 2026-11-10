# Architecture

A high-level map of the CatSmoker Android codebase and how the layers fit together.

## Stack

| Concern              | Choice                                         |
| -------------------- | ---------------------------------------------- |
| Language             | Kotlin 2.4.21                                |
| UI                   | Jetpack Compose (Material 3, BOM 2026.09.00)   |
| Navigation           | `androidx.navigation:navigation-compose`       |
| UI state collection  | Lifecycle-aware Flow collection; paused below `STARTED` |
| DI                   | Hilt (Dagger)                                  |
| Async                | Kotlin Coroutines + Flow                       |
| JSON                 | Gson                                           |
| Root                 | libsu (`com.github.topjohnwu.libsu:core`, shared) |
| Piecemeal privileges | Shizuku (`dev.rikka.shizuku:api` + `provider`, shared) |
| Runtime hooking      | Xposed framework — LSPosed or Vector (Xposed API, `fullCompileOnly` — Full flavor only) |
| Ads                  | Start.io (Full only) / AdMob (Play flavor only) |
| Analytics            | Firebase Analytics (shared, same project)      |
| Min / target SDK     | API 27 / API 37                                |

## Module layout

Single application module under `app/` with `distribution` product flavors
(`full`, `playstore`) — see [`FLAVOR_WORKFLOW.md`](FLAVOR_WORKFLOW.md). There
is no separate `core`/`feature` Gradle split and no separate Git branch;
features are separated by Kotlin packages, variants by source set:

```
app/src/
├── main/                   # shared code — ships in BOTH variants
│   └── java/com/catsmoker/app/
│       ├── system/         # entry, DI, shell, navigation, ads call points
│       │   ├── navigation/ # Routes + AppNavHost (+ variant spoofGraph())
│       │   ├── shell/ShellRunner.kt  # root + Shizuku command execution
│       │   └── ads/        # ( Implementations live per-flavor; see below)
│       ├── shared/         # data/models, ui/theme, components
│       └── features/       # main, gamingtools, editgamefiles, permissions,
│                           # logs, settings, about (+ legal + licenses screens)
├── full/                   # Full/GitHub-only: spoofdevice/ (+ root/ Xposed module,
│                           # Magisk builder, native spoof channel: native_init +
│                           # NativeSpoofChannel), SpoofRepository et al, Start.io
│                           # AdManager/AdBanner/AdsInit, SelfUpdater, scope
│                           # array, xposed_init, spoof manifest entries,
│                           # VPN controls/service/help, native bridge + jni/,
│                           # FullSettingsViewModel + updater/free-ad UI, SpoofRoutes
├── playstore/              # Play-only: AdMob AdManager/AdBanner/AdsInit,
│                           # Billing policy/repository/purchase UI + Rate App,
│                           # empty extensions, AdMob App ID manifest entry
├── testFull/               # full-only unit tests
└── testPlaystore/          # Play-only unit tests (src/test/ runs on both)
```

Variant seams are same-FQN pairs (`VariantCapabilities`, `AdsInit`,
`AdManager`, `AdBanner`, `spoofGraph()`, `ProfileShareHandler`,
`Distribution*Settings`, `rememberAvailableUpdateName`, `DistributionQuickAction`,
`StorageAccess`, background network controls/help): `src/main` never imports `src/full`, so the Play compile
cannot see the restricted implementations at all.

Both native C implementations and `system/shell/NativeBridge.kt` are full-only.
The main Android.mk selects them only when `CATSMOKER_FULL=1`; it builds no app
native module for Play. VPN state/consent and its Hilt ViewModel are also full-only,
not fields in the shared gaming ViewModel. `StorageAccess` selects modern all-files
APIs versus an unavailable Play implementation; legacy storage stays shared.

Common settings retain appearance, analytics, notification status and APK sharing.
Full owns the entire updater state/UI/notes parser, free-ad donation gate/artwork,
graphics probe used by spoof diagnostics and the reference-only Unity target-list
parser. Play owns purchase state, billing policy/repository and store actions.
There is no Play SelfUpdater or Full RemoveAdsRepository stub. Selected resource
files own their corresponding labels/disclosures; the common license screen uses
one flavor-provided ads-component label. Spoof route constants and the dashboard
entry are Full-only; the shared navigation extension is intentionally empty on Play.

## Dependency direction

Higher-level feature screens depend on the `system` and `shared` layers,
not the other way around:

```
features/*  ──►  system/*  ──►  shared/*
            └──────────┬──────────┘
                       ▼
              Android framework + 3rd-party SDKs
```

Pure-Kotlin logic (parsers, template builders, models) has no Android
imports and is unit-tested under `app/src/test` (both variants) plus
`app/src/testFull` (Full-only: spoof/Xposed/Magisk).

## Key components

### `ShellRunner` (`system/shell/ShellRunner.kt`)
The single choke point for privileged work. It routes a command through
whatever channel is available — `su` (libsu), the Shizuku binder, or a
plain `/system/bin/sh` — and caches which channel works. Everything else
calls `exec`/`execSafe` rather than spawning processes itself.

### `CatsmokerApp` + `MainActivity`
Application entry point and Compose host. A Hilt module provides
`ShellRunner`, the engines and repositories used across screens.

### Engines
- `MetricsEngine` — telemetry/overlay data (see `METRICS_ENGINE.md`).
- `GamingEngine` — Gaming Mode + booster services (see `GAMING_MODE.md`).
- `GamingModeJobRunner` — the app-scoped job that activates/reverts Gaming Mode. A screen never
  launches that toggle itself: the write takes ~90 s and must survive the activity closing
  (see `GAMING_MODE.md`, "Who runs a toggle").

### Overlay & background services
Several long-running `Service` implementations live under
`gamingtools/tools/` (crosshair, performance overlay, auto force-stop,
app booster). The local `VpnFirewallService` is full-only following this app's
Play rejection (the general policy has eligible categories/exceptions). Each reads/writes shared prefs and StateFlows
so the Compose UI and the overlay stay in sync. Both visible overlays
reposition through a move mode — the window is `FLAG_NOT_TOUCHABLE` until the
user asks to drag it, so taps otherwise reach the game; the crosshair stores an
offset from screen centre, the performance readout absolute top-left pixels
(`PerformanceOverlayPositionStore`), each clamped against the live display on
read. Services broadcast their own start/stop/mode changes and the ViewModel
follows those reports rather than the request that caused them.

The crosshair picker bundles only `scope7.png`; its library reads a numeric PNG catalog from
`catsmoker/catsmoker-app` on GitHub and stores validated downloads under the app's private
`files/crosshairs/` directory. The library needs an internet connection to browse/download, but
using an already downloaded or bundled crosshair needs only the overlay permission. Catalog and
image files must be published to the repository's `main` branch before the online library is
available to users.

`gamingtools/tools/glyph/` is the one exception to that pattern: `GlyphController`
binds Nothing's GDK proxy only to *display* something, mirrors
`GamingEngine.state` onto the LEDs, writes nothing, and no-ops on every other
phone (see `GAMING_MODE.md`).

## Key data flows

- **Privileged work**: UI → ViewModel → `ShellRunner.execSafe*` → (root |
  Shizuku `IFileService` AIDL | plain shell); settings writes are verified by
  reading the value back — for silent commands the exit code is the only
  signal.
- **Spoof publish**: store → `renderConfig` (→ `ProfileRenderer`; keys owned by
  `disabledFields` ids are omitted; the per-app skip lists ride in the global
  block) → exported provider +
  `Settings.Global` base64 + legacy keys → `ACTION_CONFIG_CHANGED` broadcast;
  the Magisk ZIP carries model keys only. Targets read provider → Global →
  legacy (see `SPOOF_DEVICE.md`). Inside a target process the same reload also
  fills the native tables (`NativeSpoofChannel` → `catsmoker_spoof.c`) that
  answer the process's own native property and GL reads.
- **Gaming Mode**: snapshot device state → apply levers → report measured
  facts → restore from the snapshot (null = don't touch; see `GAMING_MODE.md`).
- **Game files**: back up the device's bytes → push → read-back verify
  (`md5sum`, never `cat` for bytes; see `FILE_ENGINEERING.md`).
- **Scheduled dexopt**: `DexoptSweepScheduler` unique periodic work (`UPDATE`)
  → ordinary `DexoptSweepWorker` → `GamingEngine.runArtOptimization`. Cleaner
  scheduling is ordinary work too; progress/Stop notifications do not elevate
  either worker to a foreground service. System time limits and deferral apply.
- **Privacy choices**: `GamingPrivacyGate` records separate, versioned, optional
  inventory/foreground-usage/notification-dismissal choices before feature
  composition. Readers check `GamingAccessConsent`; retained game-list state is
  cleared on withdrawal and watchers stop on their next cycle. Settings can review
  or withdraw choices. Android permissions remain a separate prerequisite.
- **Analytics**: automatic `FirebaseInitProvider` startup is removed. Both flavors
  use `AnalyticsConsent` to initialize Firebase only after a current explicit
  Settings opt-in; a legacy default-on flag is not consent. Advertising consent
  is independent and Analytics advertising consent types remain denied.
- **Play advertising**: the selected `DistributionPrivacyGate` gathers UMP consent
  after onboarding. `AdConsentManager` gates SDK initialization and banners on a
  successful current update/form result, `canRequestAds`, initialization and
  ad-free entitlement. Required privacy options appear in Play settings; Full
  has no UMP implementation. Billing acknowledgement is additionally reconciled
  by ordinary, network-constrained unique work using fresh Play queries, not
  purchase tokens stored in work input.
- **Diagnostics**: own-PID logcat → in-memory buffer (`AppLogCollector`) → the
  crash handler writes `filesDir/crash_reports` (newest five) → the Logs screen's
  Export action builds a ZIP in `cacheDir/shared` and shares it through the
  FileProvider (see `LogsViewModel` / `BugReportExporter`).
- **Share app**: the Settings card copies the installed
  `applicationInfo.sourceDir` into `cacheDir/shared_apk` (the previous copy is
  deleted first, so one APK is ever cached) → FileProvider `content://` URI →
  `ACTION_SEND` chooser (see `AppApkSharer`). An App Bundle / per-ABI install
  shares its base APK only and tells the user it may need the splits beside it.

## Capability detection (pattern, not a registry)

Features probe their own prerequisites instead of assuming a uniform device:
`OemPackageResolver` (OEM/vendor keys), `DisplayMetricsProvider` /
`DisplayRefreshRateProvider` (panel facts, `null` when unreadable),
`ShellRunner` strategy caching (cheapest working channel wins), Qualcomm levers
gated on prop existence + `getprop` read-back, `RefreshRateController` and
`FixedPerformanceModeCard` checking platform support before exposing switches.
New device- or vendor-specific capabilities follow the same shape: detect,
expose when supported, hide when not. There is no central registry — do not
invent one without a task asking for it.

## Concurrency model

- Every engine exposes `MutableStateFlow`/`StateFlow` so Compose can
  collect it, and keeps a `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- Shell work is always launched on a background dispatcher and funneled
  through `ShellRunner`.
- Long-running sweeps (e.g. ART dexopt) guard against re-entrancy with
  `AtomicBoolean` flags and check a cancellation flag between items, so a
  stop always lands cleanly.
- Shared prefs are the persistence layer for "is Gaming Mode active",
  user game library, spoof profiles, etc. State is restored on restart.

## Persistence

- `SharedPreferences` — feature state, user game library, spoof profile
  assignments, Gaming Mode snapshots.
- Game files — read/written through SAF, Shizuku or manual export.
- Spoof profiles — JSON (Gson) with device presets.
- Crash records — plain text under `filesDir/crash_reports`, five newest kept;
  exported diagnostics ZIPs live under `cacheDir/shared` and are pruned to three.

## See also
- [`GAMING_MODE.md`](GAMING_MODE.md)
- [`METRICS_ENGINE.md`](METRICS_ENGINE.md)
- [`SPOOF_DEVICE.md`](SPOOF_DEVICE.md)
- [`SECURITY.md`](SECURITY.md)
- [`BUILD.md`](BUILD.md)
- [`CODING_STYLE.md`](CODING_STYLE.md)
