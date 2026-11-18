# File Engineering (game config editors)

How CatSmoker edits game-side files, and the rules each editor follows.
Owned by `screens/editgamefiles/`: `EditGameFilesScreen` +
`EditGameFilesViewModel` (game registry, `GameProfile.labelRes`) plus one
package per game under `games/` (see the complete mapping in `ARCHITECTURE.md`),
with shared `model/`, `storage/` and scoped-control presentation in `editor/`
beside that collection. The frozen Shizuku service identity remains separate.

HSR, WuWa, GRID, the Roblox test-only editor and the 21 research-backed scoped editors
below are **embedded editors** (`embeddedEditor = true`): they are
hosted inline by File Engineering and have no navigation route. Never push a
bundled template over an embedded editor's screen.

All games use `editor/GameEditorLayout.kt` for full-width sections with the same
12 dp spacing. The picker identifies the selected game and owns scrolling for
embedded editors. Section headings describe the file or controls in each card.

## Game picker

`GameDefinitions.kt` is the authoritative File Engineering registry: supported package
identities, store destination, implementation, capabilities, access channels, readiness
and limitations belong together. Every non-`NONE` `GameType` must have a definition.
`GameConfigRegistry` still owns profile builders; `AdditionalGameEditors` wires concrete
game schemas, not a generic fallback for otherwise unimplemented entries.

Every game uses the parent selection gate, including embedded editors:
select → fresh package query → tools, or the shared **Game Not Found** card with
**Open Play Store / Recheck / Show Anyway**. Recheck replaces cached installation
answers and re-enters hosted editors. Show Anyway exposes the same tools; missing files
and permissions still disable the operations that need them. A query failure is unknown,
not confirmed absence. Explicit manifest package queries must match the registry.
Store/browser fallbacks retain the selected game's ID; an unavailable listing is never
substituted with another game. Regional availability still belongs to Google Play.

See [the complete 33-entry audit](FILE_ENGINEERING_AUDIT.md) for per-game readiness,
targets, changes and remaining validation. No current entry has CatSmoker real-device
validation evidence from this audit. Editor screens and apply dialogs omit the
introductory validation paragraphs.

The popup uses compact, rounded logo-first rows: installed game icon, game name,
install-state dot, and a fixed trailing selection-check slot. The selected row
uses a neutral surface highlight. The bounded list scrolls on short/landscape
screens, and exposes single-choice selection semantics to accessibility services.

Artwork comes only from the named game's installed package, loaded and rasterized
off the main thread at the displayed size. Unavailable artwork gets a neutral
game-controller icon, without changing the install dot or disabling selection.
There are no downloaded/bundled third-party logos or broader app-inventory queries.
PUBG prefers the selected installed variant's icon, then another installed variant;
HSR tries the same regional package list its editor uses for detection. PUBG's
single group row, three-state group dot, and separate version chips are unchanged.

## The write discipline (every game, every channel)

1. **Back up first.** `ConfigBackupStore` keeps the *device's* bytes (10
   backups per game). A channel that cannot read the current file backs up
   nothing rather than the wrong thing. Roblox uses its dedicated journal below
   to capture confirmed absence as well as existing bytes.
2. **Push** through the available channel — root/Shizuku shell, or SAF /
   hand-moved export for the non-privileged path. There is no wireless ADB
   channel (removed 2026-09-20; do not re-add).
3. **Read back and verify.** `md5sum` on device for byte exactness — never
   `cat` for bytes. Roblox compares bounded base64 snapshots byte-exactly instead.
    The additional scoped editors compare bounded byte snapshots directly (root reads
    use base64; Shizuku and document reads use bytes). A write is not done until
    the selected file's written bytes have been confirmed. This does not prove the
    game consumed the setting.

The method chooser's **ROOT** route reads, backs up, writes, and verifies through
the root shell; it must not require a running Shizuku binder. If root is
unavailable, it reports the fallback and uses the Shizuku route instead.

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

### Game-specific scoped editors (2026-11-14)

