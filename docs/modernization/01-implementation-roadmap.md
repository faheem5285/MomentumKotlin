# Zylo Modernization — Implementation Roadmap

**Status:** Historical planning roadmap. M1–M13 are implemented on `impl/m1-data-safety` as uncommitted work; executed verification is recorded in `03-testing-and-acceptance-checklist.md` and the milestone contracts `06-m4-*` through `15-m13-*`. Device, remote and CI claims remain Unverified where no execution record exists. This roadmap follows `00-complete-repository-audit.md`.
**Branch policy:** Implement each milestone on a dedicated branch off `audit/modernization-zylo` (or the agreed integration branch); never commit directly to `main`. Group CI-triggering changes at milestone boundaries.
**Verification policy:** A test is "passed" only after it actually runs successfully. Device-dependent items remain unverified until executed on a device.

## Sequencing principles

1. Data safety and security before UI polish.
2. No destructive Room migration fallback; explicit migrations, exported schemas, migration tests.
3. Preserve RLS, encryption, optional sign-in, and all existing records.
4. One module at a time using its dedicated prompt; review, test, then proceed.
5. Avoid unnecessary dependencies and abstractions; reuse the existing glass/neon design system.

---

## Milestone 1 — Critical bugs and data safety

**Scope:** Account switching/WAL safety, full-reset scoping, sync conflict resolution, REPLACE cascade upserts, production QA reset removal, notification critical bugs.
**Modules affected:** `core/data`, `core/data/sync`, `features/vault`, `core/notifications`, `features/loans`.
**Files likely affected:** `core/data/Data.kt` (`activateAccount`, DAO upserts, migrations 16→17/18→19), `core/data/sync/LocalAppDataReset.kt`, `SupabaseSyncRepository.kt`, `SyncModels.kt`, `ProjectWipeManager.kt`, `features/vault/VaultScreen.kt`, `core/notifications/CustomNotificationScheduler.kt`, `CustomNotificationScreen.kt`, `CustomNotificationTimeCalculator.kt`.
**Dependencies:** None (foundation milestone). Requires backup of a real multi-account test device before any destructive-path testing.
**Risks:** Very high — wrong changes can destroy financial/habit/vault data. Migration changes change schema identity; do not edit existing migration SQL for already-shipped versions without an additive follow-up migration.
**Implementation steps:**
1. Add failing instrumentation tests that reproduce: WAL-pending account switch; two-account reset; data-bearing 16→17/18→19; REPLACE parent upsert child survival; dirty/local-deleted conflict; one-time notification retention; weekly mask correctness.
2. Fix `activateAccount` with unconditional checkpoint/SQLite backup API + atomic rename and rollback.
3. Scope `LocalAppDataReset` to active account + synced files; clear both DataStores; cancel/reschedule weekly backup.
4. Replace dirty/deleted conflict logic; use `@Upsert`/`UPDATE` for sync parents; add tombstone-aware candidate.
5. Remove/gate `resetForQa`; fix one-time notify cancellation and weekday mask (with migration for stored masks).
6. Re-run all migration, sync, and notification tests.
**Tests:** All instrumentation migration tests; new multi-account/WAL tests; sync resolver unit tests; notification calculator unit tests; one UI smoke test.
**Acceptance criteria:** No existing user data lost in any reproduction test; no `fallbackToDestructiveMigration`; no destructive QA button in release UI; one-time alerts remain visible; weekly weekdays fire on selected day; all executed tests pass.

---

## Milestone 2 — Architecture and security

