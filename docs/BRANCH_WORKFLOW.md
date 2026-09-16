# Git Branch Workflow: `main` → `playstore`, with narrow reverse exception

We have two permanent branches:

* `main` = the complete/full application.
* `playstore` = the Google Play Store-compatible version.

## Critical Rule

The default direction is **main → playstore** (selective synchronization):

```text
main
  │
  │  selective synchronization
  ▼
playstore
```

`main` is always the source of truth for the complete application.

### Reverse direction: narrow exception only

Selective `playstore → main` **cherry-picks** are allowed as a narrow
exception for individually reviewed safe fixes (see "Reverse Direction"
below). Merges and rebases from `playstore` into `main` stay banned:

```bash
git merge playstore        # NEVER on main
git rebase playstore       # NEVER on main
```

The following Play-compliance changes must **never** flow back into `main`:

* the spoof/LSPosed/Magisk removal (`features/spoofdevice/`,
  `SpoofRepository`, `LSPosedConfig`, `DeviceProfile`, `GetPropInterceptor`,
  `MagiskModuleBuilder`, `xposed_init`, scope array, `strings_spoof.xml`)
* the Start.io → AdMob swap (`AdMobBanner`, `play-services-ads`, `ADMOB_*`)
* the GitHub-APK self-updater removal
* `assets/adi-registration.properties` (intentional Play Store file)
* permission / `resConfigs` locale trims that break `main`
* locale renames that conflict with `main`'s spoof strings
* version bumps without an explicit decision

---

## Goal

Avoid manually implementing the same safe changes twice.

When a change is compatible with Google Play, it should be possible to propagate that change from `main` to `playstore`.

However, **DO NOT blindly synchronize every commit** from `main`.

Some functionality intentionally exists only on `main` because it is not appropriate for the Play Store version.

Examples of potentially `main`-only functionality include:

* LSPosed/Xposed hooks
* device/identifier spoofing
* Magisk modules
* fake device properties
* other functionality that may violate Google Play policies

These must never accidentally appear in `playstore`.

---

# Commit Classification

Use commit prefixes to explicitly classify changes.

### Play Store safe

```text
[PLAY-SAFE] Improve gaming dashboard
[PLAY-SAFE] Fix game configuration backup
[PLAY-SAFE] Add new supported game
[PLAY-SAFE] Improve FPS configuration UI
[PLAY-SAFE] Fix overlay behavior
```

These commits are candidates for synchronization to `playstore`.

### Main only

```text
[MAIN-ONLY] Add device spoofing
[MAIN-ONLY] Add LSPosed hooks
[MAIN-ONLY] Add Magisk module
[MAIN-ONLY] Add identifier spoofing
```

These commits must remain on `main`.

They must NEVER be synchronized to `playstore`.

### Unclassified commits

If a commit does not contain either:

```text
[PLAY-SAFE]
```

or

```text
[MAIN-ONLY]
```

treat it as **NOT safe to automatically synchronize**.

Do not guess.

---

# Synchronization Strategy

Use `git cherry-pick` for Play Store synchronization.

Example:

```text
main:

A → B → C → D → E → F
                    ↑
                    MAIN-ONLY

playstore:

A → B → C → D → E
```

If commit `C` is `[PLAY-SAFE]`, it can be cherry-picked into `playstore`.

Do NOT merge the entire `main` branch into `playstore`.

Do NOT use:

```bash
git merge main
```

for normal synchronization.

Do NOT automatically rebase `playstore` onto `main`.

The reason is that `main` may contain functionality that must never reach Google Play.

---

# Required Workflow

When working on a new feature:

1. Work on `main`.
2. Implement the feature normally.
3. Determine whether the resulting commit is Play Store compatible.
4. If it is safe for Play Store, commit it as:

   ```text
   [PLAY-SAFE] Description
   ```

5. If it is intentionally only for the full version, commit it as:

   ```text
   [MAIN-ONLY] Description
   ```

6. Only `[PLAY-SAFE]` commits may be synchronized to `playstore`.

---

# Synchronizing a Safe Commit

From `main`:

```bash
git log --oneline
```

Find the `[PLAY-SAFE]` commit.

Then:

```bash
git switch playstore
git pull --ff-only origin playstore
git cherry-pick <PLAY-SAFE-COMMIT>
git push origin playstore
```

After synchronization, verify that the Play Store branch builds successfully.

---

# Reverse Direction (`playstore` → `main`): narrow exception

