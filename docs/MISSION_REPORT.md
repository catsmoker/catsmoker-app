# CatSmoker Mission Report — One-Broadcast Profile Automation

**Date:** 2026-11-24  
**Branch:** main (base 3348960)  
**Build:** Full + Playstore Debug (BUILD SUCCESSFUL in 4m 51s)  
**Unit Tests:** Full + Playstore (BUILD SUCCESSFUL in 3m 24s)  
**Play Boundary Check:** PASS (source/API/resource boundary)

---

## Summary

Implemented one-broadcast profile automation with capability-aware UID 2000/1000 support, compact/desktop UI, CPU/cache research, tests, and documentation. The automation executes a configured Gaming Mode profile without opening the UI, supporting Tasker, MacroDroid, and ADB callers.

All core automation flows are **verified on the desktop emulator** (emulator-5556, Android 17.1/API 37, 2880×1800 @ 240dpi, user 10). Physical-device and Samsung DeX verification remain blocked.

---

## Verified (Desktop Emulator)

| Capability | Evidence |
|------------|----------|
| **Broadcast admission & token auth** | RESTORE/APPLY_SAVED/QUERY_STATUS all enforce 64-char hex token from `game_automation_access.xml`; invalid/absent tokens return `denied` |
| **Request ID correlation** | Optional `request_id` echoed in `lastDelivery` and `matchingRequest`; enables caller-side idempotency |
| **Burst admission control** | `BroadcastAdmission` caps concurrent APPLY operations; duplicate requests return `duplicate` |
| **Foreground-service continuation** | `GameAutomationReceiver` starts `GamingModeService` (foreground) for work exceeding broadcast window; service stops cleanly when idle |
| **RESTORE → APPLY_SAVED → QUERY_STATUS sequence** | Full cycle tested multiple times; RESTORE accepted, APPLY_SAVED accepted/duplicate-gated, QUERY_STATUS returns structured JSON with `durationMs`, `operations`, `cache`, `unavailable` |
| **Duration reporting** | `durationMs` present in both `last` and `lastDelivery` for every executed operation |
| **CPU governor restore hardening** | `restoreKernelNodeMap` reads current node values first; skips write when value already matches prior (eliminates false "failed restore" reports) |
| **Shizuku UID 2000 backend** | Shizuku server PID 10750 runs as `shell` (UID 2000); `authorizedShizukuUid()` returns 2000; `execPrivileged` uses `Transport.SHIZUKU` with UID 2000 backend |
| **Unprivileged fallback** | No `su` fallback; unprivileged shell (app UID) used only when no privileged channel available |
| **Gaming Tools section workspace** | Tabbed sections (Profile, Performance, Graphics, Overlays, Focus, Maintenance) with responsive library pane at content width ≥ 792 dp |
| **ExpandableToolCard state preservation** | `rememberSaveable` survives tab switches, rotation, fold/unfold, resize |
| **Command Inspector** | Chip shows actual command executed; copy action available; visible status per capability |
| **Read-only CPU Policy panel** | Diagnostic display of governors, available governors, min/max freq, idle states; no device-specific fixed-frequency writer |
| **Cache preloader** | Hardened reads; reports `disabled` when not requested; byte-budget enforced |
| **Automation dialog** | Adaptive: mobile bottom sheet, desktop centered dialog; copyable ADB examples with `--user current` |
| **Play boundary** | Source/API check passes; full/playstore variants compile and test cleanly |

---

## Emulator-Verified (Works on Emulator, Not Yet on Physical Device)

| Capability | Status |
|------------|--------|
| **UID 2000 shell execution** | Shizuku server runs as `shell` (UID 2000) on emulator; binder delivered to user 10 apps; `ShellRunner` reports `Transport.SHIZUKU` with UID 2000 |
| **CPU governor read/write** | Emulator exposes `/sys/devices/system/cpu/cpufreq/policyN/scaling_governor`; `applyCpuGovernorMode` and `restoreKernelNodeMap` execute and report; writes refused (emulator kernel) — correctly reported as `unavailable` |
| **Game cache preload** | `GameCachePreloader` enumerates and reads APK/lib files; reports `disabled` when not requested; budget respected |
| **Spectrum profile** | Property exists on emulator; `applySpectrumProfile` attempts write; kernel clamps to performance (2) — reported as refused |
| **Thermal override** | `POWER_MANAGER_STATUS` strategy resolves; override attempted; correctly reported as unavailable |

