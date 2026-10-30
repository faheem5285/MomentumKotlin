# Momentum roadmap

## Completed

- Phase 1 — audit and data safety: Room migrations/schema export, sync-ready
  columns, minor-unit money storage, versioned non-vault export/import,
  weekly local backups, integrity check, and migration/backup tests.

## Deferred

- Physical-device verification of migrations, SAF permissions, weekly backup
  retention, import failure rollback, and Android background behavior.
- Vault export/import recovery flow and testing with a user-entered password.
- Refactoring screens over 400 lines after data-safety work is stable.
- Supabase synchronization and encrypted end-to-end vault sync.
