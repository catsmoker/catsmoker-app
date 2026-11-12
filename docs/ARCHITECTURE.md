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

## Naming and grouping rules

Packages name what the code is, not which pass introduced it:

- **`screens/`** — a navigation destination (or a group of sibling destinations)
  owns the screen, its ViewModel, its state and the logic only that screen uses.
  Opening one destination from another never moves its code under the opener.
- **A capability owns its whole vertical slice.** A feature's UI, state, service,
  catalog, assets, controls and stores live in one folder when they exist only for
  that capability. Crosshair Library is the reference case: screen, ViewModel,
  catalog repository, asset resolution, position store, overlay service and its
  Compose controls share `screens/gaming/crosshair/`.
- **Collections hold one kind of entry.** `screens/editgamefiles/games/` contains
  only games — one folder per supported game. Shared storage, models, the registry
  and the delivery pipeline live beside it, never inside it.
- **Reusable presentation, telemetry, platform access and app wiring have separate
  named homes** (`ui/`, `telemetry/`, `platform/`, `application/`, `integrations/`)
  instead of a mixed `shared/` or `system/` bucket.
- Kotlin packages and filenames are lowercase; files are named after their primary
  responsibility, and oversized mixed files are split by responsibility.

## Module layout

Single application module under `app/` with `distribution` product flavors
(`full`, `playstore`) — see [`FLAVOR_WORKFLOW.md`](FLAVOR_WORKFLOW.md). There
is no separate `core`/`feature` Gradle split and no separate Git branch;
areas are separated by Kotlin packages, variants by source set:

```
app/src/
├── main/                   # shared code — ships in BOTH variants
│   └── java/com/catsmoker/app/
│       ├── screens/        # one area per destination or destination group
│       │   ├── home/       # HomeRoute/HomeScreen + HomeViewModel
│       │   ├── permissions/# first-run steps + permissions, one destination
│       │   ├── gaming/     # Gaming Tools screen + one folder per capability
│       │   │   ├── GamingToolsScreen.kt, GamingToolsViewModel.kt,
│       │   │   │   GameInfo.kt, GameLibrarySelection.kt, GameLibraryTile.kt
│       │   │   ├── mode/          # Gaming Mode engine, session, snapshots, device levers, its UI
│       │   │   ├── compilation/   # ART dexopt: ArtCompiler, decision, history, schedule, UI
│       │   │   ├── cleaning/      # junk scan, rules, schedules, UI
│       │   │   ├── network/       # DNS + background data controls
│       │   │   ├── graphics/      # resolution, ANGLE, interventions, developer options
│       │   │   ├── apps/          # game library, auto force-stop, suspend/appops state
│       │   │   ├── crosshair/     # library screen, catalog, assets, overlay, controls
│       │   │   ├── performanceoverlay/ # readout service, position store, controls
│       │   │   ├── audio/ + glyph/ # audio boost, Nothing glyph mirror
│       │   │   └── components/    # card shells, info topics, picker, schedule labels
│       │   ├── editgamefiles/     # File Engineering screen + delivery pipeline
│       │   │   ├── games/         # games only: pubg, genshin, newstate, starrail,
│       │   │   │                  # wutheringwaves, grid
│       │   │   ├── model/         # GameType, GameConfig/GameProfile
│       │   │   └── storage/       # backups, history, install probes, push plan
│       │   ├── legal/      # Licenses, Privacy Policy, Terms + shared LegalComponents
│       │   ├── about/      # About only; linked destinations are peers
│       │   ├── donate/ ├── settings/ ├── logs/
│       ├── ui/             # reusable presentation, no screen ownership
│       │   ├── components/ # ScreenScaffold, cards, chips, dialogs, info button
│       │   └── theme/      # colours, typography, spacing, log-line palette
│       ├── telemetry/      # MetricsEngine, GPU reader, model/, parsers/
│       ├── platform/       # Android/OS access with one named responsibility each
│       │   ├── shell/      # ShellRunner
│       │   ├── display/    # the two display-fact readers
│       │   ├── device/ ├── apps/ ├── memory/ ├── thermal/ ├── notifications/
│       ├── application/    # app composition, not screens
│       │   ├── navigation/ # Routes + AppNavHost (+ variant spoofGraph())
│       │   ├── di/ ├── appearance/ ├── privacy/ ├── startup/
│       ├── integrations/   # third-party SDK seams (ads)
│       ├── format/         # byte-size formatting
│       └── logging/        # log-line parsing, reused by log UI/theme
├── full/                   # Full/GitHub-only: screens/spoofing/ (hub, profiles,
│                           # profileeditor, appassignment, safemode, diagnostics,
│                           # state/, data/, components/, hooks/ target-process
│                           # hooks, tools/ Magisk builder, games/monsterhunter/),
│                           # Start.io ad adapters, SelfUpdater, scope array,
│                           # xposed_init, spoof manifest entries, VPN
│                           # controls/service/help, native bridge + jni/,
│                           # FullSettingsViewModel + updater/free-ad UI, SpoofRoutes
├── playstore/              # Play-only: AdMob ad adapters, Billing
│                           # policy/repository/purchase UI + Rate App, empty
│                           # extensions, AdMob App ID manifest entry
├── testFull/               # full-only unit tests
└── testPlaystore/          # Play-only unit tests (src/test/ runs on both)
```