All 21 additions are explicitly **not device-validated support**. Their schemas define
specific toggles, units, documented choices and (Minecraft only) an evidenced unlimited
sentinel. Unknown ordinal mappings remain unavailable with a reason, not unrestricted raw
inputs or invented presets. Numeric Feral frame caps are not FPS unlock implementations.
No anti-cheat/account
safety is claimed. No third-party scripts, executables, donor configs or runtime downloads
are used. Each game owns its scope/key declarations and source URL; `AdditionalGameEditors`
only wires these schemas; the central definition owns shared selection and routing.

| Game | Format / selected settings |
| --- | --- |
| GRID Legends: Deluxe Edition | Feral XML: screen height and SSR |
| Alien: Isolation | Feral XML: height, chromatic aberration, depth of field, motion blur, planar reflections, volumetric lighting |
| Saints Row: The Third Remastered | Feral XML: height and separately scoped preset SSLR off/on |
| Total War: NAPOLEON | Feral XML: height, soft shadows, HDR |
| Hitman: Blood Money — Reprisal | Feral XML: height, shadow resolutions, reflections |
| Lara Croft and the Guardian of Light | Feral XML: shadows and reflections |
| Lara Croft and the Temple of Osiris | Feral XML: height and separately scoped preset bloom/FXAA |
| Sid Meier's Railroads! | Feral XML: height and render-under-notch |
| Tomb Raider | Feral XML: height, motion blur, post-processing, screen effects, tessellation |
| Hitman: Absolution | Feral XML: height, bloom and FXAA |
| Company of Heroes | Feral XML: height; unresolved quality ordinals remain unavailable |
| Total War: EMPIRE | Feral XML: height, soft shadows, HDR |
| Tropico | Feral XML: height and string-valued scene/shadow/texture/water quality |
| ROME: Total War | Feral XML: height and battle/campaign/unit shadows |
| Total War: MEDIEVAL II | Feral XML: height and campaign shadows |
| ROME: Total War — Barbarian Invasion | Separate package/scope: height and battle/campaign/unit shadows |
| ROME: Total War — Alexander | Separate package/scope: height and battle/campaign/unit shadows |
| XCOM 2 Collection | Feral XML: height and bloom |
| Minecraft Bedrock | User-selected `options.txt`: unlimited FPS (`0`), render distance (blocks), fancy graphics |
| Terraria | User-selected `config.json`, documented mobile-capable visual limits/effects fields only |
| Civilization VI | Space-delimited graphics: shadow-map pixels, AO/bloom/shadows; app options: width/height pixels |

