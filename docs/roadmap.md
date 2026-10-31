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
- Phase 7 — feature data foundation: Room v12 fields and migrations for
  flexible habits, completion notes/quantities/timers, to-do scheduling,
  accounts and recurring money, loan due dates/payments, file metadata, and
  category budgets; shared domain logic covers quick-add, insights, budgets,
  and loan balances.
- Phase 8 — sync design and safety substrate: Supabase RLS schema, offline-first
  design, Keystore-backed session access tokens, Room sync state, deterministic
  conflict ordering, first-sync backup, WorkManager retry scheduling, and
  ciphertext-only vault sync path.
- Phase 9 — quality and repository foundations: CI checks, Dependabot,
  current project documentation, Compose UI smoke-test scaffolding, migration
  validation through Room v13, explicit Android SDK package setup in CI, and
  executable Gradle wrapper permissions.

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
- Phase 7 UI wiring remains deferred for the next slice: habit freeze controls,
  Glance widget, to-do drag/repeating/subtask UI, money charts/export/accounts,
  loan reminder actions, file thumbnail/tag management, screen-time limit
  notifications, global search, app-wide biometric lock, and About/settings
  localization.
- Phase 8 non-vault adapters, sync Settings UI, local-change debounce, merge
  result UI, configured-project integration tests, and complete cloud deletion
  remain deferred; see [phase-8-supabase-sync.md](./phase-8-supabase-sync.md).
- Phase 9 physical-device UI/accessibility verification, full isolated
  end-to-end Compose assertions, and repository metadata cleanup remain
  deferred; see [phase-9-quality-ci-repo.md](./phase-9-quality-ci-repo.md).
