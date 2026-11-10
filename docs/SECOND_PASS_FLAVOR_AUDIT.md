# Independent distribution boundary second pass — 2026-11-10

Follow-up: [THIRD_PASS_FLAVOR_AUDIT.md](THIRD_PASS_FLAVOR_AUDIT.md) reinspects the
same artifact hashes with stronger completeness/native/SDK checks. The build,
app-test and signing results below remain the second-pass record.

## Scope and conclusion

Review of the current implementation, not acceptance of the earlier report.
Only `:app` exists; no composite/dynamic-feature modules, custom source roots,
flavor fallbacks or missing-dimension selection were found in executable config.
The standard `main` + selected flavor + build type providers are checked below.
No version, package ID, signing configuration, account, device, staged index or
published release was intentionally changed. No commit/push/upload is authorized.
Boundary verification is not a Google Play approval or general security audit.

## Proven issues and corrections

| Issue independently found | Correction / ownership |
|---|---|
| Shared Settings still orchestrated Full updates (preferences, cache, dialogs, download/cancel/install calls), despite an inert Play updater stub. | `src/full/.../features/settings/FullSettingsViewModel.kt`, `DistributionSettings.kt`, `SelfUpdater.kt`, `UpdateNotesPreview.kt`. Play has no updater stub/parser/state and only an empty selected update section/badge adapter. Common `SettingsViewModel.kt` owns appearance, analytics, notification state and installed-APK sharing. Existing update verification/prompt/cancellation logic is preserved. |
| Free-ad gate/dialog/artwork and Play purchase/store UI were selected inside shared Settings with flags. Billing policy was shared; Full had an unnecessary billing repository stub. | Full owns `AdsDonationDialog.kt`, the cat drawable, free-ad state/UI; Play owns `RemoveAdsPolicy.kt`, `RemoveAdsRepository.kt`, purchase state/UI and `SettingsActions.kt`. Selected `AdManager.isPreferenceKey()` keeps billing preference knowledge out of the shared dashboard. No Full repository stub. Common UI primitives are reused. |
| Spoof quick action/routes, spoof-only graphics probe and reference-only Unity target-list parser were shared build inputs. | Full `DistributionQuickAction.kt`, `SpoofRoutes.kt`, `GraphicsApiDetector.kt`, `UnityTargetList.kt`; Play quick-action extension is empty. Existing route strings, spoof parent-entry VM scope, algorithms and parser behavior are retained. |
| Distribution labels, updater/free-ad/billing strings and Full legal defaults remained in main; common licenses selected two SDK resource names with flags. | Existing translations moved byte-for-byte to selected `strings_distribution.xml`; existing Play disclosures retained. One flavor-provided `about_license_ads_components` key replaces two shared SDK labels. Common labels stay common. Full updater external-files FileProvider path moved to the Full XML overlay; shared log/APK-sharing paths stay shared. |
| Regression guard could miss Kotlin file facades, extra classes inside same-path adapters, reverse leaks, stale R8 maps, configuration-only APKs and unexpected assets. | Guard compares per-flavor symbols/resources in both directions, reads all DEX type tables plus original R8 names, binds external map to embedded AAB map, checks selected/generated source directories and compile/runtime graphs, requires Full payload. New read-only artifact inspector decodes APK binary manifests, checks resources and all split APKs, and matches derived DEX/native/asset hashes to the supplied AAB. Negative tests cover the new cases. |

Moved tests follow ownership (`testFull`, `testPlaystore`); shared tests contain
no excluded implementation references. Locale parity inventories both flavor
resource sets, while artifact checks enforce which set actually ships. This does
not waive any existing missing-translation failure.

## Classification

