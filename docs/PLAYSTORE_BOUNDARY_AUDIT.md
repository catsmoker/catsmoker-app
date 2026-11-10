# Play distribution boundary audit — 2026-11-10

> Historical first-pass evidence, not certification of the current build. The
> independent second pass found additional shared updater/monetization inputs;
> see [SECOND_PASS_FLAVOR_AUDIT.md](SECOND_PASS_FLAVOR_AUDIT.md) for their fixes
> and fresh two-direction release evidence. In particular, the updater/billing
> stubs described below have since been removed. Never reuse these old hashes,
> generated APKs or signing observations to approve a newer artifact.

## Outcome and scope

This review concerns the existing app, not a new module or application. It does
not authorize publication. **Distribution isolation is not Play policy approval.**
No account, device, release-track, signing identity, version or application ID
changes are part of this work. Existing uploaded releases remain unchanged.

Build/artifact results are recorded below after execution. Unverified release
requirements must not be inferred from a successful compilation.

## What the distribution boundary means

The two flavors choose features; the two build types choose debugging and
optimization. `playstoreRelease` means both choices together. `src/main` ships
in both distributions; `src/full` is not an input to Play; `src/playstore`
provides Play replacements. A hidden button, false feature flag or R8 removal
does not by itself exclude an implementation, library, manifest or resource.
R8 can rename/inline classes, so searching a release DEX for original names alone
cannot establish absence.

### Inputs and paths reviewed

| Surface | Actual selection / conclusion |
|---|---|
| Modules, fallbacks | Only `:app` in `settings.gradle.kts`; standard flavor source selection, no custom fallback/source-set redirection or dynamic feature modules. `main` + selected flavor + build type are combined. |
| Identity | Both flavors retain `com.catsmoker.app`, version 2.0.7 / code 13, min 27, target/compile 37. Signing configuration was not changed. The normal private release configuration is used when complete, otherwise the existing debug fallback applies. |
| Shared dependencies | AndroidX/Compose/Hilt/WorkManager, Firebase Analytics, libsu, Shizuku, Gson/Coil and the vendored Nothing Glyph AAR intentionally remain shared. Neither root nor a package-name match alone proves a policy breach. |
| Full dependencies | Resolved `fullReleaseRuntimeClasspath` contains Start.io 5.4.0 (including its JSON dependency), not AdMob/Billing or Xposed API. Xposed API is `fullCompileOnly`, never a packaged runtime dependency. Full keep rules stay full-only. |
| Play dependencies | AdMob and Play Billing are `playstoreImplementation`; actual transitive release graph is exported by `writePlaystoreReleaseAudit`, rather than inferred from the version catalog. |
| Full implementation | `features/spoofdevice`, LSPosed/Vector activation/hooks, Magisk generation, spoof models/repository/provider, profile import/export, GitHub updater implementation, VPN controller/service and JNI caller live in `src/full`. |
| Entry points / DI | Shared `AppNavHost` calls flavor `spoofGraph`; Play registers no spoof destinations. Play profile intake is inert. Hilt generates bindings from selected sources; shared GamingToolsViewModel no longer injects a VPN controller. |
| Manifest | Full owns all-files access, package-install permission, VPN service, Xposed metadata/scope/provider and profile intake filters. Play owns AdMob metadata and an explicit VPN service removal marker. Final manifests, not markers, are checked. |
| Assets/resources | `xposed_init`, `native_init`, spoof strings/scope are full-only. VPN help/service/state strings moved to full in all seven existing locale directories. Japanese already lacked five service translations; this move does not introduce that fallback. Flavor legal overrides remain intentional. Shared PNG/INI/SAV/JSON/CVar game data are data assets, not hook entry points. |
| Native | Previously Play also compiled `catsmoker_bridge.c` (Unity delivery); only spoof C was guarded. Both C sources and NativeBridge now live in full; the main Android.mk selects **no app native module** for Play. Third-party native libraries must be inventoried independently. |
| Dynamic behavior | Shared source has no app-authored DEX/JAR/SO downloader/loader. Thermal-service reflection reads platform APIs, not downloaded code. CrosshairLibraryRepository downloads bounded, filename-validated PNGs with PNG structure/CRC checks; not executable updates. Play SelfUpdater has no network/download/install implementation. Full native preload and full GitHub APK updater remain excluded. The final DEX does contain DexClassLoader/PathClassLoader references; R8's original-name map includes Google Ads/Dynamite loader code. These are not evidence that CatSmoker's full updater leaked. This is source/static inspection, not certification of SDK runtime loading or traffic. |

### Proven gaps corrected

1. Removed shared VPN UI, consent launcher, state/actions and injection. Full now
   owns `BackgroundNetworkControls` + `VpnControlsViewModel`; Play's extension is
   empty, not a fake VPN controller. Full network help/icon and translated VPN
   strings are selected separately. Shared Data Saver is preserved.
