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
- Phase 5 — reminder reliability: centralized alarm policy, exact/inexact
  fallback, boot/time-change rescheduling, notification actions, six-hour
  safety verification, and reminder health diagnostics.
- Phase 6 — vault security foundation: VMK key wrapping, legacy encrypted
  migration backup, biometric Keystore unlock, brute-force delay, secure
  clipboard cleanup, and secure-screen handling.

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
- Phase 5 per-reminder sound/vibration, evening nudges, vendor-specific
  settings deep links, and physical-device delivery verification remain
  deferred; see [phase-5-reminders.md](./phase-5-reminders.md).
- Phase 6 full encrypted import, configurable lock timeout UI, a complete
  change-password screen, and rooted-device testing remain deferred; see
  [vault-security.md](./vault-security.md).
