# File Engineering audit — all 33 games

Audit date: 2026-11-14. Scope: every non-`NONE` `GameType`, its package
identity, installation detection, file targets, implemented controls, write
path and current evidence.

## Status vocabulary

| Status | Meaning in this document |
| --- | --- |
| `IMPLEMENTED_UNVERIFIED` | Functional code path with an established format, exercised by unit/source tests only. No real-device confirmation in this audit. |
| `PRELIMINARY` | Working editor over a real file format, but at least one material question (game consumption, mapping of some fields, current Android layout) is unanswered. |
| `BLOCKED` | The editor exists but the advertised effect is not established, so it is not presented as a supported outcome. |

`VERIFIED` is deliberately absent: nothing here was validated on a device
during this audit. Tests prove parsing, policy and wiring — not that a running
game adopts the written values.

## Shared flow (all games)

`GameDefinitions.kt` declares, for every non-`NONE` `GameType`: package
candidates, store destination, implementation, capabilities, access channels,
readiness and a written limitation. Registry construction fails if a game is
listed without a definition.

1. Select game → fresh `PackageManager` query of the declared packages.
2. Query failure is *unknown*, not *absent*; only a clean negative hides tools.
3. **Game Not Found** card → **Open Play Store** (declared package, market URI
   then browser), **Recheck** (re-probes and re-enters the editor), **Show
   Anyway** (same game-specific tools, every game).
4. Show Anyway never fabricates paths or claims a file exists: reads and
   writes that need an install, a readable file or a writable destination stay
   disabled with their own reason.

Legacy standalone routes (`onBack != null`) keep their own historical gate;
the embedded/hosted entry point they use from File Engineering is gated once,
by the parent.

## Core implementations (12 entries)

| Game | Package(s) | Target / actions | Status | Limitations |
| --- | --- | --- | --- | --- |
| PUBG MOBILE (Global) | `com.tencent.ig` | Profile push (root / Shizuku / SAF), save import, inline GVAS `IntProperty` editor, backup + restore | `IMPLEMENTED_UNVERIFIED` | Requires a recognized existing save; layout varies by regional build and version. Force-stop must now succeed before the baseline read; a failed backup refuses the write. |
| PUBG MOBILE (KRJP) | `com.pubg.krmobile` | Same as Global | `IMPLEMENTED_UNVERIFIED` | Same; variant-specific save layout unconfirmed. |
| PUBG MOBILE (VN) | `com.vng.pubgmobile` | Same as Global | `IMPLEMENTED_UNVERIFIED` | Same. |
| BGMI | `com.pubg.imobile` | Same as Global | `IMPLEMENTED_UNVERIFIED` | Same. |
| PUBG MOBILE (Rekoo) | `com.rekoo.pubgm` | Same as Global | `IMPLEMENTED_UNVERIFIED` | Same. |
| PUBG MOBILE (Flash) | `com.tencent.igfit` | Same as Global | `IMPLEMENTED_UNVERIFIED` | Same. |
| Genshin Impact | `com.miHoYo.GenshinImpact` | Legacy `hardware_model_config.json` profile delivery (root / Shizuku / SAF), backup + restore | `PRELIMINARY` | Reference template only. Current game versions have not been shown to consume it, so it is not a verified FPS unlock. CN/cloud packages are not supported by this implementation. |
| PUBG: New State | `com.pubg.newstate` | Root-only in-place `GameUserSettings.ini` transform, backup + restore | `IMPLEMENTED_UNVERIFIED` | Private data directory: root required, no SAF alternative. Requires the recognized INI layout; no reset, custom upload or inline save editor. |
| Honkai: Star Rail | 5 known variants (`com.HoYoverse.hkrpgoversea`, `com.miHoYo.hkrpg`, `com.HoYoverse.hkrpg`, `com.cognosphere.hkrpg`, `com.HoYoverse.hkrpgsamsung`) | Root-only `playerprefs.xml`: graphics blob + QoL keys, presets, backup + restore | `IMPLEMENTED_UNVERIFIED` | Root and UID-0 required. Flat-format clients expose no proven FPS field. Unknown JSON members and unsupported PlayerPrefs entry types now fail closed rather than being dropped. |
| Wuthering Waves | `com.kurogame.wutheringwaves.global` | Generate/import INI configs, deploy over root/Shizuku/SAF, byte-exact restore | `IMPLEMENTED_UNVERIFIED` | Global package only. Engine CVars may be ignored by a given build. Deployment merges generated keys into existing files; SAF cannot force-stop the game or refresh its hash monitor. Multi-file deploys report per file, never as one atomic success. |
| GRID Autosport | `com.feralinteractive.gridas` | Edit existing Feral `preferences` registry XML (root / Shizuku / SAF), backup + restore | `IMPLEMENTED_UNVERIFIED` | Requires an existing file with the expected keys; missing keys are not created. FPS edits may only raise a present positive ceiling. Binary FPS caps stay game-managed. Duplicate or type-mismatched keys are unavailable rather than guessed. |
| Roblox (test-only) | `com.roblox.client` | Single experiment: raise `DFIntTaskSchedulerTargetFps` to 240, backup + original recovery + close-game | `BLOCKED` | Roblox consumption of this flag/path is not established and the published allowlist omits it. Offered as an explicitly labelled file experiment, not an FPS feature; lowering or replacing a higher/unlimited value is refused. |

