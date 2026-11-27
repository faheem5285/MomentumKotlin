# Zylo / Momentum — Complete Repository Audit

> **Historical audit record:** This file preserves the original read-only snapshot and must not be read as current implementation status. Current status and executed verification are recorded in `01-implementation-roadmap.md`, `03-testing-and-acceptance-checklist.md`, `14-m12-settings-and-global-search-contract.md` and `15-m13-performance-accessibility-final-qa-contract.md`. Device, remote and CI claims remain Unverified where no execution record exists.

**Audit date:** 2026-11-26
**Branch:** `audit/modernization-zylo` (created from `phase-9-quality-ci-repo`; `main` was not modified)
**Method:** Static read-only source inspection of the complete repository. No application source, Gradle, database, auth, or schema files were changed. No build, unit test, instrumentation test, benchmark, device test, or remote Supabase operation was executed during this audit.
**Verification legend:** **Verified** = directly confirmed in the source file cited. **Unverified** = runtime, remote-service, device, or build behavior that cannot be established from local source; explicitly labelled.

---

## Executive summary

### Counts

| Item | Count |
|---|---:|
| Gradle modules | 2 (`:app`, `:benchmark`) |
| Logical modules/packages discovered | 25 (18 `features/*`, 5 `core/*`, app shell, benchmark) + cross-cutting Supabase SQL/Edge Function |
| Logical modules audited | 25 of 25 + backend artifacts present in repo |
| Top-level screens | 21 (Home + 15 module routes + session gate, permission setup, Auth, restoring) |
| Internal sub-screens/flows | 20+ (tabs, editors, dialogs, gate/detail views) |
| Room entities / database version | 23 entities / v22 (explicit migrations 3→22, all schemas exported) |
| Unit tests / instrumentation tests | 8 classes / 35 `@Test`; 5 classes / 15 `@Test` |
| Module prompts produced | 22 (grouping closely coupled packages) — see `docs/modernization/module-prompts/` |
| Critical findings | 8 |
| High findings | 28 |
| Medium / Low findings | 34 / 12 |

### The most important conclusions

1. **The app is already offline-capable in code**, contrary to README: `MainActivity.kt:194` sets `isAuthenticated = true` for `NO_SESSION`, and `AuthScreen` is unreachable from normal startup/sign-out flows. The requested *optional sign-in* is therefore mostly implemented but accidental and poorly documented.
2. **Local data loss risks are real and concentrated in three places:** account-scoped database switching (`Data.kt:939-976`), the all-directory reset (`LocalAppDataReset.kt:20-49`), and sync conflict handling (`SupabaseSyncRepository.kt:516-525` plus `@Insert(REPLACE)` DAO upserts). These must be fixed before visual work.
3. **Backup is stale and incomplete:** format declares schema 20 while Room is 22; four user-data tables (language vocabulary/notes/review/history) are excluded (`MomentumBackup.kt:19-24,168-177`).
4. **A destructive "Reset Vault QA data" path is exposed in the production UI** with no `BuildConfig.DEBUG` guard (`VaultScreen.kt:506-515,1181-1214`).
5. **Notification defects cause missed or wrong-day reminders:** one-time custom notifications are posted then cancelled (`CustomNotificationScheduler.kt:118-131`), and weekly masks are off by one day (`CustomNotificationScreen.kt:264` vs `CustomNotificationTimeCalculator.kt:35-36`).
6. **Vault security docs overstate the implementation:** no `FLAG_SECURE`, biometric/password unlock methods are UI-dead, v3 encrypted export cannot be consumed by the exposed import path, and there is no idle autolock. The cloud key is escrowed in Supabase (documented; not E2EE).
7. **Translation is cloud-first while the UI claims on-device/private/offline** (`LanguageTranslator.kt:37-54,92-104`).
8. **There is no first-install onboarding beyond a permission-setup gate**; it is not a card-based onboarding experience and can be bypassed with system back without persisting completion (`MainActivity.kt:265-273`, `PermissionsSetupScreen.kt:74-196`).
9. **RLS ownership checks exist per table** (`docs/supabase-schema.sql`) and no production secret is committed. This is a strength that must be preserved.
10. **No test was executed in this audit.** All "passed" claims in older docs are historical and unverified in this workspace.

---

## 1. Actual project architecture

### 1.1 Build and dependencies

- Root: `settings.gradle.kts` includes `:app` and `:benchmark`; `build.gradle.kts` declares plugins without applying them.
- `app/build.gradle.kts`: AGP 8.5.2, Kotlin 2.0.20, KSP, Hilt, Detekt, baseline profile plugin; compileSdk/targetSdk 34, minSdk 26, Java/Kotlin JVM target 17; Compose BOM 2024.09.00, strong skipping enabled; Room 2.6.1 with schema export to `app/schemas`; WorkManager 2.9.1; Navigation 2.8.2; DataStore 1.1.1; Supabase postgrest 2.6.1 + Ktor; Biometric 1.1.0; Credentials/Google ID; ML Kit Translate/Language ID; LeakCanary debug-only.
- Supabase URL/publishable key and Google web client ID are read from Gradle properties/environment (`app/build.gradle.kts:16-26`) with empty defaults — correct; no hardcoded production credentials found.
- Release signing: dedicated signing config plus `verifyReleaseSigning` task wired to `preReleaseBuild` (`app/build.gradle.kts:28-60,84-100`). CI generates a throwaway key (`.github/workflows/android.yml:41-58`); artifacts are not distributable (unverified whether users treat them as distributable).
- `benchmark`: macrobenchmark + baseline profile module targeting `:app`.

### 1.2 Runtime layering (verified)

```text
MainActivity (FragmentActivity, @AndroidEntryPoint)
 ├─ AppearancePreferences (SharedPreferences) -> MomentumTheme
 ├─ SupabaseAuthApi.restoreSession() / ProjectWipeManager
 ├─ PermissionsSetupScreen (first-run gate) or MomentumNavigationHost
 │    └─ NavHost: home | module/{module}?itemId&requestToken
 │         └─ ModuleScreen -> feature composable (one of 15 AppModule values)
 ├─ Broadcast receivers: reminders, boot/time changes, power events
 └─ WorkManager: reminder rescheduling, usage limit monitor, weekly backup, sync, clipboard cleanup

Feature composables -> repositories/DAOs (Room, DataStore, SharedPreferences)
                    -> Supabase REST (publishable key + bearer token) via sync workers
```

- Room is the local source of truth; `AppDatabase` is a singleton with per-account file switching.
- Most feature screens are self-contained composables holding state directly; only Profile, Battery, Network, Screen Time, Settings have ViewModels. Hilt DI exists (`core/di/AppModule.kt`) but most objects are accessed via application singletons.
- Sync is a one-time WorkManager chain triggered by DB invalidation, app resume, sign-in, and manual action; **no periodic sync worker exists** (verified). Docs describe a periodic chain — stale.
- Navigation is Home-tile based; no bottom nav/rail/drawer anywhere (verified via grep).

---

## 2. Complete module and screen inventory

### 2.1 Logical modules, important source files, implementation status

