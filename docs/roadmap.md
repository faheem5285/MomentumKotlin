# Momentum roadmap

## Completed

- Phase 1 — audit and data safety: Room migrations/schema export, sync-ready
  columns, minor-unit money storage, versioned non-vault export/import,
  weekly local backups, integrity check, and migration/backup tests.
- Phase 2 — architecture foundation: version catalog, Hilt, DataStore
  migrations, repository/ViewModel boundaries for three features, Navigation
  Compose routes, lifecycle-aware collection, and Detekt baseline.
- Phase 4 — glass UI foundation: centralized glass tokens, shared glass
  components, light/dark dynamic color support, API-aware blur, and shared
  hero/sheet adoption.

## Deferred

- Physical-device verification of migrations, SAF permissions, weekly backup
  retention, import failure rollback, and Android background behavior.
- Vault export/import recovery flow and testing with a user-entered password.
- Refactoring screens over 400 lines after data-safety work is stable.
- Supabase synchronization and encrypted end-to-end vault sync.
- Remaining Phase 2 architecture migration listed in
  [phase-2-architecture.md](./phase-2-architecture.md).
- Phase 4 settings controls, feature-card migration, predictive back, haptic
  feedback, and device/reference-image comparison remain deferred; see
  [phase-4-glass-ui-polish.md](./phase-4-glass-ui-polish.md).