## Scoped config editors (21 entries)

All 21 are `SCOPE_CONFIG` implementations in `additionalGameEditors`, PRELIMINARY
and not device-validated. Every field carries an explicit control contract;
`AdditionalConfigStorage` re-checks that contract before writing, so an
unsupported field cannot be changed through the UI or by a stale edit.

Common properties:

- Existing file only: no editor creates a config, and a missing key is shown
  disabled with "load the existing configuration" rather than seeded.
- Unedited bytes and unknown keys are preserved; restore replays the original
  backup bytes.
- Feral rows read `Android/data/<pkg>/files/feral_app_support/preferences`;
  the listed scopes are exact registry paths.
- Numeric Feral frame caps are *not* offered as FPS unlocks unless a documented
  unlimited/raise-only semantic exists. None of these 21 qualifies.

| Game | Package | File / scopes | Implemented controls | Unavailable (reason) |
| --- | --- | --- | --- | --- |
| GRID Legends: Deluxe Edition | `com.feralinteractive.gridlegends_android` | `preferences`: `GRIDLegends/GraphicsSettings`, `GRIDLegends/Setup` | `FixedScreenHeight` (px), `EnableSSR` toggle | `FramerateLimit`, `Shadows`, `TextureQuality` — no established unlock/ladder mapping |
| Alien: Isolation | `com.feralinteractive.alienisolation_android` | `preferences`: `Alien Isolation/Setup` | `FixedScreenHeight`; chromatic aberration, depth of field, motion blur, planar reflections, volumetric lighting toggles | Shadow mapping/resolution and texture filtering ordinals — quality-index meaning unconfirmed; spec files can clamp or reset values |
| Saints Row: The Third Remastered | `com.feralinteractive.saintsrow3_android` | `preferences`: `Saints Row The Third/Setup` + each of `BatterySaverPreset`/`GraphicsPreset`/`PerformancePreset` | `FixedScreenHeight`; `GfxSSLR` `off`/`on` per preset scope | `GfxShadow` (only `medium` observed), `GfxResponsiveFps` (a cap, not an unlock) |
| Total War: NAPOLEON | `com.feralinteractive.napoleon_android` | `preferences`: CA `Napoleon Total War - Mobile/Setup`, `.../GraphicsSettings` | `FixedScreenHeight`; `SoftShadows`, `HDR` toggles | Quality ordinals and the engine frame manager — tier labels and unlimited semantics unconfirmed |
| Hitman: Blood Money | `com.feralinteractive.hitmanbloodmoney_android` | `preferences`: `Hitman Blood Money/Setup` | `FixedScreenHeight`, directional/plain shadow resolution (px), `EnableReflections` toggle | Per-suffix preset triples and FPS caps — non-uniform ladders, no active-suffix selector |
| Lara Croft and the Guardian of Light | `com.feralinteractive.laracroftgol_android` | `preferences`: `Crystal Dynamics/Lara Croft: GoL/Graphics` | `EnableShadows`, `EnableReflection` toggles | Texture detail/filter and AA indices; VSync; `MaxFramesPerSecond` (cap) |
| Lara Croft and the Temple of Osiris | `com.feralinteractive.laracrofttoo_android` | `preferences`: `Feral Interactive/.../Setup` + `Crystal Dynamics/.../Graphics/{BatterySaver,Graphics,Performance}Preset` | `FixedScreenHeight`; `Bloom`, `FXAA` toggles per preset scope | Quality indices and `VSync`; `MaxFramesPerSecond` (cap) |
| Sid Meier's Railroads! | `com.feralinteractive.railroads_android` | `preferences`: `Original Developer/Sid Meier's Railroads!/Setup` | `FixedScreenHeight`; `RenderUnderNotch` toggle | Shader/shadow/texture/tree quality (only `2` observed), `engineFSAA`, unknown active FPS key |
| Tomb Raider | `com.feralinteractive.tombraider_android` | `preferences`: `Crystal Dynamics/Tomb Raider/Setup`, `.../GraphicsSettings` | `FixedScreenHeight`; motion blur, post-process, screen effects, tessellation toggles | Shadow/texture/AA ordinals, `ShadowMode`, preset selector |
| Hitman: Absolution | `com.feralinteractive.hitmanabsolution_android` | `preferences`: `IO Interactive/Hitman Absolution`, `.../Setup` | `FixedScreenHeight`; `Bloom`, `FXAA` toggles | Shadow/texture ordinals, VSync, suffix quality variants |
| Company of Heroes | `com.feralinteractive.companyofheroes_android` | `preferences`: `Feral Interactive/Company of Heroes/Setup` (+ developer graphics scope) | `FixedScreenHeight` | Shadows, physics, post-processing, reflections — all tiers inferred, not per-key proven |
| Total War: EMPIRE | `com.feralinteractive.empire_android` | `preferences`: CA `Total War EMPIRE/Setup`, `.../GraphicsSettings` | `FixedScreenHeight`; `SoftShadows`, `HDR` toggles | Quality tiers and the engine frame manager |
| Tropico 3 | `com.feralinteractive.tropico3` | `preferences`: `Haemimont Games/Tropico 3: Gold Edition/Setup` and `.../Setup/GFX` | `FixedScreenHeight`; `SceneRez`, `ShadowQuality`, `TextureLevel`, `WaterQuality` as `Off`/`Low`/`Medium`/`High` | Anisotropy tokens and other GFX keys — mapping inferred |
| ROME: Total War | `com.feralinteractive.rometw` | `preferences`: `The Creative Assembly Limited/Rome Total War/Setup` | `FixedScreenHeight`; battle-map, campaign-map, detailed-unit shadow toggles | VSync and remaining quality keys — mapping unconfirmed |
| Total War: MEDIEVAL II | `com.feralinteractive.medieval2_android` | `preferences`: `Original Developer/Medieval II Total War/Setup` + `.../Setup/VideoSettings` | `FixedScreenHeight`; `CampaignShadows` toggle | String quality vocabularies (only lowercase `low`/`medium`/`high` observed), `ResponsiveFPS` (cap), filtering |
| ROME: Total War — Barbarian Invasion | `com.feralinteractive.rometwbi` | `preferences`: `Rome Total War - Barbarian Invasion/Setup` | `FixedScreenHeight`; three shadow toggles | Same as ROME: Total War |
| ROME: Total War — Alexander | `com.feralinteractive.rometwalex` | `preferences`: `Rome Total War - Alexander/Setup` | `FixedScreenHeight`; three shadow toggles | Same as ROME: Total War |
| XCOM 2 Collection | `com.feralinteractive.xcom2_android` | `preferences`: `Feral Interactive/XCOM 2/Setup` | `FixedScreenHeight`; `GFXBloom` toggle | Quality ordinals, `NonIdleFPS` (cap) |
| Minecraft Bedrock | `com.mojang.minecraftpe` | User-selected `options.txt` | `gfx_max_framerate` unlock to `0` (documented unlimited), render distance in **blocks**, fancy graphics toggle | Storage path varies by Bedrock version — the user selects the document; no invented universal path |
| Terraria | `com.and.games505.TerrariaPaid` | User-selected `config.json` | Five numeric visual-limit fields (non-negative counts), `DisableIntenseVisualEffects` boolean toggle | Mobile/desktop schemas differ; absent or desktop-only keys are unavailable |
| Civilization VI | `com.aspyr.civvi` | Root-only `files/Sid Meier's Civilization VI/GraphicsOptions.txt` and `AppOptions.txt` | Shadow-map resolution (px), AO/bloom/shadows toggles, render width/height (px) | Impact presets can override individual settings; files apply separately (not atomic); `Version` header and duplicate sections preserved; root only |

