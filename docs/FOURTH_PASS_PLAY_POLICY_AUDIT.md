# Fourth pass: other Google Play policy risks

Historical findings before remediation. See
[`PLAY_POLICY_REMEDIATION.md`](PLAY_POLICY_REMEDIATION.md) for subsequent changes
and remaining gates; this report's artifact hash describes the old release,
not the modified source or a newly built artifact.

Date: 2026-11-10. Scope: the existing Play distribution, its shared features,
current source and previously inspected release. This is a policy-risk audit,
**not approval, a completed Data safety declaration, or a claim that the findings
have been fixed**. No app behavior, permissions, product features or signing
configuration were changed in this pass.

The flavor boundary checks in `THIRD_PASS_FLAVOR_AUDIT.md` answer whether the
right implementation ships. They do not establish whether the implementation
that intentionally remains is eligible for Google Play.

## Findings and release gates

“Confirmed gap” means the described implementation or disclosure mismatch is
observable. It does not mean Google has issued a policy decision. “Eligibility
unverified” requires product-specific justification and sometimes Console or
runtime evidence, not a generic warning or a root grant.

### 1. Ads have no consent gate — confirmed implementation gap, high priority

`app/src/playstore/java/com/catsmoker/app/system/AdsInit.kt` initializes Mobile
Ads unless a cached ad-free entitlement exists. Its `shared/ui/components/AdBanner.kt`
requests an ad without a consent-state check. UMP 4.0.0 resolves transitively,
but no app integration, consent update/form flow or privacy-options entry was
found. A dependency alone is not consent handling.

Startup reaches `CatsmokerApp.initDeferredTasks()` after the splash, not after
the onboarding agreement. Google’s EU user consent policy applies in the EEA,
UK and Switzerland; necessary consent must precede the relevant storage or
personal-data use. This is not a claim that every country requires an identical
opt-in dialog. Non-personalized ads are not automatically consent-free either.

**Closure:** implement a compliant consent-management flow, gate initialization
and requests appropriately, provide required withdrawal/privacy options, and
test first launch, returning consent, refusal, errors and relevant regions.
Verify the AdMob account’s published privacy messages. UMP is an available
implementation, not the only possible compliant CMP.

### 2. Default analytics starts before agreement — confirmed ordering, consent unresolved

`app/src/main/java/com/catsmoker/app/system/config/AnalyticsConsent.kt` defaults
to enabled. Firebase’s provider initializes before `Application.onCreate()`,
where `CatsmokerApp.kt` synchronizes the saved preference. Settings offers a real
SDK collection switch, and Firebase persists that setting, but this is not a
prior-consent design on a fresh install. No manifest collection-disabled default
or explicit consent-state integration was found.

The legal copy does disclose default-on analytics. Accepting general terms later
does not itself establish any required prior consent. No custom `logEvent` calls
is not the same as no SDK events or identifiers.

**Closure:** decide the applicable analytics/advertising consent model and startup
defaults; verify it with SDK traffic and fresh/saved opt-out cases. Preserve
existing opt-outs. Do not describe opt-out as eliminating unrelated SDK traffic.

### 3. Sensitive access is broader than its explanation — confirmed disclosure gaps

`features/permissions/PermissionScreen.kt` presents generic permission captions
and optional information sheets. Usage access is described as tracking game
playtime, but `GameSessionService` observes the foreground package across apps
every three seconds, and `tools/forcestop/AutoForceStopService.kt` does so every
two seconds while enabled. The latter force-stops the previous foreground app,
not just games. Core packages are protected; other third-party apps can lose
background activity or timely messages. There is no user exception list in this
service. Both services provide ongoing notices and Stop controls.

`engine/GamingNotificationListener.kt` cancels existing and newly posted
notifications during suppression, except this app’s own. It does not merely
silence them, and turning suppression off cannot reconstruct dismissed notices.
This class does not read message bodies or upload them. Android may prevent
some notifications from being cancelled; do not promise universal cancellation.

Root availability checks also run from onboarding’s resume observer, including
before the agreement step has completed. `PermissionViewModel` can request root
automatically, and `ShellRunner.isRootAvailable()` can open a root shell. Thus
the claim that every privileged request follows a deliberate feature activation
is not supported.

**Closure:** provide accurate, prominent explanations and affirmative consent
where unexpected/background sensitive access requires it, before that access.
Explain all-app observation, force-stop consequences, actual notification
dismissal, local handling and how to stop each feature. A system grant, optional
info icon or bundled legal agreement is not automatically sufficient. Align root
request timing with an explained user action.

