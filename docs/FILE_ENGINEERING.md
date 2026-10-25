# File Engineering (game config editors)

How CatSmoker edits game-side files, and the rules each editor follows.
Owned by `features/editgamefiles/`: `EditGameFilesScreen` +
`EditGameFilesViewModel` (game registry, `GameProfile.labelRes`) plus one
package per game — `genshin/`, `grid/`, `hsr/`, `pubg/`, `wuwa/`, `service/`
(shared backup/SAF plumbing).

HSR, WuWa and GRID are **embedded editors** (`embeddedEditor = true`): they are
hosted inline by File Engineering and have no navigation route. Never push a
bundled template over an embedded editor's screen.

## The write discipline (every game, every channel)

1. **Back up first.** `ConfigBackupStore` keeps the *device's* bytes (10
   backups per game). A channel that cannot read the current file backs up
   nothing rather than the wrong thing.
2. **Push** through the available channel — root/Shizuku shell, or SAF /
   hand-moved export for the non-privileged path. There is no wireless ADB
   channel (removed 2026-09-20; do not re-add).
3. **Read back and verify.** `md5sum` on device for byte exactness — never
   `cat` for bytes. A write is not done until the device has confirmed it.

## Editor UI contract (2026-10-25)

The editors had three recurring honesty bugs: dead controls for fields the
file does not carry, Apply buttons that force-stopped a game to rewrite
identical bytes, and restore buttons that silently discarded unsaved edits.
The rules now:

- Every control shows the file's real value or an explanation of why the file
  cannot answer; never a control that cannot do anything.
- A pending-edit line (`gf_unsaved`) appears whenever edits differ from the
  parsed file. Apply is disabled when the working copy is clean, with
  `gf_no_pending_changes` explaining why instead of a live no-op button.
- Restore asks for confirmation when edits are pending and states that the
  current file is backed up first (so the restore itself is reversible).
- Advanced-only controls stay reachable but collapse behind
  `gf_more_options` / `gf_fewer_options` (GRID's FXAA + window scale, WuWa's
  engine/files/advanced cards, HSR's QoL preferences); collapsing is
  presentation only and never removes a performance control.

## Per-game rules

- **Genshin** (`genshin/GenshinConfigTemplate`): `hardware_model_config.json`
  is *deliberately invalid JSON* (the game tolerates it) — text substitution
  only, never a JSON parser. The model placeholder is substituted at delivery
  with the device's real `Build.MODEL`.
- **GRID** (`grid/GridPreferences`): Feral registry XML — targeted `<value>`
  substitution only, never whole-file replacement (the file carries binary
  blobs and hidden fields). The editable set is the README's keys plus the
  shipped file's `FeralUseFXAA` and `gfxconfig_high_window_scale` (exact name,
  the file's own integer type); the `Feral*FPSCap` binary doubles stay
  game-managed — every one is a ceiling and this editor only raises. The
  frame-rate control offers only the game's top 144 Hz, never a lower ceiling.
- **PUBG** (`pubg/`): `Active.sav` GVAS **byte-patch** (`PubgSavePatcher`,
  `IntProperty` only, all-or-nothing — any unrecognized field refuses the
  whole edit). Three profile pushes: Max FPS, tablet view, and the eu GFX
  tool's own `UserCustom.ini` template (`PubgUserCustom`) — the last merges,
  so the device's `[BackUp DeviceProfile]` tail survives and the previous file
  always goes to `ConfigBackupStore` first. The save editor offers only the
  120 FPS tier; the former 60/90 tiers were removed under the no-cap rule. Shipped saves under `assets/PUBG/`
  double as test fixtures. **PUBG: New State is a separate registry entry**:
  root-only, because its `GameUserSettings.ini` lives in the game's private
  data directory; APPLY runs `NewStateIniTransform` in place (reference tuple
  verbatim, frame rate raise-only (0 = UE4 unlimited stays 0), `chmod 0440` lock, read-back) with no
  method chooser. The folder picker gets a version-branched
  `Android/data/<package>` starting point (`PubgSafAccess`) where the platform
  accepts one — selection and grant stay the user's.
  - **Custom import is typed, not a free-form file push** (2026-10-25).
    `GameUploadTarget` (`shared/data/model`) names the only two supported
    files — `Active.sav` (`GameUploadFormat.GVAS_SAVE`) and
    `UserCustom.ini` (`USER_CUSTOM_INI`) — and `PubgUploadPlan` resolves each
    destination from the selected package. The old untyped picker always wrote
    `Active.sav`, so importing `UserCustom.ini` replaced the save with text.
    Format is validated before any write (GVAS magic; the
    `[UserCustom DeviceProfile]` header, and a file whose only section is
    another device's `[BackUp DeviceProfile]` is refused because it carries no
    active profile to import), the INI merge keeps the device's
    `[BackUp DeviceProfile]` tail, the previous file is backed up, and the
    destination is read back and byte-compared before success is reported.
    Profile and import share one card: both are ways to change the same file.
  - The save editor's chips initialize from the **parsed saved values**
    (`PubgSaveSelection`), and selecting the value already in the file creates
    no pending edit. Fields the save does not carry hide their chips behind an
    explanatory line instead of rendering dead controls; the camera presets
    always move `TpViewValue` and `FpViewValue` together. A pending-edit line
    names unsaved changes, and APPLY stays disabled without them.
- **HSR** (`hsr/`): `HsrGraphicsSettings` (URL-encoded JSON codec),
  `HsrPlayerPrefsXml`, `HsrGameManager` (path templates; backup → cp → chmod →
  read-back), `HsrGamePreferences`. Every graphics preset writes 120 FPS and
  the frame-rate control offers only that top value. Restore backs the live
  file up before replacing it — a restore is therefore reversible — and parses
  the result back, comparing values with the backup's before reporting
  Success. The fifth resolution chip was the 4K preset clipped by a
  non-wrapping `Row`; all chip groups use `ChipFlowRow`, and a caption keeps
  the width/height presets distinct from the resolution-quality slider.
- **WuWa** (`wuwa/`): `WuWaConfigGenerator` + `WuwaForbiddenCvars` (verbatim
  key list) + `WuwaSmartBrain`/`WuwaBenchmarkTuner` (measured-evidence axes
  only; unmeasured axes are named in `logOnlyAxes`, never scored neutral) +
  `WuwaLogDecryptor`/`WuwaLogParser` + `WuwaConfigManager` (`DeviceChannel`:
  shell = backup → push → md5 verify → hash refresh; SAF = no shell, no
  force-stop, no hash sync) + `WuwaProfileExtractor` + `WuwaCommunityPack` +
  `EditHistory`. `WuwaConfigManager.cleanShaderCache` implements the
  reference's Protocol A cache delete (`files/UE4Game/ProgramBinaryCache` and
  `VulkanProgramBinaryCache`): force-stop first, `rm -rf`, read-back with
  `test ! -e`, deleted/absent/refused kept distinct, root/Shizuku only —
  surfaced by the deploy card's Clear-shader-cache button. The generator always
  writes 120 FPS (`MAX_FPS`); the lower 30/60/90 picker was removed.

