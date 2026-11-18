# CatSmoker application audit — 2026-11-15

## Outcome and evidence limits

The two requested presentation changes are implemented without redesigning other
screens:

- Automation is a compact icon/text action in the **existing Gaming Tools
  header**, above Your Games, with a minimum 48-dp touch target. Its existing
  authorization/setup sheet remains intact.
- Experimental Cache Preloading is an **inline expandable tool card near the
  bottom**, after Cleaning. Global shows the select-a-game explanation; a game
  exposes the existing settings, permissions, progress, cancellation and results.

This is a source/targeted-test audit plus safe runtime sampling, **not evidence
that every control works**. Privileged writes, live game configuration deployment,
purchase flows, device reboot, hooks and all OEM combinations were not exercised.
No new dependency, permission, profile system, FPS cap, thermal bypass, analytics
policy change or monetization change was introduced by this audit.

## Working-tree review

At entry there were **86 changed paths: 56 modified tracked files and 30
untracked files**. All were unstaged. There were no staged changes, indexed
additions, deletions or renames. Earlier analytics/onboarding, File Engineering
and Gaming Tools work was preserved rather than attributed to this request.

The file-by-file review ledger below records purpose, owning feature and evidence.
“Reviewed” means source/diff/caller inspection, not device validation. The audit
adds this report; final status is recorded separately after checks.

Evidence codes:

- **A**: initial changed-feature batch, 25 classes / 165 tests in each flavor.
- **B**: functional batch, Full 47 classes / 333 tests; Play 45 / 268.
- **D**: actual POCO workflow observations described below.
- **C**: additional locale/native/telemetry/support batch, Full 12 classes /
  90 tests; Play 8 / 48, zero failures/errors/skips.
- **S**: source/diff/caller inspection only; remaining runtime limitations apply.

Tests include both pure behavior tests and source-layout guards. A guard is not
an integration test. B overlaps A in two classes; counts must not be added as
distinct coverage. The test batches were explicitly filtered **per variant task**,
not a full-suite run.

Both Full and Play debug APK assemblies passed. The final Full ARM64 APK was
installed with existing data retained. Play was built/tested but not installed
over the user's Full installation. The Play source/API/resource boundary checker
passed, which is not artifact/upload certification. `git diff --check` passed
apart from Git's existing CRLF-conversion warning for `docs/DECISIONS.md`.
Gradle reports deprecated behavior for a future Gradle 10 migration; this audit
does not change Gradle/dependencies. Native compilation/tests are not real-device
coverage of every ABI or a 16-KB-page-device certification.

### Final working-tree status

**56 modified tracked files, 31 untracked files (87 total), all unstaged.**
No staged/deleted/renamed paths; no commit, push, merge or publication.
The initial 86 paths are listed individually below; the additional untracked
path is `docs/FINAL_APPLICATION_AUDIT_2026-11-15.md` itself.
No legitimate pre-existing path was discarded.

### Build, privacy and onboarding

| Changed path | Purpose, conflicts checked and validation |
| --- | --- |
| `app/build.gradle.kts` | Earlier analytics-default comment only; no dependency change. Reviewed against startup behavior, A/S. |
| `app/src/main/AndroidManifest.xml` | Explicit automation components; exported entry authentication and private services reviewed, both APKs assembled, S/B. |
| `app/src/main/java/com/catsmoker/app/application/di/EngineModule.kt` | Inject shared desired settings/passive observation into engine; both Hilt variants built, A/B. |
| `app/src/main/java/com/catsmoker/app/application/privacy/AnalyticsConsent.kt` | Earlier default-on analytics with stored opt-out retained; advertising storage separate, A; live SDK traffic not measured. |
| `app/src/main/java/com/catsmoker/app/application/privacy/PrivacyPolicy.kt` | Earlier analytics allowance semantics; sensitive-access consent remains separate/versioned, A. |
| `app/src/main/java/com/catsmoker/app/screens/permissions/AgreementStepScreen.kt` | Earlier responsive terms layout/default analytics choice; A/source review, fresh onboarding and large-font device layout unverified. |
| `app/src/main/java/com/catsmoker/app/screens/permissions/OnboardingDraftStore.kt` | Earlier distinct analytics draft persistence; A; no user-data reset to manufacture first-run evidence. |
| `app/src/main/java/com/catsmoker/app/screens/permissions/OnboardingState.kt` | Earlier onboarding defaults; A. |
| `app/src/main/java/com/catsmoker/app/screens/permissions/PermissionsListScreen.kt` | Earlier onboarding checklist/back behavior; A, existing-install permission screen D; no grants changed. |
| `app/src/main/java/com/catsmoker/app/system/CatsmokerApp.kt` | Earlier startup analytics synchronization comment; stored disabled value observed in Settings, A/D; no new policy modification. |

