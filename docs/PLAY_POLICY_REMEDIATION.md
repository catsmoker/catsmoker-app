# Play policy remediation

Started: 2026-11-10; verification continued 2026-11-11.
Follows `FOURTH_PASS_PLAY_POLICY_AUDIT.md`. The owner authorized
the fixes and explicitly chose **Keep pending review** for gameplay edits,
crosshair and inter-app controls. Those features remain available in Play; their
policy eligibility is not established by these changes.

## Implemented in source

| Finding | Change | Limit |
| --- | --- | --- |
| Premature Play ads | Activity-scoped UMP update/form flow after onboarding; SDK initialization and banners gated on current success/eligibility, initialization and entitlement; required privacy-options row | AdMob account messages, regional consent UI and SDK traffic still need authorized verification |
| Default-on Analytics | Remove automatic Firebase provider; metadata defaults denied/disabled; manual initialization only after a current explicit opt-in in Settings | Old default-on values do not opt users in; withdrawing collection does not erase past provider data |
| Unexpected sensitive access | Separate optional discovery, foreground monitoring and notification-dismissal choices; all default off, refusal keeps unaffected tools available, Settings review/withdrawal | Android grants are separate; withdrawing access does not undo already cancelled notifications |
| Discovery scope | Add LAUNCHER to the general MAIN query; guard inventory readers and broad privileged enumeration; clear retained game-list state on withdrawal | Launcher/game interoperability and core-purpose eligibility still require review; no categorical exemption |
| Root prompt timing | Remove automatic onboarding request and avoid opening an unrequested fresh root shell during status checks | Previously granted/requested root may still be used; privilege itself does not authorize every inter-app effect |
| Scheduled specialUse work | Dexopt and cleaner use ordinary WorkManager work with normal progress/Stop notifications; remove WorkManager foreground-service component | Runs can be deferred or cancelled by system limits; six manual/visible service purposes still need Console eligibility evidence |
| Battery exemption | Remove direct-request permission/intent; open general battery settings instead | User/OEM behavior is not controlled by the app |
| Backup/privacy mismatch | Disable backup and exclude all legacy/cloud/device-transfer app domains; update retention/sharing copy | Old cloud backups are not erased; OEM transfers must be tested and cannot be universally guaranteed |
| Cleaner promises | All supported locales now explain selected-file deletion, installers, non-rebuildability, no undo and scheduling consequences | This does not certify every vendor filesystem or remove existing categories |
| Billing acknowledgement | Queue network-constrained, exponentially backed-off unique reconciliation work before immediate acknowledgement; query Play afresh and handle response codes | No real purchase performed; offline/force-stop delays can still miss the three-day deadline |

## Ownership and state

- Shared: `system/config/PrivacyPolicy`, `AnalyticsConsent`, `GamingAccessConsent`
  and `GamingPrivacyGate`; choice version 1. `app_prefs/analytics_enabled` retains
  its key, but only matching `analytics_choice_version` enables collection.
  Other choices live in their own `gaming_access_consent` preferences file.
- Before initial feature composition, choices are shown after terms acceptance
  (also for existing users without a current choice). Save records individual
  choices; Decline/Back/outside dismissal records refusal, not consent. Settings
  can reopen the same controls. No optional choice is required to finish setup.
- Inventory entry points check consent; foreground services refuse/stop without
  usage consent; the notification listener checks its choice before cancellation.
  Game-list state listens for withdrawal. Already issued system operations are
  not magically undone by a privacy preference; existing restore/Stop paths remain.
- Analytics advertising consent types stay denied. An Analytics opt-in is not
  consent for AdMob. Enabling opens the complete localized explanation and needs
  affirmative confirmation; the two-line Settings summary is not the consent
  surface. Disabling is immediate. Firebase is bundled in both distributions,
  not removed.
- Play-only: `AdConsentManager`, `AdConsentPolicy`, pinned UMP 4.0.0, privacy-options
  UI and `BillingAcknowledgementWorker`. Full's selected privacy adapter is empty;
  Start.io integration, free ad setting and Full features remain separate.
- A failed CMP update/form or stale Activity callback does not authorize requests.
  Banner composition is removed while privacy choices change. This deliberately
  fails closed on an unsuccessful current update rather than relying on an old
  record. Consent-server/network problems may therefore mean no ads that launch.
- Acknowledgement work names contain only a SHA-256 token digest, not a token in
  work input/logs. Each ordinary worker opens its own Billing connection, queries
  current INAPP purchases and acknowledges only purchased, unacknowledged ad-removal
  entries. It does not grant entitlement itself. Pending/refunded/missing entries
  are not acknowledged from stale saved tokens. Transient/stale-ownership errors
  retry; configuration errors fail. Client acknowledgement is supported; no
  purchase-verification backend was invented or claimed.
- Broad suspension reconciliation returns unknown without inventory consent;
  recorded targeted undo remains available. Full's app-assignment picker also
  respects discovery refusal and clears cached rows on withdrawal.
