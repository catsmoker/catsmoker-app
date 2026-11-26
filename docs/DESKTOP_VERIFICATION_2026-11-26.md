# Desktop and automation verification, 2026-11-26

Runtime verification of the one-broadcast profile path, the Shizuku (UID 2000)
backend and the desktop Gaming Tools workspace on the Android desktop emulator
(`CatSmoker_Desktop_37_1`, 2880x1800 @ 240 dpi, Android 17.1 emulator image).
Artifacts are labelled by the kind of evidence behind them. Screenshots are
local only and are not committed.

## What was executed

| Step | Command / action | Result |
| --- | --- | --- |
| Build | `:app:assembleFullDebug :app:testFullDebugUnitTest --tests '*Gaming*'` | BUILD SUCCESSFUL (4m 52s) |
| Build | `:app:assemblePlaystoreDebug :app:testPlaystoreDebugUnitTest --tests '*Gaming*'` | BUILD SUCCESSFUL (5m 18s) |
| Build | `:app:assemblePlaystoreRelease` | BUILD SUCCESSFUL; 4 split APKs + `mapping.txt` |
| Artifact check | `check_playstore_boundary.py --variant playstore --archive <each playstore release APK> --mapping ...` | PASS on all four APKs and on the R8 original-name inventory |
| Source check | `check_playstore_boundary.py --variant playstore` | PASS (source/API/resource only) |

The artifact run is **partial evidence**: it proves the shipped dex type
tables, R8 map and packaged natives, but the complete input set (bundletool
resource dump, manifest copy, resolved dependency exports, source-input list)
was not produced for that run. The complete set was produced afterwards — see
[PLAY_ARTIFACT_EVIDENCE_2026-11-26.md](PLAY_ARTIFACT_EVIDENCE_2026-11-26.md) —
for a newer artifact (the AAB hashes there supersede the one above, because a
later `abiFilters` change rebuilt both bundles), and a fresh AAB is still
required after any code change.

## Broadcast automation (verified end to end)

Privileged backend: Shizuku server started over adb and running as `shell`
(UID 2000), permission granted to the app from the Permission screen; the
Automation dialog reports `Shizuku · UID 2000`.

| Case | Result |
| --- | --- |
| Wrong token | `{"result":"denied"}` |
| `SELECT_GAME` with `request_id` | `{"result":"selected","operation":"SELECT_GAME","game":"com.catsmoker.labgame","request_id":…}`; the dialog header and the workspace switched to that game |
| `APPLY_SAVED` with `request_id` | `{"result":"accepted"}`; foreground `GamingModeService` started |
| Same `request_id` again | `{"result":"duplicate"}` — admission gate, nothing re-executed |
| `APPLY_SAVED` with no privileged backend (earlier run, Shizuku not yet granted) | `{"result":"failed","error":"Root or Shizuku permission required","durationMs":29}`, `mode:"Error"` — honest failure, no false success |
| `APPLY_SAVED` with UID 2000 | `{"result":"partial","durationMs":15440,"operations":{"backgroundDataRestricted":true,"fixedPerformance":true,"lockedRefreshHz":60,"networkWhitelisted":true,"suspendedPackages":7,"touchResponseBoost":true,"unavailable":["Silencing notifications — …","Performance governor - your phone refused the switch"]}}` |
| `QUERY_STATUS` after that | `mode:"Active"`, `sessionOpen:true`, `unavailableCount:2`, `cache:{"result":"disabled"}` |
| `RESTORE` | `{"result":"restored","durationMs":5456,"operations":{"suspendedPackages":0,…}}`; `mode` back to `Idle`, no owned changes left to restore |
| UI sync | The Automation dialog's "Last action result" and "Latest delivery" showed the broadcast results without the UI having triggered them |

Suspending 7 packages and writing `settings`/refresh-rate values is exactly the
work an ordinary app cannot do, so this run also proves the UID 2000 backend
end to end on the emulator. It does **not** prove UID 1000 (never available) or
the UID 0 path (no root on this emulator).

## Desktop workspace

| Check | Evidence |
| --- | --- |
| 1200x750 dp window | Library pane on the left (278 dp), section chips in one scrollable row, content fills the rest |
| Performance section, wide | Two columns: CPU policies + Volume Boost left, App Compile Optimisation + Developer Options right, equal widths, no clipped text |
| 380x800 dp window (same process, resized with `wm size`/`wm density`) | Library pane hides, tabs scroll, Performance collapses to a single column, selected game and open card survive the resize |
| 1200x750 dp again | Selected game (`CatSmoker Lab Game`) and the open CPU card still present |
| CPU panel | Policies discovered from sysfs at runtime (`policy0…policy3`), driver, governor, related/affected CPUs, scaling and hardware limits, frequency table and available governors listed; the panel states that the reported frequency is not the hardware clock |

Two defects were found and one was fixed in this change:

- **Fixed:** Performance rendered a single full-width card strip at 1200 dp,
  wasting the right half of the workspace. It now uses two columns at the same
  792 dp breakpoint that shows the library pane. Narrow windows are unchanged.
