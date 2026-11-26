# vmtouch feasibility and ULTROID reference review

## Current decision — bounded experiment, not a performance promise

The earlier static-only decision is superseded: CatSmoker now has an
**off-by-default per-game cache-preloading experiment**, independently written
in Kotlin and built with the app. No ULTROID binary, script, APK code, or asset
is copied or executed. No extra native binary or dependency is shipped.
It uses Android's read-only file descriptors, `mmap`/`mincore` and bounded
sequential reads, warming the same filesystem cache as vmtouch `-t`.

Select a game -> **Experimental cache preloading**. Enable for that package,
choose **8 / 32 / 64 MiB** (default 32), optionally select asset documents through
Android's file picker, then **Test preload only**. This test does not activate
Gaming Mode and needs no root/Shizuku. Preparation also runs the saved experiment
after successful mode activation. Options save automatically to the same game
record; no profiles. **Cancel preload** cancels owned work.

The feature uses an expandable card in the Gaming Tools **Profile** section.
Global context asks for a selection; the inline controls identify the selected package
and shows friendly outcome/data/time, budget and permission explanations.
Raw diagnostics are behind an expandable section. A report for a different game
is not misattributed to the selected game. Selection alone never starts a preload.

Selected documents are tried first, then Android-reported APK/split paths and
directly accessible OBBs. No recursive directory crawl, private game-data access,
shell command, root escalation, cache eviction, page locking, thermal change or
daemon is used. SAF grants authorize only the files the user selected; a cloud
provider/pipe or nonregular file is rejected. Android scoped storage may make
OBBs inaccessible even if a privileged shell could read them.

Budgets are **cumulative**, not upstream's per-file `-m`: at most 64 MiB,
24 candidates, 8 selected documents, a three-second cooperative deadline and
64-KiB read/cancellation/memory checks. Check cancellation, time and pressure
before opening each candidate as well. Stop if Android reports low memory or
available RAM drops below max(256 MiB, 10% total RAM). Mappings and owned
descriptors close on completion/cancellation/error. Reads/providers can block:
the deadline is checked between operations, not a guaranteed hard I/O timeout.
Cached pages are reclaimable kernel resources; restore does not evict them.

The JSON report includes actual bytes read, elapsed time, process CPU time,
available RAM before/after, and per-file failures/residency where reliable.
App-side residency is deliberately unknown for files not owned by CatSmoker,
because newer kernels may fabricate `mincore` answers. Mapping/residency failures
do not block a safe read. Reading bytes is not proof
the game uses them or that they remain cached. Interrupted persisted runs are not
reported as completed.

Cache touching might improve a measured cold file-read workload, but is not
durable RAM locking and is not evidence of higher FPS or less in-game stutter.
Without validated file scope, total byte/time budgets, device measurements, and
reliable cancellation, the memory/I/O pressure can make the outcome worse.
Evicting other games, disabling thermal controls, and broad memory locking are
not acceptable defaults.

## Read-only reference evidence

Input directory: `C:\Users\Lenovo\Desktop\u`. APK decompiled statically using the
existing local JADX CLI 1.5.2, not executed. JADX reported six decompilation
errors overall; findings below are limited to readable methods and ELF/ZIP
inspection, not a claim of complete equivalence to the original program.

| Input | SHA-256 |
| --- | --- |
| `ultroid_1.7(Revamped).apk` | `5d340c0010ed266b501f5dbad659b93dc6d15e9f6287818340fc2e8e231bad61` |
| `vmtinstall/install.sh` | `428b21b15150a6e8d3407e4f1b077676917de2cb71c9d25d3fb174b246dc9709` |
| `vmtinstall/vmtouch_arm64` | `b91a8926a77cb0c32dd6895baa2fca71c6b585cac7df2e08682b3b764543dcf6` |
| `vmtinstall/vmtouch_armv7` | `e95f00bc8ed87c9e44b7c83b61d9ea5a836c9f9ddd9e2364eb03ac53d7f4114e` |
| APK `assets/vmtouch_android` | `ee36626f18e56d8e648ac37024362c566dfea9736205deb04157c7849b90fe42` |

ELF headers identify the loose binaries as ARM64/ARM32 respectively. The APK's
different, 23,512-byte asset is ARM32 with `/system/bin/linker`; its hash is not
either loose binary's. Provenance, correspondence to upstream source, supported
Android releases, 64-bit-only devices, x86, and page-size compatibility are not
established by a filename or header.

Observed reference behavior (`com.droideverything.ultdroid`):

- `PreloaderFragment.setupVmtouchBinary` and
  `OptimizeGamesFragment.setupVmtouchBinary` copy an APK asset into
  `/data/local/tmp/vmtouch_android` only if missing, using ordinary Java file I/O
  and `setExecutable(true)`. Existing files are trusted without digest/version
  verification. App-side directory access may fail even when Shizuku can run
  a command there. No atomic verified installation or upgrade path is shown.
