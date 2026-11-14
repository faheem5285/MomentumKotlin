# Phase 8 - Supabase sync

## Completed

- Added the design and paste-ready Supabase schema in
  [sync-design.md](./sync-design.md) and [supabase-schema.sql](./supabase-schema.sql).
- Added in-memory access-token handling while keeping the refresh token
  Keystore-protected.
- Added sync state and explicit Vault sync metadata migrations. Phase 8
  introduced the sync schema at Room v13; the current database has since
  advanced to Room v20.
- Added deterministic last-writer-wins comparison, device identity, encrypted
  first-sync local backup, WorkManager network scheduling, retry/backoff, pause,
  and cloud-delete support.
- Added push/pull adapters for habits, completions, skips, reminders, expenses,
  category budgets, contacts, loan people/records/payments/attachments, files,
  to-dos, life rules/attachments, custom notifications, and Vault entries.
- Added debounced local-change scheduling, a startup dirty-row check, and the
  Supabase status, retry, diagnostics, and admin-only project cleanup screen.
- Vault entry rows sync as ciphertext. Account recovery also stores the raw
  Vault Master Key in Supabase; see the limitation in
  [vault-security.md](./vault-security.md).

## Verification

- `gradlew.bat :app:compileDebugKotlin --no-daemon` passed.
- Sync conflict and ciphertext-only unit tests are included. Android
  instrumentation sources cover local backup round trips.
- End-to-end sync, project cleanup, and Vault recovery still require a
  configured Supabase project, authenticated account, applied SQL, and
  physical-device or emulator verification.

## Known limits

- The sync screen does not yet show a per-run merge summary.
- Configured-project integration testing and physical-device verification of
  offline recovery, retries, conflicts, and encrypted Vault sync remain
  outstanding.
- Project cleanup is admin-only and distinct from signing out or deleting one
  account; verify the deployed Edge Function and RLS policies before use.