### 4. Broad app visibility still needs core-purpose justification — eligibility unverified

The main manifest omits `QUERY_ALL_PACKAGES` but includes a broad `MAIN` query;
installed-app queries and privileged package enumeration also exist. Current
Play policy explicitly restricts alternative methods that approximate broad
visibility to user-facing core functionality and interoperability.

**Closure:** inventory which packages each feature needs, narrow discovery where
possible, document the gaming-core justification for remaining discovery and
keep inventory separate from ads/analytics monetization. No project-owned
inventory uploader was found, but that alone does not certify SDK behavior.
“We do not request QUERY_ALL_PACKAGES” is not an exemption.

### 5. Foreground-service declarations need individual justification — eligibility unverified

The final Play manifest contains seven `specialUse` services: Crosshair,
Performance Overlay, Booster, Silent Hunter, Gaming Mode, Auto game session and
WorkManager’s foreground service. User controls, ongoing notices and Stop
actions are positive evidence, not proof that `specialUse` is the right type or
that every duration is necessary.

`DexoptSweepWorker` and `CleanerSweepWorker` can elevate scheduled, potentially
deferrable maintenance to foreground work. The shared manifest subtype says
only “Scheduled dexopt sweep”, despite serving cleaner work too. Some subtype
labels, particularly “Silent Hunter”, do not explain the operation. The workers
fall back to ordinary WorkManager execution if foreground setup is refused;
that is not proof of a hidden foreground-service bypass.

**Closure:** review type, core need, deferrability, duration and stopping behavior
for each feature. Supply accurate subtype descriptions and the required Console
declarations/demo videos. The policy allows user-initiated **or perceptible**
work under its conditions; it does not require a new tap for every service start.
Test boot restoration and relevant background-start restrictions on devices.

### 6. Direct battery-optimization exemption — eligibility unverified