| # | Module (package) | Important source files (all under `app/src/main/java/com/faheem/habits/...`) | Actual functionality / status |
|---:|---|---|---|
| 1 | `app` shell | `app/MainActivity.kt`, `app/Navigation.kt`, `app/MomentumApplication.kt` | Entry, theme, session restore, wipe notices, permission gate, deep links, reminder scheduling, power receiver. Verified. |
| 2 | `core/data` | `core/data/Data.kt` (23 entities, 20 DAOs, v22 DB, migrations 3→22), `Util.kt` | Local persistence, money conversion, per-account DB files. Verified; migration tests exist. |
| 3 | `core/data/backup` | `backup/MomentumBackup.kt`, `backup/WeeklyBackupWorker.kt` | JSON export/import (v3, schema 20), SAF weekly backups. Verified; stale schema and unencrypted PII. |
| 4 | `core/data/supabase` | `supabase/SecureTokenStore.kt`, `SupabaseAuthApi.kt`, `SupabaseRestClient.kt`, `SupabaseProvider.kt` | Auth REST, token encryption, profile cache, storage upload/download, vault key escrow. Verified. |
| 5 | `core/data/sync` | `sync/SupabaseSyncRepository.kt`, `SupabaseSyncWorker.kt`, `SyncModels.kt`, `SyncModuleSettings.kt`, `FileStorageSync.kt`, `LocalAppDataReset.kt`, `ProjectWipeManager.kt`, `DeviceIdentity.kt` | Push/pull 17 tables + profiles, module resets, wipe handling, first-sync backup. Verified; highest-risk area. |
| 6 | `core/di` | `core/di/AppModule.kt` | Hilt providers. Verified; limited use. |
| 7 | `core/notifications` | `NotificationSystem.kt`, `CustomNotificationScheduler.kt`, `CustomNotificationTimeCalculator.kt`, `CustomNotificationScreen.kt`, `ReminderSettingsScreen.kt`, `ReminderTimeCalculator.kt`, `ReminderVerificationWorker.kt`, `UsageLimitNotifications.kt`, `ZyloNotifications.kt` | Alarms, channels, quiet hours, snooze, custom notifications, usage alerts. Verified; critical bugs present. |
| 8 | `core/settings` | `settings/AppearancePreferences.kt`, `settings/MomentumSettingsRepository.kt` | DataStore app/money settings, appearance SharedPreferences, backup key mapping. Verified. |
| 9 | `core/ui` | `ui/Theme.kt`, `Glass.kt`, `Ui.kt`, `DesignComponents.kt`, `Charts.kt`, `AppIcon.kt`, `AppIdentity.kt`, `DesignSystemPreview.kt` | Material3 theme, glass/neon tokens, shared cards/buttons, charts, branding. Verified; substantial reusable system. |
| 10 | `features/home` | `home/HomeScreen.kt`, `home/HomeRepository.kt` | Dashboard tiles, device insights, settings shortcuts. Verified; no onboarding/quick actions/loading/error states. |
| 11 | `features/habits` | `HabitsScreen.kt`, `HabitsToday.kt`, `HabitEditor.kt`, `HabitLogic.kt`, `HabitRepository.kt`, `HabitReminderManager.kt`, `HabitsInsightsScreen.kt`, `HabitsManageScreen.kt`, `HabitsTemplatesScreen.kt`, `HabitTemplates.kt` | Full habit CRUD, streaks, templates, insights, schedules/reminders. Verified. |
| 12 | `features/todos` | `ToDoScreen.kt`, `data/ToDoRepository.kt` | Task CRUD, priorities, due dates, recurring rules, subtasks, reminders. Verified. |
| 13 | `features/money` | `ExpensesScreen.kt`, `data/ExpenseRepository.kt` | Expenses/income, categories, budgets (DataStore), charts. Verified. |
| 14 | `features/loans` | `LoansScreen.kt`, `data/LoanRepository.kt` | People, loans, payments, attachments, due dates. Verified; outstanding balance ignores payments. |
| 15 | `features/contacts` | `ContactsScreen.kt`, `ContactImport.kt` | Local contacts + VCF/CSV/XLSX import. Verified; no Contacts permission used. |
| 16 | `features/files` | `FilesScreen.kt`, `data/FileRepository.kt` | SAF-linked files, folders, search, previews. Verified; URIs only, no copy into app storage. |
| 17 | `features/liferules` | `LifeRulesScreen.kt`, `data/LifeRuleRepository.kt` | Personal rules/notes with attachments, pinned, synced. Verified. |
| 18 | `features/vault` | `VaultScreen.kt`, `VaultCrypto.kt`, `data/VaultRepository.kt`, `SensitiveClipboardWorker.kt` | AES-GCM + PBKDF2 vault, account unlock, clipboard cleanup, QA reset. Crypto verified strong; UI lifecycle gaps. |
| 19 | `features/screen_time` | `ScreenTimeScreen.kt`, `ScreenTimeRepository.kt`, `ScreenTimeViewModel.kt`, `ScreenTimeLimits.kt`, `UsageLimitWorker.kt`, `InstagramScrollCounterService.kt` | Usage stats, limits, accessibility-based scroll counter. Verified. |
| 20 | `features/battery` | `BatteryScreen.kt`, `BatteryRepository.kt`, `BatteryViewModel.kt`, `BatteryChargingMonitorService.kt`, `BatteryChargingActivity.kt`, `BatteryChargeOverlay.kt`, `BatterySessionHistory.kt`, `AppBatteryUsage.kt`, `BatteryChargeAnimations.kt` | Charging animations, sessions, overlay with notification fallback. Verified. |
| 21 | `features/network` | `NetworkScreen.kt`, `NetworkIndicatorService.kt`, `NetworkRepository.kt`, `NetworkViewModel.kt`, `AppNetworkUsage.kt`, `NetworkLimitPeriod.kt` | Per-app network usage, limits, ongoing notification. Verified. |
| 22 | `features/language` | `LanguageScreen.kt`, `LanguageTranslator.kt`, `LanguageProofreader.kt` | Translation, proofreading, vocabulary/grammar, SRS review, history. Verified; cloud-first translation. |
| 23 | `features/settings` | `AppSettingsScreen.kt`, `SettingsViewModel.kt`, `PermissionsSetupScreen.kt` | Settings groups, backup/restore UI, permission setup. Verified. |
| 24 | `features/profile` + `features/auth` | `ProfileScreen.kt`, `ProfileViewModel.kt`, `ProfileRepository.kt`, `AuthRepository.kt`, `ProfileValidation.kt`, `AuthScreen.kt` | Sign-in/up, Google credentials, password reset, account deletion request, profile edit. Verified; two divergent auth UIs. |
| 25 | `features/supabase` + `benchmark` | `SupabaseScreen.kt`, `CloudSyncRepository.kt`, `CloudSyncViewModel.kt`; `benchmark/MomentumMacrobenchmark.kt`, `MomentumBaselineProfile.kt` | Cloud/sync status, module toggles, wipe admin UI; macrobenchmark and baseline profile. Verified; benchmark metrics empty. |
| Cross-cutting | Supabase backend artifacts | `docs/supabase-schema.sql`, `docs/supabase/profiles.sql`, `docs/supabase/language.sql`, `docs/supabase-wipe-state.sql`, `supabase/functions/admin-wipe-cloud/index.ts`, `supabase/config.toml` | RLS policies, storage policies, wipe guard/RPC, admin Edge Function. Verified statically; remote deployment unverified. |

### 2.2 Screen inventory (routes and entry points)