- `vmtinstall/install.sh` chooses ARM64 if the ABI string contains `arm64`,
  otherwise ARMv7, without rejecting x86. It copies only when missing and uses
  `chmod 777`. It checks a `vmtouchlib` name it never creates and does not validate
  command results or binary origin. A world-writable privileged executable is
  not a pattern to reuse.
- `PreloaderFragment.ProcessAllTask` uses `-t` to touch or `-e` to evict. The
  single-game processing path first iterates all added games and evicts them
  before preloading the chosen one. This is broad cache churn, not locking.
- Targets are `pm path` output, `/data/data/<package>/lib` `.so` files, and
  `/sdcard/Android/obb/<package>` `.obb` files. `OptimizeGamesFragment` likewise
  uses `-t`, optionally with a `su` prefix. App-side `File.exists/listFiles`
  checks do not inherit Shizuku/root privileges and can silently omit targets.
  Split-APK multiline output is flattened rather than parsed as separate paths;
  command strings concatenate paths without robust shell quoting.
- `ShellCommandUtil.runCommand` invokes a Shizuku remote `sh -c`, waits for exit,
  drains stdout on a thread, and destroys the child afterward. No timeout or
  separate stderr drain is visible. Nonzero exit becomes generic text, while
  normal vmtouch stdout is also treated as an error in several callers. Final
  “success” messages can follow skipped targets/errors. Progress counts work
  items rather than verified resident pages. Readable preload paths contain no
  `mlock`, daemon PID ownership, total-memory limit, or cancellation/restore
  contract. A confirmation Cancel button is not cancellation of running work.

These observations explain design risks; they do not justify copying
proprietary implementation or infer that every APK feature behaves this way.

## Upstream source assessment

