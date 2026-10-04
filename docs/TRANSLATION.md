# Translation & Localization

Current state of internationalization (i18n) in CatSmoker and how to move
strings into resources.

## TL;DR (verified 2026-09-28 — the pre-2026 "English-only" description below is obsolete)

- The project uses Android resources for user-visible copy, split by area:
  `values/strings.xml` (core) + `strings_core/gamefiles/gaming/legal/sys.xml`
  in `src/main`, plus `strings_spoof.xml` in `src/full` (Full flavor only),
  each mirrored key-for-key in `values-en-rGB/`, `values-ar/`, `values-es/`,
  `values-zh-rCN/` (`en en-rGB ar es zh-CN`, declared in `resConfigs` +
  `res/xml/locales_config.xml`; in-app tags are `en/ar/es/zh-CN`).
  A key missing from a mirror falls back to the wrong language at runtime, so
  `LocaleParityTest` pins parity for user-visible copy — extend it when adding
  user-facing strings.
- There IS a runtime language switcher: `LocaleHelper.wrap` in both
  `attachBaseContext`s, the choice tagged in `appearance_prefs`, `recreate()`
  for language (theme recomposes live). Never cache app/VM-context `getString`
  in UI state (the app locale is frozen at process start while ViewModels
  survive the `recreate()`); carry `@StringRes` and resolve with
  `stringResource` at composition.
- The migration backlog below is CLOSED: a 2026-09-28 sweep found exactly one
  hardcoded `Text("…` label in `app/src/main` — a package identifier in
  `GridScreen.kt`, which the allowlist covers. Everything else user-facing is
  a resource.

This document records the current state, the conventions to follow when
adding strings, and a checklist for closing the migration gap.

## How strings flow today

Compose UI:

```kotlin
text = stringResource(R.string.booster_status_done, optimized, skipped, failed)
```

ViewModel / service (non-Compose context):

```kotlin
context.getString(R.string.custom_upload_failed, msg)
```

Formatting placeholders (`%1$s`, `%2$d`, `%3$s`) are already used correctly
in resources such as `booster_status_compiling` and `booster_notification_progress`,
so reordering per-language is supported.

## Naming & organization conventions

`strings.xml` is grouped by feature with banner comments
(`GENERAL`, `MAIN ACTIVITY`, `GAMING TOOLS SCREEN`, `SERVICES`, …). Follow
that layout:

- Prefix keys by feature/screen: `gt_*` (Gaming Tools), `dash_*` (dashboard),
  `logs_*`, `booster_*`, `overlay_*`, `crosshair_*`.
- Keep the explanatory comment when a string has non-obvious context — e.g.
  `dash_fps_label_ui` documents *why* it says UI FPS.
- The LSPosed module scope game list lives in `src/full/res/values/spoof_scope.xml`
  (`scope` array, Full flavor only, never translated) — per-game metadata lives
  in Kotlin and must **not** be duplicated as strings.

## Known gaps (CLOSED 2026-09-28 — kept as a checklist for future screens)

The migration backlog below was verified complete: the only hardcoded
user-facing-adjacent literal left in `app/src/main` is a package identifier
(`GridScreen.kt`), which the allowlist covers. `LocaleParityTest` guards
against regressions. When adding a screen, verify the same holds:

1. ~~**Compose screens** — dialog titles/buttons, section headers, status~~
   ~~copies across `GamingToolsScreen.kt`, …~~ — migrated; keep new copy in resources.
2. ~~**Cards** — `GamingModeCard.kt`, … define their copy inline.~~ — migrated.
3. ~~**Foreground service notifications** — hardcoded notification bodies.~~ —
   migrated; notification copy lives in resources like everything else.

> [!WARNING]
> Duplication hazard: some labels already exist as resources *and* as
> hardcode (e.g. "Done"/"Centre"/"Clear" are in `strings.xml` while sibling
> strings like "Move"/"CANCEL" are inline). When migrating, prefer the
> existing resource keys and remove the inline copy.

## Adding a new locale

Create a locale folder next to `values/` and mirror the keys:

```
app/src/main/res/values-xx-rYY/strings.xml   # e.g. values-es, values-zh-rCN, values-in
```

Update `resConfigs` (`app/build.gradle.kts`), `res/xml/locales_config.xml`,
and `LocaleParityTest.localeDirs` to match. Full-only copy goes in the
mirrored `src/full/res/values-*/strings_spoof.xml` instead.

Rules:

- **Same keys, same placeholders.** Translation values must keep `%1$s`
  markers; the target language is free to reorder them (that is why they are
  numbered).
- **No plural strings yet** — the codebase currently uses `%1$d of %2$d`
  phrasing rather than `plurals`. If a translation needs plural rules, add a
  `<plurals>` resource and a small `pluralStringResource`-style helper.
- Keep the app name and URL strings identical across locales.
- Do not translate the `scope` array (package names).

## Adding a new string — checklist

1. Add to `values/strings.xml` under the right section, or create the
   section header comment if none matches.
2. Reference it via `stringResource(R.string.<name>, args...)` in Compose or
   `context.getString(R.string.<name>, args...)` outside Compose.
3. Only put format args that vary at runtime. Never break a sentence into two
   resources — that defeats per-language reordering.
4. Verify the build still assembles (stale `R.string.*` references fail the
   build; the resource merger refuses unused ids in release).

## Useful tooling

- **Android Studio lint** flags hardcoded strings (`HardcodedText`) on XML
  and many Compose string literals. The project sets `lint.abortOnError=false`,
  so these are warnings, not build breakers — expect them to stay warnings
  until the migration backlog is cleared.
- Android Studio's **Extract string resource** refactoring (Alt+Enter on a
  hardcoded literal) is the fastest way to migrate the backlog item by item.
- CSV round-trip (Translations Editor) works for team-managed translations.

## When hardcoded strings are acceptable

A short, deliberate allowlist:

- Version/flavour literals composed, not translated (`v${BuildConfig.VERSION_NAME}`).
- Dynamic values (file names, package names, sizes) — translatable copy
  should not be concatenated into them where a placeholder works instead.
- Technical package/property identifiers.

Everything else user-facing should be a resource. If you're touching a file
that still has inline copy, migrate those strings while you're there.