`system/` and `features/` survive only as frozen identities, never as homes for
new code:

| Frozen name | Why it cannot move |
| --- | --- |
| `system.CatsmokerApp`, `system.MainActivity` | AndroidManifest application/launcher entries |
| `system.config.SpoofConfigProvider` | exported provider authority and component name used by hooks |
| `system.shell.NativeBridge`, `features.spoofdevice.root.NativeSpoofChannel` | JNI symbol names compiled into the C bridges |
| `features.spoofdevice.root.LSPosedModule` | `xposed_init` entry point and release keep rule |
| `system.ads.BillingAcknowledgementWorker` | persisted WorkManager work name |
| `features.gamingtools.engine.GamingNotificationListener` | platform-granted notification-listener component |
| `features.gamingtools.engine.BootRecoveryWorker`, `features/gamingtools.tools.booster.DexoptSweepWorker`, `features/gamingtools.tools.cleaner.CleanerSweepWorker` | persisted WorkManager work names |
| `features.editgamefiles.service.FileService` | Shizuku user-service class name resolved by reflection, AIDL `IFileService` |

Everything else inside those two packages is gone. Ordinary services and
receivers moved with their manifest entries and call sites; no forwarding
classes or duplicate implementations were introduced.

## Variant seams

Variant seams are same-FQN pairs (`VariantCapabilities`, `AdsInit`,
`AdManager`, `AdBanner`, `spoofGraph()`, `ProfileShareHandler`,
`Distribution*Settings`, `DistributionSupportAction`, `rememberAvailableUpdateName`, `DistributionQuickAction`,
`StorageAccess`, background network controls/help): `src/main` never imports `src/full`, so the Play compile
cannot see the restricted implementations at all.

Both native C implementations and `system/shell/NativeBridge.kt` are full-only.
The main Android.mk selects them only when `CATSMOKER_FULL=1`; it builds no app
native module for Play. VPN state/consent and its Hilt ViewModel are also full-only,
not fields in the shared gaming ViewModel. `StorageAccess` selects modern all-files
APIs versus an unavailable Play implementation; legacy storage stays shared.

Common settings retain appearance, analytics, notification status and APK sharing.
Full owns the entire updater state/UI/notes parser, free-ad donation gate/artwork,
graphics probe used by spoof profile generation and the reference-only Unity target-list
parser. Play owns purchase state, billing policy/repository and store actions.
There is no Play SelfUpdater or Full RemoveAdsRepository stub. Selected resource
files own their corresponding labels/disclosures; the common license screen uses
one flavor-provided ads-component label. Spoof route constants and the dashboard
entry are Full-only; the shared navigation extension is intentionally empty on Play.

