# Gaming Tools: game selection and external automation

## Automatic game settings

Gaming Tools opens in **Global** on a new install and explicitly remembers that
context or the last selected package. Discovery never picks a game for you.
Tap a game icon to select it; tap it again, or use the heading's Global control,
to deselect it. The highlight identifies the selection. **Long press any game
tile**, selected or not, to open **Launch**, **Pre-compile**, and **Remove** without
changing the selection. Accessibility services expose the same actions directly;
there is no visible three-dot button. Launch and Pre-compile keep their existing
behavior; selecting an icon does neither.

Render scale, pointer-speed choice, experimental cache options, and the resolution editor's draft belong to
the selected Android package. They are written automatically to private app
preferences; there is no save/load workflow. A new package inherits a frozen
copy of the pre-existing render/pointer defaults, not the last game's changes.
Choosing Off is a real saved value. Removal/uninstallation does not delete the
package's saved inputs; re-adding/reinstalling can reuse them. Uninstalling
CatSmoker or clearing its data does delete them. Normal updates preserve them.

Selection restores **desired inputs**, not measured device state. Editing context
and sessions have independent targets: only an explicit `SELECT_GAME` request
changes the editing context; preparing or launching another package does not
replace that choice.

Render scale and pointer speed are consumed on the next Gaming Mode activation. Resolution
still needs its existing Apply action and any existing risk confirmation.
Changing selection dismisses an outstanding resolution confirmation. ANGLE is
already an immediate, persistent per-package Android setting; its picker follows
the selected game and reads the device's current value. Selection does not
rewrite ANGLE. File Engineering remains its own explicit editing/deployment flow.

## Authorize a caller

In **Gaming Tools > Automation**, enable the access switch, then
copy the token into a trusted automation app. Access is off initially. The
256-bit bearer token is not displayed or logged, but copying it deliberately
exposes it to the clipboard and to the recipient. Do not paste it into public
scripts, logs, bug reports, URLs, or screenshots. ADB examples below put it in
shell history: use only a trusted development machine.

The bottom sheet separates Tasker, MacroDroid, ADB and launcher-shortcut setup.
The key stays masked; only Copy key exposes it to the clipboard. **Replace key**
and disabling access require confirmation before existing integrations are
invalidated. Advanced setup contains a placeholder-only command reference,
not the actual secret. Accepted requests are not presented as applied settings.

Disabling access invalidates the token, disables this feature's pinned shortcuts,
and cancels the pending continuation notification. Re-enable and copy the new
token to authorize again. The token is local and excluded from Android backup,
as is the rest of this app's data. Revocation prevents future requests; it does
not cancel an already executing restore or leave a half-applied session.

The onboarding agreement and current game-inventory consent are still required.
A target must be installed, launchable, and present in Your Games, and must not
be CatSmoker itself. Explicitly added games work; this API invents no fixed
supported-games list. Removing a game makes it unavailable to this API.

## Intent contract (version 1)

- Action: `com.catsmoker.app.GAME_AUTOMATION`
- Foreground component:
  `com.catsmoker.app/.screens.gaming.automation.GameAutomationActivity`
- Broadcast component:
  `com.catsmoker.app/.screens.gaming.automation.GameAutomationReceiver`
- String extras only: `token`, `operation`, and, where required, `game`.
- No data URI, selector, ClipData, additional extras, shell commands, filenames,
  arbitrary settings, or caller-supplied components are accepted.

| `operation` | `game` | Effect |
| --- | --- | --- |
| `SELECT_GAME` | Required | Select and restore inputs only; no system changes |
| `APPLY_SAVED` | Required | Prepare the target's saved render/pointer and optional cache choices without changing editing context |
| `ENABLE_MODE` | Required | Same as `APPLY_SAVED` |
| `LAUNCH_GAME` | Required | Launch through Android's package launch intent without changing editing context |
| `APPLY_AND_LAUNCH` | Required | Activate, then launch if activation is applied or partial |
| `DISABLE_MODE` | Forbidden | Restore the existing Gaming Mode-owned session |
| `RESTORE` | Forbidden | Same as `DISABLE_MODE`; not a reset of every manual tool |
| `QUERY_SELECTION` | Forbidden | Query selection and status |
| `QUERY_STATUS` | Forbidden | Same query |

Apply does **not** deploy physical resolution, edit game files, replay immediate
global controls, or change ANGLE. It uses the existing engine and its existing
privilege checks, snapshot, read-back report, and restore path in both flavors.
No privileges are silently granted. An unchanged already-active game returns
`already_applied` without repeating preparation. A different game, changed saved
inputs, or pending recovery produces `restore_required`; restore first. Selecting a new
game alone does not retarget the active session. Unsupported/refused steps
produce `partial`, not a performance promise.

### Tasker / MacroDroid

**Recommended game-entry task: explicit broadcast `APPLY_SAVED`.**
In the authorization dialog, open **Battery settings** and, if desired, manually
allow CatSmoker unrestricted battery use / exclude it from battery optimization.
This is a documented Android background foreground-service exemption, not a
silent grant. OEM settings differ; the dialog displays Android's current
exemption state. CatSmoker does not request a restricted exemption permission.

