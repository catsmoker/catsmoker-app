# Distribution boundary third pass — 2026-11-10

## Outcome and scope

No additional app-feature leakage was found in this pass. Three weaknesses in
the inspection tools were reproduced and corrected instead. App sources and
build configuration are unchanged from the second pass; their hashes and the
five release artifact hashes were checked again. No app rebuild, app JUnit run,
signing change, device/account action, publication or Git index change was made.

This extends [the second pass](SECOND_PASS_FLAVOR_AUDIT.md), not a claim of Play
approval or runtime certification. That report retains the actual release-build,
app-test and signing evidence.

## Proven checker gaps fixed

| Gap | Correction |
|---|---|
| Iterating the APKs present in a ZIP could certify an incomplete set. | Parse bundletool's `toc.pb`; reject missing, duplicate, unsafe or unlisted APK paths, wrong packages and unsupported modules. Require both universal and split sets, one code base per targeted variant, matching versions, and complete per-variant coverage of the AAB's code/assets/native payload. |
| Finding one native bridge filename did not prove every Full output retained its intended bridge. | Bind each APK to Gradle's `output-metadata.json`, package/version and ABI filters. Universal requires all three configured bridge ABIs; each ABI APK requires its own. Verify exact Xposed/native loader declarations as well as the existing required native exports. |
| Public SDK package checks missed internal AdMob/Billing/consent implementations and an AdMob API-only dependency. | Reject the corresponding internal namespaces and rendering/API dependency family in Full. Keep shared `ads.identifier` / `internal.ads_identifier` allowed: those are not the AdMob rendering SDK. |

Changed tools: `tools/check_playstore_boundary.py`,
`tools/inspect_distribution_artifact.py`, and
`tools/test_check_playstore_boundary.py`. Focused negative cases cover all three
families. No production implementation needed moving or disabling in this pass.

The APK-set parser uses the authoritative
[bundletool 1.18.3 schema](https://raw.githubusercontent.com/google/bundletool/1.18.3/src/main/proto/commands.proto),
not an assumed protobuf layout. The official JAR's SHA-256 was rechecked as
`a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`.

## Verification results

- **37/37 focused Python boundary tests passed.** These are checker tests, not
  Android app tests.
- Both Gradle audit exports completed successfully in 44 seconds: 24 tasks,
  one executed and 23 up-to-date. Selected/generated source directories and
  resolved compile/runtime inputs were checked again; no assemble, bundle,
  clean, lint or app-test task was requested.
- The Play AAB and **all 58 derived APKs** passed the strengthened inspection:
  one universal APK plus the complete default split set, including four
  code-bearing APKs overall. TOC completeness, versions and per-variant payload
  coverage passed, alongside the existing manifest/resource/DEX/original-R8-name
  checks and exact derived code/native/source-asset hashes.
- **All four Full APKs** passed, including metadata-bound ABI coverage, correct
  loader targets, eight required mapped Full class definitions and 14 required
  native exports per packaged bridge ABI. Full retains its features and excludes
  Play rendering/Billing implementations.
- The original-name inventory contains zero forbidden internal SDK classes in
  Full. Play legitimately contains 3,173 `internal.ads` and 152
  `internal.play_billing` original classes; these are not Full features leaking
  into Play. Unused transitive UMP code need not survive shrinking.

The second-pass derived APK sets were reused, not regenerated: the input AAB is
byte-identical, and this pass checked their TOCs, versions and payload hashes
against that exact AAB. Converted manifests/resources are checked structurally,
not claimed byte-identical. Known converted ART profiles receive the documented
format checks rather than a false byte-equality claim.

### Unchanged release artifacts

| Artifact | SHA-256 |
|---|---|
| `app-playstore-release.aab` | `020cf013f880a7118d92e394da29b9b5b0d06bf342fb2a1f6facaf100442b772` |
| `app-full-universal-release.apk` | `f5fe160b8e6965f35ce2e9697177cef78b2676b8c2af16193845db1f13427d7b` |
| `app-full-arm64-v8a-release.apk` | `7422172a18b6dd68b27d3d729bc5f6908d2d1f64ba8120b4cfa5939c96ea931c` |
| `app-full-armeabi-v7a-release.apk` | `39a16cede0f590ebfc1372260ae8a9f88aaa58bc4c67ab22e5586381ba86252c` |
| `app-full-x86_64-release.apk` | `37425d5497b0911b2f3d2e8c8bd32ffd0cb0d2fc434070dea17d7852daa17eb3` |

Paths remain `app/build/outputs/bundle/playstoreRelease/` and
`app/build/outputs/apk/full/release/`. Inspect Full outputs with their original
Gradle filenames and metadata before any GitHub release alias is applied.

## Reproduction and local receipts

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew :app:writeFullReleaseAudit :app:writePlaystoreReleaseAudit --console=plain
python -m unittest discover -s tools -p test_check_playstore_boundary.py -v

$temp="$env:LOCALAPPDATA\Temp\opencode"
$aapt2="$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\aapt2.exe"
$java="$env:JAVA_HOME\bin\java.exe"
python tools/inspect_distribution_artifact.py --variant playstore --artifact app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab --audit-directory app/build/reports/playstore-boundary --mapping app/build/outputs/mapping/playstoreRelease/mapping.txt --aapt2 "$aapt2" --java "$java" --bundletool "$temp/bundletool-all-1.18.3.jar" --apks "$temp/second-pass-artifacts/universal.apks" --apks "$temp/second-pass-artifacts/splits.apks" --output "$temp/third-pass-artifacts/app-playstore-release"

Get-ChildItem app/build/outputs/apk/full/release/app-full-*-release.apk | ForEach-Object {
    python tools/inspect_distribution_artifact.py --variant full --artifact $_.FullName --audit-directory app/build/reports/full-boundary --mapping app/build/outputs/mapping/fullRelease/mapping.txt --aapt2 "$aapt2" --output "$temp/third-pass-artifacts/$($_.BaseName)"
}
```

Receipts are local under
`C:/Users/Lenovo/AppData/Local/Temp/opencode/third-pass-artifacts/`:
`audit-export.log`, `guard-tests.log`, five `*-inspection.log` files, and each
artifact's `evidence.json`. Receipts bind artifact, mapping, audit inputs,
inspection-tool hashes, APK-set/TOC hashes and inspected APK hashes; Full also
records its output-metadata hash. The initial source/build hash snapshot and
staged-index snapshot permit a final unchanged-input/index check.

## Limits still open

No app JUnit tests were rerun because app inputs did not change. The second pass
had 43 selected app tests pass and one existing `LocaleParityTest` failure on
110 required Japanese gaming keys already absent in HEAD. This remains open;
37 passing checker tests do not erase it.

Prior signing warnings and unverified Play Console certificate/version
continuity remain; signatures were not reverified in this pass. The identical
artifact hashes link to the second pass's signing evidence, not to Console
acceptance. Ads consent/sample configuration, disclosures/declarations,
privileged gaming-feature policy eligibility, SDK/runtime behavior and vendor
licensing still need their separate review. No device, SDK traffic, purchase,
account or Play review was exercised. Static flavor isolation is not approval.