Forward flow stays the default. A reverse cherry-pick is allowed only when
**all** of these hold:

1. The commit is an individually reviewed safe fix (bugfix, hygiene, or UX
   improvement that makes sense with `main`'s full feature set).
2. It is **not** on the never-flow-back list in "Critical Rule".
3. It is adapted for `main` at pick time: Start.io (not AdMob), spoof
   feature still present, no `adi-registration.properties`, no Play-only
   locale/permission trims.

Procedure (from `main`):

```bash
git switch main
git pull --ff-only origin main
git cherry-pick <playstore-commit>
```

Verify with `compileDebugKotlin` plus the affected unit tests before
committing to anything further. Unclassified `playstore` commits are not
safe to pick — do not guess.

---

# Conflict Handling

If cherry-picking a `[PLAY-SAFE]` commit causes a conflict:

**STOP.**

Do not automatically resolve the conflict if doing so could introduce `main`-only functionality.

Show the conflict and explain:

* which files conflict
* why they conflict
* what functionality is affected
* whether the change can safely exist in `playstore`

The user must decide how to resolve policy-sensitive conflicts.

Never silently remove Play Store restrictions just to make the cherry-pick succeed.

The same STOP rule applies in reverse: never silently drop `main`-only
functionality (spoofing, LSPosed, Magisk, Start.io) just to make a
`playstore → main` pick succeed.

---

# Important Architecture Rule

Prefer making common functionality genuinely shared between the two branches.

For example:

```text
main
 ├── common UI
 ├── gaming tools
 ├── game configuration
 ├── performance tools
 ├── overlays
 └── main-only features
      ├── spoofing
      ├── LSPosed
      └── Magisk
```

while `playstore` contains:

```text
playstore
 ├── common UI
 ├── gaming tools
 ├── game configuration
 ├── performance tools
 └── overlays
```

Do not unnecessarily duplicate safe functionality.

If a safe feature can be implemented once on `main` and cleanly cherry-picked into `playstore`, prefer that approach.

---

# Never Modify `main` Because of `playstore`

The Play Store branch may require Play-specific changes.

For example:

```text
main:
full functionality

playstore:
Play-compatible implementation
```

If a Play Store restriction requires changing or removing something from `playstore`, make that change **only on `playstore`**.

Do not modify `main` just to make `playstore` easier to maintain.

`main` must remain the complete version.

Exception: the narrow reverse cherry-pick described above may touch `main`,
but only to land a reviewed safe fix adapted for `main` — never to carry a
Play restriction backwards.

---

# Before Making Git Changes

Before executing any destructive or branch-changing Git command:

1. Check the current branch.
2. Check the working tree.
3. Check whether there are uncommitted changes.
4. Show what operation will be performed.
5. Never reset, drop, force-push, or delete commits without explicit approval.

Use:

```bash
git status
git branch --show-current
git log --oneline --decorate -10
```

before important branch operations.

---

# Remote Branches

The expected remote branches are:

```text
origin/main
origin/playstore
```

Both branches should remain available on the remote.

Do not delete either branch.

Do not force-push either branch unless explicitly instructed.

Prefer:

```bash
git push origin main
```

and:

```bash
git push origin playstore
```

---

# Optional Automation

If implementing automation, it must follow these rules:

1. Trigger from changes to `main`.
2. Inspect commits for `[PLAY-SAFE]`.
3. Only synchronize explicitly marked `[PLAY-SAFE]` commits.
4. Never synchronize `[MAIN-ONLY]` commits.
5. Never synchronize unclassified commits.
6. Never merge the entire `main` branch into `playstore`.
7. Never modify `main` (automation never performs reverse picks; those are manual only).
8. If a cherry-pick conflict occurs, stop the automation and require manual resolution.
9. Never automatically resolve conflicts involving Play Store-specific code.
10. Never force-push `playstore`.

A failed synchronization should leave both branches intact.

---

# Final Invariant

At all times, maintain this rule:

```text
                 ┌── [PLAY-SAFE] ──► playstore
                 │
main ────────────┤
                 │
                 └── [MAIN-ONLY] ──► stays on main
```

plus the narrow exception:

```text
playstore ── reviewed safe fix only, cherry-pick ──► main
            (merges/rebases banned; compliance removals never flow back)
```

The Play Store branch is a **derived branch**, not a second source of truth.

The complete application lives on `main`.

The Play Store-compatible subset lives on `playstore`.

Reverse flow is cherry-pick-only, per-commit, and manual — never a merge,
never a rebase, never automatic.