- **Not fixed:** after changing the emulator's display size while the app was
  running, the app window rendered blank until it was force-stopped and
  restarted. This looks like a desktop-windowing/emulator artifact rather than
  app state loss (the state survived once the window re-attached), but it is
  unproven and is recorded here rather than hidden.

## CPU frequency

Read-only by decision (see `DECISIONS.md`, 2026-11-24). This run confirms
discovery works on an emulator whose `cpufreq` driver is `dummy`: writes are
refused by the kernel there, so a writer could not be verified on this host.
No fixed-frequency writer was added, and no per-device preset table exists.

## Cache preloading

Reported `{"result":"disabled"}` because no preload was requested in the saved
profile. The budgeted reader and its tests are unchanged from the previous
session; no new benchmark evidence was produced here, so the feature stays
experimental.

## vmtouch

No new work. The previous review (`docs/VMTOUCH_REVIEW.md`) concluded that
Android's `FileChannel.force()` + `MADV_WILLNEED` cover the useful part of what
vmtouch does for a preload pass, and that `mlock`-style pinning is not
appropriate as a default. Nothing in this session contradicts that.

## Not verified

| Item | Why |
| --- | --- |
| Samsung DeX | No Samsung hardware; the desktop emulator is a different windowing implementation |
| UID 1000 backend | No authorized UID 1000 shell on this emulator |
| UID 0 path | Emulator has no root |
| CPU frequency writes / restore | Emulator kernel refuses writes |
| Real game A/B benchmarks | No retail game installed |
| Play upload certification | Partial artifact evidence only (see above) |
| Third-party callers (Tasker, MacroDroid) | adb broadcasts were used; a third-party app's own delivery path was not exercised |

## Files touched in this session

```
app/src/main/java/com/catsmoker/app/screens/gaming/GamingToolsScreen.kt  (Performance: two columns when wide)
docs/DECISIONS.md                                                        (2026-11-26 entry)
docs/GAMING_WORKSPACE.md                                                 (Performance layout note)
docs/DESKTOP_VERIFICATION_2026-11-26.md                                  (this record)
```

No commits, tags or pushes were created.

## Full JVM suite

Both flavors were run once at the end: `:app:testFullDebugUnitTest` (1,366
tests in 192 classes) and `:app:testPlaystoreDebugUnitTest` (1,071 tests in 165
classes), zero failures, errors or skips. The focused `--tests '*Gaming*'` runs
earlier in the session covered the changed Compose screen and the ABI/packaging
change is covered by the artifact audits above, so no other suite was expanded.

## Desktop UI pass: tile badges and the Settings two-column layout

Two defects reported against the desktop build were fixed in shared UI code and
re-checked on the same `CatSmoker_Desktop_37_1` emulator (2880x1800, 240 dpi).

- `QuickActionButton` drew the glyph at Material's fixed 24 dp inside a hand-sized
  badge, so a wide tile showed a phone-sized icon in a large rounded square. The
  badge now scales the icon's density (`min(0.56 x badge, 24 dp)`), the chevron
  follows the badge (`0.45 x badge`, floor 20 dp), and the Home dashboard chooses
  the badge from the window width (40 / 46 / 56 / 64 dp for < 600 / >= 600 /
  >= 840 / >= 1200 dp). Observed at 1920 dp: badges 64 dp, glyphs legible, chevrons
  still subordinate to the title.
- `SettingsScreen` capped one column at 720 dp and centred it, leaving two empty
  strips. At >= 792 dp it now renders two columns (General + Updates left;
  Privacy & Data, Help & Info, About right), each capped at 720 dp, whole block
  capped at 1680 dp. Measured bounds: both section labels on the same top line
  (y = 168 px), left column ending at y ~ 930 px, right at y ~ 1000 px.
- Phone behaviour unchanged: at 1080x2400 (411 x 914 dp) Settings stays a single
  full-width column and the Home tiles keep their 40 dp badges with no clipping.

Unit tests: both flavors re-run after the change (1,366 / 1,071 tests, zero
failures). `ScreenOwnershipTest` caught a CRLF line-ending regression introduced
while editing those three files; all three were normalised back to LF before the
final run.

## Gaming Tools Profile at desktop width

Checked the two QuickActionButton hosts that the badge change also reaches, and
one section that the earlier two-column pass had missed.

- File Engineering and Gaming Tools do not use `QuickActionButton` tiles; File
  Engineering's picker screen has no icon grid and Gaming Tools uses its own
  `ExpandableToolCard` family, so no badge regression exists there.
- Gaming Tools' Profile section was still one full-width stack: at 2880x1800 the
  Gaming Mode card spanned ~2,300 px, title at one edge and toggle at the other.
  It now uses two columns above a 1200 dp window (Gaming Mode left at weight
  1.15, Fixed Performance + cache preloading right), measured at that size and
  re-checked at phone width (1080x2400), where it stays a single column.
- Observed but not changed: File Engineering's empty state is one selector card
  stretched across the window (~2,660 px text field) with nothing below it. That
  is an empty-state layout, not a tile-scale defect; capping the card measure
  would leave symmetric dead space, so it needs a product decision rather than a
  silent fix.

