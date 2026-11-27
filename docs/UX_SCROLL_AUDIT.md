# Scroll and navigation audit, 2026-11-26

Runtime audit of how much movement CatSmoker asks for, what was changed, and what the change
measured. Numbers come from the running app over adb (`tools/ux_audit/measure_scroll.py`), not
from reading source. Screenshots and raw UI dumps are **local only** and are not committed.

## Third pass: physical device and the remaining list

This pass ran on a **Poco X3 NFC (`M2007J20CG`, Android 15, 393x873 dp, root granted)** as well as
the emulators. It is narrower than the 411 dp emulator, so every "it fits" claim below is the
tighter case. Builds installed with `adb install -r app-full-universal-debug.apk`.

| Surface | Device result |
| --- | --- |
| Gaming Tools — six section tabs | 6 of 6 visible at 393 dp; at **font scale 1.3** captions would ellipsize, so the row goes icon-only and keeps the labels as content descriptions |
| Gaming Tools — Profile section | 2 swipes / 1322 px (PUBG installed, so the library is populated) |
| File Engineering — PUBG editor | 2 swipes / 2482 px at font scale 1.3 |
| Star Rail editor (root not yet granted to the app) | 5 swipes / 6688 px; bar present, `APPLY TO GAME` reachable |
| Wuthering Waves | bar present with one `DEPLOY`; the sheet offers both channels |
| Tap targets (393 dp) | none below 48 dp; the two Material icon buttons audited report a 40 dp shape and are listed as informational because the component expands them |
| Tabs at font 1.3 (uiautomator) | six tabs, each `content-desc` = its caption, `selected` on exactly one |
| Desktop File Engineering | editor capped at 1040 dp: widest text ends at x=1707 in a 2856 px window |

Delivered in this pass beyond the scroll work:

- **Find a tool** (`screens/gaming/components/ToolSearchDialog.kt`): 15 controls searchable by
  their own translated name and subtitle; each row names the section it lives in, and choosing one
  switches to that section. Verified on the device: "Refresh rate" landed on the Display section.
- **Wuthering Waves' deploy** is reachable from the bar: one `DEPLOY` opening a sheet with the
  shell and SAF channels. It is the editor with four targets, so the bar carries a chooser rather
  than pretending they are one action. The undo/redo arrows are hidden there (`showHistory = false`)
  because a generated config has no history to undo.
- **Desktop File Engineering** caps the editor column at 1040 dp; the game rail stays where it is
  and the form keeps a reading width instead of stretching across the whole window.
- **`tools/ux_audit/a11y_audit.py`**: reports the screen size in dp, the font scale, every custom
  clickable below 48 dp (a real finding) separately from Material shapes the component expands
  itself, and text drawn past the window edge. It re-reads the tree once before reporting, because a
  dump taken mid-transition reported three phantom 32 dp targets on a screen whose tabs are 56 dp.
- **`tools/ux_audit/screenshot_regression.py`**: capture/compare against a local baseline with a
  pixel tolerance; proved by capturing a screen twice (0.00% changed, PASS).
- **Dead code removed**: `GamingToolSheet.kt` (43 lines) existed only as something a test asserted
  was unused.

One defect this pass found and fixed, which the automated measuring could not see: at font scale
1.3 the six tab captions were **ellipsized** ("Prof…", "Displ…"). The bounds dump said they fitted,
because a clipped label reports the *cell's* bounds; only the screenshot showed the ellipsis. Above
1.15x the row now drops captions and publishes them as content descriptions instead — sight gets six
unclipped icons, TalkBack still announces "Display, tab, 2 of 6". Same reason the game grid drops
from four columns to three at that scale.

### Editor sub-tabs (same session, same device)

The three long editors were one scrolling form each. Each now shows one group of cards at a time,
with the deploy bar pinned underneath. Measured on the Poco with `measure_scroll.py`, per view:

| Editor | One form (before) | After, per tab |
| --- | --- | --- |
| GRID Autosport | 5 swipes / 7722 px | Display 1 / 507 · Quality 2 / 1996 (rows collapsed, see below) · Switches 1 / 940 |
| Star Rail | 5 swipes / 6691 px | Display 2 / 1673 · Quality 3 / 3660 · Prefs 0 / 0 |
| Wuthering Waves | 4 swipes / 4664 px (Advanced collapsed; ~9.5k px expanded) | Preset 2 / 2499 · Display 1 / 1020 · Advanced 2 / 2838 · Tools 1 / 1464 |