**Scope:** Auth state machine, token refresh single-flight, error sanitization, SQL hardening, vault lifecycle security, SAF persistence.
**Modules affected:** `app`, `features/auth`, `features/profile`, `core/data/supabase`, `features/vault`, `features/files`, `features/contacts`, backend SQL.
**Files likely affected:** `MainActivity.kt`, `SupabaseAuthApi.kt`, `SecureTokenStore.kt`, `SupabaseRestClient.kt`, `ProfileScreen.kt`, `ProfileRepository.kt`, `VaultScreen.kt`, `FilesScreen.kt`, `ContactsScreen.kt`, `docs/supabase-schema.sql`, `docs/supabase/profiles.sql`.
**Dependencies:** Milestone 1 (data safety baseline).
**Risks:** High — auth/session changes can lock users out; SQL changes need deployment verification; token storage migration can invalidate sessions.
**Implementation steps:**
1. Extract app state machine; unify auth entry point; delete/route `AuthScreen`.
2. Add refresh single-flight + 401 retry; sanitize HTTP error bodies; cancel sync work on sign-out.
3. Migrate token storage to hardened EncryptedSharedPreferences/MasterKey with AAD and rotation; preserve/invalidate safely.
4. Add `TO authenticated`, explicit grants/revokes to SQL; canonicalize schema and account-deletion RPC; restrict admin CORS.
5. Add `FLAG_SECURE` while vault unlocked, idle autolock, picker-close lock; wire/remove biometric and password flows; wire encrypted export/restore.
6. Surface SAF persistable-permission failures; add bounded file reads.
**Tests:** Session restore matrix, token rotation tests, sign-out/local-data tests, vault lifecycle tests, SQL policy smoke test (remote), SAF denial UI test.
**Acceptance criteria:** Signed-out users retain and can use local data; no tokens/PII in logs or raw error surfaces; RLS ownership verified per table; vault plaintext never exposed in screenshots/recents while unlocked (device-verified before marking complete).

---

## Milestone 3 — Shared design system and navigation

**Scope:** Token cleanup, reusable card/button/sheet/state components, navigation contract, deep-link map, accessibility primitives.
**Modules affected:** `core/ui`, `app`, all screens (consumer migration).
**Files likely affected:** `core/ui/Theme.kt`, `Glass.kt`, `Ui.kt`, `DesignComponents.kt`, `Charts.kt`, `app/Navigation.kt`, `MainActivity.kt`.
**Dependencies:** Milestones 1–2 for state APIs; no behavior changes to data.
**Risks:** Medium — broad visual regression surface; channel/theme changes can be permanent on installed devices (notification channels, not theme).
**Implementation steps:**
1. Define `MomentumTokens` usage rules; derive glass colors from active scheme or document brand override.
2. Add shared loading/empty/error/form/dialog/bottom-sheet components; migrate Home and Settings first.
3. Add `animationSpecFor(LocalReduceEffects)` and replace fixed tweens.
4. Document deep-link contract and route constants; decide Home-as-hub vs persistent nav; hidden-module guard.
5. Add 360dp/2×/dark/reduced-motion previews and contrast tests.
**Tests:** Compose UI tests for shared components; previews; TalkBack/contrast device checklist.
**Acceptance criteria:** No duplicated one-off styles in migrated screens; readable at 360dp and 2× font; reduced-motion honored; navigation/deep links documented and tested.

---

## Milestone 4 — First-install onboarding and dashboard

**Scope:** Card-based onboarding, permission reminders, signed-out backup card, Home quick actions/states.
**Modules affected:** `features/home`, `features/settings`, `app`, `core/settings`, auth/profile status.
**Files likely affected:** `HomeScreen.kt`, `HomeRepository.kt`, `PermissionsSetupScreen.kt`, `SettingsViewModel.kt`, `AppSettingsScreen.kt`, `MomentumSettingsRepository.kt`, `MainActivity.kt`.
**Dependencies:** Milestone 3 components; permission APIs verified in Milestone 2.
**Risks:** Medium — onboarding must never block local use or repeatedly interrupt.
**Implementation steps:**
1. Add local onboarding progress store (tri-state: unknown/done/pending) with task ids, dismiss/snooze metadata, revisit from Settings.
2. Implement prioritized cards: Welcome, Create first habit, Add first task, Track first expense, Explore features, Enable required permission, Sign in for cloud backup.
3. Add signed-out backup card with exact wording: local data available; no cloud backup may be unrecoverable; sync unavailable; backup failure.
4. Add contextual permission cards (notifications, exact alarms, usage access, overlay/accessibility where used) with verified status, explanation, remind-later/don't-show-again, refresh on return from settings.
5. Add Home loading/error/retry and 2–3 conditional quick actions; adaptive grid.
**Tests:** Onboarding state persistence tests, permission-denied UI tests, Compose tests for cards and dismiss/snooze, device checks for return-from-settings refresh.
**Acceptance criteria:** Fresh install shows relevant cards, not all at once; completed/dismissed cards hidden appropriately; Settings can revisit; app fully usable with all optional permissions denied; no false "data lost/synced" text.

