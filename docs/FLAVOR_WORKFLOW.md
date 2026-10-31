# Distribution flavors — development workflow

Catsmoker is **one application with two distribution variants**, built from a
single codebase with Android Gradle product flavors. There is no maintained
`playstore` copy of the code: both APKs/AABs come from this branch.

## Variants

| Variant | Dimension | Who | Contains |
|---|---|---|---|
| `full` | `distribution` | GitHub release ONLY | Everything: spoofing/LSPosed/Magisk, Start.io ads, GitHub self-updater, All files access, local VPN |
| `playstore` | `distribution` | Google Play release ONLY | Same app minus the restricted set: no spoof implementation, AdMob ads, no self-updater, no `MANAGE_EXTERNAL_STORAGE`, no `VpnService` |

> [!IMPORTANT]
> **GitHub Releases Rule**: GitHub Releases must ONLY contain the `full` variant assets: the universal `app-full-release.apk` plus all per-ABI split APK files (`app-full-arm64-v8a-release.apk`, `app-full-armeabi-v7a-release.apk`, `app-full-x86_64-release.apk`). NEVER upload or publish the `playstore` variant (`app-playstore-release.apk` / `.aab`) to GitHub Releases — it is strictly reserved for Google Play Console distribution.

Gradle tasks use the flavor name: `assembleFullDebug`, `assemblePlaystoreRelease`,
`testFullDebugUnitTest`, `testPlaystoreDebugUnitTest`, `installPlaystoreDebug`.

Play policy omissions (2026-10-31): the `playstore` manifest declares neither
`android.permission.MANAGE_EXTERNAL_STORAGE` nor the
`VpnFirewallService`/`android.net.VpnService` intent filter. Both live in
`src/full/AndroidManifest.xml`. Shared code consults
`VariantCapabilities.HAS_ALL_FILES_ACCESS` / `HAS_VPN_FIREWALL` to hide the
corresponding controls; Play uses SAF/MediaStore for files and Data Saver
(`cmd netpolicy`, root/Shizuku) for network blocking.

## Source sets

```text
app/src/
├── main/        # Shared code — everything used by both variants. Default home.
├── full/        # Full-only: spoofdevice/, SpoofRepository et al, Start.io
│                # wiring, xposed_init, strings_spoof, scope array, spoof tests
│                # (src/testFull/), All files access + VpnFirewallService,
│                # full manifest additions.
└── playstore/   # Play-only: AdMob wiring, adi-registration.properties,
                 # VPN no-op shim, Play manifest additions (AdMob App ID).
```

`src/main` must **never** import from `src/full`: those classes do not exist in
the Play compile. Variant boundaries use same-FQN shims — one file per variant
with identical package, name and API, different bodies:

- `system.VariantCapabilities` (`HAS_SPOOF`, `HAS_SELF_UPDATE`)
- `system.AdsInit` (SDK bootstrap, called by `CatsmokerApp`)
- `system.ads.AdManager` (same constructor + methods; Start.io vs AdMob)
- `shared.ui.components.AdBanner` (same signature; Start.io vs AdMob banner)
- `system.navigation.spoofGraph()` (real destinations vs empty block)
- `system.ProfileShareHandler` (profile intake vs no-op)
- `features.settings.SelfUpdater` (GitHub updater vs unsupported stub)
- `features.gamingtools.tools.firewall.VpnFirewall` (real local VPN vs no-op)
  with `VariantCapabilities.HAS_VPN_FIREWALL` / `HAS_ALL_FILES_ACCESS` deciding
  whether the control is shown at all

Shared callers (`AppNavHost`, `MainScreen`, `MainActivity`, `CatsmokerApp`,
`SettingsViewModel/Screen`) use only the shim API plus the capability flags.

## Where does new code go?

1. **Shared feature** (UI, gaming engine, game editors, settings, utils):
   `src/main`. It ships in both variants automatically. This is the default.
2. **Full-only capability** (root-only, LSPosed/Xposed, Magisk, self-update,
   non-Play ad SDK): `src/full`, same package path. If shared code must touch
   it, add a same-FQN shim pair instead of an `if (isPlayStore)` check.
3. **Play-only requirement** (Play-safe replacement, store messaging, Play
   SDK): `src/playstore`.
4. **Dependencies**: shared → `implementation`; full-only →
   `add("fullImplementation", …)` / `add("fullCompileOnly", …)` (Xposed is
   `compileOnly` — never packaged); Play-only →
   `add("playstoreImplementation", …)`.
5. **Manifest entries**: shared in `src/main/AndroidManifest.xml`; variant-only
   in `src/full/AndroidManifest.xml` / `src/playstore/AndroidManifest.xml`
   (merged automatically).
6. **Tests** for full-only code: `src/testFull/` (same package path).
   `src/test/` runs against **both** variants and must not reference
   full-only classes.

## Verification (both variants, every migration-sized change)

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew :app:assembleFullDebug :app:assemblePlaystoreDebug
./gradlew :app:assembleFullRelease :app:assemblePlaystoreRelease
./gradlew :app:testFullDebugUnitTest :app:testPlaystoreDebugUnitTest
```

Plus APK inspection: the Play APK must not contain spoof/Start.io classes
(search dex for `spoofdevice`, `SpoofRepository`, `LSPosedConfig`,
`MagiskModuleBuilder`, `com/startapp`) and its manifest must not list
`xposedmodule`, `configprovider` or the profile-share `application/json`
filters — while still containing the `gms.ads.APPLICATION_ID` meta-data.

## Branches

`main` is the single canonical development branch; both variants build from
it. The historical standalone `playstore` Git branch has been **deleted** —
do not recreate it, do not develop on a second branch for the Play
version, and do not cherry-pick shared changes between branches. Its
Play-specific pieces live in `src/playstore`; its shared work was already in
`main`. Never force-push, never rewrite history.
