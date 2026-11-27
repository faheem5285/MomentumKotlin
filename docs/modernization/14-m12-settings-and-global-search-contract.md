# Milestone 12 — Settings and Global Search Contract

**Status:** Implemented on `impl/m1-data-safety`; JVM suites executed, instrumentation/device/Compose runtime behavior **Unverified**.
**Date:** 2026-11-26.

## 1. Settings have one stable structure

`SettingsLayout` declares the eight required groups in order: Appearance, Notifications, Privacy & Security,
Data & Backup, Cloud & Sync, Screen Time & Device, Account, and About. `SettingsDestination` gives every
setting one canonical id and section; `SettingsLayout.duplicateDestinationIds()` is covered by a JVM test, so
a setting cannot be rendered twice by accident. `AppSettingsScreen` renders only these groups and no longer
contains the old duplicate security/backup/profile blocks.

Appearance controls live only in the Appearance group. Notifications and permissions have one home each.
Backup/restore and the search entry point live in Data & Backup. Cloud status and Profile intentionally
share the same accurate account subtext but are not duplicate sign-in controls: both route to existing
screens. Getting started and the privacy note live in About. Search is a separate `MomentumRoutes.SEARCH`
route rather than a new `AppModule`, so module visibility semantics are unchanged.

## 2. Appearance is DataStore-backed and backup-included

`AppearanceBackupPolicy` owns the four backup keys (`appearance_theme_mode`, `appearance_reduce_motion`,
`appearance_dynamic_accent`, `appearance_glass_intensity`) and the legacy SharedPreferences names. A
legacy/preferences map is normalized to one complete `AppearanceValues` object; invalid enums and missing
values fall back to stable defaults, and migration always writes all four keys.

`MomentumSettingsRepository` now exposes `appearanceValues` and `setAppearance`. Its preferences export
uses a set of prefixes so both `module_enabled_` and `appearance_` keys are included; a replace restore
removes and re-applies the same keys. `AppearancePreferences` keeps SharedPreferences only as a first-frame
cache. `MomentumApplication` calls `synchronize` at startup; the DataStore copy wins when present, otherwise
legacy values are migrated once. Restoring a backup also calls `settingsViewModel.syncAppearance()` so the
cache, in-memory flows and DataStore agree.

## 3. Hidden modules are recoverable without a reset

`VisibilityRecovery.hiddenModules` treats absent keys as visible, matching the repository. The Settings
screen shows the exact count and names in the Privacy & Security group, and its dialog calls the existing
`showAllModules()` helper. Visible and hidden module rows use the same source of truth: a hidden Vault or
screen-time/battery/network module does not open its screen from Settings; it opens Hidden modules instead.
Profile and Settings remain reachable through `isModuleEntryAllowed`, so recovery never requires an
unsupported deep-link bypass. JVM tests cover absent keys, false keys, recovery values and unrelated-key
preservation.

## 4. Status wording is accurate and local-first

`AccountStatus.subtext` has one priority order: attention, signed-in, offline, local-only. Attention uses
"Account needs attention — local data is still available on this device"; local-only uses the exact
`OnboardingCopy.LOCAL_DATA` phrase; offline says local records are available and cloud sync resumes when
connected. A signed-in account reads "Cloud backup is connected" only when there is no attention/offline
notice. The same helper feeds Cloud & Sync and Profile, and `MainActivity` passes the real session flags
into navigation and Settings. No signed-out user is blocked from local features by this wording.

## 5. Global search is scoped, bounded and vault-free

`GlobalSearchPolicy` indexes only local Room rows for habits, tasks, expenses, contacts, files and life
rules. Vault is not a category, is never decrypted, and is excluded from the UI copy and result routing.
Contacts here are local app contact records, not device Contacts-provider rows. Queries are trimmed,
whitespace-collapsed and capped at 120 characters; matching requires every token to occur across the
document's indexed text. Results are capped at 50 and hidden-module matches are excluded rather than merely
hidden in the UI, with a banner reporting that matches were omitted. A file with missing persisted read
permission is treated as unavailable and produces a permission-denied banner instead of a dead result.
`GlobalSearchViewModel` debounces queries by 120 ms and combines the local flows with module visibility;
errors use `HttpErrorSanitizer.forDisplay` before reaching the UI.

`GlobalSearchScreen` has distinct blank, loading, empty, error/retry, permission-denied and results states.
Results route to the existing module destination only when `isModuleEntryAllowed` passes, so a stale index
or crafted navigation cannot reopen a hidden module. Search is reachable from Home and from Settings.

## 6. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**
- `.\\gradlew :app:compileDebugKotlin --no-daemon` — PASS after one fix iteration (missing
  `KeyboardOptions` import in `GlobalSearchScreen.kt`); the identical command passed afterward.
- `.\\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 209 tests, 0 failures/errors/skips, 44 suites.
- `.\\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS, 209 tests,
  0 failures/errors/skips, 44 suites. Release signing remains unconfigured and no signed APK was produced.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).

The 22 added JVM tests cover appearance migration/defaults/round trips, the eight-group layout and unique
destination ids, hidden-module recovery, account-subtext priority, query bounds/token matching, hidden and
unavailable scope handling, the 50-result cap, and loading/empty/results/error state mapping.

**Unverified:** no connected device or emulator run; no Compose runtime interaction, visual review or
instrumentation execution; no real SAF re-grant flow, real search latency measurement, TalkBack/focus order,
360dp/2× font/dark/reduced-motion/dynamic-accent visual check, real signed-out release-candidate run, lint,
detekt, assemble, R8 smoke, CI run or signed release. The M12 UI, navigation and backup paths are compiled
and pure-policy tested, but device-visible behavior is claimed only where an executed test provides evidence.