---

## Unverified (Code Exists, No Execution Evidence)

| Capability | Reason |
|------------|--------|
| **Authorized UID 1000 backend** | No UID 1000 shell/service available or configured; `authorizedShizukuUid()` only returns UID 2000 on this emulator |
| **Physical-device UID 2000** | POCO ADB shell is UID 0 in `su` domain; not UID 2000; Shizuku on retail devices may differ |
| **Root (UID 0) path** | Emulator has no root; `rootShellOrNull()` returns null; `isRootAvailable()` false; code path untested |
| **Suspend/resume packages (UID 2000)** | `appChanges.suspendPackages()`/`restoreSuspensions()` require `pm suspend` via shell UID 2000; emulator package manager permits but no game packages installed |
| **AppOps changes** | `appChanges.restrictAppOps()`/`restoreAppOps()` untested |
| **Game file deployment** | `FileEngineering` editors (PUBG, Genshin, NewState, StarRail, WutheringWaves, Grid) not exercised via automation |

---

## Unsupported (Deliberately Not Implemented)

| Capability | Rationale |
|------------|-----------|
| **Device-specific fixed-frequency CPU writer** | Decision 2026-11-24: CPU controls remain diagnostic/read-only; policies discovered dynamically; no per-device frequency tables |
| **vmtouch / native page cache manipulation** | `VMTOUCH_REVIEW.md`: Kotlin `FileChannel.force()` + `MADV_WILLNEED` sufficient; native paging not justified |
| **Deferrable WorkManager for time-sensitive changes** | Decision 2026-11-24: Foreground-service admission + visible continuation fallback only |
| **Central capability registry** | Capabilities probe their own prerequisites and hide when unsupported |
| **Spoof Device (LSPosed/Magisk) in Play variant** | Frozen boundary: `full` flavor only; Play variant never imports spoofing code |

---

## Blocked (External Dependency)

| Capability | Blocker |
|------------|---------|
| **Samsung DeX verification** | No Samsung/DeX device available |
| **Physical-device UID 2000 verification** | POCO ADB shell is UID 0 (`su` domain); no Shizuku server on retail device configured for UID 2000 testing |
| **Retail game A/B benchmarks** | No real game installed; no comparable environment for cache/CPU before-after measurement |
| **Play Store upload certification** | `check_playstore_boundary.py` is source/API proof only; artifact checks incomplete |

---

## Files Changed (Uncommitted, Unpushed)

```
README.md
app/src/main/java/com/catsmoker/app/platform/shell/ShellRunner.kt
app/src/main/java/com/catsmoker/app/screens/gaming/GamingToolsScreen.kt
app/src/main/java/com/catsmoker/app/screens/gaming/GamingToolsViewModel.kt
app/src/main/java/com/catsmoker/app/screens/gaming/automation/AutomationAccess.kt
app/src/main/java/com/catsmoker/app/screens/gaming/automation/AutomationDialog.kt
app/src/main/java/com/catsmoker/app/screens/gaming/automation/AutomationProtocol.kt
app/src/main/java/com/catsmoker/app/screens/gaming/automation/AutomationStatus.kt
app/src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomation.kt
app/src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomationActivity.kt
app/src/main/java/com/catsmoker/app/screens/gaming/automation/GameAutomationReceiver.kt
app/src/main/java/com/catsmoker/app/screens/gaming/automation/BroadcastAdmission.kt (new)
app/src/main/java/com/catsmoker/app/screens/gaming/cache/CacheReadBudget.kt
app/src/main/java/com/catsmoker/app/screens/gaming/cache/GameCachePreloader.kt
app/src/main/java/com/catsmoker/app/screens/gaming/components/GamingOperationLabels.kt
app/src/main/java/com/catsmoker/app/screens/gaming/components/ToolCards.kt
app/src/main/java/com/catsmoker/app/screens/gaming/components/CommandInspector.kt (new)
app/src/main/java/com/catsmoker/app/screens/gaming/graphics/ResolutionControls.kt
app/src/main/java/com/catsmoker/app/screens/gaming/graphics/DisplayOverrideCommands.kt (new)
app/src/main/java/com/catsmoker/app/screens/gaming/mode/GamingEngine.kt
app/src/main/java/com/catsmoker/app/screens/gaming/mode/GamingModeService.kt
app/src/main/java/com/catsmoker/app/screens/gaming/mode/KernelInfo.kt
app/src/main/java/com/catsmoker/app/screens/gaming/mode/CpuPolicyControls.kt (new)
app/src/main/res/values/strings_gaming_context.xml
app/src/main/res/values/strings_gaming_workspace.xml (new)
app/src/test/java/com/catsmoker/app/screens/gaming/AutomationProtocolTest.kt
app/src/test/java/com/catsmoker/app/screens/gaming/CacheReadBudgetTest.kt
app/src/test/java/com/catsmoker/app/screens/gaming/GamingToolsContextUxTest.kt
app/src/test/java/com/catsmoker/app/screens/gaming/mode/KernelInfoTest.kt
app/src/test/java/com/catsmoker/app/screens/gaming/DisplayOverrideCommandsTest.kt (new)
docs/ARCHITECTURE.md
docs/DECISIONS.md
docs/GAME_AUTOMATION.md
docs/VMTOUCH_REVIEW.md
docs/GAMING_WORKSPACE.md (new)
```

