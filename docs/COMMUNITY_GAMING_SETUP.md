# Community gaming setup: automation, DeX and cache experiment

## What is available

| Requested feature | Status | What works / what remains |
| --- | --- | --- |
| Authorized broadcasts | Partially implemented; requires caller/device testing | Prepare/restore attempt an unattended foreground service. Android refusal falls back to a notification; foreground permission is not guaranteed on every OEM. |
| Tasker / MacroDroid | Partially implemented; requires physical-device testing | Explicit broadcast contract and copy-ready fields below. Neither caller was installed on the test phone. |
| ADB | Fully implemented for the documented interface | Authorized background delivery, rejection of an invalid token, selection and missing-privilege reporting were observed. Successful privileged preparation/restoration still needs an authorized channel. |
| Launcher shortcuts | Implemented; requires physical-device testing | User-requested pinned shortcut prepares and launches through the foreground Activity. Launcher confirmation and background-Activity rules still apply. |
| One-action saved preparation | Implemented; requires privileged-device testing | Manual, monitor, broadcast and Activity use the same coordinator, repository, runner and engine. No separate automation tuning store. |
| Automatic per-game settings | Fully implemented for supported package-specific controls | Grid selection, saved render/pointer/resolution drafts and cache options; no profiles. Actual resolution and game-file deployment remain explicit. |
| vmtouch investigation | Completed, including ARM64 device mechanism tests | Independently built upstream tool; no ULTROID executable was run. Cache warming was demonstrated, not an FPS benefit. |
| Experimental cache preloading | Implemented; requires broader physical-device testing | Opt-in per game, readable files only, byte/time/pressure limits, cancellation and resource cleanup. No eviction or locking. |
| Samsung DeX / external displays | Partially implemented; requires Samsung DeX testing | External-display detection prevents phone-wide resolution/refresh/rotation/brightness interference. No Samsung device was connected. |
| Arena Breakout | Requires physical-device testing | Capability-based installed-game selection, not a special game patch. Arena Breakout was not installed on the test phone. |
| Mouse, keyboard, Panda Mouse Pro | Partially implemented; requires physical-device testing | Known mapper/automation packages, active IME and accessibility services are protected from the preparation sweep. No input injection or permission changes. |
| Better FPS stability | Requires physical-device testing | Removed automatic kill-all, cache trimming, forced Doze, lifecycle/process limits and FPS targets from preparation. No measured FPS gain is claimed. |
| Always-unattended launching / unsupported privileges | Technically unsupported as a general Android guarantee | FGS permission does not grant background Activity launch permission. Root/Shizuku access does not exempt the app UID from Android background-start rules. |

“Implemented” describes the code path, not universal OEM compatibility or a
successful privileged benchmark. The connected POCO had no authorized CatSmoker
root/Shizuku channel; its request correctly failed before opening a session.

## Configure once

1. Install the appropriate CatSmoker flavor and complete the existing agreement
   and game-inventory consent. Grant the existing root/Shizuku channel only if
   you choose to use privileged Gaming Mode operations.
2. Open **Gaming Tools**, add Arena Breakout if discovery does not list it, and
    tap its icon. The heading changes from Global to the game's name. Tap that
    icon again, or the compact Global control, to deselect it; both contexts are
    remembered. Set its render scale and pointer choice if wanted. Leave pointer
   speed Off initially when using Panda Mouse Pro. Changes save automatically.
3. Keep physical resolution under Samsung DeX's own settings. Do not enable
   overlays unless wanted; independently check the game's rules and mapper
   compatibility. CatSmoker does not bypass anti-cheat or automate player input.
4. Open **Automation** in the Gaming Tools header, enable access and copy the private token to your trusted
   automation app. For reliable background preparation, optionally use the
   offered Android battery-settings page to allow unrestricted operation.
   Android/OEM rules still decide admission; no recurring confirmation is added
   when the foreground service is permitted.
5. Configure an entry task using `APPLY_SAVED` and an exit task using `RESTORE`.
   Do not use `APPLY_AND_LAUNCH` for a game-detection event: the game is already
   launching. Restore before preparing another game or changed inputs.

## Copy-ready caller configuration

Verify the installed Arena Breakout package. `com.proximabeta.mf.uamo` below is
an example for its global distribution, not an invented supported-game list.
Replace `TOKEN` locally; never include the real token in reports.

Tasker **Send Intent**, attached to game entry:

```text
Action: com.catsmoker.app.GAME_AUTOMATION
Cat: None
Mime Type:
Data:
Extra: token:TOKEN
Extra: operation:APPLY_SAVED
Extra: game:com.proximabeta.mf.uamo
Package: com.catsmoker.app
Class: com.catsmoker.app.screens.gaming.automation.GameAutomationReceiver
Target: Broadcast Receiver
```

Exit task: change the operation to `RESTORE` and remove the `game` extra.

MacroDroid **Send Intent**:

```text
Target: Broadcast
Action: com.catsmoker.app.GAME_AUTOMATION
Package: com.catsmoker.app
Class: com.catsmoker.app.screens.gaming.automation.GameAutomationReceiver
String extra token = TOKEN
String extra operation = APPLY_SAVED
String extra game = com.proximabeta.mf.uamo
```

Exit macro: `operation = RESTORE`, no `game` extra. Leave data/MIME/category
empty; do not attach extra metadata.

ADB:

```text
adb shell am broadcast -n com.catsmoker.app/.screens.gaming.automation.GameAutomationReceiver -a com.catsmoker.app.GAME_AUTOMATION --es token TOKEN --es operation APPLY_SAVED --es game com.proximabeta.mf.uamo
adb shell am broadcast -n com.catsmoker.app/.screens.gaming.automation.GameAutomationReceiver -a com.catsmoker.app.GAME_AUTOMATION --es token TOKEN --es operation QUERY_STATUS
adb shell am broadcast -n com.catsmoker.app/.screens.gaming.automation.GameAutomationReceiver -a com.catsmoker.app.GAME_AUTOMATION --es token TOKEN --es operation RESTORE
```

User-initiated prepare-and-launch:

```text
adb shell am start -n com.catsmoker.app/.screens.gaming.automation.GameAutomationActivity -a com.catsmoker.app.GAME_AUTOMATION --es token TOKEN --es operation APPLY_AND_LAUNCH --es game com.proximabeta.mf.uamo
```

Or request **Add selected game shortcut** in the Automation dialog. Keep the
foreground preparation Activity visible until completion. A broadcast asking
to launch still uses a notification tap, because a service-start exemption is
not an Activity-start exemption.

`accepted` means the request was admitted, not that settings were applied.
Query for a newer matching operation/game in `last`/`lastDelivery` and inspect
`operations`, `error`, `notRequested`, `sessionOpen` and `recoveryRequired`.
`cache` is the latest cache experiment report and may predate a failed
preparation; its presence alone does not mean that request ran preloading.
An exit restore can wait behind preparation; duplicate preparation is rejected
as `busy`. Unchanged active inputs return `already_applied`.

## Try cache preloading safely

With the game selected, expand **Experimental cache preloading** near the bottom,
after Cleaning. It is off by
default. Start with 8 MiB and **Test preload only (no Gaming Mode)**. Explicitly
selected readable asset documents take priority over APKs and accessible OBB
files. App-private/blocked files are not bypassed with root or Shizuku.

The inline card shows the selected game, read-access requirements, budget, friendly
operation outcome, actual data read and elapsed time. Cancel is available while
that game's preload runs. Diagnostics are optional advanced details; another
game's last result is not shown as this game's outcome. In Global, the entry asks
you to select a game rather than editing an implicit target.

The 3-second deadline and cancellation are checked between regular-file reads,
not a hard real-time guarantee against a stalled storage provider. Cached pages
are reclaimable immediately; releasing mappings does not evict them.

Record repeated OFF/ON runs before deciding whether to retain it. APK prefixes,
especially compressed/unrelated data, may not help the game's actual load path.
There are no measured Arena Breakout FPS, loading-time or frame-time gains yet.

## Evidence and remaining tests

Both variants compiled and passed **116 targeted tests each** (14 classes).
The Full debug APK was assembled, installed and exercised on a POCO X3 / Android
15. The Play source boundary check passed. Cache-only reads and authorization/
selection/error paths were observed; privileged Gaming Mode success was blocked
by the phone's missing CatSmoker root/Shizuku grant. This is not a DeX benchmark.

See [automation contract](GAME_AUTOMATION.md) for authorization, results and
foreground limitations; [vmtouch review](VMTOUCH_REVIEW.md) for pinned-source
ARM64 measurements and the repeatable game-performance protocol.

Required on the community user's setup: Samsung DeX external-display changes,
Arena Breakout entry/exit events, real Tasker/MacroDroid delivery, Panda input
continuity, notification-denial fallback, successful privilege/readback and
restoration, rapid duplicate/exit requests, process death/reboot recovery, and
paired loading/frame-time/RAM/pressure/CPU measurements. These are not passed
merely because host tests or an ADB broadcast succeeded.