### File Engineering changes

| Changed path | Purpose, conflicts checked and validation |
| --- | --- |
| `app/src/main/java/com/catsmoker/app/screens/editgamefiles/GameSelector.kt` | Installed/unknown/missing ordering bands, existing game families preserved; A/D. |
| `app/src/main/java/com/catsmoker/app/screens/editgamefiles/model/GameType.kt` | Earlier Roblox display-label change, not a claim of supported live deployment; A/B. |
| `app/src/main/java/com/catsmoker/app/screens/editgamefiles/storage/InstallProbes.kt` | Stable install ranking; failed queries remain unknown, A. |

### Gaming context, settings and presentation

| Changed path | Purpose, conflicts checked and validation |
| --- | --- |
| `app/src/main/java/com/catsmoker/app/screens/gaming/GameLibraryOrdering.kt` | Stable recent-selection ordering, selected collapsed-slot retention including search, A/D. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/GameLibraryTile.kt` | Selection/deselection icon versus existing three-action menu, accessible action target, A/D. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/GameSettings.kt` | Versioned per-package desired values/cache options; bounded decoding, A. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/GameSettingsRepository.kt` | Explicit persisted Global, upgrade migration, independent package edits/recent history, A. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/GameSettingsStore.kt` | Single SharedPreferences-backed selection/settings owner, A/D. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/GamingContextBar.kt` | Compact Global/game label and return control, A/D; no active-session ownership. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/GamingToolsScreen.kt` | Context/grid wiring and the two requested entry placements, A/B/D. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/GamingToolsViewModel.kt` | Selection only edits context; explicit prepare/restore, package-addressed drafts, stale ANGLE reads guarded, A/B/D; privileged writes unverified. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/components/GamingOperationLabels.kt` | Friendly result labels distinguish admission from successful application, A/S. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/components/GamingToolSheet.kt` | Shared existing sheet presentation; no new settings owner, A/D. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/graphics/DeveloperOptionsControls.kt` | Device-global ownership wording/ANGLE exception; A/S, device-setting writes not exercised. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/graphics/ResolutionControls.kt` | Keep physical display global; desired game controls need game selection, A/S; no `wm` write performed. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/graphics/AngleDriverOptions.kt` | Serialize paired package-table writes and validate package/readback; A; actual driver mutation unverified. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/graphics/GameInterventions.kt` | Mode uses scale-only path, not legacy FPS-target fallback; A/B; actual OEM interpretation requires device testing. |

### Automation

| Changed path | Purpose, conflicts checked and validation |
| --- | --- |
| `app/src/main/java/com/catsmoker/app/screens/gaming/automation/AutomationAccess.kt` | Private random token, reactive enabled state, revoke before launcher cleanup, A/S; existing key preserved D. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/automation/AutomationDialog.kt` | Compact header action only in this revision; existing masked key/confirmation/integration sheet retained, A/D. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/automation/AutomationProtocol.kt` | Closed action/extra/package contract and constant-time authentication, A/D. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/automation/AutomationStatus.kt` | Process-restart status normalization, A; not proof of privileged transaction recovery. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomation.kt` | Shared serialized preparation/restoration, independent editing selection; fixed launch-only lazy-engine side effect and unrelated-cache global result, A/B; privileged success unverified. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomationActivity.kt` | Foreground lifecycle/auth recheck before launch, request-specific result, S/B; actual external launch not exercised. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomationReceiver.kt` | Authenticate before dispatch; Android decides foreground-service admission; immutable notification fallback, A/D queries/rejections; Tasker/OEM fallback unverified. |

### Experimental cache

| Changed path | Purpose, conflicts checked and validation |
| --- | --- |
| `app/src/main/java/com/catsmoker/app/screens/gaming/cache/CacheReadBudget.kt` | Aggregate byte/deadline/memory/cancellation accounting, exact-budget termination, A. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/cache/CacheUiReport.kt` | Only present matching-game outcomes; reject malformed history, A. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/cache/GameCacheControls.kt` | Existing controls moved inline to bottom card; captured SAF target and automatic settings unchanged, A/D. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/cache/GameCachePreloader.kt` | Read-only bounded regular files/owned descriptors/mappings; fixed mutex coverage and disabled-request status race, B/D; slow-provider/pressure/cancellation stress unverified. |