| Screen | File / composable | Entry | Verified behavior |
|---|---|---|---|
| Restoring session | `MainActivity.kt:312` `RestoringSessionScreen` | startup while session restore pending | Splash/progress. |
| Auth | `features/auth/AuthScreen.kt:61` | `MainActivity.kt:285-306` (only if `isAuthenticated == false`) | Effectively unreachable in normal flows (all restore branches set true). |
| Permission setup | `features/settings/PermissionsSetupScreen.kt:74` | first run + Settings revisit | Non-blocking; marks seen through the top bar/Continue action. |
| Home | `features/home/HomeScreen.kt:156` | NavHost `"home"` | Dashboard hub with module tiles/device rows. |
| Habits (+ Today/Library/Insights/Manage/Editor) | `features/habits/...` | route `module/Habits` (+ tabs) | Full feature. |
| To-dos (+ editor) | `features/todos/ToDoScreen.kt:104,531` | `module/ToDos` | Full feature. |
| Money | `features/money/ExpensesScreen.kt:79` | `module/Money` | Overview/activity/budget sections. |
| Loans (+ people/details/editors) | `features/loans/LoansScreen.kt:195+` | `module/Loans` | Full feature. |
| Contacts | `features/contacts/ContactsScreen.kt:114` | `module/Contacts` | Full feature. |
| Files | `features/files/FilesScreen.kt:86` | `module/Files` | Full feature. |
| Life rules | `features/liferules/LifeRulesScreen.kt:146` | `module/LifeRules` | Full feature. |
| Screen time (+ apps/Reels) | `features/screen_time/ScreenTimeScreen.kt:98+` | `module/Screen` | Full feature. |
| Vault (gate/home/detail/editor) | `features/vault/VaultScreen.kt:856+` | `module/Vault` | Gated by account unlock. |
| Battery | `features/battery/BatteryScreen.kt:138` | `module/Battery` | Full feature. |
| Network | `features/network/NetworkScreen.kt:102` | `module/Network` | Full feature. |
| Language (+ editors/history) | `features/language/LanguageScreen.kt:311+` | `module/Language` | Full feature. |
| Cloud & Sync | `features/supabase/SupabaseScreen.kt:176` | `module/Supabase` + Settings link | Sync status, module visibility, wipe admin UI. |
| Profile | `features/profile/ProfileScreen.kt:94` | `module/Profile`, Settings link, `momentum://auth/*` | Auth actions, profile edit, password recovery dialog. |
| Settings | `features/settings/AppSettingsScreen.kt:88` | `module/Settings`, Home header | Grouped settings incl. backup, reminders, permissions. |
| Reminder settings / Custom notifications | `core/notifications/ReminderSettingsScreen.kt:40`, `CustomNotificationScreen.kt:74` | Settings | Reminder health, quiet hours, custom alerts. |

**Deep links:** `momentum://module/{module}` with `itemId`/`requestToken` query args (`Navigation.kt:79-83`); module token is the enum name (e.g. `LifeRules`), not the display label. `momentum://auth/*` is handled in `MainActivity.kt:125-137` outside NavHost. Hidden modules remain deep-link reachable (verified).

**Dead screens:** none found statically; all public screen composables have call sites. `AuthScreen` is reachable only through a state that current restore logic never produces.

---

## 3. Existing functionality and implementation status

| Feature | Status (verified) | Notes |
|---|---|---|
| Offline local use | **Working** | Signed-out/locally-only mode uses Room normally (`MainActivity.kt:194`). |
| Optional sign-in | **Mostly working, accidental** | No forced gate; dead duplicate AuthScreen and inconsistent validation. |
| Habit CRUD/streaks/templates/insights | **Working** | `HabitLogic`, templates, insights implemented; shared Phase7 helpers unused. |
| To-do CRUD/recurrence/subtasks | **Working** | No dedicated unit tests. |
| Money/expenses/budgets | **Working with defects** | Two budget stores; formatting inconsistency; mapped to minor units correctly. |
| Loans/payments | **Partially incorrect** | Outstanding balance ignores recorded payments. |
| Contacts/files/life rules | **Working** | SAF URI persistence failures swallowed; import lacks size caps. |
| Vault crypto | **Strong primitives** | AES-256-GCM, PBKDF2 310k, zeroization, biometric Keystore key (unit tests exist). |
| Vault UX/key management | **Incomplete/risky** | Cloud key escrow; dead biometric/password UI; no FLAG_SECURE; broken v3 import; QA reset in prod UI. |
| Screen time/network/battery | **Working** | Permission-gated; high-privilege permissions and FGS policy concerns. |
| Reminders | **Working with bugs** | One-time notification cancelled; weekly day off-by-one; quiet hours discard reminders; stale alarms. |
| Sync | **Partially working** | RLS present; dirty-edit overwrite risk; REPLACE cascade risk; no periodic reconciliation. |
| Backup/restore | **Working but stale** | v3/schema 20; excludes language tables; plaintext PII; non-atomic settings. |
| Onboarding | **Minimal** | Single permission-setup gate; no card-based first-install guidance; no revisit dashboard. |
| Design system | **Substantial** | Shared theme/glass/tokens; inconsistent adoption in feature screens. |
| Tests | **Partial** | Strong migration/backup/crypto tests; many no-assertion tests; broad coverage gaps. |
| CI | **Partial** | lint/detekt/unit/assemble run on every push/PR; no connected tests/benchmark/artifacts. |

---

## 4. UI/UX problems

| ID | Module | Source (file / class / function) | Current behavior | Problem | Recommended improvement | Priority | Change risk | Dependencies / verification |
|---|---|---|---|---|---|---|---|---|
| U1 | app shell / onboarding | `MainActivity.kt:164-165,265-273`; `SettingsViewModel.kt:26-30`; `PermissionsSetupScreen.kt:74-196` | First launch shows one permission-setup screen; `permissionSetupSeen` false before DataStore emits; system back can bypass marking seen. | No card-based first-install onboarding; possible repeated gate; no task-level progress or revisit dashboard. | Implement card-based onboarding with persisted task state (see `03-onboarding-permissions` prompt); use tri-state loading; handle system back. | High | Low | Settings DataStore, permissions. Returning-user flash unverified on device. |
| U2 | home | `HomeScreen.kt:68-179,315-366` | Zero/default stats indistinguishable from loading/error; no direct add actions; fixed 2-column tiles (`142.dp`), `maxLines=1`, `softWrap=false`. | Confusing fresh-install dashboard; truncation at 360dp/large fonts; no retry. | Add loading/error/retry states, contextual quick actions, adaptive grid, theme typography. | High | Medium | Navigation, shared state components. Layout issues unverified on device. |
| U3 | design system | `Theme.kt:198-323`; `Glass.kt`, `Ui.kt`; feature screens with literal colors/sp | Shared system exists; Battery 32, Loans 33, Expenses 25 raw `.sp` values; Home 11 literal `Color(0x…)`. | Inconsistent spacing/typography; high regression cost. | Migrate to `MomentumTokens`, `MaterialTheme.typography`, shared components. | Medium | Medium | All screens. |
| U4 | accessibility | `Glass.kt:323`; `AppSettingsScreen.kt:367-371`; `Charts.kt:163,234,267-339` | Some rows use bare `clickable`; charts expose only point counts; HeroCard white text on bright gradients. | TalkBack action labels and contrast may be poor. | Use `clickable(role, onClickLabel)`, merged semantics, chart summaries, luminance-based `onColor`; add contrast tests. | High | Low-Medium | Core UI. Actual ratios/TalkBack unverified. |
| U5 | motion / theme | `Ui.kt:53,355,401-405`; `Charts.kt:150-153,287`; `Glass.kt:974-978,1114` | `LocalReduceEffects` honored selectively; fixed-duration `tween` calls remain. Dynamic accent affects only Material colors, not glass palette/gradients. | User preference partially ignored; visual clash possible. | Centralize `animationSpecFor` and derive glass tokens from active scheme or document override. | Medium | Low | Theme. On-device behavior unverified. |
| U6 | copy/i18n | `res/values/strings.xml:1-11`; hardcoded UI strings everywhere | Almost all user text hardcoded; "Supabase fetch failed", "tables healthy", "Stored on this device" (files/life rules) shown when cloud sync may be active. | Inconsistent product vocabulary, misleading data-location claims, no localization path. | Extract strings; use "Cloud & Sync"; derive storage copy from sync state. | Medium | Low | All modules. |
| U7 | giant files | `LanguageScreen.kt` 2,704 lines; `VaultScreen.kt` 1,889; `SupabaseScreen.kt` 1,133; `LoansScreen.kt` 1,031; `BatteryScreen.kt` 1,022 | State, platform IO, and UI colocated. | Hard to test/review; high merge conflict risk. | Split by state holder/tab/subcomponent without behavior change. | Medium | Medium | Feature packages. |
| U8 | navigation | `Navigation.kt:39-100`; `HomeScreen.kt:315-366` | Only Home + generic module route, no persistent nav component; hidden modules still deep-linkable. | Module-to-module movement limited; deep-link contract undocumented. | Decide Home-as-hub vs persistent nav; document/route constants; visibility guard. | Medium | Medium | App shell. |

