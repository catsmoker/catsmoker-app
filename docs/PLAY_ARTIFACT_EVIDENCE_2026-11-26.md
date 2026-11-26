# Full and Play artifact boundary evidence, 2026-11-26

Both directions of the distribution boundary were audited against release AABs
with the complete input set (`--require-artifacts`), and both passed with no
`LIMIT` line. This closes the "artifact checks incomplete" gap recorded in
`MISSION_REPORT.md` for this artifact pair. It is still a boundary check, not a
Play policy review and not evidence that the bundle will be approved.

## Artifacts

| Item | Play | Full |
| --- | --- | --- |
| Bundle | `app-playstore-release.aab` | `app-full-release.aab` |
| Size | 14,112,076 B | 11,263,375 B |
| SHA-256 | `91347e16d9221d2a0d755e90cef62de93bb59db9a8d68d1b87030458002e96a4` | `ea0223f879913b7223bf18afc07bcb8bb81991ddf25e114937d97ce113923936` |
| DEX | 1 / 9,811 class definitions / 11,656 type references | 1 / 7,339 / 8,982 |
| R8 original names | 11,506 | 8,407 |
| Resource strings | 1,815 | 2,083 |
| Local universal APK (debug-signed, not for distribution) | `3d7436f7…8ece8b86` | `8c49684b…c6c467c4a912` |

Both bundles carry exactly three ABIs after the 2026-11-26 `abiFilters` change
(see `DECISIONS.md`): `arm64-v8a`, `armeabi-v7a`, `x86_64`. Before that change
the release AABs also packed a 32-bit `x86` bridge that no APK split shipped.

## Commands used

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
./gradlew :app:bundleFullRelease :app:bundlePlaystoreRelease

# Resolved graphs (Play must show ads + billing; Full must show Start.io and
# only compile-only Xposed)
./gradlew -q :app:dependencies --configuration playstoreReleaseRuntimeClasspath   > dependencies.txt
./gradlew -q :app:dependencies --configuration playstoreReleaseCompileClasspath   > compile-dependencies.txt
./gradlew -q :app:dependencies --configuration fullReleaseRuntimeClasspath        > full-dependencies.txt
./gradlew -q :app:dependencies --configuration fullReleaseCompileClasspath        > full-compile-dependencies.txt

# Final manifest + resource table from each signed bundle
java -jar bundletool-all-1.18.3.jar dump manifest  --bundle=<aab> > <variant>-manifest.xml
java -jar bundletool-all-1.18.3.jar dump resources --bundle=<aab> > <variant>-resources.txt

# Local inspection APK sets (bundletool debug-signs them; never publish)
java -jar bundletool-all-1.18.3.jar build-apks --bundle=<aab> --output=universal.apks --mode=universal
java -jar bundletool-all-1.18.3.jar build-apks --bundle=<aab> --output=split.apks
```

`source-inputs.txt` / `full-source-inputs.txt` are one
`kind<TAB>absolute-directory` line per AGP-selected directory for that variant
(`main`, flavor, `release`, variant, plus generated `ksp` and
`ap_generated_sources` roots; 22 lines each). No Gradle task exports this today;
the recipe lives in [FLAVOR_WORKFLOW.md](FLAVOR_WORKFLOW.md).

## Guard invocations

Play:

```bash
python tools/check_playstore_boundary.py --variant playstore \
  --manifest AndroidManifest.xml --dependencies dependencies.txt \
  --compile-dependencies compile-dependencies.txt \
  --archive app-playstore-release.aab --archive u/universal.apk \
  --archive s/splits/base-master.apk --archive s/splits/base-master_2.apk \
  --archive s/splits/base-master_3.apk \
  --mapping app/build/outputs/mapping/playstoreRelease/mapping.txt \
  --resources resources.txt --source-inputs source-inputs.txt --require-artifacts
```

Full:

```bash
python tools/check_playstore_boundary.py --variant full \
  --manifest full-manifest.xml --dependencies full-dependencies.txt \
  --compile-dependencies full-compile-dependencies.txt \
  --archive app-full-release.aab --archive fu/universal.apk \
  --mapping app/build/outputs/mapping/fullRelease/mapping.txt \
  --resources full-resources.txt --source-inputs full-source-inputs.txt --require-artifacts
```

## What each direction proves

Play (all `PASS`):

- Manifest has no spoofing/updater/VPN components; the graph resolves
  `play-services-ads:25.5.0` + `billing:9.1.0` and contains no Start.io,
  Truenet, Xposed or `xposed:api` token in either configuration.
- Shipped DEX has no `de/robv/android/xposed/`, `android.net.VpnService`,
  spoofing, updater or Start.io types.
- No `xposed_init`, `native_init`, `libcatsmoker_bridge.so` or `ic_cat_sad.png`
  entry is packaged; natives are only `libandroidx.graphics.path.so` and
  `libdatastore_shared_counter.so`.
- Every packaged asset matches its reviewed source/SDK hash; the resource table
  has no full-only string or artwork.
- The AAB's embedded R8 map matches the external `mapping.txt`, and each
  code-bearing APK's DEX map ID matches it too.

Full (all `PASS`):

- The graph resolves `com.startapp:inapp-sdk:5.5.1` and keeps
  `de.robv.android.xposed:api:82` in the compile classpath only; no Play ads,
  billing or UMP appears.
- `xposed_init` and `native_init` are packaged exactly once with the expected
  contents, the bridge defines all 14 JNI entry points from
  `app/src/full/jni/*.c`, and `libcatsmoker_bridge.so` exists for all three
  shipped ABIs.
- The R8 inventory contains the required Full classes and no Play-only resource
  or artwork leaks in (`ic_cat_sad` present, `sys_remove_ads_*`/`billing_*`/
  `rate_app_*` absent).

## Known gaps

| Gap | Detail |
| --- | --- |
| Split-set nuance | The Full guard evaluates each archive on its own, so only archives carrying the bridge payload can be passed. `base-master` APKs carry DEX but no `.so`, so the Full run uses the AAB and the universal APK; the same DEX is covered there and in the Play `base-master` splits. |
| Guard coverage | Proven for these two AABs and the APKs derived from them by bundletool 1.18.3. It is not a malware or Play policy scanner. |
| Signing | Local inspection APKs are debug-signed. The upload key, Play App Signing and any artifact re-signing were not exercised. |
| Version | Evidence is bound to versionCode 16 / versionName 2.0.10-beta.1. Any code change requires a fresh build and a fresh run; these hashes must not be cited for a later artifact. |

## Debug install path after the ABI change

The three-ABI filter was re-checked on the install path the release bundles do
not exercise: `:app:assembleFullDebug` on the x86_64 desktop emulator produced
universal and per-ABI APKs with `arm64-v8a`, `armeabi-v7a` and `x86_64` and no
`lib/x86/` entry. `adb install -r` succeeded, the installed package reports
`primaryCpuAbi=x86_64`, the app drew its home surface with the Shizuku badge
active and ROOT inactive, and logcat carried no `UnsatisfiedLinkError` or
`dlopen failed`. A `QUERY_STATUS` broadcast after the reinstall still returned
the app's state, so the automation path survived the repackaging.