---

## Milestone 5 — Authentication and account experience

**Scope:** Sign-in/up states, email verification, password reset/recovery, session expiry, network failures, account deletion, safe account linking.
**Modules affected:** `features/auth`, `features/profile`, `core/data/supabase`, app shell.
**Files likely affected:** `AuthScreen.kt` (or replacement), `ProfileScreen.kt`, `ProfileViewModel.kt`, `AuthRepository.kt`, `SupabaseAuthApi.kt`, `Data.kt` (`activateAccount`), SQL docs.
**Dependencies:** Milestones 1, 2, 4.
**Risks:** High — first-sync behavior and account switching can overwrite local data; deletion is destructive.
**Implementation steps:**
1. Single auth UI with unified validation; handle signup response/confirmation/resend/expiry/network errors.
2. Recovery deep-link validation and intent clearing; password change via session with reauth.
3. Safe account-linking flow: snapshot local DB, explicit "claim/merge local data" decision, deterministic first-sync, migration path for `unassigned-legacy-database.db`.
4. Account deletion: canonical RPC + storage cleanup + accurate local-data warning.
**Tests:** Auth state matrix tests, cold/warm deep-link tests, first-sync with local data tests, deletion RPC smoke test.
**Acceptance criteria:** Local-only use uninterrupted; signing in never silently overwrites local records; expired sessions produce clear recovery; deletion behavior matches UI warning.

---

## Milestone 6 — Habits and tasks

**Scope:** Habit creation/schedules/streaks/insights/reminders; to-do creation/priority/due dates/subtasks/recurrence; wire or remove Phase7 shared logic.
**Modules affected:** `features/habits`, `features/todos`, `features/phase7`, `core/notifications`.
**Files likely affected:** `HabitScreen.kt`, `HabitEditor.kt`, `HabitLogic.kt`, `HabitRepository.kt`, `HabitsInsightsScreen.kt`, `ToDoScreen.kt`, `ToDoRepository.kt`, `Phase7Logic.kt`.
**Dependencies:** Milestones 1, 3, 4.
**Risks:** Medium — reminder behavior and streak math must stay correct; sync upserts fixed in M1.
**Implementation steps:** Simplify forms; transaction-backed save/delete; clear progress/streak explanations; wire shared helpers or remove them; add unit tests for streaks and quick-task parsing; ensure reminder state preserved when delivery blocked (M1 fix).
**Tests:** `computeStreaks` unit tests, habit reminder tests, Compose editor tests, sync child-preservation tests.
**Acceptance criteria:** Streak math tested; no partial reminder writes; forms usable at 360dp; no duplicate insights.

---

## Milestone 7 — Money and loans

**Scope:** Financial overview, budgets, recurring transactions, correct outstanding balances, charts, minor-unit arithmetic.
**Modules affected:** `features/money`, `features/loans`, `core/data`, `core/data/sync`.
**Files likely affected:** `ExpensesScreen.kt`, `ExpenseRepository.kt`, `LoansScreen.kt`, `LoanRepository.kt`, `Data.kt` (DAO balances), `Util.kt`, `MomentumSettingsRepository.kt`.
**Dependencies:** Milestones 1, 3.
**Risks:** High for correctness — financial figures shown to users change; must preserve historic amounts and backups.
**Implementation steps:** Unify budget source of truth; expose paid totals via Flow; compute outstanding = principal − payments (excluding tombstones); keep arithmetic in `Long` minor units; add range validation; add charts summaries; add unit tests.
**Tests:** Money conversion/balance tests, budget sync/restore tests, UI total assertions.
**Acceptance criteria:** All displayed balances mathematically correct with partial payments; one budget store; no crash for extreme input; backup restores budgets.