| Module / input / dependency | Shared | Full only | Play only |
|---|---|---|---|
| Gradle module | `:app` | selected `full` source set | selected `playstore` source set |
| UI / app shell | Common navigation, settings preferences, legal renderer, logs, donation route, game dashboard/editors | Spoof graph/routes/quick action, updater/free-ad UI/state | Purchase/store UI/state |
| Gaming / privileges | Gaming engine, root/Shizuku shell and file runner, Data Saver, DNS/other-VPN detection, overlays, game configuration editors, cleaner, Glyph | VPN consent/controller/service; modern all-files queries/intents | Unavailable modern-storage and empty VPN extensions |
| Ads / payment | Selected `AdsInit`, `AdManager`, `AdBanner` call contracts; common voluntary tips grant no entitlement | Start.io SDK 5.4.0 + transitive SDK dependencies; free preference toggle | AdMob 25.5.0 + SDK dependencies, UMP transitive library, Billing 9.1.0 + entitlement policy/repository |
| Platform libraries | AndroidX/Compose/Material/Lifecycle/WorkManager/Hilt, coroutines, Gson, Coil, Firebase BOM/Analytics, libsu 6.0.0, Shizuku 13.1.5, vendored Glyph AAR | Xposed API 82 **compile-only**, Full keep rules | Play SDK dependencies above; no Xposed |
| Hooking / modules | None | LSPosed/Vector, spoof repository/models/provider, Magisk generator, native preload/Unity bridge | None |
| Native / assets | AndroidX graphics-path + DataStore counter; game JSON/INI/SAV/CVar data and bundled crosshair PNG | Both C sources, JNI caller, `libcatsmoker_bridge.so`, `xposed_init`, `native_init`, cat artwork | `adi-registration.properties` |
| Permissions/components | Shared services, launcher, guarded Shizuku, private FileProvider/receivers; existing shared permissions | All-files/install permission, VPN service, Xposed metadata/scope, UID-scoped spoof provider, profile intake filters | AdMob metadata/providers and billing permission from selected dependencies |
| Tests | Common behavior (`test`) | Updater/gate/notes/reference parser/native/spoof (`testFull`) | Billing policy (`testPlaystore`) |

The resolved release runtime graphs contain 234 Full and 247 Play components
(including the project node). Differences include the following; they are actual
selected versions, not additional direct declarations:

| Resolved difference | Full | Play |
|---|---|---|
| Ads | Start.io `inapp-sdk:5.4.0`, `json:1.0.2` | Google `play-services-ads` / `ads-api:25.5.0`, UMP `4.0.0` |
| Billing | Absent | `billing` / `billing-ktx:9.1.0` |
| WebKit / app-set conflict resolution | WebKit `1.15.0`, appset `16.1.0` | WebKit `1.12.1`, appset `16.0.1` |
| Other Play SDK transitives | Absent at these coordinates | Browser `1.8.0`, location `19.0.0`, places-placereport `17.0.0`, Play `hsdp:2.0.1`, transport and Firebase encoder components |

`play-services-ads-identifier:18.0.0` is intentionally shared, not the AdMob
rendering SDK. A namespace-wide `ads.*` ban would incorrectly classify that
separate Analytics/ads identifier dependency. Both graphs' only third-party
native producers are the resolved AndroidX `graphics-path` and `datastore-core`
AARs; their SO names are explicitly inventoried by the guard. Full adds only the
app-owned bridge. Unclassified future SOs or assets require review.

## Reactivation / policy investigation

Shared calls reach only selected adapters; neither a stored flag, navigation
string, old update preference nor a delivered profile can link a missing Full
class/provider/native payload. Play registers no spoof graph or profile intake;
its native selector builds no app module. App-authored network downloads in
shared code are the bounded numeric-catalog PNG crosshair library, validated as
image data, not DEX/JAR/SO/APK updates. Thermal reflection resolves platform
services, not downloaded code. Full executable download/install paths are not
selected for Play. SDK-owned Google Ads/Dynamite loaders are a separate SDK
behavior, not an app-authored mechanism to restore Full; their runtime traffic
and future SDK changes are not certified by static analysis.

Current official [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646)
was consulted again: outside-Play self-updates/downloaded executable code and
unauthorized interference, including game-cheating apps affecting other apps,
remain concrete concerns. Root/Shizuku and file editing are not classified from
their names alone. Intended shared FPS/graphics editors, app-data access,
process/network management and system settings are retained, not represented as
approved. Their listing/purpose/authorization eligibility remains an owner/store
decision; removing them merely because classification is uncertain would change
the requested product. VPN policy has eligible-use exceptions, not an absolute
core-VPN-only rule; misleading manifest comments were corrected without adding
the rejected Play service.

The earlier audit's open consent/disclosure/FGS/Glyph-license/SDK/runtime/Console
questions remain open. UMP being resolved is not consent-flow integration;
sample AdMob configuration is not production setup. A valid local signature
does not establish that Console accepts this upload certificate. No billing
transaction, SDK traffic, device behavior or Play Console account was tested.

## Fresh execution evidence

### Builds and focused tests

