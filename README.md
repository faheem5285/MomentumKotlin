# Momentum

Momentum is a personal, offline-first Android app for habits, to-dos, money,
loans, files, screen time, reminders, and an encrypted vault. It is built for
one owner and is not intended for Google Play distribution.

## Features

- Habit schedules, streaks, reminders, insights, templates, and archive.
- To-do lists with priorities, reminders, and smart date grouping.
- Expense tracking, budgets, loans, file links, screen-time usage, and device
  indicators.
- AES-256-GCM vault with PBKDF2-HMAC-SHA256, biometric unlock, secure
  clipboard cleanup, and auto-lock protections.
- Offline-first Room storage with versioned migrations, local backups, and
  opt-in Supabase sync design. Vault sync sends ciphertext and nonce only.
- Glass-style light/dark Compose UI with lifecycle-aware state collection.

## Architecture

The app is a single Gradle module using Kotlin, Jetpack Compose, Room,
ViewModel, Hilt, WorkManager, DataStore, and Supabase Auth/PostgREST.

```text
app/
  core/       database, backup, sync, notifications, settings, UI/theme
  features/   habits, todos, money, loans, files, vault, screen_time, settings
  app/        activity shell and navigation
```

Room is the local source of truth. Background work is retryable and must not
make the app unusable offline. Database schema changes require an explicit
migration, exported schema, and migration test.

## Build and run

Requirements: Windows, JDK 17, Android SDK 34, and a connected Android device
or emulator.

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat detekt --no-daemon
.\gradlew.bat installDebug --no-daemon
```

Release builds require user-level `RELEASE_*` Gradle properties. Never commit
`local.properties`, signing files, passwords, Supabase keys, or tokens.

## Supabase setup

Put `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` in the user-level Gradle
properties file. Apply [docs/supabase-schema.sql](docs/supabase-schema.sql)
only after reading [docs/sync-design.md](docs/sync-design.md).

## Screenshots and design references

The glass UI reference designs and screen-by-screen review are documented in
[docs/phase-4-glass-ui-polish.md](docs/phase-4-glass-ui-polish.md). Screenshots
are intentionally not generated in CI; capture them from a debug build on the
target phone so system bars, font scale, and dynamic color are represented.

## Quality

Unit tests cover schedule/streak math, money conversion, reminder timing,
crypto, and sync conflict rules. Instrumentation covers Room migrations,
backup round trips, reminder behavior, and Compose smoke flows. Run the same
checks locally or inspect the GitHub Actions workflow for the CI commands.

## Project status

See [docs/roadmap.md](docs/roadmap.md), [NOTES.md](NOTES.md), and
[CHANGELOG.md](CHANGELOG.md).