The shared support popup delegates its external action to
`application/distribution/DistributionSupportAction`: Full opens the existing GitHub
repository, Play opens its `BuildConfig.APPLICATION_ID` listing in the Play app with
an HTTPS fallback. Play Settings reuses that action. Shared `SupportLink` handles
launch failures; launch success never means a star or rating was submitted. Play
resource overlays replace both the popup body and label in every shipped language.
There is no support-ad placement: AdMob disallows the requested support appeal,
and Start.io permission for the exact no-reward placement remains unconfirmed.

## Screen ownership and deliberate exceptions

The navigation graph, not the screen that opens a destination, determines its
area. `application/navigation/AppNavHost.kt` wires the 12 shared destinations.
Full's `spoofGraph` wires six sibling areas under `screens/spoofing/`: `hub/`,
`profiles/`, `profileeditor/`, `appassignment/`, `safemode/`, and `diagnostics/`.
This is a feature-family grouping, not nesting screens under the hub implementation.
Its `state/` owns the existing shared `SpoofDeviceViewModel` and status probes;
`components/` owns the preset picker and profile import/sharing helpers;
`data/model/`, `data/repository/`, and `data/device/` own profiles, persistence,
rendering and the graphics probe. `hooks/` owns target-process hooks plus
`LSPosedConfig`; `tools/` owns Magisk generation and `UnityTargetList`.
The one game-specific hook (`hookMonsterHunterGpm`, MH Outlanders'
`com.tencent.mhadv` GPM tier override) lives in `games/monsterhunter/`; every
other hook in `hooks/` is generic.

Crosshair Library still selects through the Gaming Tools parent ViewModel.
The spoof profile list, editor, app assignment and safe-mode destinations still
use the hub's back-stack entry for their shared ViewModel. Package separation
does not change these lifetimes, route values, callbacks or back behavior.

Star Rail, Wuthering Waves and GRID are embedded File Engineering panels, not
destinations; their screens/ViewModels stay in `editgamefiles/games/<game>/`.
Onboarding steps remain within the Permissions destination. Gaming cards,
overlays, dialogs and startup UI are not invented as new routes. Log collection
stays with Logs despite its application-wide lifetime. Legal section lists remain
owned by Privacy Policy/Terms and are reused by onboarding; `screens/legal/`
also owns the rendering components those three screens share.

The retired `shared/data` and `shared/util` buckets stay retired: game-file
models live with File Engineering, gaming models/snapshots with their gaming
capability, telemetry in `telemetry/`, and platform helpers under named
`platform/` responsibilities. Gson field names, preferences/files, provider
authority and resource IDs are unchanged; release keep rules follow relocated
model/config classes.

## Supported games and where their code lives

File Engineering is the only place with a fixed game list (`GameType`). The
Gaming Tools "Your Games" grid is a launcher-style library of installed or
user-added apps — it is capability-based and has no fixed game list, so it
appears here as no per-game entry.

| Game | Selector entry | Code |
| --- | --- | --- |
| PUBG Mobile — Global (`com.tencent.ig`) | `PUBG_GLOBAL` | `games/pubg/` (shared save/profile pipeline) |
| PUBG Mobile — KR/JP (`com.pubg.krmobile`) | `PUBG_KRJP` | `games/pubg/` |
| PUBG Mobile — VN (`com.vng.pubgmobile`) | `PUBG_VN` | `games/pubg/` |
| BGMI (`com.pubg.imobile`) | `BGMI` | `games/pubg/` |
| PUBG Mobile — Rekoo (`com.rekoo.pubgm`) | `PUBG_REKOO` | `games/pubg/` |
| PUBG Mobile — Flash (`com.tencent.igfit`) | `PUBG_FLASH` | `games/pubg/` |
| Genshin Impact | `GENSHIN_IMPACT` | `games/genshin/` |
| PUBG: New State | `PUBG_NEW_STATE` | `games/newstate/` (root-only in-place INI) |
| Honkai: Star Rail | `HSR` | `games/starrail/` (embedded editor) |
| Wuthering Waves | `WUWA` | `games/wutheringwaves/` (embedded editor) |
| GRID Autosport | `GRID` | `games/grid/` (embedded editor) |