2. Removed modern all-files API queries/settings actions from shared code through
   flavor `system.StorageAccess`. Legacy Android 8–10 runtime storage access
   remains shared. Full uses the app-specific settings action with general fallback.
3. Moved the previously shared Unity/native bridge and its test to Full. No native
   functionality was intentionally removed from Full, and no FPS cap was added.
4. Added read-only input export and `tools/check_playstore_boundary.py`. The guard
   checks source/resource regressions, real resolved dependencies, final manifest,
   all DEX type/class tables, full-only original names in R8 mapping and packaged
   hook/native assets. It is targeted, not a complete policy or dependency-security audit.

### Intentionally retained shared features

Data Saver uses `cmd netpolicy`; DNS reads `TRANSPORT_VPN` to warn about another
VPN, without creating one. libsu/Shizuku power shared settings, app maintenance,
process management and game editors. Root-manager package names in protected
lists/queries keep those managers from being stopped; they are not Xposed hooks
or Magisk generation. Game editors change graphics/FPS/resolution configuration,
including PUBG save fields, New State INI, GRID XML, Genshin config and HSR/WuWa
preferences. They use file backup/write/read-back mechanisms; some reach other
apps' data through root/Shizuku. These are real retained behaviors, **not** declared
approved merely because the spoof screen is absent.

## Policy review — unresolved release decisions

Official sources were fetched on 2026-11-10. Rules can change; Console-specific
declarations, SDK Index status and actual prior rejection evidence were not
accessible. The historical VPN/all-files rejection is a project record, not an
independently retrieved Console decision.

| Area | Finding / required follow-up |
|---|---|
| VPN / all files | Current policies have eligible use categories/exceptions; it is inaccurate to say every non-core VPN or every file tool is categorically forbidden. This app's previous rejection is respected by exclusion. No attempt to requalify or resubmit those permissions was made. Every active track must be reviewed by the owner; a local fix cannot retire older uploads. |
| Game manipulation / sandbox | Device and Network Abuse explicitly includes game-cheating apps affecting other apps and unauthorized interference/access. Review the actual game-file modifications, private app-data access, suspend/force-stop and system-setting commands against that policy and the store listing. User consent/root authorization alone does not prove Play eligibility. No blanket deletion of intended shared tools was made without a product decision. |
| Outside-Play updates | Play's selected updater is inert; no install permission or executable update path is intended. GitHub crosshair PNG downloads are data, not self-updating executable code. Runtime SDK internals were not reverse-engineered. |
| Ads / consent | UMP 4.0.0 resolves transitively, but Play AdsInit calls MobileAds.initialize and banner requests have no UMP consent gate or privacy-options flow in app code (the configured interstitial ID has no request implementation). A library's presence does not establish integration. Where Google's consent requirements apply, a compliant consent platform/configuration and gating before ad requests are required. A legal-screen acknowledgement or ad-free purchase is not a substitute. The built release manifest also selects Google's sample AdMob application ID: this is an inspection artifact, not verified production monetization configuration. This audit did not replace private IDs, add a consent system or alter ad monetization. |
| Firebase / privacy | Analytics is enabled by default, provider-initialized, and Settings applies a saved opt-out in Application.onCreate. That is not prior consent and may permit collection before application code runs. Region/age/consent strategy, SDK disclosures and Data safety need owner review; no consent-mode correctness is claimed. |
| Sensitive data | Installed-app inventory, usage access, audio Visualizer permission, notifications, overlays, diagnostics and background work need accurate purpose-specific disclosures and affirmative consent where required. Existing legal/onboarding text is not proof each access is adequately disclosed before it occurs. No audio/video recording feature was found or added. |
| FGS / battery / visibility | Shared services use specialUse foreground-service declarations; Play requires justified core, user-visible/stoppable work and declarations/demo evidence. Usage access, broad MAIN intent package queries and battery-optimization exemption requests need minimization/eligibility review. No QUERY_ALL_PACKAGES declaration is intended; no broad policy approval follows from that. |
| Billing | Play Billing handles remove_ads; only PURCHASED is granted, pending is not granted, reconnect restores purchases, acknowledgement is retried. No server/token verification backend was found. Validate refund/revocation/acknowledgement behavior and product setup in an authorized billing test; none was performed here. |
| Donations | External voluntary tips grant no digital entitlement in source/legal copy. Payments policy has a 100%-to-creator/no-benefits tip exception, not a blanket donation exemption. Confirm actual processor flows, eligibility and market-specific rules; never route ad-removal purchases outside Play. |
| Privacy URL / listing | App link is the owner's general website legal page. Verify Play's requirement for the app/developer identity, SDK/data handling, retention/deletion and contact information there or at an appropriate app-specific public policy URL. Do not rewrite the separate general website into an app-specific page. Console declarations/listing and website content were not changed or certified. |
| SDK / packaging / target | Target 37 exceeds the current API 36 new-app/update requirement. That does not prove all target-37 runtime behavior, SDK compliance, native page-size support, ad configuration, or signing continuity in Console. The vendored Glyph SDK's redistribution terms remain an existing unresolved legal/documentation issue. |