---

## 5. Architecture and code-quality problems

| ID | Module | Source | Current behavior | Problem | Recommended improvement | Priority | Change risk | Dependencies / verification |
|---|---|---|---|---|---|---|---|---|
| A1 | app shell | `MainActivity.kt:153-309` | Session restore, wipe handling, notices, permission gate and navigation all live in one composable; `isAuthenticated` never set false. | Dead auth path; two divergent auth UIs; state machine implicit. | Extract explicit `AppState` state machine; unify auth path; remove or route `AuthScreen`. | High | Medium | Auth, profile, wipe. |
| A2 | multiple | `LanguageScreen.kt:313-318`, `ContactsScreen.kt:115`, `CustomNotificationScreen.kt:78` | `collectAsState()` used instead of `collectAsStateWithLifecycle()`. | Unnecessary work off-start; stale observation. | Use lifecycle-aware collection. | Medium | Low | Lifecycle runtime dependency already present. |
| A3 | multiple | `HomeScreen.kt:131-179`, `SupabaseScreen.kt:188-279`, `PermissionsSetupScreen.kt:84-146` | Business/system IO in `LaunchedEffect`/`remember`. | Poor testability, restoration issues. | Move to repositories/ViewModels. | Medium | Medium | Feature VMs. |
| A4 | data | `HabitRepository.kt:31-64,118-125`; `LoanRepository.kt:103-108,73-78`; `ContactsScreen.kt:75-110` | Multi-step writes outside transactions; person dedupe via name scan. | Partial writes; duplicate people; sync observes half-updated state. | Wrap in `withTransaction`, normalized lookups, suppress sync until commit. | High | Medium | Room, sync scheduler. |
| A5 | data/sync | `SupabaseSyncRepository.kt:161,167,298-304` | `pending = 0` returned regardless of dirty rows; conflict count accumulates on retry. | Misleading sync status UI. | Recompute pending/conflicts per run from DB state. | Medium | Low | Sync UI. |
| A6 | quality | `config/detekt-baseline.xml` (342 issues), `config/detekt.yml:1-14` | `maxIssues: 0`; baseline large; `LongMethod`, `LargeClass`, `MagicNumber`, `MaxLineLength` disabled; ~10 wildcard imports unbaselined. | Regressions hidden; detekt gate may be red. | Ratchet baseline, fix unbaselined imports, re-enable rules gradually. | Medium | Low-Medium | CI detekt step. Gate outcome unverified. |
| A7 | CI | `.github/workflows/android.yml:3-5,14-64` | Triggers on every push/PR; lint/detekt/unit/assemble; no connected tests, benchmark, artifacts, `workflow_dispatch`, `concurrency`. | Duplicate runs; critical migration/UI tests never run in CI. | Add manual trigger/concurrency, connected-test job, artifact upload; keep grouping meaningful. | High | Medium | Emulator strategy. CI outcome unverified. |
| A8 | CI/security | `.github/workflows/android.yml:41-58` | Hard-coded throwaway keystore passwords in YAML. | Scanner noise; unsafe pattern if copied to real release. | Randomize per-run values; use GitHub Secrets for real signing. | Medium | Medium | Signing config. |
| A9 | benchmark | `benchmark/.../MomentumMacrobenchmark.kt:31-55`, `MomentumBaselineProfile.kt:16-24` | `metrics = emptyList()`; hardcoded swipe coordinates; waits for "Momentum" string not present; fresh install shows permission gate. | Benchmarks measure nothing / may time out; baseline profile may capture wrong screen. | Add `FrameTimingMetric`, stable tags, test launch path. | Medium | Medium | Device required. Runtime unverified. |
| A10 | R8 | `app/proguard-rules.pro:3-17` | Broad keeps for `**_Impl`, all `@Entity`, `**$$serializer`, all `**$Companion`. | Shrink effectiveness reduced. | Narrow with package-scoped rules; verify release build. | Medium | High | Release smoke test. R8 behavior unverified. |
| A11 | docs | `README.md:3-7,33`; `docs/sync-design.md:6-7`; `docs/phase-*.md`; `NOTES.md:9-10,33,38` | README says account required + single module; code allows local mode + two modules; many phase docs claim v12/v20, stale test counts and contradictory verification results. | Documentation cannot be trusted as implementation state. | Mark historical docs, create single current-state page, update after each milestone. | Medium | Low | Docs only. |

---

## 6. Database and migration risks

**Verified baseline:** `Data.kt:910-914` declares `@Database(version = 22, exportSchema = true)` with 23 entities; all schemas `3.json`–`22.json` exist; explicit migrations `MIGRATION_3_4`…`MIGRATION_21_22` registered at `Data.kt:1422-1440`; **no `fallbackToDestructiveMigration` anywhere**; `AppDatabaseMigrationTest.kt:45-151` tests every step, full chain, row preservation, FK migration, UUID backfill. This is a strong baseline — preserve it.

