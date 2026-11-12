# Security Model

How CatSmoker handles the privileges it holds, the security-relevant surface
area, and an honest assessment of the risk. Read this before contributing
code that touches privileged paths.

## Overview

CatSmoker is an app that, by design, holds **powerful privileges** (root,
Shizuku, or a mix) so it can tune system settings and game processes. This
document describes the boundaries around that power and the invariants the
codebase is written around.

The app's stated privacy position: **no unnecessary data collection; all
modifications are performed locally** (see README). Intermittent network
behaviour is limited to ads (Start.io on Full, AdMob on Play — see below),
Firebase Analytics (shared, same project), DNS optimisation for the
game, and a ping to `8.8.8.8` for the latency metric.

## Privilege model

There are four execution channels, in ascending order of privilege:

| Channel      | Privilege                   | Used for                              |
| ------------ | --------------------------- | ------------------------------------- |
| Plain shell  | none — app sandbox          | fallback command execution            |
| SAF / manual | user-granted file access    | file engineering when no root/Shizuku |
| Shizuku      | `shell`/elevated UID binder | non-root privileged commands          |
| Root (libsu) | UID 0                       | everything, preferred when present    |

`ShellRunner` (`system/shell/ShellRunner.kt`) is the **single choke point**.
It picks the best available channel, caches which one works, and routes every
privileged command through itself. Rocket-prone ad-hoc process spawning is
avoided by design — the one documented exception (the ART dexopt sweep,
which must own the `Process` to cancel it) is explained at its call site.

### Root outranks Shizuku

When root is available, Shizuku is neither bound nor used (`ShellRunner`)
unless root is missing. This avoids holding two privileged channels open and
reduces the surface — the app won't spawn a second privileged process when it
doesn't need to.

## The privilege boundary (ShellRunner)

- **Central routing.** All `exec`/`execSafe(`*`...`*`)`/`execResult` go
  through `ShellRunner`. Nothing else forks processes for privileged work.
- **Argument quoting.** `joinArgs` single-quotes any argument containing
  whitespace or a shell metacharacter and escapes embedded quotes, so an
  untrusted string cannot break out of an argument into a shell command. Use
  `execSafe`/`execSafeResult` when passing dynamically-derived values.
- **Fallback discipline.** A command is only retried at a *lower* privilege
  level when the higher one genuinely failed; an already-failed root call is
  not re-attempted with weaker privileges that cannot do better.

## The Shizuku user service (`FileService`)

`FileService` runs inside the Shizuku helper process (shell UID) and is what
actually executes `sh -c` commands on behalf of `ShellRunner`. It also binds
to `/proc/stat` and thermal sysfs for telemetry. Notes:

- The user service is launched by Shizuku, **not** by this app — Shizuku gates
  who may bind it.
- The service's AIDL interface covers command execution (`executeCommand`,
  `executeAndGetOutput`, `executeForResult`), telemetry reads
  (`readSysfsThermal`, `readProcStat`), and a file bridge (`readFile`,
  `writeFile`), plus `destroy`.
- The helper is versioned via `BuildConfig.VERSION_CODE` (currently 8 — the
  bump is what forced Shizuku to restart the daemon when `readFile`/`writeFile`
  changed the AIDL contract) so Shizuku restarts it when the AIDL contract
  changes, instead of reusing a stale helper whose interface no longer matches.

## Content provider boundary (`SpoofConfigProvider`)

`SpoofConfigProvider` is **intentionally exported** (it cannot be otherwise)
because the LSPosed hooks read spoof profiles from inside the target game's
process, under that game's own UID. The entire security of this provider rests
on one invariant enforced in `resolvePackageName`:

> **A caller only ever receives its own profile.**