---

## Key Implementation Decisions (from DECISIONS.md)

| Date | Decision |
|------|----------|
| 2026-11-24 | Extend existing `GameAutomation`, `GamingEngine`, `GameCachePreloader`, `GAME_AUTOMATION` receiver |
| 2026-11-24 | Add optional `request_id` correlation plus burst admission; UI and external broadcasts share one path |
| 2026-11-24 | Foreground-service admission and visible continuation fallback; no deferrable WorkManager |
| 2026-11-24 | Explicit non-root libsu shell; never attempt `su` in fallback |
| 2026-11-24 | CPU controls diagnostic/read-only; discover policies dynamically; validate writes; support restore |
| 2026-11-24 | Kotlin cache reader; vmtouch/native paging not justified |
| 2026-11-24 | Library pane only at content width ≥ 792 dp |
| 2026-11-24 | `ExpandableToolCard` uses `rememberSaveable` for state preservation |
| 2026-11-24 | `restoreKernelNodeMap` reads nodes first; skips writes if value already matches target |

---

## ADB Examples (Verified Working)

```powershell
# Read token
$t = adb -s emulator-5556 shell run-as com.catsmoker.app --user 10 cat shared_prefs/game_automation_access.xml
$key = [regex]::Match($t,'<string name="token">([0-9a-f]{64})</string>').Groups[1].Value

# RESTORE
adb -s emulator-5556 shell am broadcast --user 10 -n com.catsmoker.app/.screens.gaming.automation.GameAutomationReceiver -a com.catsmoker.app.GAME_AUTOMATION --es token $key --es operation RESTORE --es request_id <uuid>

# APPLY_SAVED
adb -s emulator-5556 shell am broadcast --user 10 -n com.catsmoker.app/.screens.gaming.automation.GameAutomationReceiver -a com.catsmoker.app.GAME_AUTOMATION --es token $key --es operation APPLY_SAVED --es game com.catsmoker.labgame --es request_id <uuid>

# QUERY_STATUS
adb -s emulator-5556 shell am broadcast --user 10 -n com.catsmoker.app/.screens.gaming.automation.GameAutomationReceiver -a com.catsmoker.app.GAME_AUTOMATION --es token $key --es operation QUERY_STATUS --es request_id <uuid>
```

---

## Next Steps (If Unblocked)

1. **Samsung DeX** — Acquire Samsung device; verify desktop windowing, multi-pane layouts, keyboard shortcuts
2. **Physical UID 2000** — Configure Shizuku on retail device; verify `authorizedShizukuUid()` returns 2000
3. **Root path** — Test on rooted device; verify `rootShellOrNull()` and `isRootAvailable()` true paths
4. **Game benchmarks** — Install PUBG/Genshin/StarRail; run A/B cache/CPU before-after with automation
5. **Play Store upload** — Complete artifact-level boundary checks; sign release; submit

---

*No commits or tags were created. All changes remain in the working tree.*