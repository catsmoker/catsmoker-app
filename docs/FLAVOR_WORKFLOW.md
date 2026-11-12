# Distribution flavors — development workflow

Catsmoker is **one application with two distribution variants**, built from a
single codebase with Android Gradle product flavors. There is no maintained
`playstore` copy of the code: both APKs/AABs come from this branch.

## Variants

| Variant | Dimension | Who | Contains |
|---|---|---|---|
| `full` | `distribution` | GitHub release ONLY | Shared gaming features plus spoofing/LSPosed/Magisk, Start.io ads, GitHub self-updater, All files access, local VPN; no Play Billing/AdMob |
| `playstore` | `distribution` | Google Play release ONLY | Same app minus the restricted set: no spoof implementation, AdMob ads, no self-updater, no `MANAGE_EXTERNAL_STORAGE`, no `VpnService` |

> [!IMPORTANT]
> **GitHub Releases Rule**: GitHub Releases must ONLY contain the `full` variant assets: the universal `app-full-release.apk` plus all per-ABI split APK files (`app-full-arm64-v8a-release.apk`, `app-full-armeabi-v7a-release.apk`, `app-full-x86_64-release.apk`). NEVER upload or publish the `playstore` variant (`app-playstore-release.apk` / `.aab`) to GitHub Releases — it is strictly reserved for Google Play Console distribution.

Gradle tasks use the flavor name: `assembleFullDebug`, `assemblePlaystoreRelease`,
`testFullDebugUnitTest`, `testPlaystoreDebugUnitTest`, `installPlaystoreDebug`.
The generated universal Full filename is `app-full-universal-release.apk`;
`app-full-release.apk` is its GitHub publication alias (see `BUILD.md`).

Play policy omissions (2026-10-31): the `playstore` manifest declares neither
`android.permission.MANAGE_EXTERNAL_STORAGE` nor the
`VpnFirewallService`/`android.net.VpnService` intent filter. Both live in
`src/full/AndroidManifest.xml`. Since 2026-11-10, VPN consent, controller, UI,
help and service strings are also full-only, with no Play VPN controller stub.
Android 11+ all-files queries/settings intents live in the full `StorageAccess`
implementation; the Play implementation returns unavailable without those APIs.
Play uses SAF/MediaStore for files and Data Saver
(`cmd netpolicy`, root/Shizuku) for network blocking.

The Play manifest also explicitly removes `VpnFirewallService` during manifest
merging, keeping it out of every Play build without changing the full variant.
Existing uploads are not changed by source edits: replace affected artifacts in
each Google Play release track with a newly built Play bundle.

Play-specific resource overrides in `src/playstore/res/values*/strings_legal.xml`
describe Data Saver rather than an available VPN filter, including in onboarding,
and omit unavailable spoofing/Magisk features from storage and risk disclosures.
The Play `strings_gaming.xml` describes metered background-data restrictions,
not a blanket internet block or an exclusive connection for the game, and omits
advice to use the hidden VPN switch on unmetered networks. Its card uses a data
usage icon; the full build retains its VPN icon, disclosure and switch guidance.
Shared Data Saver captions distinguish metered network type from enabled state
and include metered Wi-Fi. Mirror Play overrides in every supported locale: a
default-only override would still let translated shared wording appear.

The app's external legal link points to `https://catsmoker.vercel.app/legal`.
The website is maintained in a separate project. Its legal page is intentionally
general to all offerings, not specific to any app or service. Website inquiries
use the contact form; do not publish email addresses or add app-specific policy
sections there. The in-app privacy policy remains specific to the installed
flavor. Website changes do not update the Play Console declaration or replace
active app releases.

## Source sets

```text
app/src/
├── main/        # Shared code — everything used by both variants. Default home.
├── full/        # Full-only: spoofdevice/, SpoofRepository et al, Start.io
│                # wiring, xposed_init, strings_spoof, scope array, spoof tests
│                # (src/testFull/), All files access + VPN controls/service,
│                # system/shell/NativeBridge.kt + jni/ Unity/spoof bridge,
│                # updater state/UI/notes, free-ad gate/artwork, spoof quick action,
│                # graphics probe/reference parser, full manifest additions.
└── playstore/   # Play-only: AdMob wiring, adi-registration.properties,
                 # empty background-network extension, unavailable modern
                 # storage API, Billing policy/repository/purchase settings,
                 # Rate App action, Play manifest additions (AdMob App ID).
```

