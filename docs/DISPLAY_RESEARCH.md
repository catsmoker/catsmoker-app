# Display & refresh-rate control: research findings

What was investigated before `screens/gaming/display/` was written, what the platform
actually permits, and what remains unverifiable. Reading order for a maintainer: the
capability table (section 4) and the limitations (section 8).

## 1. Question

CatSmoker already forced `min_refresh_rate`/`peak_refresh_rate` to the panel's measured
peak. That cannot express "run at 90 Hz", cannot touch an external display, and does
nothing about Samsung DeX's 60 Hz cap. A published project (**HzUnlock**) claimed the DeX
cap is liftable from the Shizuku `shell` identity with no root. The task was to establish
which of its mechanisms are real, which apply to CatSmoker, and how to build them without
copying its code.

## 2. Repositories reviewed

| Repository | Licence | What was taken from it |
| --- | --- | --- |
| [npfp222/HzUnlock](https://github.com/npfp222/HzUnlock) | **Source-available, all rights reserved** — reading allowed, redistribution and "copy any part of this source code into another project" forbidden | Read only. Confirmed the mechanism list, the compatibility tiers, and the failure reports. **No code was copied, ported or adapted.** |
| [mahmutaunal/Adaptive-Hz](https://github.com/mahmutaunal/Adaptive-Hz) | MIT | Confirmed the settings-key approach and the vendor key vocabulary. |
| [neveSZ/onehz](https://github.com/neveSZ/onehz) | MIT | Samsung panel rate enumeration via `SEC_FLOATING_FEATURE_LCD_CONFIG_HFR_SUPPORTED_REFRESH_RATE`; `refresh_rate_mode` as a Samsung secure key; a per-vendor capability gate. |
| [Enki013/hyperosunfcker](https://github.com/Enki013/hyperosunfcker) | LGPL-3.0 | HyperOS tuning scope; nothing display-specific was reusable. |
| [Kangmori/Hz120Switch](https://github.com/Kangmori/Hz120Switch) | none stated | `peak_refresh_rate` on ColorOS; no licence to build on, so no reuse. |
| [xtawa/samsung_hz_ops](https://github.com/xtawa/samsung_hz_ops), [anlaki-py/akihz](https://github.com/anlaki-py/akihz), [Lkxuii/refresh-rate-switch](https://github.com/Lkxuii/refresh-rate-switch), [cameleonnbss/originos-toolkit](https://github.com/cameleonnbss/originos-toolkit), [lab-ok/galaxy-optimization-advanced-tool](https://github.com/lab-ok/galaxy-optimization-advanced-tool) | MIT / GPL-3.0 / none | Survey only: every one of them drives the same `min_refresh_rate` / `peak_refresh_rate` / `user_refresh_rate` / `miui_refresh_rate` rows CatSmoker already writes. None reaches a display mode. |
| [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku), [Shizuku-API](https://github.com/RikkaApps/Shizuku-API) | Apache-2.0 | The `shell`-UID channel (`newProcess`, user service) already used by `ShellRunner`. |
| [LineageOS frameworks_native](https://github.com/LineageOS/android_frameworks_native), [frameworks_base](https://github.com/LineageOS/android_frameworks_base) | Apache-2.0 | Cross-checked against AOSP `main` for the permission checks quoted below. |

HzUnlock's own compatibility reports (issues #1–#7) were read as evidence of what the
mechanisms do in the field: 1080p modes that render in a corner of a 1440p frame, flicker
at 4K/100, black screens at 1080p/240, and a DeX session that restarts when the monitor
cannot be driven. Those reports are the reason this feature refuses a rate the display
does not enumerate and puts a countdown on every mode switch.

## 3. What the platform actually allows

Read from AOSP source (`frameworks/native/services/surfaceflinger/SurfaceFlinger.cpp`,
`frameworks/base/core/res/AndroidManifest.xml`, `packages/Shell/AndroidManifest.xml`,
`core/java/android/view/SurfaceControl.java`).

- `SurfaceComposerAIDL::setDesiredDisplayModeSpecs` → `checkAccessPermission()` →
  `callingThreadHasUnscopedSurfaceFlingerAccess()` → `ACCESS_SURFACE_FLINGER`.
- `ACCESS_SURFACE_FLINGER` is `protectionLevel="signature"` …
- … and `com.android.shell` **declares** `<uses-permission android:name="android.permission.ACCESS_SURFACE_FLINGER"/>`
  and is platform-signed, so **UID 2000 holds it** and UID 0 (root grants all permissions) does too.
- The SurfaceFlinger **debug backdoor** (transaction codes 1000–1046: force composite, mode
  set, `frame-rate flexibility`, and the "show refresh rate" overlay) is gated on
  `HARDWARE_TEST`, also `signature`, and **`com.android.shell` does not declare it**. So that
  whole family is genuinely out of reach for a Shizuku client — a hidden API being hidden is
  not the same as it being reachable.
- `SurfaceControl.setDesiredDisplayModeSpecs/getDesiredDisplayModeSpecs` exist in AOSP
  `main`. `getPhysicalDisplayIds`/`getPhysicalDisplayToken` **do not** — verified against
  `framework.jar` pulled from an Android 15 device: `setDesiredDisplayModeSpecs` is present
  as a string, every display-token accessor is absent. Samsung's builds keep them, which is
  why a composer path exists there and not on stock Android.

### The comment this corrects

`GameDeveloperOptions` states the overlay toggle is blocked because shell cannot hold
`ACCESS_SURFACE_FLINGER`. The *conclusion* is right, the *permission* is the wrong one: shell
does hold `ACCESS_SURFACE_FLINGER` (the composer policy proves it), and the overlay is blocked
by `HARDWARE_TEST`. The original comment has been corrected rather than left standing.

## 4. Capability matrix (UID 2000 / 1000 / 0)

| Mechanism | app | shell (2000) | system (1000) | root (0) | Gating |
| --- | --- | --- | --- | --- | --- |
| `min_refresh_rate` / `peak_refresh_rate` | yes\* | yes | yes | yes | `WRITE_SETTINGS` for the app, else `settings put` |
| `SurfaceControl.setDesiredDisplayModeSpecs` | no | **yes** | yes | yes | `ACCESS_SURFACE_FLINGER` **and** a display-token accessor existing |
| `forcePacesetter` / `resetForcedPacesetter` (Samsung) | no | yes | yes | yes | same composer access; firmware must define the calls |
| `enableHighResolutionsForExternalDesktop` (DeX) | no | yes | yes | yes | Samsung multi-tasking service present |
| SurfaceFlinger debug backdoor 1000+ (incl. refresh overlay) | no | **no** | yes | yes | `HARDWARE_TEST`, signature-only |
| sysfs display nodes | no | no | no | yes | SELinux never grants these to shell |

\* with the user-granted `WRITE_SETTINGS` permission.

Verified on a rooted POCO X3 NFC / Android 15: the `shell`-UID child reports
`getSpecs: true, setSpecs: true, displayIds: false, displayToken: false`. The composer path
is therefore **unavailable on stock Android 15** and degrades to the settings rows, which
moved the panel 120 → 60 → 120 Hz exactly as recorded.

## 5. Mechanisms worth building on

1. **Enumerated modes, not a rate catalogue.** `Display.getSupportedModes()` is the only list
   of rates a display has actually promised. Every rate the UI offers comes from it, per
   display, per connection.
2. **`setDesiredDisplayModeSpecs`** is the composer's own policy — stronger than a settings
   row, which the display manager may override. Needs a display token, which is the part
   that disappears on newer Android.
3. **Samsung's pacesetter** decides whose vsync applications render against. In DeX that is
   the difference between "the monitor runs at 120 Hz" and "the app renders at 120 Hz".
   Its transaction numbers are vendor-specific, so they are **discovered from
   `/system/lib64/libgui.so`** (the AIDL proxy loads the code into `w1` before `transact()`)
   rather than hardcoded — a hardcoded number would address an unrelated method after a
   firmware update. The discovery is implemented from the ELF64 and AArch64 specifications;
   nothing was taken from HzUnlock.
4. **`enableHighResolutionsForExternalDesktop`** lifts DeX's external timing filter.
5. **Thermal status** from `dumpsys thermalservice` is the basis for backing a rate down.

## 6. Rejected

- **Hardcoded transaction codes / modes.** Rejected: reassigned by firmware updates.
- **Calling the backdoor code range to probe for a mode setter.** Rejected: an unknown code
  on SurfaceFlinger is not a safe guess.
- **`vendor.display.hdmi_cfg_idx` and driver reconnects.** Root-only, model-specific, and
  the kind of permanent change this project does not make.
- **"120 Hz display ⇒ 120 FPS" framing.** Rejected outright; see `DisplayEngineState.appRenderHz`
  and the separate `Now` / app-rendering rows.
- **A second broadcast API.** The display operations ride the existing authenticated
  `GAME_AUTOMATION` intent (version 2), not a parallel one.

## 7. Distribution

The `shell`-UID child, the pacesetter discovery and the DeX switch exist **only** in
`app/src/full/`. `app/src/playstore/` carries a `VariantDisplayBackend` with the same
Fully Qualified Name whose every method answers `refused`, and the Full implementation is not
in Play's source inputs at all. Play keeps the AOSP settings rows, which need no hidden API.

## 8. Known limitations

- **The composer path is unavailable on stock Android 14/15** because the display-token
  accessors were removed. It is reachable on builds that keep them (Samsung One UI among
  them). The card reports which specific method is missing instead of claiming a backend.
- **Samsung DeX and the pacesetter could not be verified here.** No Samsung device was
  available. The code paths are gated on a runtime probe that must succeed before use, and
  every write is read back, but "compiles and refuses honestly" is not "works on a Galaxy".
- **`app_process` indirection is unverified by unit test.** The command string, the classpath
  and the shell-UID launch were exercised by hand on the rooted POCO (`probe` returned a JSON
  object) but not on a Shizuku-only device.
- **Kotlin/Compose localisation**: the new strings exist in `values/` only. A missing key
  falls back to English, the same behaviour as any untranslated string.
- **Application rendering rate** is not measured by this feature; it is shown only when the
  metrics engine is already producing an FPS sample.