| ID | Module | Source | Current behavior | Problem | Recommended improvement | Priority | Change risk | Dependencies / verification |
|---|---|---|---|---|---|---|---|---|
| DB1 | core/data | `Data.kt:1298-1315` (`MIGRATION_16_17`), `1182-1187` | Rebuilds `loans` via create/insert/drop/rename while `loan_payments` has `ON DELETE CASCADE`. | Potential cascade loss of payment history; test uses empty DB. | Disable FK outside transaction or use non-destructive rebuild; add data-bearing test. | High | High | Loans, sync, backup. Runtime PRAGMA state unverified. |
| DB2 | core/data | `Data.kt:1350-1377` (`MIGRATION_18_19`) | PRAGMA toggles inside migration; drops `loans`; schema identical to v18. | Same cascade risk; toggle effectiveness inside Room transaction unverified. | Replace with no-op/repair; add test with payments and attachments. | High | High | Loans. Runtime unverified. |
| DB3 | core/data | `Data.kt:939-976` (`AppDatabase.activateAccount`) | On cold start with pending WAL, copies only main DB file then deletes WAL/SHM; overwrites prior account copy. | Committed local rows can be lost; account data can be overwritten non-atomically. | Checkpoint always; SQLite backup API or atomic rename with rollback; add WAL-pending switch test. | Critical | High | Auth, sync, all local data. Loss unverified at runtime. |
| DB4 | core/data/sync | `LocalAppDataReset.kt:20-49` | `clearAllTables()` + clears all `shared_prefs` except allowlist + recursively deletes `filesDir`, `cacheDir`, `noBackupFilesDir`, external dirs. | `filesDir/account-databases/` holds **other accounts' databases** → cross-account irreversible loss. DataStore settings not cleared by default callers; weekly backup prefs cleared while WorkManager job remains. | Scope deletion to active account + synced files; clear both DataStores; cancel/reschedule backup work; add multi-account test. | Critical | Very High | Auth, sync, settings, WorkManager. |
| DB5 | core/data | `Data.kt:392-399,569-572,705,...` + `SupabaseSyncRepository.kt:414-506` | DAO sync upserts use `@Insert(REPLACE)` on parents with `ON DELETE CASCADE` children. | Remote habit/loan/life-rule update deletes local completions, reminders, payments, attachments. | Use `@Upsert`/`ON CONFLICT DO UPDATE` preserving PK/FK identity; add child-count tests. | Critical | High | All sync modules, backup. |
| DB6 | core/data/sync | `SupabaseSyncRepository.kt:516-525` (`accept`) | `dirty` parameter unused; local candidate hardcodes `deleted=false`; remote wins on timestamp/device only. | Unsynced local edits overwritten; local tombstones resurrected. | Never overwrite dirty rows; include real deleted state; explicit conflict record. | Critical | High | All synced modules. |
| DB7 | core/data/sync | `SupabaseSyncRepository.kt:330-382` | `resetModule` deletes cloud first then local; `queueModuleDelete` deletes local immediately and keys pending cloud delete by mutable current user id. | Failure after cloud delete leaves no local copy; account switch leaves queued delete unusable. | Durable op log keyed by immutable account id; local export/snapshot first; reconcile before switch. | High | Medium-High | Sync, settings, vault. |
| DB8 | core/data/sync | `ProjectWipeManager.kt:20-55` | Clean-install exemption uses package `firstInstallTime`; unchecked `commit()` on early marker writes. | Restored pre-wipe backups can escape wipe; failed marker write causes repeated wipes. | Explicit clean-install marker; check all commits; server nonce/ack. | Medium | Medium | MainActivity, REST client. |
| DB9 | core/data/backup | `MomentumBackup.kt:19-24,168-177,477-479` | Backup v3 requires `schemaVersion == 20`; excludes `vocabulary_words`, `grammar_notes`, `review_states`, `translation_history`; replace doesn't clear `sync_state`. | Language user data not backed up; stale cursors after restore. | Backup v4 schema 22 with backward-compatible reader; reset cursors; tests. | High | Medium | Language, sync, backup UI. |
| DB10 | core/data/backup | `MomentumBackup.kt:213-440` | Merge restore skips existing UUIDs without timestamp comparison; DataStore import outside Room transaction. | Wrong version of data wins silently; partial DB rollback with applied settings. | Compare timestamps/dirty; pre-validate; compensate settings on failure. | High | Medium-High | Settings, all modules. |
| DB11 | core/data | `Data.kt:347-361,511-515`; `MomentumSettingsRepository.kt:53,80-82`; `ExpenseRepository.kt:15-19` | Room `category_budgets` synced/backed up, but UI reads DataStore `money_limit_*`; DAO doesn't filter deleted. | Cloud/restore budget changes invisible in UI. | Single authoritative store or write-through; filter tombstones. | Medium | Medium | Money, settings, sync, backup. |
| DB12 | core/data | `Util.kt:24-27`; `Data.kt:1410-1413` | `toMinorUnits()` uses `longValueExact()`; migration rejects non-finite doubles. | Extreme typed amount crashes UI; corrupt legacy value blocks startup (no destructive fallback — fail-safe). | Range validation with user error; migration test for NaN/Infinity. | Low | Low-Medium | Money, loans. |

---

## 7. Security and privacy risks

**Preserve verified strengths:** no production secrets in source; publishable key only in Android client; AES/GCM keystore-wrapped refresh token (`SecureTokenStore.kt:14-37`); vault AES-256-GCM/PBKDF2 310k with zeroization (`VaultCrypto.kt:77-265`); RLS ownership per table (`docs/supabase-schema.sql:421-476`); service role confined to Edge Function with JWT + role validation (`supabase/functions/admin-wipe-cloud/index.ts:49-110`); non-exported receivers.