`src/main` must **never** import from `src/full`: those classes do not exist in
the Play compile. Variant boundaries use same-FQN shims — one file per variant
with the same package/name and shared-call contract, different bodies. Additional
owner-only methods need not be reproduced as unused stubs:

- `system.distribution.VariantCapabilities` (`HAS_SPOOF`, `HAS_SELF_UPDATE`)
- `system.ads.AdsInit` (SDK bootstrap, called by `CatsmokerApp`)
- `system.ads.AdManager` (shared ad/observer contract; Start.io vs AdMob)
- `system.ads.AdBanner` (same signature; Start.io vs AdMob banner)
- `system.navigation.spoofGraph()` (real destinations vs empty block)
- `system.sharing.ProfileShareHandler` (profile intake vs no-op)
- `system.privacy.DistributionPrivacyGate` (Play consent UI vs Full no-op)
- `features.settings.Distribution*Settings` and `rememberAvailableUpdateName`
  (full updater/free-ad controls versus Play purchase/store controls)
- `features.home.DistributionQuickAction` (spoof entry versus empty extension)
- `features.gamingtools.BackgroundNetworkControls` (full VPN panel vs empty
  Play extension), plus flavor network icon/help builders
- `system.storage.StorageAccess` (real modern all-files queries/intents vs unavailable)

`src/main/jni/Android.mk` is only a selection script: `CATSMOKER_FULL=1`
selects both C sources from `src/full/jni`; Play builds no app native module.
Third-party shared native libraries are a separate inventory, not automatically
prohibited. DNS's `TRANSPORT_VPN` check remains shared: detecting another VPN
is not establishing a VPN or declaring a `VpnService`.

Shared callers use only these adapters and shared data. The common Settings
ViewModel owns appearance, analytics, notifications and APK sharing, not updater
or purchase state. `SelfUpdater`/`FullSettingsViewModel` exist only in Full;
`RemoveAdsRepository`/`RemoveAdsPolicy` exist only in Play. No reciprocal no-op
repository/updater stub is necessary. Capability constants describe availability;
they must not select full-only implementation or Play billing inside shared code.

## Where does new code go?

1. **Shared feature** (UI, gaming engine, game editors, settings, utils):
   `src/main`. It ships in both variants automatically. This is the default.