The grouping is the same question in all three: **how fast** (frame rate, resolution, render scale),
**how it looks** (quality ladder, effects, presets), and **the rest** (per-file switches, engine
levers, QoL keys). WuWa adds TOOLS for packs, backups, the installed profile and auto-tune, because
those are operations rather than settings.

Two details worth keeping:

- **The row is in the flow, not pinned.** The bar is pinned because a write must be reachable from
  anywhere; a tab row is reachable with one swipe, and pinning it would cost 56 dp of every view.
- **Switching returns to the top.** The scroll owner publishes `LocalEditorScrollToTop` and the row
  calls it, because the new group is usually shorter and the old offset landed mid-card.

Two defects found while verifying this on the device, both invisible to the measurement tool:

- At font scale 1.3 the WuWa recommendation headline broke mid-word ("Recomme / nded:") because the
  headline and USE RECOMMENDED shared a row. Past 340 dp of available width the button now moves
  below the sentence; the trigger is width, not font scale, so a longer translation is covered too.
- WuWa's ADVANCED and TOOLS tabs had the same wrench icon; TOOLS is now a crossed hammer and wrench.

### Wuthering Waves' deploy card

The deploy card is visible from every tab, and it had kept the two channel buttons it owned before
the bar existed — a second path to the same write, next to the sheet that offers both channels with
the explanation. Embedded, the card now shows its report, the shader-cache clean and a collapsed
"what a deploy writes"; standalone routes (`onBack != null`, where no bar is drawn) keep both
buttons. The three-line explanation became a `CollapsibleExplainer`, which is the same text one tap
away instead of three lines charged to every tab. Every tab lost ~400 px with it.

### GRID's quality ladder

Sixteen axes times five tiers was eighty chips on one card — four screens of scrolling, and the
question a tuning screen must answer first ("what is it set to now?") was the one you had to scroll
to answer. Each row is now closed by default and reports the axis and the tier it will write; tapping
it swaps in the five tiers. Both were verified on the device: the closed list (2 swipes / 1996 px,
down from 4 / 4528) and an opened row showing `ultralow … ultrahigh`.

Costs one tap to change a tier, and buys a list where all sixteen current tiers read at a glance.
The value column shows the *pending* choice when one is queued (`ladderRowValue`), so the list can
never contradict the deploy bar, which counts those pending edits.

### The permissions step

A screenshot review of the first-run permissions screen found three things worth fixing, all of them
visible on the device and none of them copy:

| | Before | After |
| --- | --- | --- |
| Scroll to the end of the list | 1085 px | **852 px** (−21 %) |
| Essential / System / Additional tile height | 86 dp | **72 dp** |
| Air between a tile's title and its status line | ~39 dp | ~25 dp |
| Granted tile's border | accent red | **green tint** |

- **A granted tile wore the accent colour.** Red is this screen's "act on me" colour, so the Shizuku
  tile — green dot, green check, status "Granted" — sat inside a red border and read as an error.
  Granted tiles now take a tinted `PermissionGrantedGreen` border; ungranted keep the neutral outline.
- **The tiles carried a hole in the middle.** A fixed 86 dp height with a `Spacer(weight)` between
  title and status meant two lines of text floated 39 dp apart. 72 dp keeps the status rows aligned
  at each tile's bottom (which is what makes the grid scannable) without the gap.
- **Two lines of text were below the app's own floor:** the status line at 10 sp and the `GRANT`
  affordance at 9.5 sp. Both are 11 sp now, and the state dot went from 45 % to 70 % alpha — "Not
  running" and "Unavailable" were the two states that most needed to be told apart from "Not
  granted".
- **The last group sat flush against the pinned footer**; the list ends with 16 dp of its own now.
- **The step counter** ("3/3") ended exactly on the content edge, where the dot-matrix glyph's final
  stroke reads as a clipped digit. Two dp of end padding.

### The header of the permissions step