| ID | Module | Source | Current behavior | Problem | Recommended improvement | Priority | Change risk | Dependencies / verification |
|---|---|---|---|---|---|---|---|---|
| S1 | vault | `VaultScreen.kt:506-515,1181-1214` | Destructive "Reset Vault QA data" button in normal UI; deletes cloud vault tables/key and local vault data; no debug guard found. | Irreversible user data loss from a QA affordance; vault rows excluded from normal backup. | Gate to debug/internal or remove; require re-auth + verified encrypted export first. | Critical | Low | Sync, vault, backup. Release intent unverified (no guard found). |
| S2 | vault | `VaultScreen.kt:1227-1237`; docs `vault-security.md:54-64` | Autolock only on `ON_STOP`, skipped while SAF picker open; no idle timeout. Vault plaintext remains in memory. | Long foreground sessions or picker backgrounding expose secrets. | Idle timeout + lock on picker completion; single `lockVault()` helper. | High | Medium | Settings, SAF. Activity timing unverified. |
| S3 | vault | Repo-wide grep; `VaultScreen.kt:24-25,1885-1889`; docs `vault-security.md:7-9,54-64` | No `FLAG_SECURE` anywhere; unused `Window` import/helper. | Docs claim screenshot/recents protection; code has none. | Add lifecycle-scoped `FLAG_SECURE` while unlocked or correct docs. | High | Low-Medium | Activity window. OEM recents unverified. |
| S4 | vault | `VaultScreen.kt:140-141,249-271,308-352,1265-1274` | Biometric/password setup, unlock, change-password methods have no production call sites; only account unlock used. | Documented security controls unavailable to users; dead code. | Wire BiometricPrompt flows or remove claims/dead methods. | High | Medium | Keystore alias, UI. |
| S5 | vault | `VaultScreen.kt:517-565,586-599,665-721,1611-1637` | v3 export includes password-protected payload + raw VMK; `importEncrypted()` accepts only v1–2; `restoreEncryptedBackup()` accepts v2–3 but is not wired into `VaultHome`. | App's own encrypted backup cannot be restored through the exposed UI. | Wire export/restore buttons; fix v3 import compatibility; remove stale copy. | High | Medium | SAF, crypto. |
| S6 | vault / sync | `SupabaseRestClient.kt:345-358`; `docs/vault-security.md:23-36` | VMK Base64 stored in `momentum_vault_keys`; account unlock works without password. | Not E2EE against Supabase operator/admin; documented but high-impact. | Document threat model in UI; consider password-wrapped VMK + cross-device flow. | High | High | Auth, SQL, migration. |
| S7 | vault | `VaultScreen.kt:1735,1750-1758,1842-1851` | TOTP secret shown plainly; no editor field. | Seed as sensitive as password exposed without masking. | Mask by default; add reveal/copy and editor field. | Medium | Low | Vault UI. |
| S8 | vault | `VaultScreen.kt:1857-1883`; `SensitiveClipboardWorker.kt:16-32` | Worker + Handler both clear clipboard after ~30s; Worker receives SHA-256 hash only. | Duplicate mechanisms race/double-clear; handler dies with process. | Keep one primary with worker fallback; cancel duplicate; telemetry. | Medium | Low | WorkManager. OEM behavior unverified. |
| S9 | auth | `SecureTokenStore.kt:14-85` | Refresh token AES/GCM in plain SharedPreferences; Keystore key not user-auth-bound; no AAD/key versioning. | Any process in app sandbox on unlocked device can use key. | Migrate to hardened EncryptedSharedPreferences/MasterKey; AAD binding; rotation plan. | Medium | Medium | Token migration/invalidation. |
| S10 | auth | `SupabaseAuthApi.kt:76-86,234-277` | No single-flight for refresh-token rotation. | Concurrent refreshes can invalidate each other → false session expiry. | Mutex/single-flight + one retry on 401. | High | Medium | Remote rotation config unverified. |
| S11 | auth | `ProfileScreen.kt:191-204`; `SupabaseAuthApi.kt:142,208-217` | Password recovery reads raw `access_token` from fragment; no type/expiry/error validation; intent not cleared. | Crafted links/non-recovery tokens can drive password change; token lingers. | Validate `type=recovery`/expiry, clear intent, use PKCE where supported. | Medium | Medium | Supabase redirect config. |
| S12 | auth | `SupabaseAuthApi.kt:133-139,181-206`; `AuthScreen.kt:178-191`; `ProfileViewModel.kt:74-78` | Signup ignores response; two password policies (6 vs 8+regex); password change re-signs in; Google password set lacks reauth. | Inconsistent state and weaker duplicate path. | Parse response; unify validation; use session-based change with reauth. | Medium | Medium | Auth config. Remote settings unverified. |
| S13 | auth | `ProfileScreen.kt:159-171`; `ProfileRepository.kt:79`; `SupabaseRestClient.kt:80-93`; `docs/supabase/profiles.sql:60-82` | Account deletion RPC only in legacy SQL; storage objects orphaned; local data intentionally kept. | Main schema deploy leaves deletion broken; cloud files remain; users need accurate warning. | Fold RPC into canonical schema; server-side storage cleanup; explicit local-data policy. | High | High | SQL, storage, auth. Remote unverified. |
| S14 | auth/sync | `SupabaseAuthApi.kt:289-320`; `SupabaseSyncRepository.kt:606-640` | Sign-out best-effort POST logout; WorkManager not canceled; local binding retained. | Offline sign-out leaves server session; queued sync fails. | Cancel work; mark pending logout; deterministic local binding choice. | Medium | Medium | WorkManager, auth semantics. |
| S15 | sync | `SupabaseSyncRepository.kt:526-529`; `LocalAppDataReset.kt:38-41` | `sync-first-local-backup.json` persisted indefinitely in `filesDir`. | Durable plaintext PII copy with no user-facing deletion. | Encrypt/rotate/expire; expose deletion. | Medium | Low | Backup. |
| S16 | data/backup | `MomentumBackup.kt:38-183`; `AppSettingsScreen.kt:295-296` | JSON backups contain contacts/PII in cleartext; UI warns generally but not specifically. | Privacy risk when exported/shared. | Optional authenticated encryption; explicit "contacts included" warning; checksum. | High | Medium | SAF, backup compatibility. |
| S17 | sync | `SupabaseRestClient.kt:390-399`; `SupabaseAuthApi.kt:130-131,352-355`; `SupabaseScreen.kt:576-592` | Raw server error bodies (up to 500 chars) surfaced in UI/worker output. | Possible PII leakage in error text. | Map to stable localized messages; never persist raw bodies. | Medium | Low | Error handling. |
| S18 | SQL | `docs/supabase-schema.sql:435-476` | Ownership policies omit `TO authenticated`; no explicit grants/revokes for most `momentum_*` tables. | Larger anon surface; sync can 401/403 if auto-exposure disabled. | Add `TO authenticated`, explicit grants, revoke anon; smoke test. | Medium | Low | Remote project settings unverified. |
| S19 | SQL | `docs/supabase-schema.sql` vs `docs/supabase/profiles.sql` | Two profile schemas/policies; only legacy file has delete RPC; trigger overwrites client `updated_at`. | Deployment drift and duplicate policies. | Single canonical schema; mark legacy; align timestamps. | Medium | Medium | Deployment. |
| S20 | backend | `supabase/functions/admin-wipe-cloud/index.ts` | CORS wildcard on admin endpoint; storage prefix semantics unverified. | Broad cross-origin exposure; potential pagination bug. | Restrict origins; test storage listing. | Low | Low | Deployment unverified. |
| S21 | language | `LanguageTranslator.kt:37-54,92-104`; `LanguageScreen.kt:1762-1784` | Cloud translation attempted first with spoofed UA; ML Kit fallback; UI claims "On-Device / Private & offline capable". | Text leaves device before offline path; privacy misrepresentation. | Offline-first with explicit opt-in for cloud or remove cloud path; update copy. | High | Medium | Privacy policy, ML Kit. Legal ToS unverified. |
| S22 | files/contacts/profile | `FilesScreen.kt:97-108`, `ContactsScreen.kt:297-301`, `ProfileScreen.kt:115-121` | Persistable URI permission failures swallowed; URIs stored anyway. | Broken links after restart; users not informed. | Check result, warn/reject, or copy into app storage. | Medium | Medium | SAF providers. Provider behavior unverified. |

**Logging check:** no `Log.*` call in audited auth/sync paths logs passwords, tokens, emails, or plaintext vault content (verified by grep); main remaining leak vector is raw HTTP error bodies (S17) and raw worker output.

---

## 8. Authentication and synchronization problems