---

## Milestone 8 — Contacts and files

**Scope:** Record management/search, SAF reliability, size caps, bounded imports, missing-file states, tag/folder metadata exposure.
**Modules affected:** `features/contacts`, `features/files`, `core/data/sync`.
**Files likely affected:** `ContactsScreen.kt`, `ContactImport.kt`, `FilesScreen.kt`, `FileRepository.kt`, `FileStorageSync.kt`.
**Dependencies:** Milestones 2, 3.
**Risks:** Medium — storage/provider behavior device-dependent; changing URI semantics affects sync/backup.
**Implementation steps:** Handle persistable grants explicitly; cap file/import sizes; transactional imports; show missing/re-grant states and accurate "linked from provider" copy; stream uploads; expose existing metadata fields or remove stale docs.
**Tests:** Parser malicious fixtures, import rollback tests, SAF permission-denied UI tests, file size cap tests.
**Acceptance criteria:** No OOM on large inputs; failed persistence surfaced; imports atomic; copy matches storage model.

---

## Milestone 9 — Vault and Screen Time

**Scope:** Remaining vault hardening + Screen Time permission/statistics/accessibility clarity.
**Modules affected:** `features/vault`, `features/screen_time`, `core/data/sync`, `core/settings`.
**Files likely affected:** `VaultScreen.kt`, `VaultCrypto.kt`, `SensitiveClipboardWorker.kt`, `ScreenTimeScreen.kt`, `ScreenTimeRepository.kt`, `ScreenTimeLimits.kt`, `InstagramScrollCounterService.kt`.
**Dependencies:** Milestones 1, 2, 4.
**Risks:** High — crypto/key changes require migration; accessibility service is policy-sensitive.
**Implementation steps:** Finish vault autolock/export-restore/biometric decisions (M2); mask TOTP; single clipboard mechanism; screen-time limitations messaging; debounce tests; battery-conscious polling; Play policy justification or replacement for scroll counter; usage-access revocation detection.
**Tests:** Vault crypto + lifecycle tests, encrypted backup round-trip, accessibility debounce tests, permission-denied tests.
**Acceptance criteria:** No plaintext secrets in UI/logs/screenshots; documented cloud-key threat model shown in UI; permission limitations explained; tests pass.

---

## Milestone 10 — Reminders and device features

**Scope:** Reminder reliability, quiet hours, snooze, boot/time recovery, battery/network foreground services and overlays.
**Modules affected:** `core/notifications`, `features/battery`, `features/network`, `app`.
**Files likely affected:** `NotificationSystem.kt`, `CustomNotificationScheduler.kt`, `UsageLimitNotifications.kt`, `ZyloNotifications.kt`, `BatteryChargingMonitorService.kt`, `BatteryScreen.kt`, `NetworkIndicatorService.kt`, `NetworkScreen.kt`, `AndroidManifest.xml`.
**Dependencies:** Milestone 1 critical notification fixes; Milestone 3 channels/components.
**Risks:** High — FGS/background-start restrictions and OEM behavior; notification channels are effectively permanent once created.
**Implementation steps:** Centralize channels and posting policy; defer blocked reminders instead of discarding; cancel stale alarms; collapse notification bursts; explicit FGS types; boot recovery for persisted monitors where permitted; accurate quiet-hours copy; single power-event owner.
**Tests:** Alarm/cancel/recovery instrumentation, permission denial, channel creation, snooze/quiet-hours, device reboot/time change checklist.
**Acceptance criteria:** Reminders survive restart/time changes where OS permits; no silent loss from quiet hours; notification copy matches behavior; no flooding.

---

## Milestone 11 — Supabase sync and backup