The step is a checklist, but it wore the welcome screen's hero header: 20 dp above the progress row,
18 below it, a title row 48 dp tall because a refresh button sat in it, and a three-sentence
paragraph. Measured top-down on the device:

| Element | Before | After |
| --- | --- | --- |
| Step subtitle ("Grant permissions to enable all features") | 2 lines | **gone** — it restated the title |
| Title row | 48 dp tall, text at the top | text-sized, refresh moved beside the step counter |
| Intro paragraph | 2 lines | **1 line** |
| First card starts at | y = 565 px | **y = 490 px** |

Three changes carried it: the subtitle is no longer passed (a blank subtitle now renders nothing
instead of an empty gap), `dense` steps use 6/8/4 dp header spacers instead of 20/18/16, and the
step's refresh action moved from the title row to the step row (`progressTrailing`), which stopped a
48 dp button from making the title row 48 dp tall and leaving a band of nothing under the words.

The paragraph itself was shortened from three sentences to one —
"Optional, on device only. Skip anything unnecessary." — **in all seven locales**, because a
one-language edit here would leave the other six describing the previous behaviour. The fact that
"Save applies what you tick, Allow grants one now" is no longer spelled out in the paragraph is
covered in place: every row says "Selected · Save to grant" and the two Android rows carry an
`Allow & open settings` button.

### Filling the screen instead of leaving a hole

Trimming the header left ~200 dp of empty screen under the last tile row. Shrinking the step to fit
had been the wrong instinct once it *did* fit: the controls kept their "as small as possible" sizes
and the slack collected at the bottom.

- **Tiles 60 → 92 dp and consent rows 48 → 60 dp**, so the permissions take the height that is there.
- **A tall tile centres its two rows.** The old layout pinned the title to the top and the status to
  the bottom with a weight spacer, which turned the extra height into a hollow middle; centring makes
  the same height read as a roomy control.

Result: content ends 50-60 px above the pinned SAVE button — the pre-CTA margin is a little larger
than the gaps between rows, and nothing is left empty. Swipe plus pixel diff: 0.00 %.

### The three consent rows are not decoration

The "optional gaming access" block is the biggest thing on the step, so it is the first candidate for
deletion — and it cannot go. Those three checkboxes are the consent the runtime asks about before it
reads anything sensitive:

| Consent | Checked by |
| --- | --- |
| Installed apps | `GamingEngine.hasInventoryConsent`, `CleaningFeature`, `GamingToolsViewModel` |
| Foreground app | `GameSessionService`, `AutoForceStopService` |
| Dismiss notifications | `GamingNotificationListener`, `GamingEngine` |

Each is a stored answer that the feature must find before it touches the Android permission, which is
why the two exist side by side: Android's grant says the app *may*, the checkbox says the user *wants*
it here. Deleting the checkboxes would let a granted permission act with no recorded intent.

So they were made cheaper instead: **three tiles became one card with three rows**, and the Android
request moved from a full-width button band onto the row it belongs to, drawn as a bordered pill
because in this screen's plain link style it read as another status label.

The last steps were layout, not copy: **System and Additional are drawn side by side**, each a
one-column stack of its three tiles (three tile rows instead of four), **tiles are 60 dp** rather
than 72, tile spacing 4 dp, and the list keeps no padding for a scroll it no longer has. On a
393 x 873 dp screen the last row ends ~150 px above the pinned CTA.

**A correction worth recording.** An earlier pass reported this step as fitting when it did not: the
measurement helper returned "0 swipes" while the last row's status row was still clipped 8 px below
the viewport. `verticalScroll` advertises a scrollable node whether or not there is anything to
scroll, and the helper's swipe heuristic read that as nothing to do. The check that actually settles
it is a swipe followed by a pixel comparison of the whole screen
(`screenshot_regression.py`: 0.01 % changed, i.e. the clock), plus the geometry of the last node
against the viewport.

The overscroll rubber band is disabled for this list (`overscrollEffect = null`). It was the last
thing that still looked like scrolling on a screen that fits. Content that genuinely overflows — a
larger font scale, a short landscape window — still scrolls, just without the stretch; nothing here
shrinks text to win a measurement.