### Mode/session ownership and background apps

| Changed path | Purpose, conflicts checked and validation |
| --- | --- |
| `app/src/main/java/com/catsmoker/app/screens/gaming/apps/SuspendSweep.kt` | Protect automation/mapping companions, plus dynamic exclusions supplied by engine; A; no live suspension performed. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/mode/GameSessionService.kt` | Shared coordinator for monitor-owned prepare/restore, not editor selection; A/B, foreground service D; historical startup crash remains open. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/mode/GamingEngine.kt` | Capture selected-package desired inputs, journal/snapshot/owned restore, safer preparation and external-display policy; A/B/source audit; full privileged rollback unverified. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/mode/GamingEnvironment.kt` | External display and protected IME/accessibility/companion discovery; S/A; DeX/Panda not available. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/mode/GamingModeDetailsSection.kt` | Report requested scale rather than removed FPS-target operation, A. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/mode/GamingModeJobRunner.kt` | Process-owned jobs, shared optional preload and foreground-holder rollback, A/B; interruption during real writes unverified. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/mode/GamingModeObservation.kt` | Passive status avoids engine initialization on query, A/D. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/mode/GamingModeService.kt` | Foreground-first execution, token recheck, overlapping request accounting, A/S; actual restricted-background caller matrix unverified. |
| `app/src/main/java/com/catsmoker/app/screens/gaming/mode/GamingOptimizationSnapshot.kt` | Nullable backward-compatible policy/ownership/prior-mode fields, committed journal and retry ownership audited, A/B. |

### Resources — all earlier translations retained

Each file below was reviewed against the changed behavior and compiled in both
flavors. Existing legal/onboarding copies describe earlier default-on analytics
with opt-out; this audit does not change that decision. New context/automation/
cache copy uses Android resources with English fallback, **not complete new
translations**. Rendering every locale/large-font/RTL combination remains untested.

| Changed path | Purpose / evidence |
| --- | --- |
| `app/src/main/res/values-ar/strings_legal.xml` | Analytics disclosure mirror; A/S. |
| `app/src/main/res/values-ar/strings_onboarding.xml` | Analytics onboarding mirror; A/S. |
| `app/src/main/res/values-en-rGB/strings_legal.xml` | Analytics disclosure mirror; A/S. |
| `app/src/main/res/values-en-rGB/strings_onboarding.xml` | Analytics onboarding mirror; A/S. |
| `app/src/main/res/values-es/strings_legal.xml` | Analytics disclosure mirror; A/S. |
| `app/src/main/res/values-es/strings_onboarding.xml` | Analytics onboarding mirror; A/S. |
| `app/src/main/res/values-ja/strings_legal.xml` | Analytics disclosure mirror; A/S. |
| `app/src/main/res/values-ja/strings_onboarding.xml` | Analytics onboarding mirror; A/S. |
| `app/src/main/res/values-pt-rBR/strings_legal.xml` | Analytics disclosure mirror; A/S. |
| `app/src/main/res/values-pt-rBR/strings_onboarding.xml` | Analytics onboarding mirror; A/S. |
| `app/src/main/res/values-zh-rCN/strings_legal.xml` | Analytics disclosure mirror; A/S. |
| `app/src/main/res/values-zh-rCN/strings_onboarding.xml` | Analytics onboarding mirror; A/S. |
| `app/src/main/res/values/strings_game_settings.xml` | Selection, authorization and cache copy, A/D; no performance promise. |
| `app/src/main/res/values/strings_gaming.xml` | Scale/untouched lifecycle report labels, A. |
| `app/src/main/res/values/strings_gaming_context.xml` | Explicit Global, scope, integration/setup and friendly cache outcomes, A/D. |
| `app/src/main/res/values/strings_legal.xml` | Earlier analytics disclosure, A/D. |
| `app/src/main/res/values/strings_onboarding.xml` | Earlier analytics/onboarding copy, A/S. |

### Changed tests

All were inspected for assertion scope and run in A; the new backend guards also
ran in B. These are not substitutes for destructive deployment or OEM tests.

| Changed path | Covered behavior |
| --- | --- |
| `app/src/test/java/com/catsmoker/app/application/privacy/PrivacyPolicyTest.kt` | Analytics versus separately gated sensitive access. |
| `app/src/test/java/com/catsmoker/app/screens/editgamefiles/games/roblox/RobloxIntegrationTest.kt` | Correct label/blocked integration contract. |
| `app/src/test/java/com/catsmoker/app/screens/editgamefiles/storage/InstallProbesTest.kt` | Known/unknown/missing stable sorting. |
| `app/src/test/java/com/catsmoker/app/screens/gaming/AutomationProtocolTest.kt` | Auth/allowlist/passive reads/session ownership and new launch/cache lock guards. |
| `app/src/test/java/com/catsmoker/app/screens/gaming/CacheReadBudgetTest.kt` | Bounds/pressure/deadline/cancellation/companion/scale constraints. |
| `app/src/test/java/com/catsmoker/app/screens/gaming/GameLibraryGridTest.kt` | Four-column preview and unchanged three actions. |
| `app/src/test/java/com/catsmoker/app/screens/gaming/GameLibraryOrderingTest.kt` | Stable unique ordering and selected preview retention. |
| `app/src/test/java/com/catsmoker/app/screens/gaming/GameSettingsRepositoryTest.kt` | Explicit Global, migration/recreation, independent edits, recent history. |
| `app/src/test/java/com/catsmoker/app/screens/gaming/GamingModeJobOwnershipTest.kt` | Service/process-owned work instead of screen-lifetime transaction. |
| `app/src/test/java/com/catsmoker/app/screens/gaming/GamingModeUiPlacementTest.kt` | Mode details placement/current report vocabulary. |
| `app/src/test/java/com/catsmoker/app/screens/gaming/GamingToolsContextUxTest.kt` | Selection is not applying, context/sheet safety and both requested placements. |
| `app/src/test/java/com/catsmoker/app/screens/permissions/OnboardingInteractionTest.kt` | Existing earlier onboarding wiring. |
| `app/src/test/java/com/catsmoker/app/screens/permissions/OnboardingStateTest.kt` | Existing earlier onboarding defaults and draft behavior. |
| `app/src/test/java/com/catsmoker/app/screens/settings/AnalyticsConsentTest.kt` | Earlier default and retained explicit opt-out. |

### Changed documentation

| Changed path | Review |
| --- | --- |
| `docs/ARCHITECTURE.md` | Existing tree/ownership/seams, now records shared settings/automation/cache presentation owners. |
| `docs/BUILD.md` | Earlier analytics initialization/default description; no new dependency mandate. |
| `docs/COMMUNITY_GAMING_SETUP.md` | Practical examples/statuses; OEM/privileged/game-performance limits retained. |
| `docs/DECISIONS.md` | Append-only history of prior selection/automation/cache decisions; append this audit, do not rewrite history. |
| `docs/GAME_AUTOMATION.md` | Real closed intent contract, authorization, admission versus completion and foreground limitations. |
| `docs/GAMING_MODE.md` | Global/package ownership, snapshot recovery and unchanged explicit deployment distinction. |
| `docs/SECURITY.md` | Export/auth/consent/flavor boundaries reviewed; no generic external shell. |
| `docs/VMTOUCH_REVIEW.md` | Earlier static research and measured cache mechanism, not invented FPS evidence. |

## Functional coverage by actual destination and capability

Status is deliberately scoped to the stated action, not an entire screen.

| Area/control family | Classification and evidence | Remaining limitation |
| --- | --- | --- |
| Home/navigation | **Working and verified** for opening main destinations and returning; telemetry labels populated D. | UI FPS is not game FPS. GPU/CPU privileges unavailable; no accuracy benchmark. |
| Global/game selection | **Working and verified**: Global, select, deselect, switch, process restart while Global, original selected game restored. A/D. | Fresh-install behavior is automated repository evidence, not a clean-device onboarding run. |
| Library/show more/fewer | **Working and verified** for ten-game expand, select Roblox outside preview, collapse retaining it and existing menu display. A/D. | Launch/Recompile/Remove side effects were not invoked on user games. Removal behavior has automated checks only. |
| Saved settings/order | **Working through automated checks**, plus persisted selection/cache options observed D. | Recent selection history necessarily records audit selections; full reboot not tested. |
| Automation sheet | **Working and verified** for opening, masked-key presentation, regeneration confirmation and cancel. | Copy, actual revoke/regenerate, shortcuts and battery settings not invoked to preserve existing integrations/policy. |
| Broadcast/query security | **Working and verified** for authorized status and invalid-token/unexpected-extra rejection. A/B/D. | Real Tasker/MacroDroid delivery, notification denial and successful privileged execution require testing. |
| Mode preparation/restoration | **Static/automated checks only**, locked UI observed correctly without an app privilege grant. A/B/D. | No live suspend/kernel/write transaction or interrupted-owned-session/reboot recovery exercised. |
| Mode auto-monitor | **Partially working**: service can run foreground, missing usage/app privilege reflected. | Historical foreground-start timeout remains **broken and unresolved** pending reproduction. |
| Physical resolution/touch/ANGLE | **Static/automated checks only**; context/permission/readback/confirmation paths inspected. | No display change, pointer write or driver mutation performed. |
| Cache card | **Working and verified** for expand, per-game enable/budget/test/result and reset to original options. A/B/D. | Cancellation/slow provider/memory-pressure stress not triggered by short run. No FPS/loading claim. |
| App sweep/Keep policies | **Static/automated checks only**, companions and active input services protected in implementation. | No live suspension/force-stop or data-policy mutation. |
| Compilation/cleaning/schedules | **Static/automated checks only** for decisions/patterns/worker ownership. | No cache deletion, dexopt/fstrim or scheduled sweep executed. |
| Crosshair library/overlay | **Static/automated checks only** for catalog/assets/position; shared parent-VM navigation inspected. | Overlay permission absent; download, visible overlay/move/recenter/notification stop not exercised. |
| Performance overlay | **Static/automated checks only** for position/telemetry parsers; permission-gated service inspected. | No actual overlay session, external display or game frame benchmark. |
| Audio/DND/glyph/network | **Requires physical-device testing** for actual output, notification policy, hardware LEDs, DNS/Data Saver/VPN mutation. | POCO is not a compatible glyph device; no system/network changes performed. |
| Settings | **Working and verified** for entry, Light theme selection, return to original System theme, existing analytics-Off display and navigation. | Language restart, notification grants, updates/download/install/share and ad toggle not invoked. |
| Permissions | **Working and verified** for existing-install page and current grant states. | No root/Shizuku/overlay/storage/usage grant changed; granted-case behavior unverified. |
| Onboarding | **Working through static/automated checks only** for draft/default/back/layout contracts. | No data wipe, fresh install, RTL/large-font/short-screen run. |
| About/Privacy/Terms/Licenses/Donate | **Working and verified** for navigation/content/return only. | External links, donation/payment/copy actions not exercised. |
| Logs | **Working and verified** for opening/live app log display. B/D. | Export/clear/share not invoked; no unrelated system error attributed to CatSmoker. |
| Full spoof hub/profiles/editor/apps/diagnostics | **Working and verified** for safe navigation/content; engine/module tests B. | No identity saved, assignment changed, module installed or hook activated. Safe-mode actions are source/test-only. |
| Full updater/ads; Play ads/billing | **Static/automated checks only** using flavor-specific policy tests and both assemblies. | No live ad, purchase, acknowledgement, update or Play-device installation test. |

There are 12 shared navigation destinations in `AppNavHost`, plus the six
Full-only spoof destinations. Embedded File Engineering and onboarding content
were inventoried separately rather than mistaken for independent routes.
A read-only inventory found **598 control/callback/route matches in 68 Kotlin
files**. This regex-assisted inventory has false positives and can miss custom
controls; it is not a claim of 598 successful interactions.

## File Engineering — every registered integration

Registry completeness, supported channels, validation and backup/readback wiring
were inspected and tested in B. Package detection/unknown handling and selection
sorting were tested in A. No existing game file was deployed, restored or deleted.

| Integration | Evidence and readiness limit |
| --- | --- |
| PUBG Global/KRJP/VN/BGMI/Rekoo/Flash | Shared PUBG implementation, patch/verification/UserCustom tests; only Global installed. Current save-layout/game consumption needs real deployment validation for each version. |
| Genshin | Missing-game message, correct package, Re-check and Show anyway exercised D. Legacy template is explicitly unverified; not a demonstrated FPS unlock. Play Store button inspected, not launched. |
| New State | Existing private INI/root contract and transform tests. Installed, but root deployment not tested. |
| Honkai: Star Rail | Installed detection and explicit root-required explanation exercised D; parser/settings tests B. Shizuku is not falsely offered for another app's private data. No save write. |
| Wuthering Waves | Merge, selection and generation tests B; declared root/Shizuku/document channels inspected. No current-game cvar consumption/deployment proof. |
| GRID Autosport | Existing-key parser tests B; missing-key creation is not implied. No installation/runtime write available. |
| Roblox | Registry remains blocked/unverified; label change does not create a live supported editor. Integration/config tests A/B; no FastFlag consumption claim. |
| GRID Legends, Alien: Isolation, Saints Row, Total War: NAPOLEON, Hitman: Blood Money, Lara Croft: Guardian of Light, Lara Croft: Temple of Osiris, Railroads, Tomb Raider, Hitman: Absolution, Company of Heroes, Total War: EMPIRE, Tropico, ROME, MEDIEVAL II, ROME: Barbarian Invasion, ROME: Alexander, XCOM 2, Minecraft, Terraria, Civilization VI | All 21 existing scoped definitions were included in registry/editor tests. Preliminary readiness remains explicit. Existing declared files/schema/channels only; no generic editor was added as a substitute. None was device deployment-validated. |

`ConfigWriteTransaction` tests cover pinned access channel, required backup,
concurrent-change checks, non-cancellable write/readback and restore safety.
They cannot prove a live game's acceptance or eliminate anti-cheat/account risk.

## Confirmed bugs corrected

| Issue | Cause and smallest correction | Validation / limit |
| --- | --- | --- |
| Launch-only could initialize privileged engine | Common result enrichment touched lazy engine even for `LAUNCH_GAME`; initialization can reapply standalone fixed-performance preference. Return launch result before engine enrichment. | B regression guard; actual launch not performed on user game. |
| Global prepare could inherit another game's cache failure | Global preparation used latest game-cache result when deciding partial success. Ignore cache outcome when no package was requested. | B regression guard; privileged global prepare not run. |
| Disabled preload could overwrite active progress | Disabled path published before taking the preloader lock. Move initialization/disabled publication inside owned lock. | B regression guard and successful bounded read D; concurrency stress still needed. |
| Preload setup exception could retain mutex | Initial memory-information setup ran before owning `try/finally`. Enclose all locked setup/work and release in `finally`. | B regression guard and successful subsequent read D; platform fault injection not performed. |

## Logcat, crash and ANR findings

Device: **POCO X3 NFC / M2007J20CG, Android 15 API 35, ARM64**, serial omitted
from public diagnostics. ADB root was available; **CatSmoker root was not
authorized and Shizuku was not running**. Installed games included PUBG Global,
New State, HSR, WuWa and Roblox. No DeX, Arena Breakout, Tasker, MacroDroid or
Panda Mouse setup was available.

The retained crash buffer and `dumpsys activity exit-info` show an **older**
`ForegroundServiceDidNotStartInTimeException` for `GameSessionService` at
20:19:49, before this audit's 20:33:34 start. Caller trace is service start from
`GamingToolsViewModel` initialization. It was **not reproduced or fixed**.
Current foreground startup and navigation worked, but that is insufficient
to explain the historical deadline miss. No unsupported claim of Hilt/root I/O
as the proven cause is made. Follow-up needs repeated controlled service starts
and an on-device startup trace under the failing conditions.

No new CatSmoker fatal exception or ANR was found in the captured audit window.
This is bounded log evidence, not a guarantee of absence. Explicit force-stop
and package-update exits correspond to this audit, not crashes.
GPU sysfs SELinux read denials recur on this unprivileged app UID; the reader
reports privilege-denied/unavailable instead of inventing GPU values. No policy
was weakened to suppress those denials. Skipped frames alone are not an ANR.

Static ANR candidates were traced, not graded from keywords alone:
refresh-rate shell fallback is called on IO; direct `readMinHz` does not execute
a shell; schedule future waits use IO; shell waits and logcat sleeps are worker
paths. Provider `runBlocking` is a Binder-thread path needing separate IPC
stress, not demonstrated main-thread blocking. No confirmed main-thread ANR
defect was established by this review.

## Security and restoration review

- Exported automation Activity/Receiver validate token, closed action/extra set,
  package and consent. Unknown extras and invalid token were rejected on-device.
- No generic root-shell API, arbitrary path/command intent or Full-only
  capability is exposed through shared automation.
- Token stays in private, non-backed-up app storage; constant-time comparison,
  sensitive clipboard flag and immutable notification PendingIntent inspected.
  Existing token/integrations were not invalidated.
- Full exported spoof provider restricts identity resolution to caller/shared-UID
  assignments; it is read-only and exposes its fixed pipe resource. Policy tests
  passed; hostile-device IPC/load testing was not performed.
- Shizuku provider permission, private FileProvider, nonexported mode/overlay
  services and notification-listener binding permission were inspected.
- Snapshot/journal persistence precedes mode writes; failed owned restoration
  retains retry records. This is automated/source evidence, not a successful
  live suspend/crash/reboot/restore drill.
- No unknown ULTROID executable, PacketSDK/proxy, thermal bypass, dependency
  download or privileged device modification was introduced.

## Reproduction artifacts and next tests

Local evidence is under
`C:\Users\Lenovo\AppData\Local\Temp\opencode\`:
`catsmoker-control-inventory.csv`, `catsmoker-uncommitted-files.txt`,
`catsmoker-audit-*.xml`, `catsmoker-audit-automation-validation.txt`,
`catsmoker-audit-existing-crash-buffer.txt`,
`catsmoker-audit-window-logcat.txt`, `catsmoker-audit-current-process-logcat.txt`,
`catsmoker-audit-exit-info.txt`, and the `final-app-audit-*.log` Gradle logs.
These local runtime files may contain device/log data and are not published.

Prioritized remaining tests: reproduce historical monitor FGS deadline failure;
authorized prepare/restore with exact app/system readbacks and interruption;
actual Tasker/MacroDroid/shortcut/fallback scenarios; first-run/RTL/font-scale;
each game's consenting backed-up deployment; live overlays and grants; Play
billing/consent and updater workflows; DeX/Panda/Arena; then comparable loading/
frame-time/RAM/pressure measurements. Do not turn source-test success into
device-readiness or guaranteed performance claims.