2. **Full-only capability** (LSPosed/Xposed, Magisk generation, self-update,
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
6. **Tests** for full-only code: `src/testFull/`; Play-only: `src/testPlaystore/`.
   `src/test/` runs against **both** variants and must not reference
    full-only classes.

Root/Shizuku are **not** synonymous with full-only. Shared gaming settings,
Data Saver, game-file editors and app-data cleaning intentionally use the shared
privilege runner. Review their actual effects and Play policies separately;
neither the word "root" nor an SDK's presence alone establishes a violation.

## Flavors versus build types

`full`/`playstore` choose distribution inputs; `debug`/`release` choose debugging,
signing and optimization. These are independent axes: both flavors have both
build types. Shared `main` is always included; only the selected flavor is added.
There are no custom source-set fallbacks or extra app modules in this project.
Changing a branch or building `release` alone does not select Play.

A hidden button or `HAS_* = false` only changes reachable behavior. R8 is the
release optimizer: it can remove unused code, rename classes and inline methods,
but it is not a reliable feature boundary. Full-only implementation, manifests,
assets/resources, native sources and dependencies must be excluded as inputs.
Same-name inert adapters are allowed; their full implementation is not compiled
into Play. Generated Hilt bindings follow the selected source inputs too.

## Focused verification

Run only the checks appropriate to the change, not all builds/tests after edits.
For a release boundary audit (explicit build authorization required):

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew :app:bundlePlaystoreRelease :app:writePlaystoreReleaseAudit :app:writeFullReleaseAudit
# Separate invocation: requesting a bundle disables APK ABI splits for that invocation.
./gradlew :app:assembleFullRelease
# Partial check until bundletool resources/APK inspection are supplied below.
python tools/check_playstore_boundary.py --manifest app/build/reports/playstore-boundary/AndroidManifest.xml --dependencies app/build/reports/playstore-boundary/dependencies.txt --archive app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab --mapping app/build/outputs/mapping/playstoreRelease/mapping.txt
```

The two Gradle audit exports resolve actual transitive compile/runtime graphs,
record AGP-selected source directories (including generated sources), and copy
the manifest through AGP's artifact provider (not an old guessed intermediate).
Also validate the AAB with official bundletool, dump its final manifest, generate
local universal and default split APK sets, and inspect those. Pass each
code-bearing APK with another `--archive` and each decoded final manifest with
another `--manifest`. The Python guard reads every DEX type/class table and R8's
original class-name map, checks known and source-derived full-only classes,
manifest permissions/components, dependencies, assets and the app JNI library.
It permits legitimate shared root, Shizuku, Data Saver and VPN detection.
Inspect the final resource table too; source-only success is not artifact proof.
Use `--resources <dump.txt> --source-inputs <sources.txt>
--compile-dependencies <compile-dependencies.txt> --require-artifacts` for the
complete input set. `--variant full` checks the reverse boundary and required
Full payload, not just Play exclusions. AAB mapping must match its embedded map;
APK mapping must match the actual DEX R8 map ID and required mapped class
definitions. Resource-table identity is checked even when R8 renames filenames.
SDK assets are reviewed/pinned separately from app assets, and unknown native
libraries/assets fail review rather than silently becoming shared.
`tools/inspect_distribution_artifact.py` inspects existing packages using official
bundletool/aapt2: final manifests/resources, every derived APK (including config
splits), code/assets/native inventories, and derived DEX/native/asset hashes
against the supplied AAB. It never builds, installs or publishes. See the dated
audit for actual commands and limitations; an old report is not evidence for a
new artifact.
APK-set inspection must reconcile every APK with bundletool's TOC, include both
universal and split modes, match package/versions, and cover each targeted
variant's AAB payload. Full APKs must match their Gradle output metadata and
bridge ABI filters, with exact hook-loader declarations. Internal SDK namespaces
are checked too; shared advertising-ID support is not AdMob rendering.
This is a targeted regression guard, not a generic policy/security certification.

See [THIRD_PASS_FLAVOR_AUDIT.md](THIRD_PASS_FLAVOR_AUDIT.md) for the latest
reinspection and guard corrections, and
[SECOND_PASS_FLAVOR_AUDIT.md](SECOND_PASS_FLAVOR_AUDIT.md) for the release builds,
app-test results, remaining policy risks and signing limits;
`PLAYSTORE_BOUNDARY_AUDIT.md` is the historical first pass. Do not
publish local inspection APKs: bundletool may debug-sign those; that does not
change the signed input AAB or represent Google Play App Signing.

For the separate review of intentionally retained Play functionality, see
[FOURTH_PASS_PLAY_POLICY_AUDIT.md](FOURTH_PASS_PLAY_POLICY_AUDIT.md): consent,
sensitive access, app visibility, foreground work, power exemptions, gameplay
changes, backup/privacy wording and Billing reliability remain independent gates.
Boundary success is not evidence that those gates passed.
Subsequent implementation changes and their remaining external requirements are
tracked in [PLAY_POLICY_REMEDIATION.md](PLAY_POLICY_REMEDIATION.md). Earlier
release hashes do not certify those modified sources; rebuild and reinspect a
release only when that work is authorized.

## Branches

`main` is the single canonical development branch; both variants build from
it. The historical standalone `playstore` Git branch has been **deleted** —
do not recreate it, do not develop on a second branch for the Play
version, and do not cherry-pick shared changes between branches. Its
Play-specific pieces live in `src/playstore`; its shared work was already in
`main`. Never force-push, never rewrite history.