## Defects fixed during the audit

- Embedded editors (HSR, WuWa, GRID, Roblox, 21 scoped) bypassed the shared
  missing-game flow; all now route through the parent gate.
- Launching HSR, WuWa or GRID could silently do nothing; launch now resolves the
  detected supported variant, then the declared store package, and reports a
  refused launch.
- Store fallback now reports when neither market nor browser can open the
  listing instead of failing silently.
- GRID: restore could fall through to SAF after a failed shell write (two
  different destinations in one operation); shell pulls used a root-owned cache
  copy; SAF writes did not truncate; concurrent-change checks were missing.
- HSR: writes could replace unknown encoded JSON fields, and PlayerPrefs entries
  of unsupported types or duplicate names could be dropped. Unknown JSON members
  now round-trip; unsupported XML fails closed. Stop/backup failures abort.
- WuWa: generated deployment replaced whole INIs, discarding unrelated
  sections and comments; it also created the app data directory for a
  not-installed package. Deployment now merges generated keys, keeps unrelated
  content, requires the installed data root, and refuses a failed force-stop.
  Restore remains byte-exact.
- PUBG profile/save and New State writes ignored force-stop and backup failures;
  they now refuse the write instead.
- Root reads through a root-owned cache copy could return an unreadable file;
  root file I/O now requires UID 0 and reads via bounded base64.