## All six Gaming Tools sections at 1920 dp

| Section | Cards | Result |
| --- | --- | --- |
| Profile | 3 | Two columns (Gaming Mode left; Fixed Performance + cache right) |
| Graphics & Display | 1 | Single card; body fields capped at 520/260 dp, Apply+Reset at 360 dp |
| Performance | 4 | Two columns (existing) |
| Overlays | 2 | Two columns: FPS overlay left, crosshair right |
| Network & Quiet | 4 | Two columns: DND + Background Data left, Background Apps group right |
| Maintenance | 1 | Single card; no full-width strip to split |

Measured on the emulator at 2880x1800: Overlays titles at x = 617 / 1796 px,
Network left column at 617 px and Background Apps group starting at 1,826 px,
Graphics fields 381 px wide (254 dp) with DPI at 390 px and the Apply/Reset pair
inside a 360 dp row. Phone width (1080x2400) re-checked: every section is a
single column again and the caps do not bite.

## Breakpoint and text-scale matrix (not just the desktop AVD)

The desktop AVD alone could not prove the breakpoints, so the same build was run
on `OBD2AI_Tablet_37` (2,560x1,600) at three densities plus the desktop AVD,
which is a genuine set of window widths rather than one resized emulator.

| Surface | Window | Text scale | Settings | Gaming Tools cards |
| --- | --- | --- | --- | --- |
| Desktop AVD | 1920 x 1200 dp | 1.18 | 2 columns | 2 columns |
| Tablet @320 dpi | 1280 x 800 dp | 1.10 | 2 columns (labels at x=56 / 1312) | 2 columns (Profile: x=710 / 1826) |
| Tablet @480 dpi | 853 x 533 dp | 1.10 | 2 columns | single column, library pane kept |
| Tablet @640 dpi | 640 x 400 dp | 1.0 | single column | single column, no library pane |
| Phone size on desktop AVD | 411 x 914 dp | 1.0 | single column | single column |

Text scale is measured, not assumed: tile title height is 33 px at 1280 dp
(density 2.0, factor 1.10) and 61 px at 640 dp (density 4.0, factor 1.0), both
15.25-16.5 dp of text. The tablet AVD was a clean install, so the first-run
onboarding was walked through on it (three pages, agreement required) rather
than bypassed.

## File Engineering: rail instead of a modal (1920 dp) and an honest trigger (phone)

- Desktop 1920x1200 dp: left rail renders all games with per-game install dots, PUBG
  collapsed to one group entry, installed games sorted first, selected row highlighted
  with a check (measured: rail rows at x=132-357, workspace pane from x=533). Choosing
  PUBG Mobile in the rail reveals the VERSION chips (Global/KRJP/Vietnam/BGMI/Rekoo/
  Flash) in the pane, so the game → variant order survives without the dialog.
- Phone 411x914 dp: no rail; the trigger is a bordered action row reading "Tap to
  choose a game" (no text-field chrome), and the dialog still lists the same rows with
  icons, install dots and Cancel.
- The desktop workspace now uses the width it has: the previous 2,660 px stretched
  picker card is gone, replaced by a 278 dp rail plus a content pane.

## Rail badges after the monogram change

Measured in the rail at 1920 dp: badges render `PB`, `GE`, `NS`, `HS`, `WU`, `GR`, `RO`,
`GL`, `AL`, `SR`, `NA`, `BM` at x=64-104, so the rows are distinguishable and the
GRID / GRID Legends pair no longer collides (`GR` vs `GL`). With no `GameType` installed
on the emulator every row exercises the monogram path; the installed path still calls the
`PackageManager` icon lookup, which the dialog used before this change.

## Installed-icon branch and per-game accents verified

The installed path of the badge had never been exercised because no `GameType` package was
installed on the emulator. A minimal stub (`com.roblox.client`, 8.6 KB, one empty class,
own icon, debug-signed) was built with `aapt2`/`d8`/`apksigner` and installed; the rail
then sorted Roblox first with its real gradient icon and a green install dot, and no
monogram, proving `GameBadge` takes the `PackageManager` branch when a game is present.

Accents after the change, observed in the rail: amber (PUBG), teal (Genshin), blue
(New State), violet (Star Rail), cyan (WuWa), orange (GRID), amber-brown (GRID Legends),
green (Alien), red (Saints Row), green (Napoleon), coral (Hitman), olive (Guardian of
Light), purple (Temple of Osiris), pink (Railroads), grey-green (Tomb Raider) — no shared
grey bucket. The stub is left installed on the desktop AVD for future runs.

## Rail filter at 1920 dp

Driven on the desktop AVD, measured from the accessibility tree:

| Query | Result |
| --- | --- |
| `tomb` | one row, "Tomb Raider" (monogram `TR`) |
| `pubg` | two rows, "PUBG Mobile" and "PUBG New State" |
| `zzzz` | no rows, text "No games match that filter." at y=349 |
| cleared | full 33-row list returns, installed games first |

The field renders at y=247-331 as "Filter games" with a search icon and a clear button.