| Permissions step | Before | After |
| --- | --- | --- |
| Scroll to the end of the list | 1085 px | **0 px — the step fits one screen** |
| Cards in the consent block | 3 | 1 |
| Request buttons outside the rows | 2 | 0 |

Left alone deliberately: the status vocabulary ("Selected · Save to grant", "Android access: not
granted"). Rewording English copy would leave six translations saying the old thing, and this screen
is one of the seven locales the parity test covers — that is a translation change, not a layout one.

### Empty space inside the cards

A screenshot review of the PUBG editor called the PROFILE card "stretched, sparse": its radio rows sat
68 dp apart for a 24 dp line of text, and every card carried 20 dp of padding on all four sides with
12 dp between cards. Measured, then tightened:

| Surface | Before | After |
| --- | --- | --- |
| File Engineering — PUBG | 2 swipes / 1923 px | 2 / **1744** (−9.3 %) |
| Gaming Tools — Profile | 1 / 1256 px | 1 / **1159** (−7.7 %) |
| Wuthering Waves — Preset | 2499 px | **2303** (−7.8 %) |
| Settings | 1300 px | 1300 (unchanged, see below) |

What changed, and why each number:

- **`SectionCard` content padding 20 → 16 dp.** The corner radius is 20 dp, so 16 keeps content
  inside the curve while giving back 8 dp of height per card.
- **`CatsmokerSpacing.CardGap` 12 → 8 dp.** Two cards already carry 16 dp each, so the visible band
  between two blocks of text was 44 dp — wide enough to read as a hole rather than as a boundary.
- **Radio and picker rows: 10 → 4 dp of their own padding.** The `RadioButton` already brings a 48 dp
  touch target, so the row was 68 dp for a 24 dp line. Now 56 dp, still above the accessibility floor.
- **8 dp above the section pane in Gaming Tools.** The first card's top edge touched the tab row's
  bottom edge exactly, and the two read as one crowded block.

Settings is unchanged on purpose: its cards pass `contentPadding = 0` and draw their own rows, so
the card default never reached them. Its density is set by those rows and was not part of the
complaint.

### What a screen reader hears

`tools/ux_audit/a11y_names.py` reads a `uiautomator dump` and asks the one question that does not need
a person: does this clickable announce anything? TalkBack merges a node's own text with its
descendants', so an icon-only `IconButton` whose glyph is null is silent while a chip's `Text` is not.

First sweep on the Poco — six Gaming Tools sections, File Engineering and its three editors, Settings,
System Logs, Crosshair — found **26 controls with no label**, all of them the same shape: a setting's
words live in a sibling `Text`, and Compose binds nothing to the `Switch` beside it. TalkBack read
"on, switch" and no setting name.

Fixed by `ui/components/LabelledSwitch.kt`, which takes the label as a required parameter and attaches
it as the switch's content description. Every switch that owns its state now goes through it — seven
screens, one helper — and `LabelledSwitchTest` sweeps the sources so the next one cannot skip it. A
switch with `onCheckedChange = null` is driven by a parent that already carries the words and is not
flagged.

Two more defects the same sweep found:

- **`ExpandableToolCard`'s leading icon was its own clickable** with a null description, so every one
  of the ~30 tool cards offered an unnamed target. It is decorative now; the title beside it is the
  target, which also removes a second control with the same name.
- **The chevron button** on those cards was a third clickable for the same action. It is drawn as an
  affordance only.

Result after the fix: **0 unlabelled controls** on every screen audited — Home, the six Gaming Tools
sections, File Engineering, GRID / Star Rail / Wuthering Waves (all ten editor views), Settings,
System Logs and Crosshair.

`tools/ux_audit/a11y_audit.py` stopped reporting targets that are cut by their scroll parent: a chip
flush against the viewport edge reports its visible height (18 dp of a 48 dp chip) and is not a
finding. It now skips them instead of asking for a fix that does not exist.

## Method

For each surface: launch it, screenshot it, dump the uiautomator hierarchy, then swipe from the
middle of the largest scroll container until the content stops moving. Two numbers are recorded:

| Metric | Meaning |
| --- | --- |
| `swipes_to_bottom` | Full-height swipes a thumb needs before the content stops moving |
| `scroll_distance_px` | Total pixels the content travelled while doing so |
| `hidden_horizontal_px` | Content width past the right edge of a scroll container |
| sections in the tree | Section labels present in the accessibility hierarchy without scrolling |

Devices:

| Role | Device | Viewport |
| --- | --- | --- |
| Phone | `Medium_Phone_API_37.0` emulator, Android 17 | 411 x 914 dp (1080x2400 @ 420 dpi) |
| Small phone | same emulator, `wm size 840x1680` | 320 x 640 dp |
| Desktop | `CatSmoker_Desktop_37_1` emulator, Android 17 | 1920 x 1200 dp (2880x1800 @ 240 dpi) |

Not verified: any physical device run, and Samsung DeX on real Samsung hardware. Stock Android
desktop mode is not DeX; nothing here is claimed about DeX.

## Baseline (before the change)

| Surface | swipes | distance | Finding |
| --- | --- | --- | --- |
| Onboarding: Terms (2/3) | 0 | 0 | Fixed-footer frame; content fits |
| Onboarding: Permissions (3/3) | 0 | 0 | Fits at 411 dp with the action row visible |
| Home (phone, portrait) | 0 | 0 | Weighted, non-scrolling by design |
| Home (desktop 1920x1200) | 0 | 0 | No scroll — but four tiles measured ~700x830 dp of mostly empty gradient |
| Gaming Tools: Profile | 1 | 450 | Section strip is a `HorizontalScrollView`: 954 px visible, content ≥ 2000 px |
| Gaming Tools: Display | 0 | 0 | Cards collapsed, fits |
| Gaming Tools: Performance | 0 | 0 | Cards collapsed, fits |
| Settings | 1 | 1011 | Five card groups, one swipe to the version footer |
| File Engineering (no game) | 0 | 0 | Fits |

The section strip is the one measurement that changed shape rather than size, so it was
measured differently: `uiautomator` listed **three of six** section labels
(`Profile`, `GRAPHICS & DISPLAY`, `PERFORMANCE`), the last one clipped at the viewport's right
edge (bounds ended at x = 1017, the row's own edge). The remaining three sections did not exist
in the hierarchy at all until the strip was scrolled sideways — so they were reachable by touch
and not by TalkBack.

## After the change

| Surface | swipes | distance | Sections in tree | Notes |
| --- | --- | --- | --- | --- |
| Gaming Tools, 411 dp | 1 | 249 | **6 of 6** | One equal-width tab row, no horizontal scroll container; a duplicated "GAMING MODE" heading removed |
| Gaming Tools, 320 dp | 1 | ~450 | **6 of 6** | Same row, labels not ellipsized |
| Gaming Tools, 320 dp @ font scale 1.3 | — | — | **6 of 6** | Labels still fit |
| Gaming Tools, desktop | — | — | **6 of 6** | Sections moved to a vertical rail beside the workspace |
| Settings, 411 dp | 1 | 1011 | — | Unchanged: the content is genuinely five groups |
| Home, phone portrait | 0 | 0 | — | Unchanged |
| Home, phone landscape | 0 | 0 | — | Unchanged; verified after the refactor |
| Home, desktop | 0 | 0 | — | Board capped at 1040 dp, centred; tiles 152 dp tall instead of ~830 dp |

`hidden_horizontal_px` for the workspace is now 0: the tab row is a plain `Row` of six weighted
cells, so there is nothing to scroll sideways.

## File Engineering editors (second pass)

Same method, one game at a time, with the editor forced open through "Show anyway" because no
game is installed on the emulator. Swipes are the primary number; the pixel totals are
approximate because a swipe that overshoots the bottom is clamped and the last part of its travel
is not counted.

| Editor | swipes before | after | Content notes |
| --- | --- | --- | --- |
| PUBG Mobile (profile editor) | 2 / 2368 px | **2 / 1975 px** | Version chips (2 rows), custom upload, save editor |
| Genshin Impact | 0 / 0 | 0 / 0 | One profile, no upload, no save editor |
| Honkai: Star Rail (embedded) | 6 / 6588 px | **5 / 6588 px** | Frame rate, render scale/sync, deploy, game preferences |
| Wuthering Waves (embedded) | — | 4 / 5885 px | Smart recommend, presets, cvars |
| GRID Autosport (embedded) | — | 5 / 7802 px | Frame rate, resolution, quality ladders |

What was fixed in this pass:

- **The install card collapsed once the override is in force.** It stayed full size above the
  editor it had just unlocked — 510 px on a phone below the game selector — explaining a
  situation the user had already acted on. It is now a two-row card (fact + both recovery
  actions) at 171 px: −339 px for every profile editor, −1 swipe on Star Rail, and PUBG went
  from 2368 px to 1975 px.
- **The store button no longer wraps.** "OPEN PLAY STORE" is longer than "RE-CHECK" and wrapped
  to two lines in an equal-weight row, leaving the two buttons at different heights.
- **"Show anyway" is not offered after it has been used.** The forced-open branch still passed the
  override callback, so the card kept a button that could change nothing.

### Persistent deploy bar

The per-game editors are composed inside File Engineering's single scroll container, and their
apply button sat wherever their "DEPLOY" card fell — about 40% of the way down a 5-swipe form.
`editor/DeployBar.kt` adds the seam that moves it out: the editor publishes `DeployView` (label,
dirty, busy, pending count, undo/redo availability) and `DeployActions` to a `DeployHost` the
screen owns, and the screen draws `DeployBar` as a sibling of the scrolling column. Publishing is
idempotent (the host compares before writing, and callbacks live outside the compared data), and
an editor leaving the tree clears the bar, so a stale APPLY can never be pressed for the game you
just switched away from — verified on the device by switching GRID → Genshin and finding the bar
gone.

| Editor | Apply reachable without scrolling | Content height change |
| --- | --- | --- |
| Star Rail | yes (bar pinned at 2227 px on a 2400 px screen) | −393 px of scroll range |
| GRID Autosport | yes | −80 px of scroll range |
| Wuthering Waves | no — excluded | its deploy card carries four target buttons (shell, SAF, variant, stripped), which is not one primary write |

The bar does not reduce the number of swipes (the form is the same length); it removes the
scrolling *between* an edit and the action that writes it. Restore backup keeps its own card in
the flow in both editors: it is the way back from a write, not another way to write.

### One apply for two working copies (Star Rail)

Star Rail keeps two working copies from one file — graphics settings and QoL preferences — and had
a button for each, mid-page, which asked the user to choose which of their own pending edits was
allowed to land first. The bar now has one APPLY that writes both, in the only order that cannot
lose work: the QoL copy is captured first, the graphics pass runs (it re-reads and re-baselines
both copies), then the captured QoL copy is written. A failed graphics write stops the sequence
rather than pushing a second write onto an unconfirmed file. The bar's undo follows whichever copy
was edited last (`HsrEditArea`), with the other as fallback so no undo step becomes unreachable.

Verified on the device: after changing the text language in the preferences card the bar read
"Unsaved changes · 1 pending" with Undo enabled, and tapping Undo returned it to "No unsaved
changes." — the combined dirty state and the cross-copy undo both work. Pressing APPLY for real
still needs an installed game, which the emulator does not have.

While doing this, the same re-baseline trap turned out to exist in the graphics write itself, not
only in the combined path: `applySettingsNow` re-read the file and replaced the QoL working copy
with what the device held, clearing `prefsDirty`. With the old separate button, pressing "Apply"
with QoL edits pending discarded them silently. That is now a property of the function rather than
of its caller — a pending QoL copy is captured before the re-baseline and kept, with only its
baseline moving to the device, so unapplied work stays visible as pending. The rule was extracted
into a pure `afterGraphicsWrite` transition and is unit-tested by `HsrGraphicsWriteStateTest`
(pending copy survives, clean copy follows the device, undo availability is not invented).
`onApplyPrefs()` and the graphics-only `onApply()` are gone: one apply, one entry point.

What was measured and deliberately left alone:

- The three embedded editors are long because they are large forms — chip ladders for
  resolution/quality and one section per graphics group — not because of empty space. Each row
  carries a 2–3 line caption (roughly 8% of GRID's total height) that explains what the write
  does; those captions are safety-relevant, not decoration, so they stay visible.
- Both Star Rail and Wuthering Waves carry **two** deploy blocks (graphics and preferences), each
  with its own apply button, undo/redo and dirty line. Merging them, and pinning one apply action
  to a persistent bar, is the right next step — but the apply action lives in the per-game
  ViewModel while the scroll container belongs to the File Engineering destination, so it needs a
  deliberate seam rather than a local patch. Left as a documented recommendation.

## What was changed

1. **`ui/components/SectionTabs.kt`** (new) — `SectionTabRow` (one equal-width tab per section,
   56 dp tall, `selectableGroup` + `Role.Tab`) and `SectionRail` (the expanded-window column).
   Both take `SectionTab(id, label, icon)` so the saved-state key is not the translated label.
2. **`screens/gaming/GamingToolsScreen.kt`** — the chip strip is gone. Compact and medium windows
   use the tab row; windows ≥ 1180 dp use the rail, which turns the workspace into three columns
   (game library, sections, content). Section captions are one word each
   (`gt_tab_profile` … `gt_tab_cleanup`); the long headings stay as content titles.
3. **`screens/gaming/GamingToolsScreen.kt`** — the game grid's column count follows its pane:
   four in the phone body, two in the 278 dp library rail, where four columns cut every name to
   `CATSMOKE R LAB GA…`.
4. **`screens/gaming/GamingToolsScreen.kt`** — the profile section drew a `GAMING MODE` section
   label 20 dp above a card that already draws `GAMING MODE` as its own eyebrow; the duplicate
   is gone, and the two-line "Device-wide controls" caption no longer sits flush against the
   next heading. Together those took the section from 450 px of scroll to 249 px.
5. **`screens/home/HomeScreen.kt`** — expanded windows get their own composition. The telemetry
   card became one shared `TelemetryCard` (it had three copies), the header one `HomeHeader`,
   and the board is capped at 1040 dp, centred, with a fixed 320 dp telemetry panel and 152 dp
   console tiles. `QuickActionsSection` takes an optional `tileHeight`; `null` keeps the phone's
   dividing behaviour exactly as it was.

## Guards added

| Test | Pins |
| --- | --- |
| `GamingWorkspaceNavTest` | Six sections, short captions, no `FilterChip`/`horizontalScroll`, one scroll owner, tab semantics and ≥ 48 dp targets |
| `HomeExpandedLayoutTest` | Expanded branch exists, board width/height are capped, tile rows still divide on phones, telemetry card stays one composable |
| `GameLibraryGridTest` (updated) | Column count comes from the pane, both call sites pass one |
| `GameNotFoundCardTest` | Compact form is tied to `showEditorAnyway`, keeps both actions, override is not offered twice |
| `HsrGraphicsWriteStateTest` | A pending QoL copy survives a graphics write, a clean one follows the device, undo availability is not invented |
| `HsrSingleApplyTest` | The pending QoL copy is captured before the graphics write, a failed first write stops the sequence, the screen shows one apply label, bar undo follows the last-edited copy |
| `DeployBarTest` | Publishing is idempotent, leaving the tree clears the bar, the bar is outside the scroll, restore stays in the flow, WuWa's exclusion is deliberate |

## Remaining limitations

- Settings still needs one swipe on a 411 dp phone. That is content, not waste: five card groups
  of real settings. Removing it would mean hiding settings behind another level.
- Expanded Gaming Tools leaves the workspace's lower half empty when a section is short. The
  alternative — stretching cards to fill — is the defect this pass removed.
- The desktop AVD (`CatSmoker_Desktop_37_1`) intermittently raises "System UI isn't responding"
  dialogs under load; every desktop screenshot was taken after confirming no such dialog was up.
- The per-game editors remain the longest surfaces (5–6 swipes each). Their length is real
  content: chip ladders per graphics group. The deploy bar removed the scroll *between* an edit
  and its apply, not the length of the form.
- Wuthering Waves has no deploy bar: four deploy targets, not one primary write.
- The bar was verified by inspection and by state (dirty/clean, present/absent); actually pressing
  APPLY needs a real game install, which the emulator does not have.
- No scroll measurement is automatic in CI. `tools/ux_audit/measure_scroll.py` needs a device and
  is run by hand; it is a measurement aid, not a test.
