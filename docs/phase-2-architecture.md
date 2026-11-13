# Phase 2 — Architecture

**Status: in progress.** Do not begin the next phase until all deferred
architecture items below are complete and the verification checklist passes.

## What changed

- Added `gradle/libs.versions.toml` and moved application/plugin dependency
  coordinates into the version catalog.
- Added Hilt application setup, a database provider, and constructor-injected
  repositories/ViewModels for to-dos, expenses, and files.
- Added Preferences DataStore repositories with one-time migration from the
  existing `momentum_app_settings` and `momentum` SharedPreferences files.
- Routed Home module visibility through DataStore and kept backup export/import
  reading and restoring those same DataStore preferences.
- Audited the remaining SharedPreferences uses: retained security/account data,
  first-frame appearance values, and flags consumed synchronously by Android
  services/receivers; these are not moved to asynchronous DataStore reads.
- Added Navigation Compose with module routes and `momentum://module/{module}`
  deep links. Item IDs from habit, to-do, and loan notifications/deep links are
  passed to the relevant feature and open the matching record.
- Migrated the refactored screens to `collectAsStateWithLifecycle`.
- Moved the Habits screen's persistence and reminder operations behind an
  injected `HabitRepository`; its ViewModel now uses Hilt and all Habits tabs
  collect state with lifecycle awareness.
- Moved Loans persistence and attachment handling behind an injected
  `LoanRepository`; loan and account-balance state is collected with lifecycle
  awareness.
- Moved Home's aggregate Room queries and vault-count observation behind an
  injected `HomeRepository`; the screen now obtains its ViewModel through Hilt.
- Moved Vault DAO access behind an injected `VaultRepository` and migrated the
  Vault screen to a Hilt-provided ViewModel.
- Added Detekt configuration and a baseline so legacy code does not block the
  build.

## Behavior

The app remains a single Gradle module. Existing module screens and database
behavior are preserved. The migrated repositories retain the existing Room
queries, scheduling calls, and money conversion behavior.

## Verification

- `gradlew.bat :app:compileDebugKotlin`
- `gradlew.bat :app:testDebugUnitTest assembleDebug`
- `gradlew.bat :app:compileDebugAndroidTestKotlin`
- `gradlew.bat :app:connectedDebugAndroidTest`
- `gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.faheem.habits.core.settings.MomentumSettingsMigrationTest"`
- `gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.faheem.habits.core.data.AppDatabaseMigrationTest"`

The 13-test connected run passed on Android 16, and the added DataStore
migration test passed individually. The final full-suite rerun after these
changes was canceled during test APK installation (`INSTALL_FAILED_USER_RESTRICTED`).
The startup navigation argument crash was fixed and verified by launching the
app and an item deep link on-device.

## Deferred

- Complete per-screen immutable `UiState`/event contracts and split remaining
  files over 400 lines.
- Revisit dependency versions against the latest stable Android toolchain
  before raising compile/target SDK; this phase keeps the known-good toolchain
  to avoid an unverified behavior change.