- If the caller is CatSmoker itself, it may resolve arbitrary packages (used
  by the UI's preview feature).
- Any other caller is identified by `Binder.getCallingUid()`. It is only
  served the profile for **its own package name** (or, for shared-UID
  siblings, a sibling it genuinely shares a UID with).
- An arbitrary package name supplied by another app is rejected, so an
  attacker cannot walk package names to enumerate the user's spoof setup.

This is a deliberate design trade-off: exported-but-scope-guarded content
must stay correct, and a regression here would leak spoof assignments.

`call("get_module_apk")` is the one other exported operation: the Xposed module
asks for this app's own APK path to locate the native bridge. The preferred load
route is the module class loader (the framework's `findLibrary` answers the
packaged zip path); the extraction fallback and its APK lookup exist for a
framework whose module loader cannot, because LSPosed/Vector may load module
code from memory, leaving no dex path in the classloader. It returns only
`ApplicationInfo.sourceDir` — no profile, no assignment, no user data — so it
is not routed through `resolvePackageName`.

## Ads: Start.io (Full) / AdMob (Play)

Each distribution flavor integrates exactly one ad SDK, isolated in its own
source set — neither SDK is packaged into the other flavor:

- **Full** uses the Start.io SDK (`src/full/…/integrations/ads/AdManager.kt`,
  `com.startapp:inapp-sdk` via `fullImplementation`). SDK auto-initialization
  is **explicitly disabled** in the manifest (`tools:node="remove"` on
  `StartAppInitProvider`); integration is manual. The SDK id can be
  overridden via `STARTIO_APP_ID` in `local.properties`.
- **Play Store** uses AdMob (`src/playstore/…/integrations/ads/AdManager.kt`,
  `com.google.android.gms:play-services-ads` via `playstoreImplementation`).
  IDs come from `ADMOB_APP_ID` / `ADMOB_BANNER_ID` /
  `ADMOB_INTERSTITIAL_ID` in `local.properties`, defaulting to Google's
  sample test ids. The App ID enters the manifest via placeholder. Ads follow
  only the verified `remove_ads` one-time product
  (`src/playstore/…/integrations/ads/RemoveAdsRepository.kt`,
  `com.android.billingclient:billing-ktx` via `playstoreImplementation`,
  acknowledged non-consumable, never consumed); the `ads_enabled` preference
  is ignored there, so no local toggle can disable ads on that variant.
- Shared code calls the selected `AdManager` API / `AdBanner()` slot and
  distribution settings extension. `RemoveAdsRepository`, entitlement policy and
  purchase UI exist only in Play; there is no Full billing repository stub.
  Full's free-ad preference/gate and updater state/UI are Full-only, not shared
  implementation hidden behind capability flags.

This is a real supply-chain + data-flow consideration: any `build.gradle.kts`
change here should be reviewed like any other third-party dependency (pinned
via the version catalog, not floating). Firebase Analytics is additionally
shared by both variants (same project, explicit opt-in initialization — see
`PLAY_POLICY_REMEDIATION.md`). Play advertising now uses a separate UMP gate;
SDK integration is not a substitute for published account privacy messages or
regional runtime verification.

Onboarding analytics starts unchecked (unless a current explicit consent already
exists). Selecting the switch and continuing applies that choice without a second
popup. Legacy default-on drafts are not migrated as opt-ins. The short card keeps
the full Firebase data disclosure in its info sheet. Optional gaming access also
keeps full purpose/risk disclosures in info sheets; its checkboxes remain distinct
from Android usage-access and notification-listener grants.

## Permissions requested (AndroidManifest)

Most are expected for the feature set. A few deserve explicit justification:

- `WRITE_SECURE_SETTINGS` — **not grantable at install** (signature|privileged).
  Declared only so `adb pm grant` can enable the animation-scale / Private DNS
  cards. Root or Shizuku remain the normal paths. The code checks
  `canWriteAnimationScales()` before offering it.
- `PACKAGE_USAGE_STATS` — needs user grant via Settings; used for app/game
  presence heuristics only after the separate foreground-usage choice. It
  observes foreground non-game apps too for Silent Hunter; this is disclosed.
- `MANAGE_EXTERNAL_STORAGE` — **full build only**. Play rejected it as
  non-core (2026-10-31), so `src/playstore` does not declare it; Play file
  work goes through SAF/MediaStore, plus the ordinary legacy storage grant on
  Android 8–10. The full cleaner still uses it for its direct shared-storage
  walk.
- `RECORD_AUDIO` — for the audio-boost visualizer. `MODIFY_AUDIO_SETTINGS` is
  for the audio boost. These are breadth of capability, not covert use.
- VPN (`BIND_VPN_SERVICE`) — **full build only**. The local VPN is a
  network block that routes selected apps into a `tun` and never forwards it;
  the user consents through Android's VPN dialog. This app's Play submission
  was rejected; the policy also has eligible-use exceptions, so a blanket
  “VPN must be the core product” claim is inaccurate. Play has no `VpnFirewall`
  stub or service, only an empty network-controls extension. It keeps
  `BackgroundDataRestrictor` (Data Saver + `cmd netpolicy`, root/Shizuku).

## Separate Play policy and backup gates

Flavor exclusion is not policy approval for shared features. The fourth-pass
audit is historical; [`PLAY_POLICY_REMEDIATION.md`](PLAY_POLICY_REMEDIATION.md)
records subsequent code changes and remaining owner/runtime gates. Gameplay
changes and inter-app controls remain in Play pending review at the owner's
explicit direction, not because their eligibility has been established.

The manifest now disables backup and supplies legacy and Android 12+ rules that
exclude every app-state domain from cloud backup/device transfer. Persistent
crash reports, config backups, consent and privilege state are included in these
exclusions. Rules cannot erase existing provider backups or guarantee every OEM
migration implementation. The legal copy states that limit; no device transport
verification or provider deletion was performed.

Root refresh does not open a fresh root shell until the user has used the Grant
action; the earlier automatic onboarding request was removed. Direct battery
exemption permission/request was removed in favor of general system settings.
Discovery queries are limited to launcher/game/explicit interoperability queries
and inventory readers require the optional choice. This narrows access but does
not automatically establish Play core-function eligibility.

## Gaming Mode invariants that matter for safety

The state machine in `GamingEngine` has security-adjacent guarantees:

1. **Snapshot before change.** Activation captures the user's real state
   *before* writing anything, so deactivation restores *their* configuration.
2. **Hard allowlist.** Certain packages are never suspended or restricted:
   Shizuku, Magisk, and CatSmoker itself (`hardWhitelist`), plus the active
   game, launchers, and system-critical packages. Guarding this list prevents
   the app (or a bug in it) from bricking the session. The user's explicit
   **Apps kept running** list can widen the exemptions at runtime (it is
   subtracted from the whole sweep), but it is never consulted to remove an
   entry from these code-owned lists.
3. **Reversible restrictions.** Background-data restriction is only turned
   *off* if the app **itself** engaged it — it never disables a restriction
   the user set on their own.

## Risk assessment (honest)

| Risk                                                             | Severity            | Mitigation                                                                                                               |
| ---------------------------------------------------------------- | ------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Privilege escalation bug in `ShellRunner`'s command construction | High (root context) | Single choke point; `joinArgs` quoting; code review on the quoting path                                                  |
| Exported provider leaks spoof config to other apps               | High                | `resolvePackageName` UID scoping; documented invariant                                                                   |
| Third-party SDK (Start.io / AdMob) supply chain / data flow     | Medium              | Pinned version; manual init; flavor-isolated (Start.io Full-only, AdMob Play-only); review dependency changes            |
| Malicious app tricking the local VPN or overlays (full only)       | Medium              | All overlay/FGS services are `exported="false"`; `BIND_VPN_SERVICE` enforced by the system; user must consent to the VPN |
| Regression in Gaming Mode allowlist bricks session               | Medium              | `hardWhitelist` + system-critical exclusion keep essential packages alive; snapshot revert                               |
| Shell injection from an untrusted profile/game value             | High (root context) | `joinArgs` quoting on all dynamic args; treat any value sourced from files or user input as untrusted                    |

## Hard rules for contributors

1. **Never bypass `ShellRunner`** for privileged work. If you think you need
   a raw `Process`, you must document the blocker, as the dexopt code does.
2. **Quote every dynamic argument.** Use `execSafe`/`execSafeResult` unless
   the argument is a known-safe literal.
3. **Preserve the provider's UID scoping.** Do not relax `resolvePackageName`;
   an exported provider that leaks is a full CVE-class regression.
4. **Never add to the allowlist.** Removing packages from suspension is the
   safe direction. Do not silently suspend system-critical or whitelisted apps.
5. **Keep the snapshot/revert pairing.** Anything set "on" needs a symmetric
   "off" that restores prior state.
6. **Pin dependencies.** New/upgraded libraries go through the version
   catalog and get reviewed. No floating versions.
7. **No covert data collection.** Anything network-egress beyond ads, DNS
   features, and the metrics ping must be reviewed and documented.

## Related documents
- [ARCHITECTURE.md](ARCHITECTURE.md) — where these components live.
- [SPOOF_DEVICE.md](SPOOF_DEVICE.md) — the spoofing channels and safe mode.
- [CODING_STYLE.md](CODING_STYLE.md) — the "honesty rules" including the
  verified-by-read-back convention.
