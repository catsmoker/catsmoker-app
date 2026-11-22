# Gaming Mode & the Gaming Engine

This document explains what **Gaming Mode** in CatSmoker actually does and
how `GamingEngine` (`screens/gaming/mode/GamingEngine.kt`) makes it
happen.

## What Gaming Mode applies

### Selected-game inputs and setting ownership

`GameSettingsStore` owns versioned, package-keyed desired render scale,
pointer speed, optional cache-preloading options and resolution-editor inputs. `GamingToolsViewModel` restores them
on selection without system writes. `GamingEngine` captures the target package's
render/pointer settings once before suspension, for manual and auto-detected
sessions alike. UI selection cannot retarget a running activation. New packages
inherit frozen legacy defaults; existing global keys are not erased. No saved
profile concept or new profile screen is involved.

#### Global and selected-game context

Gaming Tools starts in **Global** on a new install. The single authoritative
`GameSettingsRepository.selection` persists a package or the explicit `@global`
sentinel (`selection_initialized` distinguishes migration from an intentional
empty selection). Valid existing selections migrate unchanged. Discovery never
chooses the first game; only a completed inventory reconciliation can clear a
missing/removed selected package. Removal clears it immediately.

Tap an icon to select it, tap it again to return to Global, or use the compact
Global control beside the context heading. Selection only restores editor inputs
and reads ANGLE state. It does not launch, prepare, restore, preload, compile or
deploy. The heading describes editing context, not the active session.
Manual/monitor/automation preparation reads its explicit target without selecting
it in the editor; external `SELECT_GAME` remains an idempotent context command,
not the UI's tap-to-toggle gesture.

In Global, physical-resolution and other device controls remain available;
game render/pointer editing asks for a selection. With a game selected, saved
resolution fields remain **drafts for device-wide Apply**, not a per-game display
override. Device-wide controls and the mixed Developer Options/ANGLE section
carry scope explanations rather than per-game copies of global state.

