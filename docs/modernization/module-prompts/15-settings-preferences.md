# Module Prompt 15 — Settings & Preferences

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 2, 4 (U6), 6 (DB11/DB12), 7 (S15/S16), 10 (T5/T9), 12, 13 and this prompt. Settings are tied to backup, wipe and account state: no destructive clears, no raw error/PII display, preserve existing keys through migration. Report only tests actually executed.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/features/settings/AppSettingsScreen.kt` — Account/Appearance/Backup/Preferences groups (`:88-235`), SAF export/import (`:98-172`), nested permission/reminder views (`:173-183`), warnings (`:285-296`).
- `SettingsViewModel.kt` — state and `showAllModules()` (`:15-45`), `clearForProjectWipe()`.
- `PermissionsSetupScreen.kt` — permission cards and resume refresh (`:74-210`).
- `core/settings/MomentumSettingsRepository.kt` — DataStores `momentum_app_settings` and `momentum_money`, SharedPreferences migration, key mapping/backup (`:23-169`).
- `core/settings/AppearancePreferences.kt` — SharedPreferences `momentum_appearance` (`:26-79`).
- `core/data/backup/MomentumBackup.kt`, `WeeklyBackupWorker.kt` — owned by prompt 18; UI consumes them.
- `core/data/Data.kt:347-361` and `features/money/data/ExpenseRepository.kt:15-19` — Room versus DataStore budget stores.
- `app/MainActivity.kt:153-309`, `features/profile/ProfileScreen.kt` — account/sign-in status; `features/supabase/SupabaseScreen.kt` — cloud/sync status.

## 2. Existing functionality (verified)
- Settings groups are Account, Appearance, Backup and Preferences; permissions/reminders are nested full-screen views with `BackHandler`.
- Export/import uses SAF `CreateDocument`/`OpenDocument` with a preview explaining record counts, merge/replace, plaintext JSON, and vault/file exclusions.
- Two DataStores migrate from older SharedPreferences (`momentum_app_settings`, `momentum`); keys include `permission_setup_seen`, `module_enabled_*`, `budget_minor_units`, `loans_account_balance_minor_units`, `money_limit_*_minor_units`.
- `clearForProjectWipe()` clears both DataStores; `showAllModules()` exists in repository and ViewModel.
- Appearance is still in SharedPreferences (`theme_mode`, `reduce_motion`, `dynamic_accent`, `glass_intensity`) with in-memory StateFlows, wired by `MainActivity`.
- Weekly backup worker exists but no production call site enables its folder/schedule.

## 3. Problems discovered
- `showAllModules()` has no UI call site; a user who hides every module cannot recover them in-app.
- Category budgets have two sources of truth: Room `category_budgets` is synced/backed up, but the money UI reads DataStore `money_limit_*`. A legacy `budget` key is exported with no reader.
- Appearance preferences are outside the settings backup mapping, so theme/motion choices do not follow app backups.
- `AppSettingsScreen.kt` launches SAF work from composable callbacks and surfaces raw `e.message`; raw server/system text can leak detail.
- Backup preview warns generally but does not explicitly call out contacts/PII inclusion, current language-table omission, plaintext exposure, or stale sync cursors after replace.
- Settings surfaces do not clearly distinguish signed-out local-only mode from cloud backup/sync availability; overlay permission copy is wrong (prompt 03/13).
- `clearForProjectWipe()` alone does not cancel/reschedule WorkManager jobs (audit DB4/AU9).

## 4. Recommended UI improvements
- Re-group into Appearance, Notifications, Privacy & Security, Data & Backup, Cloud & Sync, Screen Time & Device, Account, About.
- Add "Show all modules" recovery and signed-in/local-only status subtext with exact wording: local data available; no cloud backup when signed out; sync unavailable; backup failure.
- Show weekly-backup controls if the worker is wired: SAF folder, enable/disable, last-backup time, retention count.
- Make backup preview list included/excluded sections, language coverage, contacts/PII warning, plaintext risk, file-content limitation, and merge/replace semantics.
- Revisit onboarding and permission cards from Settings with verified status; correct overlay purpose.
- Use typed error categories instead of raw exception messages.

## 5. Recommended backend and architecture improvements
- Migrate appearance into DataStore with a first-frame cache to preserve startup theme; add migration and backup keys; keep existing preference names readable.
- Choose one authoritative budget store. If both remain, write through transactionally and migrate existing values; remove the legacy `budget` export unless used for import migration.
- Move SAF/backup orchestration to a ViewModel/repository with typed `Loading/Success/Error` states; validate backup before applying settings.
- Coordinate DataStore clearing with WorkManager cancel/reschedule and project wipe; never clear settings independently of account/sync state.
- Keep settings and module visibility local unless explicitly designed for cloud ownership; do not make sign-in mandatory.

## 6. Useful features to add
- Global search across habits/tasks/expenses/contacts/files/life rules with permission-aware scope and no PII in logs.
- Settings search or "recently changed" summary.
- Backup checksum/verified restore receipt and optional encryption (prompt 18).

## 7. Unnecessary elements to simplify or remove
- Legacy `budget` key if no reader remains.
- Duplicate permission/reminder surfaces once card-based onboarding is available from Home.
- Raw `e.message` display and duplicated backup warnings.
- The weekly-backup code should be wired or removed; dead configuration is not a feature.

## 8. Data safety requirements
- Preserve all existing SharedPreferences→DataStore migration sources and keys; no destructive reset of visibility, budgets, appearance or language state.
- Settings import must not partially apply when Room restore fails; pre-validate and stage changes.
- `clearForProjectWipe()` must remain part of the coordinated wipe flow; coordinate with prompts 01/17 and WorkManager.
- Backups are plaintext JSON containing contacts/PII; UI must say so explicitly. Never claim vault or file contents are included.

## 9. Security considerations
- Do not log or display raw server bodies, exception messages with paths/URIs, tokens or user content.
- Permission state must be re-evaluated on resume and never claimed without verification.
- Search must not load vault plaintext or sensitive fields into logs/telemetry.
- No new cloud upload of settings without an explicit ownership decision and RLS review.

## 10. Required tests
- DataStore migration tests from `momentum_app_settings`, `momentum` and `momentum_appearance`; backup key-set tests including appearance.
- Visibility recovery test invoking `showAllModules()` from the UI.
- Budget tests proving one source of truth after export/import, sync and UI edits.
- Compose tests for grouped settings, SAF preview copy, permission-denied states, 360dp and 2× font.
- Settings/DataStore failure test proving no partial apply when Room restore fails. No tests were executed in this audit.

## 11. Dependencies on other modules
- Prompt 18 backup format, prompt 17 sync/wipe/work, prompt 03 onboarding, prompt 14 reminders, prompts 13/12 device permissions, prompt 16 auth status, prompt 20 design system, prompt 21 data layer.

## 12. Acceptance criteria
- Hidden modules recoverable; one budget source; appearance backed up; weekly backup either functional or removed; no destructive settings clear.
- Signed-out/local status and backup warnings are accurate; no raw errors or PII leakage.
- All executed tests pass.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` and `core/data/backup/MomentumBackup.kt` (prompts 21/18).
- `AndroidManifest.xml` permission set.
- `core/notifications/ZyloNotifications.kt` (prompt 14).
- Existing SharedPreferences/DataStore names and keys without migration tests.
- `core/data/sync/*` wipe/reset semantics (prompt 17).