| ID | Module | Source | Current behavior | Problem | Recommended improvement | Priority | Change risk | Dependencies / verification |
|---|---|---|---|---|---|---|---|---|
| AU1 | auth | `MainActivity.kt:182,194,199,209,217,296` | `isAuthenticated` always becomes true; `AuthScreen` unreachable; Profile handles sign-in/out. | Duplicate validation/dead UI; confusing maintenance. | Single auth entry point; delete or route AuthScreen. | Medium | Low | Profile, navigation. |
| AU2 | auth | `AuthRepository.kt:19,38-40`; `ProfileValidation.kt:9-20` | Email/password rules differ between AuthScreen and ProfileViewModel. | Weaker path can resurface. | Unify rules. | Medium | Low | Server config. |
| AU3 | sync | `SupabaseSyncRepository.kt:516-525`; `SyncModels.kt:22-24`; `SyncModelsTest.kt:10-17` | Timestamp/device tie-break only; tombstone tests absent. | Dirty overwrite and delete resurrection (see DB6). | Fix resolver; add dirty/tombstone tests. | Critical | High | All modules. |
| AU4 | sync | `SupabaseSyncRepository.kt:703-709` (`string/nullableLong/nullableInt/bool/deleted`) | Null JSON columns accessed via `jsonPrimitive?.contentOrNull`; PostgREST emits `null` values. | Pull may throw on first row with null column and fail sync repeatedly. | Use explicit `JsonNull` handling; add null-row tests. | High | Low | kotlinx.serialization runtime behavior unverified. |
| AU5 | sync | `SupabaseRestClient.kt:95-104` | Generic upsert sends `Prefer: resolution=merge-duplicates` without `on_conflict`. | PostgREST may reject batch pushes. | Add `on_conflict=id`; integration test. | Medium | Low | PostgREST version unverified. |
| AU6 | sync | `SupabaseSyncRepository.kt:622-630`; `MomentumApplication.kt:30-61` | Only one-time work; no periodic reconciliation; metered-network uploads allowed. | Remote changes can sit indefinitely until app resume; docs overstate. | Periodic reconciliation with constraints; align docs. | Medium | Medium | WorkManager, battery. |
| AU7 | sync | `FileStorageSync.kt:18-45,47-89` | Whole files read into `ByteArray`; tombstoned file metadata never deletes storage object; 404 fails whole sync. | OOM, orphaned objects, brittle sync. | Stream with caps; delete objects on tombstone; tolerate 404. | High | Medium | Storage RLS. |
| AU8 | sync | `SupabaseSyncRepository.kt:45-51,384-412`; `docs/supabase/language.sql` | 17 module tables + profiles synced; language tables have unused SQL; app settings/device identity local-only. | Unclear data ownership; language SQL drift. | Publish ownership matrix; remove/deploy SQL intentionally. | Low | Low | Docs. |
| AU9 | sync | `SupabaseSyncWorker.kt:15-22`; `SupabaseSyncRepository.kt:109-115`; `ProjectWipeManager.kt:20-55` | Wipe-in-progress returns permanent failure; background wipe clears DB/prefs/files but not DataStore; DB singleton may be open during file deletion. | Stuck sync, settings survive wipe, writes to unlinked DB file. | Route settings clear through repository; close DB before delete; delayed retry. | High | High | Room lifecycle, WorkManager. |
| AU10 | auth/sync | `ProfileRepository.kt:23-76`; `ProfileViewModel.kt:49-58,109-123`; `ProfileScreen.kt:418-421` | `SignedIn` sets `pendingChanges=false` without knowing cloud success; fetch/save failures swallowed. | "Synced" pill can be false. | Explicit cloud status in UI state. | Medium | Medium | Network status. |

---

## 9. Performance issues

| ID | Module | Source | Current behavior | Problem | Recommended improvement | Priority | Change risk | Dependencies |
|---|---|---|---|---|---|---|---|---|
| P1 | sync/files | `FileStorageSync.kt:18-44`; `ContactImport.kt:16-27,157-167` | `readBytes()`/`readText()` without size caps; XLSX entries unbounded. | OOM on large files/crafted archives. | Bounded streaming + caps; zip-bomb guards. | High | Medium | Storage, SAF. |
| P2 | files | `FilesScreen.kt:390-469` | Bitmap decode downscaled but `OutOfMemoryError` not caught; text preview capped 1 MB. | Preview crash on large inputs. | Catch OOM, cap sizes, show friendly state. | Medium | Low | UI. |
| P3 | benchmark | `MomentumMacrobenchmark.kt:31-55` | No metrics; hardcoded swipes. | Performance not measurable; false confidence. | Add metrics, stable tags. | Medium | Medium | Device. |
| P4 | UI | Giant composables + literal styling (see U7/U3); `collectAsState` misuse (A2) | Recomposition/state work not lifecycle-scoped; one-off styles. | Jank risk and costly maintenance. | Split components, lifecycle collection, stable keys. | Medium | Medium | All screens. |
| P5 | notifications | `NetworkIndicatorService.kt:117-144,213-216` | Up to 64 high-importance alerts per 15s check on threshold bursts. | Notification flooding, battery/user annoyance. | Collapse into summary/cap fewer. | Medium | Low | Channels. |
| P6 | device features | Battery/network FGS + multiple power receivers (`BatteryScreen.kt`, `MainActivity.kt:250-263`, service receivers) | Four receivers observe power events; diagnostics overwritten. | Redundant wakeups, misleading diagnostics. | Single owner + Flow. | Medium | Medium | FGS policy. |

---

## 10. Missing tests and reliability risks

**Existing:** strong 3→22 migration chain test (`AppDatabaseMigrationTest.kt:45-151`), backup round-trip/legacy tests, 8 vault crypto tests, reminder time calculator tests, validation logic tests. **No tests were executed during this audit.**

| ID | Area | Evidence | Risk | Recommended tests | Priority |
|---|---|---|---|---|---|
| T1 | Notifications | `MomentumReminderReceiverTest.kt:11-15` calls rescheduling with no assertions; habit-create UI test `Phase9UiTest.kt:44-50` no final assertion; vault UI test only checks text. | Critical reminder bugs undetected. | Assert alarms/notifications, persisted rows, real UI outcomes. | High |
| T2 | Sync | `SyncModelsTest.kt:10-17` only non-deleted tie-break. | Dirty overwrite/tombstone regression. | Dirty-vs-remote, deleted-vs-live, null-column JSON, REPLACE child preservation, idempotent duplicate push. | Critical |
| T3 | Account data | No `activateAccount`, WAL, multi-account, `LocalAppDataReset`, `ProjectWipeManager` tests. | Cross-account loss undetected. | Instrumentation tests with two accounts and pending WAL; wipe marker tests. | Critical |
| T4 | Migrations | Migration tests use empty DB for 16→17, 18→19. | Payment/attachment cascade loss undetected. | Data-bearing tests at v16/v18 with loans, payments, attachments. | High |
| T5 | Backup | Round-trip test codifies schema 20; no v22 language-table coverage. | Language data loss. | v4 backup round-trip incl. v22 tables; legacy v2/v3 import; partial-failure settings test. | High |
| T6 | Auth | No auth/session/token/account deletion tests. | Sign-out/data-linking regressions. | Session restore matrix, optional local use, token rotation single-flight, signup states. | High |
| T7 | Financial logic | No loan balance/payment or `computeStreaks` tests; README claims streak tests. | Wrong balances, false docs. | Unit tests for balances, minor-unit conversions, streaks. | High |
| T8 | Permissions/device | No permission-denied, boot, quiet-hours, background-limit tests. | Feature silently broken. | Permission rules, boot/time receiver assertions, denied-permission UI tests. | High |
| T9 | Compose UI/accessibility | No semantics/contrast/large-font/narrow-screen tests; previews only fontScale 1.3. | Accessibility regressions. | Compose tests with test tags, 360dp/2×/dark previews, TalkBack/manual device checklist. | Medium |
| T10 | CI | No connected tests; detekt gate outcome unverified; benchmark not run. | Broken gate / false status. | CI connected-test job with emulator; artifacts and reports. | High |

---

## 11. Documentation inconsistencies

