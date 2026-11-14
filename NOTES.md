# Momentum current state

This note reflects the current `agent/zylo-premium-modernization` branch.
The app is a native Android single-module project using Kotlin, Compose, Room,
Hilt, ViewModel, WorkManager, DataStore, and Supabase Auth/PostgREST.

## Database and data safety

- Current Room schema version: **20**.
- Explicit migrations cover versions 3 through 20; exported schemas are under
  `app/schemas/`.
- Destructive migration fallback is not used.
- Room is the local source of truth and remains usable offline after account
  authentication.
- Versioned JSON backup/export and weekly local backup foundations exist.
  General JSON backups are not encrypted and exclude Vault entries and file
  contents. Vault uses a separate password-protected export.
- Physical-device verification of backup retention, restore rollback, and
  external file permission recovery remains outstanding.

## Modules

Habits, to-dos, money/expenses, loans, files, Vault, Screen Time, Home, Settings,
authentication, network indicator, and battery charging overlay are present.
Reminder scheduling includes exact/inexact alarm fallback, WorkManager
verification, and boot/time-change restoration.

## Vault security and limitations

The Vault uses PBKDF2-HMAC-SHA256 with 310,000 rounds and AES-256-GCM. Records
use a random Vault Master Key (VMK), wrapped by the master-password key and an
Android Keystore biometric key. Screenshot protection, background locking,
brute-force delay, and timed clipboard cleanup are implemented.

Vault entries sync as ciphertext. **Account recovery stores the raw VMK in
Supabase**, so project administrators may be able to access it and decrypt
synced Vault entries. This is not end-to-end encryption against the Supabase
project administrator. Lost master passwords cannot be recovered through the
local password mechanism.

The complete change-password and encrypted-import user interfaces, plus a
configurable auto-lock timeout, remain deferred and require device verification.

## Authentication and sync

A Supabase account is required to open the app. After successful sign-in, Room
is the local source of truth; sync is scheduled automatically when connectivity
is available. The sync engine includes Room sync state, deterministic conflict
ordering, first-sync local backup, WorkManager retry/backoff, debounced local
change scheduling, and push/pull adapters for supported modules including
Vault ciphertext.

A configured-project integration test and physical-device verification of
offline recovery, retry, conflict resolution, Vault sync, and account recovery
remain outstanding. Review `docs/sync-design.md`, `docs/supabase-schema.sql`,
and `docs/vault-security.md` before changing cloud behavior.

## Latest verification snapshot

On the current branch at commit `eae2071cd41b08e5d4af80adcabb797e36b828d9`:

- Local lint: passed.
- Local Detekt task: passed; accepted baseline findings remain and need
  genuine cleanup rather than blanket suppression.
- Local debug unit tests: passed.
- Local debug assembly: passed.
- GitHub Actions CI: lint, Detekt, unit tests, debug assembly, and release
  assembly passed using an ephemeral CI signing key.
- Local release assembly requires user-level `RELEASE_*` signing properties;
  never commit a keystore or signing credentials.
- The user reports that the nine manual phone workflows (Habits, To-dos,
  Expenses, Loans, Files, Vault unlock/read, database integrity, Supabase
  status, and persistence) all work. This does not replace the deferred
  migration, backup/restore, permission, reminder, biometric, and sync edge
  case tests.

Run locally from Windows:

```powershell
.\gradlew.bat lint
.\gradlew.bat detekt
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Release builds additionally require the local signing properties described in
`README.md`. Never commit `local.properties`, signing files, passwords,
Supabase keys, or tokens.