- Roblox FPS selection defaulted to 120 even though only 240 is offered, and
  could lower an existing target; it now offers and applies only the raise.

## Tests run

Focused JVM tests, both flavors, plus `compileFullDebugKotlin` and
`compilePlaystoreDebugKotlin`:

`GameDefinitionsTest`, `ConfigControlsTest`, `AdditionalGameEditorsTest`,
`GamePickerIconsTest`, `EditableConfigDocumentTest`, `ConfigWriteTransactionTest`,
`Hsr*Test`, `GridPreferencesTest`, `Roblox*Test`, `WuwaIniMergeTest`,
`WuWaConfigGeneratorTest`, `WuwaRemovedFeaturesTest`,
`FileWriteSafetyWiringTest`, `RestoreSafetyWiringTest`, `RootMethodWiringTest`,
`PubgPushWiringTest`, `PubgSavePatcherTest`, `ProfilePushPlanTest`,
`NewStateIniTransformTest`, `GenshinConfigTemplateTest`, `LocaleParityTest`.

Unit/source tests establish format handling, control policy and wiring only.

## Requires device validation

1. Installation detection and store/Recheck/Show Anyway on a real device for
   each package family (especially HSR's five variants and the six PUBG builds).
2. Whether each game adopts the written values after restart (all 33).
3. GRID/HSR/WuWa channel behaviour under real root, Shizuku and SAF grants,
   including the new stop-before-write ordering.
4. Genshin template consumption by current game versions (status stays
   PRELIMINARY regardless of test outcome).
5. Roblox flag consumption (status stays BLOCKED until evidence exists).
6. WuWa merge fidelity against a live, game-written `Engine.ini`.
7. Play-flavor builds for every path that touches privileged access.

## Adding a future game

A picker entry is not support. A new game requires: a `GameDefinitions` entry
(packages, store target, implementation, capabilities, access, readiness,
limitation), a real implementation or an explicitly `BLOCKED`/`PRELIMINARY`
declaration, evidenced control contracts for every editable field, and focused
tests for its file handling. It inherits the shared selection flow automatically;
it does not inherit credibility.