### Official references

- Android [build variants](https://developer.android.com/build/build-variants),
  [manifest merging](https://developer.android.com/build/manage-manifests),
  [bundletool](https://developer.android.com/tools/bundletool).
- Play [VpnService](https://support.google.com/googleplay/android-developer/answer/12564964),
  [All files access](https://support.google.com/googleplay/android-developer/answer/10467955),
  [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646),
  [User Data](https://support.google.com/googleplay/android-developer/answer/10144311),
  [Payments FAQ](https://support.google.com/googleplay/android-developer/answer/10281818),
  [target API](https://support.google.com/googleplay/android-developer/answer/11926878).
- Google [UMP Android privacy](https://developers.google.com/admob/android/privacy)
  and [official Android examples](https://github.com/googleads/googleads-mobile-android-examples).

## Build and artifact evidence

### Observed results

| Check | Evidence / result |
|---|---|
| Play release bundle | `:app:bundlePlaystoreRelease` completed, including R8, resource shrinking, lint-vital tasks and signing. Artifact: `app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab`, 13,334,101 bytes, SHA-256 `2267d041f5939a179952d1978f3a7f031f9ffe21392b5af26ec6fbb279434984`. Application ID/version/SDK values match the table above; release is not debuggable. |
| Full compilation | `:app:compileFullDebugKotlin` and `:app:externalNativeBuildFullDebug` completed. Both moved native translation units compile; native algorithms were not rewritten. No Full release APK was assembled and no device behavior is claimed. |
| Release dependencies / generated bindings | Fresh Play export resolves AdMob 25.5.0, Billing 9.1.0, Firebase Analytics 24.0.0, UMP 4.0.0, libsu and Shizuku; no Start.io/Xposed. Selected Play KSP output contains no VPN controller/VM, NativeBridge, SpoofRepository or LSPosedModule binding. Full runtime dependency report independently confirms the opposite ad boundary. |
| Final manifest | Both AGP's merged artifact and bundletool's decoded **new AAB** manifest pass the guard. No all-files/install permission, VPN service/action/binding permission, spoof provider or Xposed metadata. Shared services and SDK providers remain inventoried, not indiscriminately removed. |
| DEX / obfuscation | AAB: one DEX, 9,271 class definitions, 11,061 type references. All types pass the full-only/API checks. R8 original-name inventory: 10,922 class headers checked, including source-derived full-only classes; supplied mapping is byte-identical to the mapping embedded in this AAB. An additional scan of 49,763 method references and constant strings finds no `VpnService` API, `Environment.isExternalStorageManager` or modern all-files settings action. This is not decompilation or a complete call-graph analysis. |
| Resources / assets | Bundletool resource table: 1,784 named string entries; no full-only VPN/spoof strings, shared Data Saver present. All seven resource moves preserve the original key/value union. Nine packaged assets are the existing game/PNG/CVar data plus Play's intentional registration properties; no `xposed_init`/`native_init`. |
| Native inventory | Only AndroidX graphics-path and DataStore shared-counter libraries, across arm64-v8a, armeabi-v7a, x86 and x86_64; no `libcatsmoker_bridge.so`. All inventoried ELF PT_LOAD alignments are 16,384 bytes; this alone is not device/page-size certification. Bundle tasks disable the APK ABI-split filter, explaining the x86 dependency libraries. |
| Derived APKs | Official bundletool 1.18.3 validates the AAB and generates universal + default split sets. All **58 APKs** have manifests/resources/assets inspected using aapt2 36.0.0 plus ZIP/DEX parsing; four code-bearing APKs each pass the complete DEX checks (the universal plus three SDK-targeted base masters). No forbidden payload was found in configuration-only APKs. No APK was installed. |
| Focused tests | Full: NativeBridgeTest 4/4 and InfoTopicsTest 6/6 pass. Play: InfoTopicsTest 6/6 passes. Guard: 8/8 Python tests pass. LocaleParityTest **fails** on 110 required Japanese keys; every reported key was already missing in HEAD. The complete gaming-string key/value union is unchanged by relocation (175 total untranslated Japanese gaming keys, including keys outside this test's required subset). The failing test was not weakened or suppressed. No full test suite or clean build was run. |
| Signing | Existing complete private release-signing configuration was selected and left unchanged. Jarsigner reports `jar verified`; SHA-256 signer fingerprint `BA:BD:27:6F:B7:4E:A8:8B:C3:02:E9:92:E5:85:C0:06:77:FC:B9:78:41:72:D6:A7:8C:2F:4F:FB:E6:DE:4E:DE`. It also warns about self-signed trust/no timestamp, ZIP attributes, and manifest placement differing for JarInputStream versus JarFile. These warnings are retained, not represented as a clean signing certification. Console upload-certificate continuity was not accessible. Derived inspection APKs use bundletool's local debug signing, not Play App Signing. |

### Commands and local evidence

PowerShell, repository root; Java from Android Studio. The build/test tasks below
are the actual focused tasks used, not a recommendation to run every task after
every edit:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
./gradlew :app:bundlePlaystoreRelease :app:writePlaystoreReleaseAudit :app:compileFullDebugKotlin :app:externalNativeBuildFullDebug :app:testFullDebugUnitTest --tests 'com.catsmoker.app.system.shell.NativeBridgeTest' --tests '*LocaleParityTest' --continue --console=plain
./gradlew :app:testFullDebugUnitTest --tests 'com.catsmoker.app.system.shell.NativeBridgeTest' --tests '*LocaleParityTest' --tests '*InfoTopicsTest' :app:testPlaystoreDebugUnitTest --tests '*InfoTopicsTest' --continue --console=plain
./gradlew :app:dependencies --configuration fullReleaseRuntimeClasspath --console=plain
python -m unittest discover -s tools -p test_check_playstore_boundary.py -v
```

The first combined invocation completed the Play bundle but exited nonzero on
test compilation: InfoTopicsTest still called the moved shared help builder.
The test now checks the selected flavor's help (four sections Full / two Play).
The subsequent focused invocation compiles both test variants and runs them;
it still exits nonzero solely on the existing Japanese LocaleParityTest failure
described above. It is **not** recorded as an all-green test run.
Earlier, Full resource processing exposed six pre-existing unescaped apostrophes
in Japanese spoof strings; only escaping was corrected. Existing Japanese
`gf_tuner_intro` formatting and API-deprecation warnings were not expanded into
unrelated cleanup. Release lint's success is not a full lint audit.

```powershell
$java="$env:JAVA_HOME\bin\java.exe"
$bt="$env:LOCALAPPDATA\Temp\opencode\bundletool-all-1.18.3.jar"
$out="$env:LOCALAPPDATA\Temp\opencode\flavor-audit-artifacts"
$aab='app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab'
# Output directory already created for this inspection; no device/account access.
& $java -jar $bt validate --bundle=$aab
& $java -jar $bt dump manifest --bundle=$aab | Out-File "$out\AndroidManifest.xml" -Encoding utf8
& $java -jar $bt dump resources --bundle=$aab --values | Out-File "$out\resources.txt" -Encoding utf8
& $java -jar $bt build-apks --bundle=$aab --output="$out\universal.apks" --mode=universal --overwrite
& $java -jar $bt build-apks --bundle=$aab --output="$out\splits.apks" --overwrite
python tools/check_playstore_boundary.py --manifest app/build/reports/playstore-boundary/AndroidManifest.xml --manifest "$out\AndroidManifest.xml" --dependencies app/build/reports/playstore-boundary/dependencies.txt --archive $aab --mapping app/build/outputs/mapping/playstoreRelease/mapping.txt --resources "$out\resources.txt" --require-artifacts
```

Bundletool was fetched from its official release and checked against the published
SHA-256: `a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`.
The one-off, read-only local APK loop is retained at
`%LOCALAPPDATA%\Temp\opencode\inspect_flavor_apks.py`; it extracts each APK from
both sets, invokes `aapt2 dump xmltree <apk> --file AndroidManifest.xml` and
`aapt2 dump resources <apk>`, checks every APK's payload, and calls the regression
guard on every code-bearing APK. Invocation: `python <that-path>`.

Local logs (not published artifacts):

- `%LOCALAPPDATA%\Temp\opencode\flavor-audit-gradle-final.log`
- `%LOCALAPPDATA%\Temp\opencode\flavor-audit-tests.log`
- `%LOCALAPPDATA%\Temp\opencode\flavor-audit-full-dependencies.log`
- `%LOCALAPPDATA%\Temp\opencode\flavor-audit-artifacts\`: `validate.log`,
  `boundary.log`, `apk-inspection.log`, decoded manifests, resource inventories,
  both APK sets and `evidence-hashes.json`.
- `app/build/reports/playstore-boundary/`: manifest, actual resolved component
  IDs and artifact paths. Reports are local build outputs, not credentials or a
  substitute for reading the signed bundle.

No upload, track retirement, device run, billing purchase, SDK traffic inspection,
commit or push occurred. **Boundary checks passed; release approval/readiness
remains unresolved** for the policy/product decisions above, production ad
configuration, Console declarations and runtime verification.