Onboarding requests `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; the manifest
declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. A generic “run smoothly in the
background” caption is not evidence that direct exemption is permitted.

**Closure:** establish that the core feature fits an acceptable use case and
cannot use suitable alternatives. Otherwise offer the general optimization
settings screen without a direct exemption request. Do not infer eligibility
from Android accepting the intent or granting the permission.

### 7. Gameplay changes and interference remain substantive risks — eligibility unverified

Play intentionally retains shared game-file editors and privileged gaming tools.
`features/editgamefiles/pubg/PubgSavePatcher.kt` has phone/tablet presets that
change `TpViewValue`/`FpViewValue`; the editor exposes a Tablet View action and
asset. This changes gameplay-view configuration, not just CPU scheduling or
display refresh. The numeric fields were read, but their physical units and a
competitive advantage were not verified; do not call them proven FOV degrees.

Crosshair overlays, game-file changes, force-stopping/suspending other apps and
global system changes warrant review against Device and Network Abuse policy,
including its game-cheating and unauthorized-interference examples. User root
permission and risk warnings neither automatically permit nor categorically
prohibit all such tools. Publisher authorization and the actual functionality
matter; eligibility has not been established here.

`GamingEngine` explicitly excludes its `gamingDaemons` list from suspension;
an old comment about a power-saving package is **not** evidence that this path
disables a thermal daemon. Ordinary crosshair mode is non-touchable; no
keypress interception or aimbot was demonstrated.

**Closure:** obtain a feature-by-feature eligibility decision based on actual
effects, publisher authorization and current Play requirements. Do not silently
remove Full features or assume a flavor flag resolves the policy question.

### 8. “Never leaves the device” conflicts with backup eligibility — confirmed mismatch

The final Play manifest has `allowBackup=true`, with no `fullBackupContent`,
`dataExtractionRules` or custom backup agent. Default Android backup can include
preferences and `filesDir`, including `features/logs/CrashLogStore.kt`’s
`crash_reports` and `features/editgamefiles/ConfigBackupStore.kt`’s
`config_backups`. The legal copy says diagnostic/configuration files leave only
when explicitly shared and describes local retention until uninstall/clear.
Those assurances need qualification or a matching backup policy.

This is OS-managed backup eligibility, not proof that a backup occurred, a
project-owned uploader, or an automatic conclusion about Data safety “sharing”.
Absence of a backup-rules XML is not itself an Android/Play violation. Cache
exports are normally excluded; do not confuse them with persistent backups.

**Closure:** decide what may migrate/restore, add suitable old/new Android backup
rules or move sensitive files to no-backup storage, and align disclosure and
retention claims. Test cloud and device-to-device behavior; some OEM transfers
do not honor `allowBackup=false` as a universal migration prohibition.

### 9. Cleaner promises exceed the implemented safety boundary — confirmed copy gap

`res/values/strings_gaming.xml` says photos, messages, files and apps are “never
touched” and suggests normal cleanup removes only rebuildable files. However,
`CleaningFeature` includes loose installer files as a non-aggressive category;
`CleanerSweepWorker` can include it in scheduled cleanup. Installers are files
and are not necessarily reconstructible. Protected paths and category checks
are real safeguards; this finding is not proof that photos are currently deleted.

**Closure:** make each destructive category and scheduling consequence explicit;
remove absolute safety/rebuildability claims that the code cannot guarantee.
Keep warning, selection and actual deletion behavior consistent in every locale.

### 10. Billing acknowledgement reliability — hardening, not a blanket backend mandate

Play uses Billing 9.1.0 for paid ad removal, displays the returned price,
restores on successful queries, withholds benefits for pending purchases and
acknowledges purchased non-consumables without consuming them. Those are positive
implementation findings, not a completed purchase test.

`src/playstore/.../system/ads/RemoveAdsRepository.kt` ignores the acknowledgement
callback’s result and retries only on a later successful purchase query. A user
who does not return may miss the acknowledgement window and receive a refund.

**Closure:** check results, implement appropriate retry/reconciliation, and test
pending, purchase, restore, acknowledgement failures and refunds in authorized
Billing tests. Client acknowledgement is supported; a server is recommended
hardening where appropriate, not an unconditional policy requirement for every
app. No purchase or account action was performed here.

### 11. External policy, declarations and audience — verification still missing

The app’s declared privacy URL responded with HTTP 200 after redirecting from
`catsmoker.vercel.app/legal` to `catsmoker.is-a.dev/legal`. The fetched HTML was
a JavaScript shell, so rendered policy contents, developer/app identity, contact
mechanism and retention coverage were **not verified**. This is not proof the
page is broken, and Play does not impose a blanket server-rendered HTML rule.
Only that declared page was fetched; the separate website was not edited.

Console privacy URL, Data safety answers, target audience, ads declarations,
foreground-service declarations, SDK Index notices and Glyph SDK redistribution
rights remain unverified. Legal “not under 16” wording alone does not establish
the Console audience or a suitable child-directed advertising setup.

The current release uses Google’s documented sample AdMob application ID.
That is a release-configuration follow-up, not proof of a policy violation by
itself. The redundant `android.permission.AD_ID` declaration is not the real
Google SDK permission; the merged manifest also contains
`com.google.android.gms.permission.AD_ID`. Removing the former would not disable
advertising-ID access by the latter.

**Closure:** validate the rendered, public policy and accurate Console answers
against actual SDK/runtime behavior and audience. Verify release ad configuration
and third-party distribution rights. Do not add app-specific legal content to
the owner's separate general website without an explicitly authorized design.

## Data-flow inventory: input to review, not a Console declaration

| Data/access | Trigger and purpose | Destination observed or documented | Retention / stopping |
| --- | --- | --- | --- |
| Firebase analytics events/device identifiers | Default-enabled SDK startup; usage analytics | Firebase/Google SDK, not solely local; precise enabled collection needs runtime verification | Settings collection switch exists; provider/server retention needs verified policy |
| Ad SDK identifiers, IP-derived approximate location, interactions and diagnostics | Mobile Ads initialization/request; advertising | Google Mobile Ads 25.5 documentation describes automatic collection/sharing | Consent/privacy choices not integrated; ad-free entitlement suppresses app ads path, not all SDKs |
| Installed packages and foreground usage | App discovery, session detection, force-stop and gaming controls | Local application/privileged operations; no project inventory-upload path found | Watchers stop with their feature; stored preferences/history and SDK isolation need inventory |
| Notification package/key | Gaming notification suppression after listener grant | Local cancellation; no content-body upload in the inspected listener | Cancellation is not reversible; switching off allows subsequent notifications |
| Audio effect waveform / Bluetooth output information | Optional audio boost and output detection; audio permission requested by user | Local audio effect; no recording/upload implementation found | Effect/Visualizer torn down when disabled; no recorded audio file established |
| App logs and crash reports | Own-app logcat reader starts with process; crash handler writes on crash | Memory tail plus `filesDir/crash_reports`; explicit export; persistent files backup-eligible | Tail 2,000 lines; five crash files, each includes up to 150 recent log lines; clearing buffer does not stop reader |
| Device game-config bytes | Backup before editor writes; restore | `filesDir/config_backups`, privileged device writes; explicit export and default backup eligibility | Ten backups per game, manual deletion; uninstall/cloud restore qualification needed |
| Purchase state/token | Billing connection, purchase and restore | Google Play Billing plus local cached entitlement | Successful queries reconcile; acknowledgement retry and refund behavior need tests |
| External support/payment navigation | User taps browser/payment link | Third-party browser/provider; no in-app wallet/exchange implementation found | Provider policies apply; no entitlement granted by this app for an external tip |

Do not copy this table blindly into Data safety: distinguish local access from
off-device collection, apply the current definitions/exemptions, include relevant
SDKs and optional paths, and verify their actual configuration. Mobile Ads test
configuration does not establish zero SDK data collection.

## Positive findings and non-findings

- No app account-creation flow or hosted user-content feed was found. Account
  deletion and UGC moderation are not automatically applicable just because
  other apps require them.
- `RECORD_AUDIO` is used for the optional audio-effect Visualizer, not a proven
  microphone/video recording feature. No recording product was added or inferred.
- Play paid ad removal uses Play Billing. The external Donate route grants no
  app entitlement; its eligibility depends on the actual 100%-to-creator,
  no-digital-benefit tip conditions, not a blanket “donations are exempt” claim.
  Processor money flows were not tested.
- The fifth-launch GitHub-star support prompt is not a Google Play rating reward.
  No rewarded or filtered Play-review flow was found in the inspected code.
- Prior artifact evidence excludes Full VPN, all-files permission, spoof hooks,
  native bridge and self-updater from Play. That does not settle the remaining
  shared-feature risks above.

## Recommended closure order

1. Resolve ads/analytics consent and startup ordering, then sensitive-access
   explanations and backup/disclosure contradictions.
2. Decide eligibility for gameplay changes, broad discovery, direct power
   exemption and each foreground feature before making release declarations.
3. Correct destructive-action wording, improve Billing acknowledgement handling,
   validate audience/privacy/Data safety/SDK rights, and test the actual flows.

Product removals, changed startup defaults and a new consent system are not
silently authorized by this audit. No feature was removed or declared approved.

## Evidence and verification limits

Source paths above are relative to `app/src/main/java/com/catsmoker/app` unless
a resource or flavor path is given. Current Play release: version 2.0.7 (13),
`app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab`, 13,315,119
bytes, SHA-256:

```text
020cf013f880a7118d92e394da29b9b5b0d06bf342fb2a1f6facaf100442b772
```

Local receipts: `%LOCALAPPDATA%/Temp/opencode/fourth-pass-artifacts/` contains
`scope.json`, `manifest-policy-facts.json`, `inputs-before.json` and
`index-before.txt`. The decoded final manifest is bound to this unchanged AAB
by the third-pass evidence. Its seven foreground services, backup attributes,
SDK permissions and sample-ad configuration were checked in this pass.

This pass is source/manifest/public-document review and documentation only:
no build, app test, lint, dependency vulnerability scan, device run, network
traffic capture, purchase, Console inspection, commit, staging change or upload.
The previous 37 checker passes and 43 selected app passes are historical
evidence, not tests run here. The existing LocaleParityTest failure on 110
required Japanese gaming keys remains unresolved. Prior signing warnings and
Console upload-certificate continuity also remain unresolved.

## Official sources consulted (2026-11-10)

- [User Data](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Permissions and APIs, including alternative broad app visibility](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)
- [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en)
- [Foreground-service requirements](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Payments FAQ and conditional tips exception](https://support.google.com/googleplay/android-developer/answer/10281818?hl=en)
- [Google EU user consent policy](https://www.google.com/about/company/user-consent-policy/)
- [Mobile Ads consent guidance](https://developers.google.com/admob/android/privacy)
- [Mobile Ads SDK 25.5 data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)
- [Firebase Analytics collection controls](https://firebase.google.com/docs/analytics/configure-data-collection?platform=android)
- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Doze and exemption requests](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Billing integration and acknowledgement](https://developer.android.com/google/play/billing/integrate)
- [Billing errors and retry strategies](https://developer.android.com/google/play/billing/errors)