`recent_packages` keeps at most 24 distinct local selection package names.
The ViewModel captures that order once for its lifetime; discovery sorts by this
snapshot, then locale-independent label and package. Taps affect a future visit,
not current icon positions. No usage collection or new Usage Access permission
is added. Existing foreground monitors retain their separate explicit access.
Android [usage statistics](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
require user-granted access, are interval-aggregated, and are not exact gameplay
measurements; they are not used for ranking.

The compact library remains four columns/two rows. When the selected game falls
outside those eight entries, it replaces the final slot without duplication.
It also remains pinned in a collapsed search preview, appending if a slot is
free; expanding preserves the search's matching entries without duplicates.
Global restores the ordinary preview. Show More uses every matching entry once;
membership still follows automatic discovery plus user additions/exclusions.

Tap a tile to select/deselect it; long press any tile to open the existing
Launch / Pre-compile / Remove popup without selecting it. The standard Compose
long-press recognizer supplies haptic feedback and does not also invoke tap.
The same package-addressed callbacks are exposed as accessibility custom actions;
Pre-compile is unavailable while that game is already compiling. No visible
overflow button is needed. Removing the selected game returns the editor to Global,
without restoring or altering an active session.

Long-press revision verification (2026-11-15): both variants compiled and passed
36 targeted tests each across game-grid wiring, selection/persistence, library
membership/order and context UX. Gesture/accessibility wiring checks are source
guards, not physical long-press, haptic or TalkBack interaction tests. No APK
installation or destructive game action was performed for this revision.

Automation is a compact action in the existing top header and retains its
scrollable setup sheet. It masks its key, confirms
replacement/revocation, explains each integration, and keeps a placeholder-only
command reference behind Advanced setup. Experimental preloading is an inline
expandable tool card near the bottom after Cleaning, unavailable in Global;
the card shows the game, opt-in switch, read permission explanation, budget,
scoped outcome/data/time, and cancellation while that game's preload runs.
Another game's last report is never displayed as this game's success.

The later [application audit](FINAL_APPLICATION_AUDIT_2026-11-15.md) records the
two entry-placement changes, bounded device observations, fixes and unresolved
historical service-start crash. It does not certify every control.

UX verification (2026-11-15): both Full and Play Store Kotlin compilation passed.
Nine targeted JVM classes passed in each flavor: **74 tests per flavor**, zero
failures/errors/skips. Pure tests cover Global/selection migration and recreation,
toggle/switch/removal, package isolation, recency/order/preview behavior, scoped
cache reports and command examples. Source guards cover read-only selection,
session/editor independence, the existing three actions, token confirmation and
cache safety wiring. The Play source/API/resource boundary check and whitespace
check passed. No assembly, installation, Compose interaction, theme/font-scale
or launcher/picker test was performed for this UX revision; those remain physical
device checks, not implied by the earlier community implementation's device runs.

| Settings/actions audited | Owner and scope |
| --- | --- |
| Render-scale intervention, pointer-speed choice | Per-package desired inputs; next Gaming Mode activation, existing snapshot/read-back/restore |
| Experimental cache touching, byte budget and selected asset documents | Per-package opt-in; shared preparation runner or explicit cache-only test; no eviction/locking |
| Resolution preset/custom width/height/DPI | Per-package draft; physical display is device-wide and still requires explicit Apply/confirmation |
| ANGLE driver | Android's persistent per-package driver table; immediate write/read-back, serialized updates; picker follows selected game |
| Gaming Mode on/off, recovery/report | One device session; original package and snapshot remain owned until restored, never stacked |
| Auto game monitor and Your Games membership | Global discovery/automation policy; not overwritten by selecting a game |
| Extra suspended apps, kept-running apps | Global safety/sweep policy; keep-running wins, not copied between games |
| Fixed performance, refresh/default frame rate, animation, finish-activity/process-limit developer tools | Existing immediate device-wide controls; not silently replayed on game selection |
| DND policy/access | Device-wide session action/permission, not a desired per-game value exposed by this screen |
| Audio boost and glyph controls | Global output/hardware controls; boost commits at slider release |
| Crosshair asset/position and performance overlay/position | Shared overlay/tool configuration, not game-file settings |
| DNS, data saver/background restriction, Full-only VPN | Global/network or session-owned controls with existing capability/privilege gates; no new Play exposure |
| Cleaner patterns, cleaner/dexopt schedules | Global maintenance policy |
| Compilation/recompile, RAM reclaim, clean, fstrim | One-shot actions, not saved per-game choices |
| File Engineering | Separate per-game file editors and explicit deployment/backup contracts; never applied by selection or automation |
| Advanced expansion, search, library expansion/menu | Presentation state only; not game tuning |

See [GAME_AUTOMATION.md](GAME_AUTOMATION.md) for opt-in external callers and
[VMTOUCH_REVIEW.md](VMTOUCH_REVIEW.md) for the independently built cache experiment,
actual ARM64 mechanism measurements and remaining performance tests.

Activation is a sequence of discrete, independently-failing steps. Each one
is **verified by reading the value back** — the report you see in the UI is
a list of measured facts, not a slogan. If a step is refused, it lands in
`GamingModeReport.unavailable` with the device's own reason rather than a
generic "failed".

In order:

1. **Capture a snapshot** of the user's real system state *before* anything
   is changed, so turning the mode off restores their configuration — not
   ours.
2. **Do not churn existing caches**: no automatic `pm trim-caches`, background
   compaction or framework repinning. Explicit maintenance tools stay separate.
3. **Suspend background apps** (`pm suspend --user 0 <pkg>`), skipping the
   game in play, the launcher, system-critical packages, and a hard allowlist
    (Shizuku, Magisk, CatSmoker itself), Tasker/MacroDroid/Panda Mouse, the active
    IME and enabled accessibility-service packages. The user's own **Apps kept running**
   list is subtracted from every sweep half, so a kept app is never frozen,
   never gets the AppOps restriction below, and is spared by RAM Reclaim's
   force-stop pass. The **extra suspended apps** list adds packages; if a
   package ever appears on both lists, `SuspendSweep.resolveTargets` lets the
   keep list win. Both lists live inside the Gaming Mode card (the same sweep's
   second input, not a separate Background Apps feature), behind one collapsed
   **Advanced** disclosure that remembers whether it is open; the freeze list
    marks a pick **frozen now** only with suspension evidence. Packages that were
    already paused, or whose prior state could not be read, are left alone.
   Every package this sweep attempts additionally gets
   `cmd appops set <pkg> RUN_IN_BACKGROUND ignore`, its prior mode recorded
   first and restored by deactivation.
4. **Focus Mode / DND** — silences notifications *only if* notification
   policy access is granted, and confirms the margin was actually reached.
5. **Hardware locks**: pin `min_refresh_rate`/`peak_refresh_rate` to the
   panel's max, lock the vendor's own display-mode switch where the OEM hides
   the panel behind one (vivo/iQOO — see "vivo/iQOO display-mode lock"), enable
    the OEM touch-response key, request fixed-performance mode, leave developer
    finish-activity/process-limit choices alone, raise the
   kernel-node levers the device declares (see "Kernel node levers"), and
   restrict metered background data.
6. **Per-game extras**: whitelist metered background data for the target; request
    platform performance game mode only if its prior mode was readable, then
    restore that exact mode. Apply saved render scale only when opted in and the
    prior overlay entry was captured; this path contains **no FPS field or
    FPS-setting fallback**. No new standby-bucket, background-level, deviceidle
    whitelist, forced-Doze or kill-all changes are made.
7. **Optional bounded cache preload**, after successful activation, through the
    shared runner. Failures are reported separately, not promoted to FPS gains.

With an active external display, phone-wide refresh, brightness and rotation
overrides are skipped. Manual physical-resolution Apply/reset is refused; use
DeX/display settings. Pointer speed defaults Off and remains explicit opt-in.
No input permission changes or overlay activation accompany preparation.

New snapshots record preparation policy/ownership and are committed before any
system write, as is the open-session journal. Refused owned restoration retains
the snapshot for retry; only clean finalization clears it. A session preserves
the independent manual fixed-performance preference, and its standalone toggle
is blocked while the session is open. Pointer Off does not acquire pointer
restoration ownership.

Legacy snapshots retain their legacy cleanup path. A preparation
of the unchanged active game is idempotent; changed inputs/targets require
restore, preventing stacked snapshots. One exit restore waits behind preparation.

## Verified-by-read-back design

`settings put` exits `0` whether or not the key took. The engine therefore
never trusts exit codes for settings; it reads the value back after writing.
Rows in `GamingModeReport` such as `lockedRefreshHz` describe read-back outcomes.
Legacy `discardActivities`/`processLimit` fields remain for compatibility but
new preparation leaves those operations unrequested. Automation's `notRequested`
list makes that distinction explicit.

> A null report field means "this didn't apply to my device" (wrong Android
> version, feature absent, already enabled by the user). It is deliberately
> distinguished from "refused".

## Fixed Performance Mode

`toggleFixedPerformanceMode` calls `cmd power set-fixed-performance-mode-enabled`.
This reaches `PowerManagerService` → the vendor power HAL's
`MODE_FIXED_PERFORMANCE`:

- It exists on **Android 11+**; older builds get a clear "phone too old" answer.
- Vendors may accept the call and ignore it, so success means *the framework
  accepted the request* — which is all any caller (including CTS) can tell.
- The point is **consistency**, not peak speed; the fixed point is often held
  slightly below the boost ceiling for thermal headroom.

## The GameManager CLI: mode and intervention are different commands

`cmd game set` takes an *intervention config* (`--fps`, `--downscale`, …); it
never takes `--mode`. The engine used to send `set --mode performance --fps
<peak>` anyway, which dies in `NumberFormatException` on Android 13 (POCO X3
NFC, 2026-10-19, found while cross-checking `reference/gamingtools/TMPAD`).
`GameCommandParser` now builds both shapes and parses both answers:

- `cmd game mode performance <pkg>` — selects the mode; `cmd game list-modes
  <pkg>` must report `current mode: performance`. A ROM that does not list
  performance logs "not offered by this ROM" instead of pretending.
- `cmd game set --fps <peak> [--downscale <factor>] <pkg>` — a retained **legacy
  helper**, not used by current preparation;
  `cmd game list-configs <pkg>` must show the `Fps`/`Scaling` that was asked
  for. Current preparation merges only `downscaleFactor` into the captured table,
  preserving existing FPS settings and other fields. It refuses ambiguous tables
  and restores the captured entry and prior mode instead of blindly resetting.

## vivo/iQOO display-mode lock

`RefreshRateController.lockVendorDisplayMode()` (rule: `VivoDisplayMode`) handles
the panels that ignore `min_refresh_rate`/`peak_refresh_rate`. Gated on
`isVivoOrIqoo()`; on any other device it answers "not applicable".

- The mode id is **never hardcoded**: the controller enumerates
  `Display.getSupportedModes()`, keeps the modes at the rate `peakHz()` already
  measured as the peak, and picks the largest panel among them (ties broken by
  the lowest id, so the choice is stable). A mode id is a display-driver fact —
  the reference's `4` means one 1080x2400@120 panel's mode, and something else
  (or nothing) elsewhere.
- Nothing is written when the panel is already in that mode; a panel with no
  mode at the measured peak writes nothing and reports the refusal instead of
  inventing an id.
- Two keys are written, both read back: `secure user_preferred_display_mode_id`
  (the enumerated id) and `global vivo_screen_refresh_rate_mode` (the vendor's
  own switch, `1`). Activation records both before writing, so deactivation puts
  the device back exactly as it was — "the key was not set" restores as
  `settings delete`, not as the reference's `-1`.

## Kernel node levers

`SnapdragonKernelNodes` holds the sysfs half of the HunterX-Reborn-II reference
payload that `SnapdragonCpuWiden` did not cover, as pure rules; `GamingEngine`
supplies presence gates, snapshot priors, read-back and restore.

Three rules apply to every family:

- **Raise-only, enable-only, never narrow.** No rule lowers a value, turns a
  boost off or removes a CPU from a set.
- **Every target comes from the device's own accounting** — a cpufreq policy's
  `scaling_available_frequencies` (its hardware max), its `related_cpus` (a
  cluster's real core count), or a ceiling the node itself declares. The
  reference's numbers (2803200 / 1766400 kHz, `input_boost_freq 0:1400000`) are
  written for a different SoC and are inexpressible by construction.
- **Ambiguity means no write.** A `hispeed_freq` raise requires a declared
  1..100 `hispeed_load` (0 reads as "disabled" or "always" depending on the
  kernel); `msm_performance/cpu_max_freq = 0` means "no cap declared", so only a
  declared ceiling is raised.

| Family | Node(s) | Rule |
| --- | --- | --- |
| schedutil | `up_rate_limit_us` | `0` when the device ships a non-zero limiter (the performance direction for a limiter on the way *up* is down) |
| schedutil | `hispeed_freq` | the policy's own hardware max when it is above the current value, gated on a declared `hispeed_load` |
| schedutil | `iowait_boost_enable` | `1` when the node declares `0` |
| msm_performance | `touchboost` | `1` when the node declares `0` |
| msm_performance | `cpu_max_freq` | each **declared** per-CPU ceiling raised to its policy max |
| core_ctl | `min_cpus` / `max_cpus` | raised toward the cluster's `related_cpus` count |
| cpu_boost | `input_boost_enabled` | `1` when the node declares `0` |
| cpu_boost | `input_boost_freq` | each CPU's boost frequency raised to its policy max, only when the device declares an `input_boost_ms` window |

Deliberately never written: `down_rate_limit_us` (a raise is an invented
constant), `pl` (a mechanism toggle, not a magnitude), `hispeed_load`,
`input_boost_ms`, `msm_performance/cpu_min_freq` (a floor with no
device-derived target), and `core_ctl`'s
`enable`/`busy_up_thres`/`busy_down_thres`/`offline_delay_ms`/`task_thres`
(hotplug heuristics with no raise direction). The GPU half of the reference
payload is out entirely: its two `governor` writes reach one node through two
symlinks (the later one is the vendor's own default) and KGSL's
`min_pwrlevel`/`max_pwrlevel` are a live thermal clamp. `SnapdragonKernelNodesTest`
pins all of this — the writable-name list fails the build if any name starts
naming a GPU, pwrlevel or thermal control, and the read-only gates are asserted
to stay out of it.

A device without the `msm_performance`, `core_ctl` or `cpu_boost` modules simply
skips those families (the lab POCO X3 has `schedutil` and KGSL only), and the
Gaming Mode report names what was written with its read-back value.

## SmartPack-derived levers

Five more families come from SmartPack Kernel Manager
(`../reference/gamingtools/SmartPack-KernelManager`; it ships no licence file,
so it is copyable). The reference exposes every knob as a free-form field; here
each family is narrowed to a raise-only rule whose target the device itself
declares, with the same presence gates, snapshot priors, read-back and restore as
the families above.

| Lever | Node(s) | Rule |
| --- | --- | --- |
| Input boost | `cpu_input_boost/enabled` | `1` when the node declares `0` |
| Input boost | `cpu_input_boost/ib_freqs` | each CPU's boost frequency raised to its policy max, only with a positive declared window (`ib_duration_ms`, `input_boost_duration`, `input_boost_duration_ms`) |
| Input boost | `dynamic_stune_boost` | raised toward the device's own static `/dev/stune/top-app/schedtune.boost`, never higher |
| Input boost | `top-app/schedtune.prefer_idle`, `schedtune.sched_boost_enabled` | `1` when the node declares `0`; the second only with a positive static top-app boost |
| CPU governor | `<policy>/scaling_governor` | `performance` when the policy lists it in `scaling_available_governors` |
| Spectrum | `persist.spectrum.profile` / `persist.vendor.spectrum.profile` | profile `3` (gaming), accepting a kernel clamp to `2` (performance); only when the support property is `1` **and the profile property already exists** |
| Storage | `/data` queue `read_ahead_kb` | raised to that queue's own `max_sectors_kb`; the device is resolved from `/proc/mounts` |
| Memory | `min_free_kbytes` | raised to 1 % of `MemTotal` |
| Memory | `extra_free_kbytes` | raised only toward the device's own `min_free_kbytes` |

Deliberately never written, with the reason:

- **Durations** (`ib_duration_ms`, `wake_boost_duration`) — a "gaming" duration
  is an invented constant; a positive declared window is only a gate.
- **The static top-app boost** — it is the device's own value and the only
  same-unit reference for the dynamic half.
- **Vendor governor names** (`turbo`, `gaming`, …) — their semantics differ per
  vendor; `performance` is the one governor with a universal contract.
- **The I/O scheduler and queue-shape toggles** (`nr_requests`, `iostats`,
  `rq_affinity`, `nomerges`, `add_random`, `rotational`) — device/workload
  specific, with no declared performance direction.
- **`swappiness` / `vfs_cache_pressure` / `dirty_*` / `watermark_scale_factor`**
  — swappiness' gaming direction is *down*, which this raise-only family cannot
  express; the rest have no device-declared target.
- **Legacy LMK `lowmemorykiller/minfree`** — modern Android runs `lmkd`, which
  owns process killing; raising the legacy table would kill background apps
  earlier without a declared target.
- **A Spectrum profile property that does not exist** — `setprop` cannot delete
  a `persist.*` property, so creating one would be a permanent,
  reboot-surviving change no revert could undo.

On the POCO X3 lab device the applicable levers are the governor (`schedutil` →
`performance`), read-ahead (128 → 512 KB, the queue's own maximum) and memory
(`min_free_kbytes` 9515 → 56915 KB; `extra_free_kbytes` 30375 is already above
the base reserve and stays). There is no `cpu_input_boost`/`cpu_boost` module
and no Spectrum property, so those rows are absent. The static boost
(`schedtune.boost=10`, `prefer_idle=1`) is already at its own values.

Order matters in both directions: activation applies Spectrum before the
governor so the immediate CPU state ends at `performance`, and deactivation
restores Spectrum, then the recorded governor, and only then the Snapdragon
priors. The reason is a kernel fact observed on that POCO: a policy's
`schedutil` directory and its tunables exist only while schedutil is the active
governor, so a rate-limit or hispeed restore attempted under `performance` is
refused and the node keeps its gaming value. Every restored node is read back;
a refused write now appears in the deactivation report ("Could not restore
&lt;node&gt;") instead of disappearing.

## Read-only evidence probes

Gaming Mode logs, without writing anything, the OEM refresh keys the device
carries (`OemRefreshKeys` via `RefreshRateController.probeOemKeys()`) and —
on Kirin/Exynos/Unisoc/MediaTek silicon — the vendor performance properties it
carries (`VendorPerformanceKeys` plus `DeviceCapabilities.isKirin/isExynos/
isUnisoc`). Both vocabularies come from counter-example references
(JordanTweaks 120FPS, BoostX); they exist so a future lever can argue from the
device's own answer instead of a copied script, and `ReferenceRejectionsTest`
keeps those scripts' mechanisms out of the source. No write path consumes
either probe today.

## ART Optimizer (UI: "App Compile Optimisation", scope: one game or all)

`runArtOptimization` recompiles installed apps with real `cmd package
compile` dexopt:

- Skips apps already in the requested filter (querying `dumpsys package
  dexopt` first), unless `force` is set.
- Excludes CatSmoker's own package so a recompile can't kill the process
  doing the compiling.
- Counts results from what the shell actually answered (compiled / skipped /
  failed), feeds a live progress bar, and supports cancellation that never
  overwrites a completed or cancelled outcome.
- Uses `ShellRunner`'s cancellable root command or Shizuku. A lost privileged
  channel never replays a compile under the normal app UID.
- Treats Android's permission denial as a blocked run and stops the sweep. The
  card shows what to try next; the full output stays in the logs.

## RAM Reclaim (UI toast prefix "RAM Reclaim:")

`manualBoostRam` measures `MemAvailable` (from `/proc/meminfo`, falling back
to `ActivityManager`) **before** trimming, then trims caches, force-stops
background targets, waits for the kernel to reclaim, and measures again. The
freed amount is the difference — reported as `null` rather than `0` when the
device won't reveal memory figures.

## Snapshot & revert

Activation persists a snapshot of every setting it touches plus the exact set
of packages it suspended. Deactivation can therefore:

- Un-suspend precisely the packages that were frozen.
- Restore each package's prior `RUN_IN_BACKGROUND` mode (a package with no
  override is stored as `default`), verified by reading the mode back; a
  refused restore stays recorded and is retried by the next deactivation.
- Restore the user's original refresh/touch/DND values.
- Only touch background-data restriction if **we** engaged it (never turn off
  a restriction the user set themselves).

## Interrupted sessions, reconciliation and Restore Apps

`is_active` is written at the **start** of activation (`beginSession()`), not at
the end: a crash or a refused step can leave suspended apps and half-applied
settings with no record otherwise. `GamingAppChanges` commits each package to
`affected_pkgs` and each prior app-op to `appops_prior` **before** its command.
If the process dies or loses the reply, the undo target is still on disk.
The confirmed-suspension count remains separate from the journal.

On startup `GamingEngine` enters `Checking`, reads the record plus the package
manager's explicit `suspended=true/false` states for user 0 (one filtered
`dumpsys package packages` call), and asks
`GamingModeSession.reconcile` what the evidence means:

- a live session keeps its record and is shown as **Interrupted** rather than
  **Active** — the settings may still be applied, so the power button must
  revert rather than start a second session;
- after a reboot the settings are already stock, but suspensions survive, so
  only the app record is pending;
- omissions from an unreadable or partial package-manager answer are **unknown**:
  only explicit evidence that a package is awake clears its entry;
- a clean record with no leftovers closes the session and returns to `Idle`.

**Restore apps** is the second, narrower action: it issues `pm unsuspend` only
for the packages in the journal, using the recorded Android caller (root or
Shell) where available. Those identities own separate suspensions. It also
restores recorded `RUN_IN_BACKGROUND` modes, without writing the settings
snapshot over values the system may have changed since a reboot. It is
hidden and refused while Gaming Mode is `Active`/`Enabling`/`Disabling`/
`Checking`; its result is reported as restored / still-suspended / app-op
counts, never as a bare success. A session whose settings still need reverting
stays Interrupted until the power button runs the full revert.

On app process start and after `BOOT_COMPLETED`, CatSmoker queues the same unique
WorkManager job when either app-change record remains. The worker
waits for startup reconciliation before restoring, so the engine cannot reject
the attempt just because it is still checking the saved session. It does not
rely on `is_active` or a readable boot count: interrupted sessions can have
their active flag cleared, and a reboot may reset settings while leaving package
suspensions behind. Failed attempts are retried a limited number of times;
remaining records stay available to the in-app recovery card. Automatic restore
uses the existing authorized root or Shizuku connection; if it is unavailable,
the records remain. Reconnect the same type of access used for the session and
use **Gaming Tools → Gaming Mode → Restore apps** to retry. Clearing CatSmoker's
data would discard its recovery record, not Android's suspensions.

For a suspension with no journal entry, open **Gaming Tools → Gaming Mode →
Advanced → Recover suspended apps**. The picker reads Android's live suspension
state in the primary profile using existing inventory consent and Shizuku or
root access. It shows unrecorded apps whose only reported suspension owners are
Shell or root. Android does not distinguish CatSmoker from another tool using
the same Shell identity, so nothing starts selected and only confirmed picks
are restored. Unidentified owners, device policy and quarantine are left alone.

Recovery rechecks the selected packages while Gaming Mode is stopped, uses the
matching owner, and verifies whether each selected app is available again.
For Shell suspensions it prefers Shizuku running as Shell; supported root
managers can also run the targeted restore as Shell after verifying the UID.
The picker does not invent missing background app-op values or write the
device settings snapshot.

## Who runs a toggle, and the auto game session

**The toggle never runs on a screen's scope.** `GamingModeJobRunner` (a `@Singleton`) owns one
`SupervisorJob` scope for the app process, and the card's power button goes through it. Activation
writes system settings for about a minute and a half — snapshot, ~100 suspends each read back,
panel/touch/kernel levers — so a phone that closes the activity mid-run (screen off, or an OEM that
finishes activities aggressively, `always_finish_activities`) used to cancel the job. The engine's
own holding service (`GamingModeService`, notification 102) starts only once the engine reports
`Active`, so at that moment nothing was keeping the process alive either. Now:

- a completed activation still starts that service, which keeps the process alive while settings
  are held and reverts them in `onTaskRemoved`;
- a **cancelled** activation is published as `Interrupted` (with the packages and app-ops the
  device actually accepted, so **Restore apps** works), and the engine rethrows
  `CancellationException` instead of reporting it as "Activation failed: Job was cancelled";
- a cancellation that arrived before `is_active` was written leaves `Idle` — there is no session to
  recover, and offering a restore with nothing to wake would be a claim, not evidence;
- a process kill is still covered by the persisted session record, exactly as before.

**`GameSessionService`** (notification 106) is the auto session behind the card's "Automatic Gaming
Mode" switch: it polls the foreground package every 3 s and starts, stops or switches Gaming Mode
for the games in the library. Ownership is the **recorded package**, not "the engine is busy":

| Engine | Recorded package | Meaning | What the monitor does |
| --- | --- | --- | --- |
| busy | the game it started | its own session | normal start/stop/switch behaviour |
| busy | none | a **manual** session (card button, notification Stop+Start) | hands off: never records over it, never reverts it |
| idle | one | the session ended some other way (the user turned it off) | drops the stale record, and **vetoes** that game |
| any | any, foreground unknown | usage access missing or the query failed | nothing |

The middle two rows are the ones that were wrong before 2026-10-28. Reading *every* busy engine as
"manual" released the record of the session the monitor had just started, so the next tick
re-labelled its own session, posted "Manual Gaming Mode active — watch released", and never reverted
it when the game was left: apps stayed frozen with nothing watching them. The stale-record row is
the other half of the same fix — without it, a user who switched Gaming Mode off by hand and on
again would have that manual session reverted on the way out of the game.

**The veto.** Dropping a stale record leaves "a library game is in the foreground and nothing is
recorded", which is exactly the state that starts a session — so the first device run of the
ownership fix switched Gaming Mode straight back on for the game the user had just turned it off
in, every 3 s. `GameSessionDecider.isUserStopped(foreground, veto)` therefore suppresses that one
game until the foreground moves elsewhere (`GameSessionManager.releaseByUser()` records the package,
`setEnabled(false)` clears it). Leaving the game lifts the veto, so going back in later starts a
session again — a return visit is a new intent, not a continuation of the suppressed one. An
unreadable foreground neither raises nor lifts the veto: a failed usage read is never evidence that
the user left the game. `GameSessionVetoWiringTest` pins the two call sites the rule needs, because
a pure rule nobody calls changes nothing.

## Glyph Interface mirror (Nothing phones)

`GlyphController` mirrors the Gaming Mode loading bar onto Nothing's Glyph
Interface through the official Glyph Developer Kit (GDK, the vendored
`app/libs/glyph-matrix-sdk-2.0.aar`). It is a **display mirror only**: it reads
`GamingEngine.state` and never writes a setting, so it cannot change what
Gaming Mode applies.

- **What the user sees.** While `Enabling(progress)` is live, the strip fills
  toward the engine's own percentage (0–100, clamped before it reaches the SDK)
  or, on matrix models, the percentage is printed on the matrix. `Active` runs
  a short blink at 100 % and then clears. `Disabling` runs the reverse: the
  lights start at 100 % and drain to 0 % over the card's own 1.5 s tween
  (`GamingModeProgress.REVERSE_DURATION_MS`, passed in so the two cannot
  drift), then `turnOff()` and a clean session close. `Interrupted` and
  `Error` clear immediately; so does leaving or closing the screen
  (`ON_STOP`/dispose), which cancels a drain in progress. A drain already
  running is allowed to finish if the engine reaches `Idle` first — a fast
  revert would otherwise cut the unwind in half.
- **Requirements.** Nothing only. Every call is gated on `Build.MANUFACTURER`/
  `BRAND` containing "Nothing" plus `SDK_INT >= 34`, and every method is a
  silent no-op elsewhere — no service binding, no permission, no crash. It
  needs no root, Shizuku or ADB.
- **Model table.** `GlyphHardware.forModel(Build.MODEL)` maps each code to the
  GDK's own constants: `A063` (Phone 1, D1), `A065`/`AIN065` (Phone 2, C1),
  `A142` (Phone 2a, C), `A142P` (Phone 2a Plus — own registration id, 2a's C
  array, no `displayProgress` branch in the SDK so the channel is simply lit
  for the run), `A059…` (Phone 3a/3a Pro, C), `A024…` (Phone 3, 25×25
  **matrix**), `A069` (Phone 4a, A), `A069P` (Phone 4a Pro, 13×13 **matrix**),
  `A009P…` (Phone 4b, A). Anything else resolves to `null`, which is what makes
  the feature absent rather than broken. `GlyphHardwareTest` pins the table.
- **Permission / key.** The manifest declares Nothing's
  `com.nothing.ketchum.permission.ENABLE` and a `NothingKey` meta-data fed by
  the `NOTHING_API_KEY` manifest placeholder (git-ignored `local.properties`;
  dummy fallback). The AAR declares `minSdkVersion 33`, so `<uses-sdk
  tools:overrideLibrary="com.nothing.thirdparty">` waives the merge for this
  app's minSdk 27 — the runtime gate keeps older devices out.
- **Toggle.** Removed from the Settings screen. The Glyph mirror is controlled
  only from the Gaming Tools screen; it is stored in `glyph_prefs`
  (`sync_gaming_mode`, default on) via `GlyphSyncStore`, and shown only where
  `GlyphController.isSupported`. Turning it off clears a live strip.
- **Lifecycle.** The service is bound only when a run starts, the session is
  closed and `unInit()` called on completion/cancel/`onCleared`, and the screen
  also drops the lights on `ON_STOP`. `unInit()` unbinds unconditionally in the
  AAR, so it is only called after a successful `init()`, always in a try/catch.
- **Revert.** Turn the toggle off, or stop using Gaming Mode; the Glyph
  Interface lights nothing else on its own. No setting, file or module is left
  behind, and nothing survives a reboot.
- **Licence caveat.** Nothing publishes no licence text with the GDK; the AAR
  is vendored byte-for-byte from
  `Nothing-Developer-Programme/Glyph-Developer-Kit` (avoid re-hosting it as our
  own work). `.gitignore` excludes `*.aar` globally and re-includes exactly
  this file.
- **Debug switch.** Dev builds whose `NothingKey` is not registered fall back to
  `adb shell settings put global nt_glyph_interface_debug_enable 1` (the
  `NothingKey` meta-data may be the literal `test` while debugging; the switch
  self-clears after 48 h). The GDK refuses registration otherwise and the
  controller tears the binding down instead of pretending a session exists.

## Engineering contract (adding a lever)

`GamingOptimizationSnapshot` is captured *before* anything is written and
persisted as JSON (`last_snapshot` in `gaming_engine_prefs`) so a restore
survives process death. Every field is either the value the device actually
held, or `null` meaning "could not read it — do not touch it on the way back".
Nothing is an assumed default; restoring one silently overwrites a preference
the user set on purpose. Copy these cases when adding a field:

- `activityManagerConstants` stores the **whole** `global
  activity_manager_constants` CSV, so the ROM's other constants stay alone.
- `backgroundDataRestrictedBefore` / `uidWhitelistedBefore` record a
  restriction that was already engaged, so deactivation leaves the user's own
  switch alone.
- `originalInterruptionFilter` exists because deactivation once hard-coded
  `INTERRUPTION_FILTER_ALL`, cancelling the user's own DND.
- `gameOverlay` reuses `SettingValue` so three states stay distinct: an
  intervention existed (put back verbatim), none (delete ours), unreadable
  (`null` → touch nothing).
- The kernel-node priors (`snapdragonCpuNodes`, `cpuInputBoostNodes`,
  `cpuGovernorNodes`, `storageTuningNodes`, `memoryTuningNodes`) are captured
  **only for nodes the raise-only rules would actually write** — a node already
  at its target, a family the device does not carry, or a rule gated away by an
  ambiguous neighbour (`hispeed_load`, `input_boost_ms`) contributes no snapshot
  entry, so the restore walks exactly the nodes that were changed and nothing
  else.
- `spectrumProfileProperty` and `spectrumProfile` are recorded as a pair, only
  when the interface is supported *and* the property already exists; the
  existence gate is what keeps a `persist.*` write reversible.

Network blocking is two switches over one app set on the full build (user-installed apps holding
`INTERNET`, minus CatSmoker and the game library): `VpnFirewall` claims a `tun` and never reads it —
blocked apps *time out*, and Android allows one active VPN, so it and any other VPN evict each
other — and `BackgroundDataRestrictor` uses Android's own `cmd netpolicy` plus Data Saver.
The Play build omits the VPN service (Play's VpnService policy requires it to be core) and keeps
only the Data Saver half; shared UI hides the VPN switch behind
`VariantCapabilities.HAS_VPN_FIREWALL`.
Both post-mortems are in the KDoc (Data Saver alone blocked nothing in
background; the first `VpnService` blackholed the *game's* traffic while
reporting success without calling `VpnService.prepare()`); read it before
touching either switch.

Auto Force Stop has **no user exceptions** (2026-10-28): it closes every app
you leave. The old `KeepAliveStore` keep list is deleted; only code-owned
protections — this app, the launcher, the current keyboard and system packages
— are exempt. Gaming Mode's "Apps kept running" list is a different feature
(`SuspendExemptStore`) and still wins over the suspend sweep.

## Key state types

- `GamingModeState` — `Idle` / `Checking` / `Enabling(progress)` / `Active` /
  `Disabling` / `Interrupted(pending count)` / `Error`.
- `GamingModeRecovery` — what a previous session left behind (pending packages,
  pending app-ops, verified flag) and therefore whether Restore apps is shown.
- `GamingModeRestoreResult` — verified counts for one Restore apps attempt,
  including a refusal while a session is active.
- `GamingModeReport` — verified outcomes + a human-readable `unavailable` list.
- `BoosterState` / `BoosterOutcome` — live progress and a terminal, never-rewritten outcome.

## Secrets to keep in mind
- The device names referenced in the code (Vivo/iQOO packages, launchers,
  etc.) are behavioral heuristics, not declarations — changes there affect
  what a particular device class gets.