## WuWa product exclusions (permanent)

**Deploy History and Game Log Analysis are permanently removed** (product
requirement, 2026-10-16). Deploys are not recorded anywhere; the deploy report
is the record. `WuwaRemovedFeaturesTest` bans the entry points, stores and
strings from returning. The tuner and installed-profile readers still read
`Client.log` internally for their own measured axes — that is not the removed
surface. Do not re-add either surface in any form.

## UI rules here

- The universal card rules live in `CODING_STYLE.md` (no informational-only
  cards; always-show-gray). The game editors add: confirmations are toasts, and
  warnings that affect the next action (e.g. the HSR external-change warning)
  sit as a line above the action. Removal precedent for both editors is noted
  in `CODING_STYLE.md`.

## Pitfalls

- WuWa SQLite reads quote identifiers (`"value"`, `"key"` are reserved words).
- WuWa log axes need a decrypted `Client.log`; without it, say so — never
  guess values.
- The game selector opens a per-game gate (installed-probe); "show anyway"
  exists for WuWa so the editor is reachable without the game installed.

## Tests

Every codec and patcher has a JVM test class under
`app/src/test/java/com/catsmoker/app/features/editgamefiles/`. The behavioural
locks worth naming: `PubgSavePatcherTest` (GVAS + the bundled real saves), `PubgUserCustomTest` (merge shape against both pinned corpora), `NewStateIniTransformTest` (tuple + raise-only), `PubgSafAccessTest` (URI shapes),
`GenshinConfigTemplateTest`, `GridPreferencesTest`,
`HsrGraphicsSettingsTest`/`HsrGraphicsPresetTest`, `WuWaConfigGeneratorTest`,
`WuwaPresetSelectionTest` (selection semantics), `WuWaSmartBrainTest`,
`WuwaRemovedFeaturesTest` (the WuWa exclusion), `EditHistoryTest`,
`InstallProbesTest`. `LocaleParityTest` (in `src/test/.../shared/`) guards
resource parity across the five locale folders.