Tasker **Send Intent** fields:

```text
Action: com.catsmoker.app.GAME_AUTOMATION
Cat: None
Mime Type: [empty]
Data: [empty]
Extra: token:TOKEN
Extra: operation:APPLY_SAVED
Extra: game:com.proximabeta.mf.uamo
Package: com.catsmoker.app
Class: com.catsmoker.app.screens.gaming.automation.GameAutomationReceiver
Target: Broadcast Receiver
```

`com.proximabeta.mf.uamo` is an example for the global Arena Breakout
distribution: **verify the installed package**, add/select it in Your Games, and
replace the example for other editions. It is not a fixed supported-game entry.
Attach this task to the game's entry event. Exit task: same fields but
`operation:RESTORE`, and **omit the game extra**. MacroDroid **Send Intent** uses
the same action, package/class, **Broadcast** target and three **String** extras
named `token`, `operation`, `game`; its exit macro similarly omits `game`.
Some versions put the fully qualified receiver class in a component field.
Do not include automation-app-specific extra fields in the outgoing Intent.

`SELECT_GAME` and queries complete synchronously. Preparation and restore attempt
the existing, non-exported `specialUse` foreground service **before any privileged
work**, posting its ordinary ongoing notification. Android decides whether the
start is permitted: a permitted start returns `accepted` and runs without a
confirmation tap. `accepted` means admission, **not completed optimization**.
Query afterward for the terminal report. Running alongside the existing foreground
session or a documented battery exemption can permit unattended execution.
Merely granting root/Shizuku does **not** exempt CatSmoker's app UID.

If Android rejects the service start, a continuation notification is the supported
fallback. With notifications denied/blocked, the result is `foreground_required`.
There is no overlay, shell-UID, background-Activity, or security-policy bypass.
OEM power management and force-stop can still prevent delivery/execution.

`LAUNCH_GAME` and `APPLY_AND_LAUNCH` broadcasts require the continuation tap:
permission to run a foreground service is **not** permission to launch an Activity
over another app. For a user-initiated task or shortcut, use **Activity** target and
`GameAutomationActivity` instead, with the same extras; it waits until Android
reports it resumed. Keep it visible until completion. Tasker/MacroDroid must have
a legitimate background-Activity-start allowance if invoked without user interaction.
For game-detection automation,
the game is already launching, so use `APPLY_SAVED`, not `APPLY_AND_LAUNCH`.
A newer continuation request replaces the older notification.

### ADB

Substitute a real game from Your Games and your locally enabled token:

```text
adb shell am start -n com.catsmoker.app/.screens.gaming.automation.GameAutomationActivity -a com.catsmoker.app.GAME_AUTOMATION --es token TOKEN --es operation APPLY_AND_LAUNCH --es game com.example.game
adb shell am broadcast -n com.catsmoker.app/.screens.gaming.automation.GameAutomationReceiver -a com.catsmoker.app.GAME_AUTOMATION --es token TOKEN --es operation APPLY_SAVED --es game com.proximabeta.mf.uamo
adb shell am broadcast -n com.catsmoker.app/.screens.gaming.automation.GameAutomationReceiver -a com.catsmoker.app.GAME_AUTOMATION --es token TOKEN --es operation QUERY_STATUS
adb shell am broadcast -n com.catsmoker.app/.screens.gaming.automation.GameAutomationReceiver -a com.catsmoker.app.GAME_AUTOMATION --es token TOKEN --es operation RESTORE
```

Ordered broadcast callers receive result data JSON (success code `-1`; denied
code `0`). The Activity supplies `status` JSON to a caller using activity results.
Use a query broadcast to inspect a longer operation after service admission or Activity start.
Query returns `version`, `selectedGame`, `mode`, `modeObserved`, `sessionOpen`,
`recoveryRequired`, `unavailableCount`, `cache`, `lastDelivery`, and the last result/time. Results include
`accepted`, `selected`, `applied`, `already_applied`, `partial`, `launched`, `restored`, `nothing_to_restore`,
`restore_required`, `busy`, `game_unavailable`, `awaiting_user`,
`foreground_required`, `failed`, and `denied`. These are execution states, not
FPS or latency measurements. After process death, a persisted `running` result
becomes `interrupted`, not success. A passive query never constructs the engine
or reapplies its manual settings: until an engine exists in this process,
`modeObserved=false`, `unavailableCount=null`, and any recorded session is
conservatively reported as `RecordedSession` requiring recovery.
Completed preparation/restoration status also contains `operation`, `game`,
`operations` (engine result fields and resolved `unavailable` explanations),
`cache`, `error`, and `notRequested`. A false legacy Boolean can mean an
intentionally untouched feature; consult `notRequested`, not just that Boolean.
Poll for the matching operation/game and a newer `time`; this v1 contract has
no caller-supplied request IDs. Do not treat a previous terminal result as the
new broadcast's outcome. Read the in-app engine report for device-specific
refusals. Requests are serialized; a concurrent preparation is rejected as `busy`.
`lastDelivery` records the most recent completed service/Activity request,
including `busy`, without replacing the report of an operation still in progress.
One restore can wait behind an in-flight preparation and cancels owned cache
work; duplicate waiting restores return `busy`. This avoids losing a game-exit
request while the game-entry task is still running.
Selection-only requests may change the visible selection while an activation
continues with its originally captured package and tuning.