| Doc | Stale/contradicted claim | Verified current state |
|---|---|---|
| `README.md:3-7` | Account required to open app; "single Gradle module". | Local mode works (`MainActivity.kt:194`); two modules (`settings.gradle.kts:16-17`). |
| `README.md:16-17` | Biometric unlock + auto-lock protections described. | Methods UI-dead; autolock partial (S2/S4). |
| `README.md:77` (per CI audit) | Streak unit tests claimed. | No `computeStreaks` test found. |
| `docs/sync-design.md:6-7` | Sign-in optional; unique WorkManager chain. | Sign-in optional in code; chain is one-time only (no periodic). |
| `docs/database-launch-recovery.md`, `docs/phase-2-data-and-release.md`, `docs/phase-8-supabase-sync.md`, `docs/phase-7-new-features.md`, `NOTES.md`, `CHANGELOG.md` | Room v11/v12/v20 references. | Room v22 with schemas through `22.json`. |
| `docs/redesign-audit.md`, `docs/redesign-decisions.md`, `docs/redesign-progress.md` | No Profile/Language routes, schema v20/"not generated", repeated AGP blocker. | Profile/Language exist; `22.json` exists; later progress notes indicate work continued. |
| `docs/vault-security.md` | Screenshot/recents protection, biometric/change-password support. | No `FLAG_SECURE`; methods not wired. |
| `docs/phase-6-release-verification.md`, `docs/phase-5-usability.md`, `docs/phase-7-new-features.md` vs `docs/redesign-verification.md` | "Passed" build/lint claims conflict with later failure notes. | All pass/fail claims unverified in this audit — no build executed. |
| Six `phase-*` doc pairs | Duplicate overlapping scope, one copy updated. | Consolidate or mark superseded. |
| `docs/supabase-setup.md:124` | "All Room records synchronized". | Language/profile/device/app settings not synced; backup excludes language. |

---

## 12. Duplicate or unnecessary functionality

| Item | Source | Verified behavior | Recommendation | Priority |
|---|---|---|---|---|
| Two auth UIs | `AuthScreen.kt` vs `ProfileScreen.kt` | Different validation; AuthScreen unreachable. | Keep Profile path; remove/dead-code AuthScreen. | Medium |
| Two budget stores | Room `category_budgets` vs DataStore `money_limit_*` | Room synced/backed up but UI reads DataStore. | One source of truth; write-through if both needed. | High |
| Dead Phase7 logic | `Phase7Logic.kt:17-97` + tests | No production call sites; docs claim coverage. | Wire into features or remove/mark experimental. | Medium |
| Vault QA reset | `VaultScreen.kt:506-515` | Destructive production affordance. | Debug-only/remove. | Critical |
| Duplicate clipboard clearing | `VaultScreen.kt:1857-1883` + `SensitiveClipboardWorker.kt` | Two mechanisms. | One primary + fallback. | Medium |
| Duplicate profile SQL | `docs/supabase/supabase-schema.sql` vs `docs/supabase-schema.sql`/`profiles.sql` | Conflicting policies/RPC. | Single canonical schema. | Medium |
| Duplicate power receivers | Battery service/screen/activity/MainActivity | Multiple diagnostics writes. | Centralize. | Medium |
| Legacy pref backup key | `MomentumSettingsRepository.kt:105-124` | Exports `"budget"` with no reader. | Import-only migration or remove. | Low |
| `SupabaseProvider.createClientOrNull()` | `SupabaseProvider.kt:15-33` | No call sites; weak URL validation. | Remove or use consistently; strict host parsing. | Low |
| `showAllModules()` recovery | `MomentumSettingsRepository.kt:64-70` | Exists but no UI call site. | Expose in Settings. | Medium |

---

## 13. Missing useful features

| Feature | Why useful | Depends on | Priority |
|---|---|---|---|
| Card-based first-install onboarding with task progress, dismiss/snooze, revisit from Settings | Requested high-priority UX; fresh install currently lands on empty dashboard/permission gate | Settings DataStore, navigation, Home | High |
| Contextual permission cards with accurate status, "remind later"/"don't show again", return-from-settings refresh | Requested; current single setup screen cannot express per-feature readiness | Permission checks, lifecycle | High |
| Signed-out cloud-backup card with exact local/cloud/sync wording | Requested; current messaging split between Profile/Cloud screens and absent in Settings | Auth state, Home | High |
| Safe account-linking/first-sync flow | Prevents offline changes being stranded in `unassigned-legacy-database.db` | `activateAccount`, sync resolver | Critical |
| Loan outstanding balance = principal − payments | Recorded payments currently ignored | DAO, loans UI | High |
| Vault encrypted export/restore reachable in UI + idle autolock + FLAG_SECURE | Requested; code partially exists | Vault UI, crypto | High |
| Backup coverage for v22 language data and optional encryption | User data currently omitted from backups | Backup format, crypto | High |
| Periodic sync reconciliation + explicit sync status/pending counts | Remote changes otherwise stale; UI can show false "0 pending" | WorkManager, sync state | Medium |
| Global search / "Show all modules" recovery | Users can hide modules with no in-app recovery | Settings/navigation | Medium |
| Adaptive/narrow-screen, large-font, reduced-motion design polish | Requested premium/accessibility goal | Design system | Medium |
| CI connected tests, artifacts, benchmark metrics | Reliability claims currently unverifiable | CI/device | High |

---

## 14. High-priority bugs and risks (immediate attention)

| Rank | Issue | Source | Impact |
|---:|---|---|---|
| 1 | Account switch/cold-start WAL copy can lose committed local rows | `Data.kt:939-976` | Irreversible local data loss |
| 2 | Full reset deletes all account DB copies under `filesDir` | `LocalAppDataReset.kt:38-41` | Cross-account irreversible loss |
| 3 | Sync resolver ignores dirty flag and local deletion | `SupabaseSyncRepository.kt:516-525` | Local edits overwritten / tombstones resurrected |
| 4 | REPLACE upserts cascade-delete child records | `Data.kt:392-399,569-572,705` | Completions/payments/attachments lost on remote update |
| 5 | Production "Reset Vault QA data" button | `VaultScreen.kt:506-515,1181-1214` | Irreversible vault loss |
| 6 | Backup schema 20/excludes four v22 tables | `MomentumBackup.kt:19-24,168-177` | Silent data omission in backups |
| 7 | One-time notification cancelled immediately; weekly mask off-by-one | `CustomNotificationScheduler.kt:118-131`; `CustomNotificationScreen.kt:264` | Missed/wrong-day reminders |
| 8 | Translation cloud-first despite "private/offline" UI | `LanguageTranslator.kt:37-54`; `LanguageScreen.kt:1762-1784` | Privacy misrepresentation |
| 9 | Vault FLAG_SECURE/biometric/export-restore gaps | `VaultScreen.kt` + `docs/vault-security.md` | Security controls claimed but absent/unreachable |
| 10 | Refresh-token race and raw error body exposure | `SupabaseAuthApi.kt:76-86,234-277`; `SupabaseRestClient.kt:390-399` | False sign-outs / PII in errors |

---

## 15. Audit limitations / unverified items

- No Gradle build, lint, detekt, unit test, instrumentation test, benchmark, or device test was run. Every historical "passed" claim in existing docs is unverified for this branch.
- Runtime `PRAGMA foreign_keys` semantics inside Room migrations (DB1/DB2) and actual WAL contents during cold-start account switch (DB3) cannot be proven statically.
- Remote Supabase settings (confirmations, token rotation, grants, deployed RPC/Edge Function, RLS at runtime) are unverified; local `supabase/config.toml` and SQL files were inspected.
- Google Play policy outcomes for `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, accessibility service, and `FOREGROUND_SERVICE_SPECIAL_USE` are unverified.
- OEM behavior for background FGS start, exact alarms, clipboard clearing, and SAF persistence is unverified.
- Visual contrast at 360dp/large fonts and TalkBack reading order are unverified without device runs.

---

**Next deliverables:** `01-implementation-roadmap.md`, `module-prompts/*.md`, `02-data-safety-and-security-review.md`, `03-testing-and-acceptance-checklist.md`, `04-critical-issues-immediate-attention.md`.