Executed with Android Studio's JBR as `JAVA_HOME`, without clean, emulator,
installation, a full test suite, publication or signing-configuration changes:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew :app:bundlePlaystoreRelease :app:writePlaystoreReleaseAudit :app:writeFullReleaseAudit :app:testFullDebugUnitTest --tests '*AdsDonationGateTest' --tests '*UpdatePromptGateTest' --tests '*UpdateNotesPreviewTest' --tests '*UnityTargetListTest' --tests '*AnalyticsConsentTest' --tests '*LocaleParityTest' :app:testPlaystoreDebugUnitTest --tests '*RemoveAdsPolicyTest' --tests '*AnalyticsConsentTest' --continue --console=plain
# Separate invocation preserves Full's APK ABI splits.
./gradlew assembleFullRelease :app:writeFullReleaseAudit --console=plain
python -m unittest discover -s tools -p test_check_playstore_boundary.py -v
```

- Play release compilation, generated Hilt/Java code, R8, resource shrinking,
  signing and bundle packaging succeeded. Both audit exports succeeded, including
  29 AGP-selected/generated source directories per distribution, and the actual
  compile/runtime graphs. No opposite-flavor implementation roots or generated
  bindings were selected. Full's Xposed API is on the compile graph only, not the
  runtime graph or packaged class definitions.
- Full release build succeeded (8m10s), including native compilation, R8 and all
  four APKs. No Full AAB was built. Existing deprecation/resource-format warnings
  remain warnings, not boundary evidence.
- **43 focused app tests passed:** Full donation gate 6, update notes 6, update
  prompt 3, Unity reference parser 10, analytics 4; Play billing policy 10 and
  analytics 4. **One test failed:** `LocaleParityTest`, with 110 required Japanese
  gaming keys missing. All 110 reported keys were independently checked against
  `HEAD` and were already absent. The test was not suppressed. Thus the combined
  Play/test invocation exited nonzero despite successful release packaging.
- **23 lightweight guard tests passed.** They exercise both leak directions,
  obfuscated original names, Kotlin file facades, same-path adapter extra classes,
  stale AAB/APK maps, resource renaming, SDK asset ownership, binary manifests,
  external DEX types and 32/64-bit ELF export parsing.

Build/test logs are local under
`C:\Users\Lenovo\AppData\Local\Temp\opencode\`:
`second-pass-play-and-tests.log` and `second-pass-full-release.log`.
JUnit reports are under `app/build/test-results/test{Full,Playstore}DebugUnitTest/`.

### Delivered artifact checks

Official bundletool **1.18.3** validated the fresh Play bundle and generated
universal and default split APK sets. The inspector then decoded fresh final
manifests/resources and checked **all 58 derived APKs**, including configuration
splits without DEX or a resource table. Four APKs contain code. Their DEX, native
libraries and source assets match the supplied AAB by SHA-256; bundletool's
relocated/transcoded ART baseline profiles are checked separately by known path
and format, not misrepresented as byte-identical source assets.

| Fresh artifact | Bytes | SHA-256 |
|---|---:|---|
| `bundle/playstoreRelease/app-playstore-release.aab` | 13,315,119 | `020cf013f880a7118d92e394da29b9b5b0d06bf342fb2a1f6facaf100442b772` |
| `apk/full/release/app-full-universal-release.apk` | 5,770,714 | `f5fe160b8e6965f35ce2e9697177cef78b2676b8c2af16193845db1f13427d7b` |
| `apk/full/release/app-full-arm64-v8a-release.apk` | 5,607,079 | `7422172a18b6dd68b27d3d729bc5f6908d2d1f64ba8120b4cfa5939c96ea931c` |
| `apk/full/release/app-full-armeabi-v7a-release.apk` | 5,604,389 | `39a16cede0f590ebfc1372260ae8a9f88aaa58bc4c67ab22e5586381ba86252c` |
| `apk/full/release/app-full-x86_64-release.apk` | 5,606,182 | `37425d5497b0911b2f3d2e8c8bd32ffd0cb0d2fc434070dea17d7852daa17eb3` |

Paths in the table are relative to `app/build/outputs/`. The generated universal
APK name is not the GitHub publication alias `app-full-release.apk`; no artifact
was renamed or uploaded in this task.

**Play:** one DEX, 9,264 class definitions, 11,054 type references; 10,913 original
R8 class entries and 1,780 named strings checked. External mapping is byte-for-byte
identical to the map embedded in the AAB. No Full implementation, updater state/UI,
spoof/VPN resources, hook init assets, app native bridge, Full SDK asset, cat artwork,
all-files/install permissions, VPN component, Xposed metadata or spoof provider
was found. Native payload is only graphics-path/DataStore counter for four ABIs.
Shared gaming data assets and Play's `adi-registration.properties` remain present.

**Full:** every APK has one DEX with 7,062 class definitions and 8,662 type
references; 8,115 original R8 entries and 2,050 named strings checked. The map's
`pg_map_id` matches the actual DEX R8 marker. Required mapped classes are also
defined in the DEX: LSPosedModule, NativeBridge, VpnFirewallService,
FullSettingsViewModel, SelfUpdater, MagiskModuleBuilder, SpoofRepository and
GraphicsApiDetector. This is stronger than finding names in an obsolete map.
All-files/install permissions, VPN component, Xposed metadata, spoof provider and
profile filters remain. Hook init assets contain the correct module/library
names; each packaged app bridge exports all **14** required native entry points.
No Play Billing/AdMob rendering/UMP implementation or purchase resources ship.
The shared advertising-identifier dependency is not confused with AdMob.

Resource obfuscation was explicitly handled: Full's `drawable/ic_cat_sad` resolves
to renamed `res/9o.png`; the referenced ZIP entry and PNG bytes exist. Play must
not contain that resource regardless of its filename. Full's SDK-owned
`io_start_sdk_vast_endcard_smart.html` matches the reviewed Start.io AAR asset
SHA-256 `f4cc7a038e64d4bd0f1cfd52683721d34f1ddd0135a62787e6ce27e00608b67e`.
Other unknown/changed assets and native libraries fail the guard rather than
silently gaining shared status.

### Reproduction and retained evidence

Local tooling and evidence root (not release assets):

```powershell
$java="C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
$aapt2="$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\aapt2.exe"
$tool="$env:LOCALAPPDATA\Temp\opencode\bundletool-all-1.18.3.jar"
$evidence="$env:LOCALAPPDATA\Temp\opencode\second-pass-artifacts"
$aab="app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab"
& $java -Xmx512m -jar $tool build-apks --bundle=$aab --output="$evidence/universal.apks" --mode=universal --overwrite
& $java -Xmx512m -jar $tool build-apks --bundle=$aab --output="$evidence/splits.apks" --overwrite
python tools/inspect_distribution_artifact.py --variant playstore --artifact $aab --audit-directory app/build/reports/playstore-boundary --mapping app/build/outputs/mapping/playstoreRelease/mapping.txt --aapt2 $aapt2 --java $java --bundletool $tool --apks "$evidence/universal.apks" --apks "$evidence/splits.apks" --output "$evidence/playstore"
Get-ChildItem app/build/outputs/apk/full/release/*.apk | ForEach-Object {
    python tools/inspect_distribution_artifact.py --variant full --artifact $_.FullName --audit-directory app/build/reports/full-boundary --mapping app/build/outputs/mapping/fullRelease/mapping.txt --aapt2 $aapt2 --output "$evidence/$($_.BaseName)"
}
```

The inspectors do not build/install/publish apps. All five final inspections
succeeded with the final guard version. Each output directory retains
`evidence.json` (artifact, mapping, audit inputs and APK hashes), decoded final
manifests/resources and tool output. Inspection logs are
`second-pass-artifacts/play-inspection.log` and
`second-pass-artifacts/app-full-*-release-inspection.log`.
Derived Play inspection APKs are locally debug-signed, not upload candidates;
their signing does not modify the input AAB or reproduce Google Play App Signing.

### Signing and verification limits

All four Full APKs pass `apksigner verify --verbose --print-certs` (v2 signatures).
The AAB reports `jar verified`; its warnings include self-signed/untrusted chain,
missing timestamp, ZIP attributes and JarFile/JarInputStream entry-order
inconsistencies. These warnings are retained, not called a clean signing pass.
The current AAB and Full APK certificate SHA-256 is
`BA:BD:27:6F:B7:4E:A8:8B:C3:02:E9:92:E5:85:C0:06:77:FC:B9:78:41:72:D6:A7:8C:2F:4F:FB:E6:DE:4E:DE`.
This differs from the immediately pre-second-pass inspected bundle's certificate
(`B8:86:…`); signing configuration was not edited by this task. Console certificate
continuity and whether version code 13 is available remain unverified.

Static artifact checks establish the inspected boundaries, not runtime perfection
or store approval. No device flow, purchase, SDK traffic, account or Console
declaration was exercised. Consent/sample ad IDs, sensitive-access disclosures,
FGS/privileged gaming-feature eligibility and Glyph licensing remain separate
open decisions. The [VPN policy](https://support.google.com/googleplay/android-developer/answer/12564964)
contains eligible-use exceptions; no exception or approval is claimed here.