### Launcher shortcut

The same authorization dialog can request a shortcut for the selected game.
It performs `APPLY_AND_LAUNCH`, not bare launch. The launcher must support pinned
shortcuts and may ask for confirmation; a successful request is not proof it was
pinned. A launcher holding the shortcut also holds its token: trust it as an
automation caller. Revoking access disables these shortcuts; recreate them after
authorizing again. They cannot bypass a required restore or privilege failure.

## Boundaries and evidence

Manual mode activation, the foreground auto-game monitor, Activity/shortcut and
broadcast preparation share `GameAutomation` -> `GamingModeJobRunner` ->
`GamingEngine`, plus the same `GameSettingsStore` and optional preloader.
The auto-game monitor restores sessions it owns; a Tasker/MacroDroid-created
session needs that automation's exit `RESTORE` task (the monitor does not steal
manual ownership). Recovery after interruption uses the existing persisted journal.

Preparation no longer trims other apps' caches, compacts memory, kills all apps,
forces Doze, sets developer lifecycle/process limits, or installs an FPS cap.
Tasker, MacroDroid, known Panda Mouse packages, the active IME and enabled
accessibility-service packages are excluded from suspension/background-data
restriction. Other companions belong in the existing Keep list.
An attached external display suppresses phone-wide refresh/brightness/rotation
overrides; physical resolution Apply/reset is refused with an explanation.
DeX owns its display configuration. CatSmoker does not alter input grants,
inject input, enable overlays automatically, or evade game protections.
See [cache experiment and device test protocol](VMTOUCH_REVIEW.md).

No full-only API is imported and no spoofing, updater, VPN, ad, or purchase
capability is exposed by this contract. Flavor capability seams are unchanged.
The exported components are explicit-only and require the token on every entry,
including queries. Local unit/source tests cover parsing, authorization,
package-addressed storage, selection isolation, and the background receiver
boundary. They do not prove launcher, notification, Tasker/MacroDroid, OEM shell,
process-death, reboot, or device UI behavior; those require on-device checks.

### Community extension verification (2026-11-15)

Both variants compiled; Full debug APK assembly and installation on a POCO X3
NFC / Android 15 succeeded. Fourteen targeted JVM classes passed in each flavor:
**116 tests per flavor, zero failures/errors**. Filters were supplied separately
to each variant's test task. The Play source/API/resource boundary check passed;
this is not Play artifact or upload certification.

Observed on the phone: selected-game highlighting and the existing three-action
menu, package-addressed persistence, bounded cache-only reads, authenticated query
and selection, rejection of a wrong token/unexpected extra, background ADB
preparation admission without a confirmation tap, and missing-privilege reporting.
The service reported `Root or Shizuku permission required`, with no open session;
an exit restore reported `nothing_to_restore`. No privilege grant was invented.
ADB admission does not prove admission from an ordinary Tasker/MacroDroid UID.
The final APK was installed and a passive query confirmed no session/recovery.

The existing token and battery policy were preserved. Experimental options and
selection changed for testing were restored. No successful privileged prepare/
restore, notification fallback, pinned-shortcut confirmation, DeX/Panda input,
process-death/reboot recovery or game-performance benchmark was validated.
See [community setup and feature status](COMMUNITY_GAMING_SETUP.md).

### Earlier verification (2026-11-15; before unattended/cache extensions)

Both `compileFullDebugKotlin` and `compilePlaystoreDebugKotlin` passed.
The ten targeted JVM test classes passed in both variants: 71 tests per
variant, zero failures, errors or skips. They cover persistence/recreation,
package isolation under rapid switching and concurrent edits, default migration,
authentication/protocol validation, passive observation and existing game-grid,
mode-session and intervention behavior. Some UI/ownership checks are source-layout
guards, not device interaction tests.

`python tools/check_playstore_boundary.py --variant playstore` passed its
source/API/resource boundary check; it is not artifact or upload evidence.
No APK assembly, installation, device automation, reboot/upgrade exercise or
performance benchmark was run. Tasker/MacroDroid, launcher confirmation,
notification permission/foreground behavior and privileged readback still
require device validation.

Android references:
[background activity starts](https://developer.android.com/guide/components/activities/background-starts),
[background foreground-service starts](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start),
[pinned shortcuts](https://developer.android.com/develop/ui/views/launch/shortcuts/creating-shortcuts).
Caller configuration references:
[Tasker Send Intent](https://tasker.joaoapps.com/userguide/en/intents.html),
[MacroDroid Send Intent](https://wiki.macrodroid.com/wiki/?title=Action:_Send_Intent).
