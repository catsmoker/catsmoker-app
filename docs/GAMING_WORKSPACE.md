# Gaming Tools workspace and capability notes

Gaming Tools starts on **Profile**. Use the section chips for Graphics,
Performance, Overlays, Focus & Network, and Maintenance instead of scrolling
past every tool. At an available content width of 792 dp (about 840 dp including
the screen's horizontal padding), Your Games moves to a left pane. Narrower
windows keep it in Profile. The selected game comes from the existing
package-addressed settings store; selected section, library search and section
scroll state do not depend on window width. Automation stays in the header and
uses two columns when its own content has at least 680 dp.

**Performance** uses two columns at the same 792 dp breakpoint: CPU policies
and Volume Boost on the left, App Compile Optimisation and Developer Options on
the right. Narrower windows stack the same four cards in one column, so the
cards and their saved expansion state are identical in both layouts.

This is a layout for resizable Android windows, not a new launcher or a
Samsung DeX integration. Existing external-display guards remain in force:
don't change the phone's resolution, or assume it owns a desktop display's
refresh and brightness. Desktop AVD results do not certify Samsung DeX.

## Commands and profile scope

**Graphics → Screen Resolution → View commands** shows the actual argument
lists used for that draft, reset and readback. Apply runs `wm size` first and
only attempts `wm density` if sizing succeeded. Reset attempts both, even if
one fails. `wm size` and `wm density` without arguments read back the effective
state. Reset removes Android overrides; it is not a rollback to a previous
custom override. The execution log records the resolved backend and exit code
for each write, then the measured result. A copied command runs under the
identity of the caller's shell, outside CatSmoker's confirmations and
external-display guard. No global shell transcript is collected. This
inspector covers physical resolution, not every CatSmoker action.

The **Automation** workspace copies the one-broadcast template for the
selected package, shows the latest run separately from its delivery outcome,
and can request the same preparation used by the UI. See
[Game Automation](GAME_AUTOMATION.md) for the actual contract, Tasker and
MacroDroid. Applying a profile means applying the saved Gaming Mode inputs;
manual resolution, file deployment and app compilation are not included.

## Capability matrix

“Eligible” describes code routing, not OEM permission or a verified write.
Read-back, Android permissions, DAC and SELinux still decide the outcome.

| Capability | App UID / public API | Authorized Shizuku UID 2000 | Authorized Shizuku UID 1000 | UID 0 |
| --- | --- | --- | --- | --- |
| Inspect CPU policies | App-readable sysfs; missing fields stay unavailable | A shell may expose more fields; not used by the read-only panel | Not verified; no write route | Existing root tuning reads its own nodes |
| Apply CPU governor/vendor tuning | No | Not enabled | Not verified; not enabled | Existing direct-root-only session with supported-node checks and restore |
| GPU/vendor settings | Telemetry where readable | Vendor tuning not enabled | Not verified; not enabled | Existing direct-root gates and per-device checks |
| App compilation (`cmd package` / ART) | No general access | Eligible where command is supported | Not enabled | Eligible through existing engine |
| Gaming Mode, graphics, DNS, display writes | Some Settings/notification operations honor individual Android grants; no general backend | Eligible for existing shell-routed operations | Diagnostic identity only; no general dispatch | Eligible; some operations require the original owning UID on restore |
| Game cache preload | Bounded reads of app-accessible files and SAF grants | Not required or used | Not required or used | Not required or used |
| Private game files and Spoof Device | Not available through this route | Not a substitute for private file access | Not enabled | Existing direct-root feature gates; spoofing stays Full-only |

The normal shell fallback is an explicitly non-root libsu shell. A Binder
execution that loses its result is not replayed on another transport. A Shizuku
manager can report UID 1000; the app does not promote that identity to a write
backend without operation-specific verification. No backend acquires UID 1000
for the user or modifies SELinux.

**Performance → CPU frequency policies** lists policy names from sysfs, affected
and related CPUs, driver, active/available governors, hardware/scaling limits,
reported current frequency and the available frequency table. Values remain
kHz internally; the panel displays MHz. Missing tables are not estimated from
hardware bounds. The preset numbers in the product brief are device-specific
and are not shipped. There is no new fixed-frequency enforcer or min/max
write tool. The existing CPU session path correctly parses whitespace-delimited
policy membership and reports unresolved kernel restoration after root loss
instead of dropping its saved recovery state. A restore reads each recorded
kernel node first: one that still holds its captured prior is left alone, so a
write the apply path refused is not later reported as a failed restore, while a
node that still holds the gaming value without root stays listed as unresolved.

## Research decisions

| Reference | Relevant implementation and decision |
| --- | --- |
| [Now in Android](https://github.com/android/nowinandroid), [Compose Samples](https://github.com/android/compose-samples), [Adaptive Apps Samples](https://github.com/android/adaptive-apps-samples) | First-party Apache-2.0 samples. Nia and JetNews separate window-derived navigation from saved state. The adaptive samples include navigation-suite and desktop input examples. Referenced the pane/state separation; no Navigation migration or copied sample code. |
| [Shizuku](https://github.com/RikkaApps/Shizuku), [Shizuku API](https://github.com/RikkaApps/Shizuku-API), [Sui](https://github.com/RikkaApps/Sui) | Upstream permission is required independently of Binder availability. Root-started and shell-started backends differ. Retained existing authorized API, no alternate broker. |
| [libsu 6.0.0](https://github.com/topjohnwu/libsu/blob/6.0.0/core/src/main/java/com/topjohnwu/superuser/Shell.java) | Apache-2.0. Explicit non-root builder avoids the default main-shell `su` attempt. Direct `newJob()` requires output destinations for readback. |
| [AxManager](https://github.com/fahrez182/AxManager/tree/7ed7f508464076d2617a9172c9de5bca2e7bcf7a) | Apache-2.0 plugin host and shell broker. Neither advertised Shizuku compatibility nor an installed plugin verifies access to CPUFreq. No plugin or scripts imported. |
| [UltDroid Shell](https://github.com/DroidEverything/UltDroid_shell/tree/5da38cfd4bbf5d81db1ba56c906e229e901cb3f0) | No observed redistribution license. Shell presets and cache trimming are distinct from APK page warming; no code reused or executed. |
| [XyloOSGameCore](https://github.com/HexXylo/XyloOSGameCore/tree/a6171a39bec1f787cb98a62f92dc748d53e42d84), [Nebula](https://github.com/galangjs-id/Nebula-Plugin/releases/tag/v4.0.0-rebuild) | Xylo's public repository has no implementation and contains inconsistent licensing text. Nebula's published payload includes thermal overrides and fixed resets rather than recorded priors. No scripts or binaries were executed or integrated. |
| [vmtouch](https://github.com/hoytech/vmtouch/blob/af86e27675843b3c7e4ddfee66ddbaf44eff43c4/vmtouch.c), [Linux CPUFreq](https://docs.kernel.org/admin-guide/pm/cpufreq.html) | Reference only. vmtouch (BSD-3-Clause) page touching and CatSmoker's bounded reads use the same reclaimable page cache. Kernel tables and measured ownership drive CPU decisions. No new binary, eviction or lock daemon. See [prior cache evidence](VMTOUCH_REVIEW.md). |
| [Perfetto](https://github.com/google/perfetto), [AOSP Simpleperf](https://developer.android.com/ndk/guides/simpleperf), [stress-ng](https://github.com/ColinIanKing/stress-ng) | Perfetto and Simpleperf are Apache-2.0 lab tools; stress-ng is GPL-2.0-or-later and is not a game benchmark. The CLI captured a short valid desktop trace, but no paired cold/warm runs on a retail game were run. Don't infer FPS from a capture or from the fixture app. |

## Lab setup

The Windows 11 x64 test host has WHPX acceleration, Android Studio Rabbit 2
2026.2.2 Patch 2, the project's Gradle wrapper (9.8.2), JDK 21, NDK
28.2.13676358 (ndk-build; CMake not required), Android Emulator 37.3.10 and
platform-tools 37.0.3. The Google Android CLI (1.1.16919734) and Android SDK
Command-line Tools 23 were installed per-user and tested. The CLI can see the
running Studio project. APK Analyzer, AVD Manager and SDK Manager are supplied
by those tools. Android CLI/ADB can capture screens and drive devices without
adding a redundant device-control MCP or scrcpy. Context7 was installed in
OpenCode and answered the Jetpack Compose constraint query without an API key.

Google's Android skills were installed with `android skills add --all --agent
opencode` into `~/.config/opencode/skills/`. Applied `android-cli`, `adaptive`,
`android-intent-security`, and `testing-setup`, plus the repository's `verify`.
The reviewed upstream Android skills snapshot was `7f7b21730a605`; the CLI
installs its published bundle, not a version-pinned Git checkout. Google has no
native OpenCode plugin for the Android CLI; OpenCode uses the installed official
skill and executable. No third-party skill scripts or tools were added.

The connected POCO X3 NFC (Android 15, arm64) reported that its ADB shell was UID
0 in the `su` domain, not UID 2000. Read-only inspection found `osm-cpufreq`
policies 0 and 6 (not the brief's 0, 4 and 7). A shell writability probe is not
a verified write. No CPU sysfs writes or Android configuration changes were
made on the physical device.

The desktop lab uses Google APIs Desktop (Preview) 37.1 x86_64 in a
`desktop_api37` profile. The default GPU startup did not reach ADB;
`-no-window -gpu swiftshader` booted the actual Android desktop guest.
Its desktop shell operates in user 10. For lab broadcasts use `--user current`
when that user is not user 0. The guest's dedicated test game and automation
sender are local fixtures, not third-party packages.