- Existing notification IDs remain unchanged: dexopt progress/skipped 103/105,
  cleaner progress/result 108/109. Their Stop routes remain available when system
  notification permission allows. Ordinary work does not gain unlimited execution.

The new choices, legal text, permission explanations, cleaner warnings and
Analytics summary are localized in English, British English, Arabic, Spanish,
Japanese, Brazilian Portuguese and Simplified Chinese. French remains the
deliberate Full-only partial dialog locale, not a newly supported locale.

## Remaining release gates: not silently “fixed”

1. Owner/publisher/Play review of retained gameplay-view changes, overlays,
   force-stop/suspension and broad discovery. User warning/root grant is not
   eligibility. They remain **pending review** by explicit owner choice.
2. Publish/configure AdMob privacy messages and appropriate production ad IDs;
   verify audience/age handling, Data safety and account declarations. Source
   integration cannot configure or validate those accounts without authorization.
3. Verify regional first-launch/return/refusal/withdrawal/error consent flows and
   traffic, real Billing pending/purchase/acknowledgement/restore/refund cases,
   service/background behavior and backup/transfer on actual devices.
4. Six manual/visible specialUse features still need accurate Console declarations,
   duration/type justification and required demo videos. Removing scheduled
   elevation does not approve the remaining services.
5. Validate the rendered public policy and listing identity/contact/retention
   details, Glyph redistribution rights, current SDK advisories and any Console
   notices. No separate website edits, account actions or policy declarations
   were performed here.
6. Build and inspect a new release only when authorized. Previous audit AAB/APK
   hashes and signatures describe earlier sources, not these changes. Existing
   signing/Console continuity questions and the unrelated 110-key Japanese locale
   failure remain open.

## Focused verification

The owner approved these seams: privacy/consent decisions, acknowledgement retry
decisions, and manifest/backup/disclosure safeguards. They do not establish
regional account configuration, real payments, device behavior or Play approval.
Only those affected classes are selected; no full suite, clean or release build.

The initial batch compiled Kotlin/Java/Hilt and resources for both debug flavors
and passed its selected tests (`BUILD SUCCESSFUL`, 8m 6s). Final review then added
the complete Analytics confirmation, guarded recovery enumeration and Full's
assignment picker, and made Billing connection callbacks single-answer. The same
focused batch was repeated for those changes: **BUILD SUCCESSFUL**, 3m 35s,
configuration cache reused, 20 tasks executed and 50 up to date. Both debug
flavors compiled successfully. All **23 selected tests passed**, with no failures,
errors or skips:

| Flavor | Test class | Passed |
| --- | --- | ---: |
| Full | PrivacyPolicyTest | 2 |
| Full | AnalyticsConsentTest | 4 |
| Full | PolicySafeguardsTest | 5 |
| Play | AdConsentPolicyTest | 1 |
| Play | BillingAcknowledgementPolicyTest | 1 |
| Play | RemoveAdsPolicyTest | 10 |

The consent/retry tests cover decisions, not the SDK's real network callbacks or
payments. Some safeguards inspect XML/source contracts; they are not device UI,
backup-transport or foreground-service lifecycle tests. Existing deprecated-API
warnings and the Japanese format-resource warning were not treated as new policy
fixes. The complete Analytics confirmation and privacy controls have not been
driven on a device.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew :app:testFullDebugUnitTest --tests '*PrivacyPolicyTest' --tests '*AnalyticsConsentTest' --tests '*PolicySafeguardsTest' :app:testPlaystoreDebugUnitTest --tests '*AdConsentPolicyTest' --tests '*BillingAcknowledgementPolicyTest' --tests '*RemoveAdsPolicyTest' --continue --console=plain
```

Fresh merged **debug** manifests were also checked: both remove Firebase's
automatic provider and WorkManager's foreground service, reference both backup
exclusion files, default collection/consent metadata to false, and omit the direct
battery-exemption and redundant invalid advertising-ID permissions. Play still
omits the Full VPN/all-files components; Full retains them. This is merged-manifest
evidence, not a newly inspected release APK/AAB. Receipt:
`%LOCALAPPDATA%/Temp/opencode/policy-remediation/debug-manifests.json`.

Logs: `%LOCALAPPDATA%/Temp/opencode/policy-remediation-tests.log` and
`policy-remediation-final-tests.log`. The previous broad locale failure was not
suppressed or rerun as part of these selected checks. No clean, full suite,
assemble/bundle, purchase, installation, upload or Console action was performed.

Local receipts under `%LOCALAPPDATA%/Temp/opencode/policy-remediation/` also include
`focused-tests.json` (JUnit counts/report hashes), `verified-inputs.json` and
`scope-safety.json`. The user's staged index remains unchanged from the fourth-pass
snapshot and no AI files are staged. The old release AAB is unchanged and **does
not contain this remediation**; a fresh authorized release build and inspection
are still required before distribution.
