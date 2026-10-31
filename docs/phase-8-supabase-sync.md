# Phase 8 - Supabase sync

## Completed

- Added the design and paste-ready Supabase schema in
  [sync-design.md](./sync-design.md) and [supabase-schema.sql](./supabase-schema.sql).
- Added in-memory access-token handling while keeping the refresh token
  Keystore-protected.
- Added Room v13 sync state and explicit vault sync metadata migration.
- Added deterministic last-writer-wins comparison, device identity, encrypted
  first-sync local backup, WorkManager network scheduling, retry/backoff, pause,
  and cloud-delete primitives.
- Added a ciphertext-only vault push/pull path. Non-vault entity adapters and
  the Settings UI are not yet enabled.

## Verification

- `gradlew.bat :app:compileDebugKotlin --no-daemon` passed.
- Sync conflict and ciphertext-only unit tests are included.
- Supabase policy execution and end-to-end sync require a configured project,
  authenticated account, applied SQL, and a device/emulator.

## Known limits

- This slice does not yet push/pull habits, completions, reminders, money,
  loans, files, to-dos, or budgets.
- The Settings sync screen, manual sync action, conflict merge-result UI, and
  automatic local-change debounce remain deferred.
- Cloud deletion currently targets the vault table only and must not be
  presented as a complete account deletion until all adapters exist.
