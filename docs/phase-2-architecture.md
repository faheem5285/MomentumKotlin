# Phase 2 — Architecture

## What changed

- Added `gradle/libs.versions.toml` and moved application/plugin dependency
  coordinates into the version catalog.
- Added Hilt application setup, a database provider, and constructor-injected
  repositories/ViewModels for to-dos, expenses, and files.
- Added Preferences DataStore repositories with one-time migration from the
  existing `momentum_app_settings` and `momentum` SharedPreferences files.
- Added Navigation Compose with module routes and `momentum://module/{module}`
  deep links while retaining the existing destination API.
- Migrated the refactored screens to `collectAsStateWithLifecycle`.
- Added Detekt configuration and a baseline so legacy code does not block the
  build.

## Behavior

The app remains a single Gradle module. Existing module screens and database
behavior are preserved. The migrated repositories retain the existing Room
queries, scheduling calls, and money conversion behavior.

## Verification

- `gradlew.bat :app:compileDebugKotlin`
- `gradlew.bat :app:testDebugUnitTest assembleDebug`

Instrumentation tests and deep-link launches still require a connected device.

## Deferred

- Move the remaining feature ViewModels (habits, loans, home, vault, and
  settings) to constructor-injected repositories.
- Replace remaining feature/service SharedPreferences with DataStore-backed
  repositories, including legacy key migration tests for each store.
- Complete per-screen immutable `UiState`/event contracts and split remaining
  files over 400 lines.
- Add exact item deep links and notification payload routing for habit, to-do,
  and loan IDs.
- Revisit dependency versions against the latest stable Android toolchain
  before raising compile/target SDK; this phase keeps the known-good toolchain
  to avoid an unverified behavior change.
