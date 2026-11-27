# Milestone 4 — Onboarding, Permissions, and Home Dashboard Contract

**Status:** Implemented on `impl/m1-data-safety`; JVM tests executed, device/permission checks **Unverified**.
**Date:** 2026-11-26.

## 1. Install-scoped tri-state progress

`OnboardingProgressStore` uses its own DataStore file, `momentum_onboarding`, and is intentionally
not part of `momentum_app_settings`. Account switches, local resets, and project wipes therefore do
not erase “getting started” progress. `clearProgress()` is an explicit opt-out only.

`OnboardingPhase` is `UNKNOWN`, `PENDING`, or `DONE`. `UNKNOWN` is the initial `StateFlow` value, so
`MainActivity` does not flash the permission gate while DataStore is loading. System back on the
first-run gate now calls `closePermissionSetup()`, which persists `permission_setup_seen`.

Task ids: `welcome`, `first_habit`, `first_task`, `first_expense`, `cloud_backup`, `notifications`,
`exact_alarms`, `usage_access`, `explore`. Each task stores `done`, `dismissed`, `snoozedUntil`, and
timestamps. `visibleOnboardingTasks` is pure and shows at most two relevant cards.

## 2. Cards and wording

Cards are rendered in the Home card slot after the header and in Settings → Getting started. Tasks
are prioritized; completed/dismissed/snoozed cards hide. Cloud-backup copy uses the exact required
signed-out strings from `OnboardingCopy`. Permission cards explain the feature impact before opening
system settings and never block local use. `Finish setup` sets `DONE`; Settings revisit clears only
`DONE`, preserving task history.

## 3. Verified permission statuses

`PermissionStatusRepository` is the single source of truth for OS checks. It returns
`GRANTED`, `DENIED`, or `UNSUPPORTED` for notifications, exact alarms, usage access, overlay,
accessibility, battery optimization, autostart, and background restrictions. `PermissionsSetupScreen`
and the onboarding signals use the same repository. Home recomputes statuses on `ON_RESUME`; no
permission is claimed enabled without an actual OS check.

## 4. Home states and quick actions

`HomeViewModel` emits `ListLoadingState` before repository data arrives and `ListErrorState` with a
generic, sanitized retry message if the flow fails. Genuine empty data keeps the existing zero-count
subtitles. Conditional quick actions (`Add habit`, `Add task`, `Add expense`) appear only when the
module is visible and has no data; they open the module and do not create records as a side effect.
The tile grid is adaptive: 1 column below 360dp, 2 below 600dp, 3 otherwise.

## 5. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**
- `.\\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 85 tests, 0 failures/errors/skips.
- `.\\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS,
  85 tests, 0 failures/errors/skips.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).

New JVM tests: `OnboardingProgressTest` (priority, tri-state, dismiss/snooze, revisit, exact copy,
permission summary). New instrumentation tests compiled but **not executed**:
`OnboardingProgressStoreTest`, `OnboardingCardsUiTest`.

**Unverified:** device grant/deny/revoke of each permission, return-from-system-settings refresh,
system back on first run, TalkBack, 360dp/2× font, narrow-screen grid, and visual review of the
loading/error/quick-action states. No device or emulator run occurred.