Reviewed upstream [README](https://github.com/hoytech/vmtouch/blob/master/README.md),
[source](https://github.com/hoytech/vmtouch/blob/master/vmtouch.c),
[manual](https://github.com/hoytech/vmtouch/blob/master/vmtouch.pod), and
[tuning notes](https://github.com/hoytech/vmtouch/blob/master/TUNING.md).
The reviewed source identifies version 1.3.1. BSD-3-Clause permits redistribution
with copyright, license/disclaimer retention and the no-endorsement condition.
Any future vendoring must pin an immutable revision and preserve the notices;
the unpinned review is not a reproducible binary supply chain.

| Behavior | Actual mechanism and limitation |
| --- | --- |
| Inspect | `mmap` + `mincore`; newer Linux ownership/write-permission restrictions can make residency reporting inaccurate |
| `-t` | Read a byte per mapped page, warming page cache; pages can immediately be reclaimed |
| `-l` | `mlock` mappings and keep a process alive; constrained by memlock limits, privilege, memory pressure, and SELinux |
| `-L` | `mlockall`, broader than game asset mappings; not an appropriate default |
| `-d`, `-w`, `-P` | Daemon/wait/PID-file mechanisms for locking, not proof of safe Android lifecycle ownership |
| `-e` | `posix_fadvise(...DONTNEED)` on Linux; eviction is deliberately rejected here |
| `-m` | Maximum **individual file** size, not a cumulative memory budget |

Upstream skips some inaccessible files with warnings; exit zero alone does not
prove meaningful work. PID-file cleanup on handled signals does not solve
Android process death/reboot, PID reuse, or stale foreign-process ownership.
The app must never kill a generic process by name to “unlock RAM.”

## Requirements from the earlier static review

1. Build reviewed upstream source ourselves with the project's pinned NDK/Bionic
   toolchain; verify ABI coverage, executable delivery/SELinux constraints, and
   Android 16-KB page support. Do not install a downloaded opaque helper.
2. Discover an installed game's actual readable APK/split/native/asset paths
   through Android APIs. Validate/canonicalize and explicitly bound files,
   symlinks, recursive depth, aggregate bytes, elapsed time, and free-memory
   reserve. Do not assume access to another app's private data or all OBB files.
3. Start with disabled-by-default, bounded cache touching, not locking/eviction.
   Reject unsupported privilege/access/ABI paths honestly. Do not silently ask
   for root for a feature that cannot establish useful readable assets.
4. If locking is ever considered separately, prove memlock/SELinux support,
   pressure safeguards, owned-process tracking and cleanup on cancellation,
   switch, restore, error, reboot, and interrupted-session recovery. A per-file
   cap is insufficient. Preserve battery, thermal and LMK protections.
5. Use `ShellRunner` or a reviewed bounded native integration, safe arguments,
   concurrent stdout/stderr handling, timeouts, cancellation, and measured
   touched/resident bytes. A successful process exit is not an optimization.
6. Benchmark representative devices/games for cold/warm startup I/O, resident
   memory, pressure/LMK, energy/temperature, and frame-time percentiles. Retain a
   feature only if a bounded useful effect outweighs its cost.

No device/performance evidence was produced by that earlier static review.

## ARM64 device experiment (2026-11-15)

Using the local NDK **28.2.13676358**, built upstream at immutable commit
`af86e27675843b3c7e4ddfee66ddbaf44eff43c4` (self-reported 1.3.1, not the
`v1.3.1` tag) for
`aarch64-linux-android27`, with `-Wl,-z,max-page-size=16384`.
The lab-only executable has ELF LOAD alignment `0x4000` and uses Android's
`/system/bin/linker64`; it is not bundled in either flavor. Retained upstream
BSD-3-Clause LICENSE with the downloaded source. Five compiler warnings were
retained in the lab log; this was not a warning-free audited production build.
Source SHA-256: `c3af27b130e7640de55563ad92eb4a88e4f4a3479f9fb4fcaa40bbc850af7738`.
Lab ARM64 ELF SHA-256:
`dfa5272c6806be155b83a07f17426bc72a70ed19b93881ece47b4add8e856a7e`.

Connected device: **POCO X3 NFC, Android 15/API 35, ARM64, kernel
4.14.356-openela-rc1, 4096-byte pages**. Not Samsung DeX; no Arena Breakout,
Tasker, MacroDroid or Panda Mouse installation was available.
`/proc/pressure/memory` was readable; an idle spot-check showed both
`some` and `full` avg10/60/300 at 0.00. This was not synchronized per-trial
pressure measurement and cannot establish a pressure benefit.

| Read-only/touch experiment | Observed result |
| --- | --- |
| New lab-owned 32-MiB sparse fixture, no eviction | 0/8192 resident before; touch 17.389 ms; 8192/8192 afterward |
| Installed PUBG APK first 32 MiB, trial 1 | reported 75/8192 before; touch 71.305 ms |
| Same prefix, trials 2 and 3 | reported 8192/8192 before; touch 11.642 / 11.706 ms |

These are **cache mechanism / warm-cache observations**, not game startup or
FPS benchmarks. The sparse fixture contains no representative assets. The APK
is not owned by the testing UID, so its residency numbers are advisory; the
changing values on this kernel do not validate all newer Android kernels.
No `-e`, `-l`, `-d`, global drop-caches, game force-stop, thermal override or
game-file modification was used. Source/build logs and raw results are local in
`C:\Users\Lenovo\AppData\Local\Temp\opencode\catsmoker-vmtouch-lab`.
The lab source/tool is not a new shipped dependency.

### Shipped integration device checks

The independently implemented Kotlin reader also ran through the actual
Gaming Tools UI on that phone, without granting root/Shizuku. Three consecutive
PUBG APK-prefix trials each read exactly **8 MiB**: elapsed **25 / 17 / 23 ms**,
process CPU delta **34 / 22 / 34 ms**. These were warm-cache reads, not comparable
OFF/ON game loads. Available system RAM fluctuated; that is not proof of reduced
game memory use. No average FPS, game frame times or loading times were measured.

A final budget-reporting retest returned `byte_budget`, **8,388,608 bytes**,
**18 ms elapsed / 57 ms process CPU**, with no further split opened.
Available RAM was 671,748,096 before / 671,952,896 bytes afterward.
The APK residency counts correctly remained unknown for a non-owned file; OBB
access was reported as absent/inaccessible. CPU delta includes other app threads.
Test settings were returned to Off / 32 MiB, original selection restored, and
the two lab-owned device files removed. No cache eviction was used.

Host verification includes byte/time/pressure/cancellation accounting, exact
budget exhaustion, opt-in settings and source cleanup guards. The integration
still needs slow-provider cancellation and actual game/OEM pressure testing.

### Remaining comparable game-performance protocol

On a consenting tester's **actual DeX + Arena Breakout + Panda Mouse** setup:

1. Record Android/One UI version, display IDs/resolution/refresh, input mapper,
   chosen saved settings, cache scope, battery/temperature and power mode.
   Keep the same game version, assets, scene, network and display for all trials.
2. Compare cache OFF versus ON with **at least five trials each**, alternate
   order, and separately label first-launch-after-reboot versus warm repeat.
   Do not manufacture cold conditions by globally evicting other apps' caches.
3. Capture launch-to-ready time and identical scene/asset-transition durations.
   Use an authorized Perfetto FrameTimeline/game-supported measurement path
   for average FPS, p50/p95/p99 frame times, missed frames and long stalls.
   `dumpsys gfxinfo` alone may not represent a SurfaceView game's frames.
4. Record process/system RAM, `/proc/pressure/memory` where readable, LMK events,
   preload CPU/elapsed time and temperature. Process CPU delta includes other
   app threads; it is not isolated per-preloader CPU accounting.
5. Repeat with the external display and mapper active, verify mouse/keyboard,
   no new overlays, successful restore/readback, game-exit during preparation,
   revoked Shizuku/root, denied FGS start, force-stop/restart and reboot recovery.
6. Report medians/spread and warm-cache effects. A faster second run is not
   evidence that the feature improved the game. Disable if pressure/stalls worsen.

No average-FPS, frame-time stability or Arena Breakout loading improvement has
been established. Page-cache warming does not intrinsically increase FPS.