The six PUBG variants deliberately share one implementation and one
`ShadowTrackerExtra` save layout. `GameConfigRegistry.populateGameConfigs`
registers the profile-based entries; the three embedded editors register no
profile config because they never push a bundled template. `spoof_scope.xml`
lists many more packages: that is the LSPosed load scope, not editor support.

## Dependency responsibilities

Screens depend on their owned logic, `platform/` helpers and genuinely reused
`ui/`, `format/` and `logging/` code. These are ownership boundaries, not new
Gradle layers: `application/navigation` imports screen entry points,
`application/di` binds engines, and app startup starts existing
logging/telemetry. Shared UI may use platform appearance helpers. No interface
layer, module split or duplicate implementation is introduced to force an
artificial one-way package dependency.

Pure-Kotlin logic (parsers, template builders, models) has no Android
imports and is unit-tested under `app/src/test` (both variants) plus
`app/src/testFull` (Full-only: spoof/Xposed/Magisk).

## Key components

### `ShellRunner` (`platform/shell/ShellRunner.kt`)
The single choke point for privileged work. It routes a command through
whatever channel is available — `su` (libsu), the Shizuku binder, or a
plain `/system/bin/sh` — and caches which channel works. Everything else
calls `exec`/`execSafe` rather than spawning processes itself.

### `CatsmokerApp` + `MainActivity`
Application entry point and Compose host. A Hilt module provides
`ShellRunner`, the engines and repositories used across screens.

### Engines
- `MetricsEngine` (`telemetry/`) — home/overlay data (see `METRICS_ENGINE.md`).
- `GamingEngine` (`screens/gaming/mode/`) — Gaming Mode + session + device levers
  (see `GAMING_MODE.md`).
- `ArtCompiler` (`screens/gaming/compilation/`) — the ART dexopt sweep. It shares
  the engine's scope, prefs, shell and consent check, and `GamingEngine`'s
  `runArtOptimization`/`cancelArtOptimization` delegate to it, so the manual card
  and the scheduled worker keep one owned implementation.
- `GamingModeJobRunner` — the app-scoped job that activates/reverts Gaming Mode. A screen never
  launches that toggle itself: the write takes ~90 s and must survive the activity closing
  (see `GAMING_MODE.md`, "Who runs a toggle").

### Overlay & background services
Long-running services live with the capability they serve: `crosshair/`,
`performanceoverlay/`, `apps/` (auto force-stop), `compilation/` (app booster),
and `network/` (full-only VPN firewall). Each reads/writes shared prefs and StateFlows
so the Compose UI and the overlay stay in sync. Both visible overlays
reposition through a move mode — the window is `FLAG_NOT_TOUCHABLE` until the
user asks to drag it, so taps otherwise reach the game; the crosshair stores an
offset from screen centre, the performance readout absolute top-left pixels
(`PerformanceOverlayPositionStore`), each clamped against the live display on
read. Services broadcast their own start/stop/mode changes and the ViewModel
follows those reports rather than the request that caused them.

The crosshair picker bundles only `scope7.png`; `screens/gaming/crosshair` reads a numeric PNG catalog from
`catsmoker/catsmoker-app` on GitHub and stores validated downloads under the app's private
`files/crosshairs/` directory. The library needs an internet connection to browse/download, but
using an already downloaded or bundled crosshair needs only the overlay permission. Catalog and
image files must be published to the repository's `main` branch before the online library is
available to users.

`screens/gaming/glyph/` is the one exception to that pattern: `GlyphController`
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
  → ordinary `DexoptSweepWorker` → `GamingEngine.runArtOptimization` (delegating
  to `ArtCompiler`). Cleaner
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
