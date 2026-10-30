# Phase 1 — Audit and data safety

## What changed

- Removed destructive Room migration fallback and retained explicit migrations
  from schema 3 through schema 11, with committed Room schema JSON.
- Added sync metadata (`uuid`, `updatedAt`, `deleted`, `dirty`) and indexed
  foreign-key/query columns, including completion `habitId`.
- Converted expense and loan persistence to `Long` minor units with safe
  HALF_UP migration and unit coverage.
- Added versioned JSON export/import for non-vault data, preview and
  merge/replace actions, UUID idempotency, and transactional database restore.
- Added persisted SAF folder selection, weekly WorkManager backups retaining
  five copies, database version display, and `PRAGMA integrity_check`.
- Kept Android backup disabled because vault key material and encrypted vault
  records do not yet have a tested recovery workflow.

## Verification

- `gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin`
- Instrumentation migration and backup tests require an available Android
  device/emulator.

## Known limits

- Vault export is separate from the JSON export and contains encrypted record
  payloads only; vault import/recovery is deferred.
- SAF provider behavior, backup retention, and rollback after storage failures
  require physical-device testing.
- Large-screen extraction and localization remain follow-up maintenance work.