Feral evidence: [legend-tuner](https://github.com/andrewr8712/legend-tuner/tree/2fba90d6e227649b7391159ea4038050e8612cc9),
its sanitized Android preference samples and `docs/catalogues/` notes (which retain open
verification questions). Title availability was cross-checked against
[Feral's Android support catalogue](https://www.feralinteractive.com/en/support/android/).
These are third-party file samples, not CatSmoker device test results. Preference files
normally reside at `/storage/emulated/0/Android/data/<package>/files/feral_app_support/preferences`.
The game must have generated the file already. Exact full registry scopes matter:
same-name presets, AutoValueRemap entries, binary values and arrays are not interchangeable.
Alien may reset settings through other specification files; no specifications are patched.

Minecraft evidence: [Bedrock options documentation](https://minecraft.wiki/w/Options.txt_(Bedrock_Edition)).
Paths differ by version/storage mode, so no universal Android path is hard-coded.
Terraria evidence: [Config.json settings](https://terraria.wiki.gg/wiki/Config.json_settings).
Desktop-only entries are not imported; an absent or type-changed mobile field stays disabled.

Civilization evidence: [Android candidate investigation](https://github.com/andrewr8712/legend-tuner/blob/2fba90d6e227649b7391159ea4038050e8612cc9/docs/candidates/civilization-vi.md).
Direct files are under `/data/user/0/com.aspyr.civvi/files/Sid Meier's Civilization VI/`:
**root only**, not shell-UID Shizuku. Keep the `Version` header and repeated sections;
never reinterpret the format as ordinary `key=value` INI. Preset impact settings may
override individual controls; their engine behavior is not validated. The two files are
not an atomic multi-file transaction. Restore selects only backups for the current basename.

Shared codec/storage contract:

- Bound input to 1 MiB and valid UTF-8 (optional BOM); preserve untouched text, whitespace,
  line endings, unknown fields, XML comments, arrays/binary values and duplicate sections.
  Reject malformed JSON/XML and XML entity declarations. Only unique names in the exact
  expected scope with the declared scalar type are editable. Never create missing fields.
- Validate scalar syntax **and the declared control contract** in storage, not only UI.
  Unsupported fields cannot be changed. Numeric dimensions require positive integers;
  enum/toggle choices are explicit. No unproven quality ladder or unlimited-FPS sentinel
  is inferred. Restore may recover original values, including unsupported settings.
- Explicit confirmation includes closing the game and keeping it closed. Direct mode also
  force-stops that package, aborting if it fails; document mode cannot stop a game without
  privileges and relies on that confirmation. Never force-stop on reads or no-op Apply.
- Pin the loaded source/game/file through the operation. Reject live-byte changes since
  loading; back up those live bytes, recheck after backup, write, then compare exact bytes.
  Failed backup means no write. Once a write starts, finish read-back despite cancellation;
  preserve cancellation before returning to UI. Retain the pre-write backup on failure.
- Root reads/writes check actual UID in the command so a lost root session cannot downgrade
  private-file operations to shell UID. No chmod, arbitrary process spawn or game-file creation.
- Existing-document mode writes **only that document**, checking its expected basename and
  schema. It never claims an exported copy changed the installed game. Copy-back is manual;
  SAF does not bypass Android/data restrictions. No new storage permission is added.
- The document picker is owned by File Engineering rather than by a conditional editor.
  The selected game, its Show anyway choice, and the game/file tab that opened the picker
  survive activity recreation. A result for a superseded selection is discarded.
- Scoped editor backups retain ten snapshots per package across all its files and sources.
  Restore matches the exact document URI or direct path as well as the package and filename.
  Legacy scoped backups remain on disk but are not eligible for Restore: they do not say
  which source produced them. Restore backs up the current file first and verifies the
  restoration. No claim of atomic writes or automatic recovery is made.

### Existing editors

- **Genshin** (`genshin/GenshinConfigTemplate`): `hardware_model_config.json`
  is *deliberately invalid JSON* in the legacy reference — text substitution
  only, never a JSON parser. Current game consumption is not established; do not
  describe it as a verified FPS unlock. The model placeholder is substituted at
  delivery with the device's real `Build.MODEL`, not a spoofed identity.
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
    `GameUploadTarget` (`screens/editgamefiles/model`) names the only two supported
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
  Writes preserve unknown fields inside the encoded graphics JSON, reject malformed
  or duplicate PlayerPrefs entries and refuse to lower an existing FPS ceiling.
  Stop before reading the write baseline; reject changes before/after backup.
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
  Deployment merges generated scalar/CVar settings into existing INIs, retaining
  unrelated settings, comments and list entries. Existing higher/unlimited FPS
  values survive. Restore remains byte-exact, not a merge. Multi-file results are
  per-file, not an atomic all-or-nothing promise.

## Roblox: explicitly test-only (2026-11-13)

`games/roblox/` is an independently implemented experimental editor, selected
as **Roblox (test-only)**. It only changes the numeric
`DFIntTaskSchedulerTargetFps` key in
`/data/local/tmp/ClientAppSettings.json`, using root or authorized Shizuku via
`ShellRunner`. There is no SAF/export fallback, client patch, private-data
write, cache edit or allowlist bypass. Both flavors use the same owner; this
is not a Play policy eligibility finding.

**No verified game support or FPS improvement is claimed.** The reference
[RbxFPS](https://github.com/HomuHomu833/RbxFPS) writes this path but does not
establish that Roblox consumes it. Roblox's published
[local Fast Flag allowlist announcement](https://devforum.roblox.com/t/allowlist-for-local-client-configuration-via-fast-flags/3966569)
does not include this target flag. File read-back is only file-write proof,
not execution or gameplay-FPS proof. The screen and Apply confirmation warn
that Roblox may ignore it and that neither account safety nor FPS gains are
guaranteed. The UI offers only the existing experiment's highest target, 240,
and refuses to lower a higher or unlimited value. This is not a measured rate
or verified Roblox FPS unlock. Original-value recovery is retained.

- Read only a regular, readable file with one hard link and at most 16 KiB;
  reject symlinks, special permission bits and unstable stat evidence. A
  failed read never means absence. Bound shell output as well as decoded bytes.
- Require strict UTF-8 and a single JSON object, rejecting duplicate keys at
  every nesting level and excessive nesting. Preserve unrelated JSON values;
  formatting may change on Apply. An unchanged numeric target is a byte-exact
  no-op. An invalid file is not overwritten.
- Before mutation, `RobloxJournal` saves the first original bytes (including
  whitespace) and mode/uid/gid, or confirmed absence, in an `AtomicFile` at
  `noBackupFilesDir/roblox_config_recovery.json`. Sync and read back this record
  before touching the target. Do not migrate recovery ownership between devices.
- Repeated Apply never replaces the first original. The record stores `last`
  and `pending` states before each attempt, so termination before or after
  replacement, failed verification, and failed final journal commits retain
  recognized recovery states. The singleton editor serializes its operations.
- Compare live bytes and mode/uid/gid with the displayed snapshot and journal;
  check again immediately before replacement. Refuse unrelated external
  changes on Apply and Restore, including after Refresh. Refresh is not an
  override or permission to rebase someone else's edits.
- Write a sibling `mktemp`, set the captured mode/uid/gid (new files use
  shell owner/group 2000 and mode 0644), require matching SELinux labels for an
  existing target, then `mv -fT` without treating a changed target as a directory.
  Run scripts in a subshell so exits/traps never affect libsu's reusable shell;
  redact payload-bearing commands from `ShellRunner` failure logs. Narrow R8
  keep rules preserve both journal model field names across release builds.
  Unsupported shell commands or metadata operations fail closed. Read back
  exact bytes and mode/uid/gid before reporting success. These checks are
  best-effort conflict detection, not an atomic lock against hostile external
  writers; users must not edit the same file concurrently.
- Restore confirms discarding the selected target, puts back the first
  original bytes and mode/uid/gid, or deletes only this target if originally
  absent, and verifies the result. The original journal is retained after
  Restore. Unlike the other editors' rotating restore history, this experiment
  has no separate redo/history UI: the journal's purpose is returning to the
  original, not promising arbitrary historical undo.
- Keep controls visible with reasons when unavailable. A missing Roblox
  install disables Apply/Close but never blocks original-state recovery.
  Apply and Restore never force-stop Roblox. **Close Roblox** has a separate
  confirmation about ending the session; reopening uses the selector's
  existing Launch game action. Its success means only command completion.
- Retain CatSmoker's app data until recovery completes. Clearing data or
  uninstalling loses the original recovery record. No device execution or
  actual Roblox FPS effect is established by JVM tests or compilation.

`RobloxConfigTest`, `RobloxEditorTest` and `RobloxIntegrationTest` cover strict
parsing, no-op/preservation, metadata, bounded snapshots, backup ordering,
absence recovery, interrupted writes, failures, conflicts, source wiring and
seven-locale resource parity without depending on a device.

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
`app/src/test/java/com/catsmoker/app/screens/editgamefiles/`. The behavioural
locks worth naming: `PubgSavePatcherTest` (GVAS + the bundled real saves), `PubgUserCustomTest` (merge shape against both pinned corpora), `NewStateIniTransformTest` (tuple + raise-only), `PubgSafAccessTest` (URI shapes),
`GenshinConfigTemplateTest`, `GridPreferencesTest`,
`HsrGraphicsSettingsTest`/`HsrGraphicsPresetTest`, `WuWaConfigGeneratorTest`,
`WuwaPresetSelectionTest` (selection semantics), `WuWaSmartBrainTest`,
`WuwaRemovedFeaturesTest` (the WuWa exclusion), `EditHistoryTest`,
`InstallProbesTest`. `LocaleParityTest` (in `src/test/.../conventions/`) guards
resource parity across the five locale folders.