**Scope:** Periodic reconciliation, deterministic conflicts, safe deletes, file lifecycle, backup v4/schema 22, optional encryption.
**Modules affected:** `core/data/sync`, `core/data/backup`, `core/data/supabase`, `features/supabase`, `features/settings`, backend SQL.
**Files likely affected:** `SupabaseSyncRepository.kt`, `FileStorageSync.kt`, `SupabaseSyncWorker.kt`, `MomentumBackup.kt`, `WeeklyBackupWorker.kt`, `SupabaseScreen.kt`, SQL docs, `supabase/functions/admin-wipe-cloud/index.ts`.
**Dependencies:** Milestones 1, 2, 5, 7, 8.
**Risks:** Very high — sync/backup touch every module; remote deployment must be verified; legacy backups must remain importable.
**Implementation steps:** Durable pending-delete log keyed by immutable account id; periodic work with constraints; correct pending/conflict counts; storage object deletion on tombstone; backup v4 with language tables and compatibility reader; reset cursors on replace restore; optional authenticated encryption with legacy import; explicit backup UI wording.
**Tests:** Sync idempotency/conflict/null-column tests, storage tombstone tests, backup v2/v3/v4 round-trip, RLS/deployment smoke tests, offline/reconnect tests.
**Acceptance criteria:** No silent overwrites or orphaned cloud objects; all user-data tables covered by backup; legacy backups import; status UI accurate; RLS preserved.

---

## Milestone 12 — Settings and global search

**Scope:** Settings grouping, appearance settings exposure/migration, module visibility recovery, global search.
**Modules affected:** `features/settings`, `core/settings`, `app`, all modules with searchable data.
**Files likely affected:** `AppSettingsScreen.kt`, `SettingsViewModel.kt`, `MomentumSettingsRepository.kt`, `AppearancePreferences.kt`, `PermissionsSetupScreen.kt`, new search component/VM.
**Dependencies:** Milestones 3, 4, 11.
**Risks:** Medium — settings migration/backups; search indexing must not load sensitive data into logs.
**Implementation steps:** Group settings into Appearance, Notifications, Privacy & Security, Data & Backup, Cloud & Sync, Screen Time & Device, Account, About; migrate appearance into DataStore with backup keys; expose `showAllModules()`; signed-in/local status subtext; global search across habits/tasks/expenses/contacts/files/life rules with permission-aware scope.
**Tests:** Settings migration tests, visibility recovery tests, search tests including empty/error states.
**Acceptance criteria:** No duplicated/confusing settings; hidden modules recoverable; status accurate; search fast and scoped.

---

## Milestone 13 — Performance, accessibility, and final QA

**Scope:** Recomposition/flow cleanup, file/bitmap memory, benchmark relevance, full device matrix, documentation finalization.
**Modules affected:** All.
**Files likely affected:** Feature screens, `benchmark/*`, `.github/workflows/android.yml`, `config/detekt.yml`/baseline, docs.
**Dependencies:** All previous milestones.
**Risks:** Medium — late broad changes can regress behavior; only freeze after acceptance.
**Implementation steps:** Replace non-lifecycle flow collection; split giant composables; bounded streams; add meaningful benchmark metrics; ratchet detekt baseline; narrow R8 keeps with release smoke test; add CI connected tests/artifacts; full checklist from `03-testing-and-acceptance-checklist.md`; finalize docs and mark historical phase docs.
**Tests:** Full unit/instrumentation suite, benchmark on device, accessibility/TalkBack/contrast checks, API 26+ matrix, offline/reconnect, battery optimization, restart.
**Acceptance criteria:** All executed tests pass; no critical/high issues unresolved; device-dependent items marked verified only where actually tested; documentation matches source; CI green on final integration branch.

---

## Cross-milestone gates

| Gate | Requirement |
|---|---|
| Data safety | No destructive migration fallback; all new migrations exported and tested; no unbacked deletion paths. |
| Security | RLS preserved; no secrets/tokens/PII in logs; vault plaintext never persisted or logged. |
| Auth | Optional sign-in preserved; signed-out local use verified in every release candidate. |
| UX | Onboarding non-blocking; permission status verified before claims; accurate local/cloud wording. |
| QA | Tests claimed passed only after actual successful execution; device tests explicitly labelled. |
| Branching | Dedicated branch per milestone; no direct commits to `main`; grouped CI runs. |
