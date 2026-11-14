# Zylo roadmap

## Completed

- Phase 1 — audit and data safety: Room migrations/schema export, sync-ready
  columns, minor-unit money storage, versioned non-vault export/import,
  weekly local backups, integrity check, and migration/backup tests.
- Phase 2 — architecture foundation (partial): version catalog, Hilt, DataStore
  migrations, repository/ViewModel boundaries for three features, Navigation
  Compose routes, lifecycle-aware collection, and Detekt baseline.
- Phase 4 — glass UI foundation: centralized glass tokens, shared glass
  components, light/dark dynamic color support, persisted Android 12+ dynamic
  accent control, API-aware blur, and shared hero/sheet adoption.
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
  encrypted Vault entry sync with a separate account-recovery key.
- Phase 9 — quality and repository foundations: CI checks, Dependabot,
  current project documentation, Compose UI smoke-test scaffolding, migration
  validation through Room v20, explicit Android SDK package setup in CI, and
  executable Gradle wrapper permissions.

## Deferred

- Physical-device verification of migrations, SAF permissions, weekly backup
  retention, import failure rollback, and Android background behavior.
- Physical-device testing of encrypted Vault export/import recovery with a
  user-entered password.
- Refactoring screens over 400 lines after data-safety work is stable.
- Configured-project Supabase integration testing and physical-device
  verification of automatic sync and encrypted Vault sync.
- Remaining Phase 2 architecture migration listed in
  [phase-2-architecture.md](./phase-2-architecture.md).
- Phase 4 still needs full per-screen device review, predictive-back validation, haptic-feedback review, and reference-image comparison; source-level accessibility refinements are recorded in [phase-4-ui-audit.md](./phase-4-ui-audit.md).
- Broader Phase 5 module-by-module usability work is tracked in [phase-5-module-usability.md](./phase-5-module-usability.md); it remains in progress until each module and its device checks are reviewed.
- Phase 5 per-reminder sound/vibration, evening nudges, vendor-specific
  settings deep links, and physical-device delivery verification remain
  deferred; see [phase-5-reminders.md](./phase-5-reminders.md).
- Phase 6 encrypted export/import paths are implemented but still need
  physical-device recovery verification. Configurable lock timeout, a complete
  change-password screen, and rooted-device testing remain deferred; see
  [vault-security.md](./vault-security.md).
- Phase 7 UI wiring remains deferred for the next slice: habit freeze controls,
  Glance widget, to-do drag/repeating/subtask UI, money charts/export/accounts,
  loan reminder actions, file thumbnail/tag management, screen-time limit
  notifications, global search, app-wide biometric lock, and About/settings
  localization.
- Phase 8 automatic non-vault/Vault adapters and local-change debounce are
  implemented. A per-run merge summary, configured-project integration tests,
  and physical-device sync verification remain deferred; see
  [phase-8-supabase-sync.md](./phase-8-supabase-sync.md).
- Phase 9 physical-device UI/accessibility verification, full isolated
  end-to-end Compose assertions, and repository metadata cleanup remain
  deferred; see [phase-9-quality-ci-repo.md](./phase-9-quality-ci-repo.md).
