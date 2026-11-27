# Zylo Modernization — Critical Issues for Immediate Attention

> **Historical audit record:** This file preserves the original read-only triage and its audit-time status. It must not be read as the current defect status. Source-level disposition and executed evidence are recorded in `15-m13-performance-accessibility-final-qa-contract.md`; device, OEM, remote and CI behavior remains Unverified where no execution record exists.

**Status:** Read-only audit findings. No issue below was fixed during that original audit. No tests were run during that audit.
**Ordering:** Data-loss and irreversible-destruction risks first, then security/privacy and correctness. Rank mirrors `00-complete-repository-audit.md` section 14.
**Rule:** Do not start visual modernization until ranks 1–5 have failing-then-passing tests and executed fixes on an implementation branch.

---

## 1. Account switch / cold-start WAL copy can lose committed local rows
- **Severity:** Critical — irreversible local data loss.
- **Evidence:** `app/src/main/java/com/faheem/habits/core/data/Data.kt:939-976` (`AppDatabase.activateAccount`): checkpoint result ignored; main DB copied without WAL/SHM on cold start; previous account copy overwritten non-atomically.
- **Impact:** Committed habit/expense/vault/language rows can vanish or land under the wrong account. Runtime WAL contents unverified.
- **Immediate containment:** Do not test account switching on production data; snapshot test devices. Add two-account WAL-pending instrumentation test before changing code.
- **Fix:** Require successful checkpoint; SQLite backup API or atomic temp-file replacement with rollback; keep DB/active-user marker consistent.
- **Verify:** Cold/warm switch, checkpoint failure, copy failure rollback, previous account intact — all executed.
- **Owner prompt:** 21 (coordinate 16/17).

## 2. Full reset deletes every account database
- **Severity:** Critical — cross-account irreversible loss.
- **Evidence:** `core/data/sync/LocalAppDataReset.kt:20-49`: recursive delete of `filesDir` (contains `account-databases/`), `cacheDir`, `noBackupFilesDir` and external dirs; DataStores not cleared by default callers.
- **Impact:** Other accounts' DBs and durable local-only data destroyed; WorkManager/settings desynchronization.
- **Immediate containment:** Remove/reset-disabled in test builds until scoped; never invoke on real profiles.
- **Fix:** Active-account scope only; synced-files allowlist; clear both DataStores; close DB before deletion; cancel/reschedule jobs.
- **Verify:** Two-account reset test; other DB byte-identical; prefs/DataStore/work state asserted.
- **Owner prompt:** 17 (coordinate 15/21).

## 3. Sync resolver ignores dirty state and local deletion
- **Severity:** Critical — local edits overwritten / deletes resurrected.
- **Evidence:** `core/data/sync/SupabaseSyncRepository.kt:516-525`; `SyncModels.kt:22-24`: `dirty` parameter unused; local candidate hardcodes `deleted=false`; tie-break only by timestamp/device.
- **Impact:** Unsynced edits lost; locally deleted records reappear; false conflict counts.
- **Immediate containment:** Avoid relying on sync for dirty devices; add failing unit tests first.
- **Fix:** Real dirty/deleted candidates; explicit tombstone policy; conflict record; null-JSON handling; cursor only advances on success.
- **Verify:** Dirty-vs-clean, tombstone both directions, equal timestamp, `JsonNull`, idempotent pull tests.
- **Owner prompt:** 17.

## 4. REPLACE upserts cascade-delete child records
- **Severity:** Critical — silent child-data loss.
- **Evidence:** `Data.kt:392-399,569-572,705` and other `@Insert(REPLACE)` DAOs; cascade FKs at `:45-49,66-69,85-89,187,206-210,303`; sync callers at `SupabaseSyncRepository.kt:414-506`.
- **Impact:** Remote parent update deletes habit completions/skips/reminders, loan payments/attachments or life-rule files.
- **Immediate containment:** Add child-count regression tests before any sync upsert change.
- **Fix:** Targeted update-then-insert/`@Upsert` preserving local PK/UUID/FK identity.
- **Verify:** Child rows survive by UUID/id and count for every affected parent; remote-before-child case handled.
- **Owner prompt:** 21 (coordinate 17).

## 5. Production "Reset Vault QA data" button
- **Severity:** Critical — irreversible vault loss.
- **Evidence:** `features/vault/VaultScreen.kt:506-515,1181-1214`; no `BuildConfig.DEBUG` guard found. Vault rows excluded from normal backup.
- **Impact:** User can delete cloud vault/key and local vault state from a QA affordance; unrecoverable.
- **Immediate containment:** Remove/gate immediately; do not ship release with the control.
- **Fix:** Debug/internal-only or deleted; require re-auth and verified encrypted export before any destructive vault action.
- **Verify:** Release-configuration Compose/instrumentation test asserts control absent.
- **Owner prompt:** 11.

## 6. Backup schema 20 and four excluded v22 tables
- **Severity:** High/Critical impact — silent backup omission.
- **Evidence:** `core/data/backup/MomentumBackup.kt:19-24,168-177,477-479`; `Data.kt:1396-1407`: `vocabulary_words`, `grammar_notes`, `review_states`, `translation_history` absent; JSON plaintext PII.
- **Impact:** Language study data not backed up; new backups invalid as Room advances; contacts/PII exposed in exports.
- **Immediate containment:** Tell users backup does not include language/vault/file contents; do not rely on current backups for language data.
- **Fix:** v4 with compatibility matrix, all user-data tables, `sync_state` reset on replace, optional encryption, checksum.
- **Verify:** v2/v3/v4 round-trip incl. language, stale cursor reset, no vault plaintext in export.
- **Owner prompt:** 18.

## 7. One-time notifications cancelled; weekly weekday mask off-by-one
- **Severity:** High/Critical reliability — missed/wrong-day reminders.
- **Evidence:** `core/notifications/CustomNotificationScheduler.kt:118-131` posts then cancels; `CustomNotificationScreen.kt:264` vs `CustomNotificationTimeCalculator.kt:30-37` weekday bit mismatch; `NotificationSystem.kt:341-378,454-493` quiet hours discard.
- **Impact:** Reminder never visible, fires on wrong day, or silently cleared.
- **Immediate containment:** Avoid depending on one-time/weekly custom reminders until fixed; add failing calculator/delivery tests.
- **Fix:** Keep notification until user action; single weekday mapping with additive stored-mask repair; defer blocked reminders.
- **Verify:** All seven weekdays, one-time retention, quiet-hour retry, boot/time change tests executed.
- **Owner prompt:** 14.

## 8. Translation is cloud-first while UI claims private/on-device
- **Severity:** High — privacy misrepresentation.
- **Evidence:** `features/language/LanguageTranslator.kt:37-54,92-104` sends text to Google with spoofed UA before ML Kit; `LanguageScreen.kt:1761-1784,2229` claims on-device/private/offline.
- **Impact:** Personal text leaves device without informed consent; provider terms unverified.
- **Immediate containment:** Stop claiming private/offline in docs/UI until copy is corrected; do not add more cloud paths.
- **Fix:** Offline-first with explicit opt-in or prominent disclosure; remove UA spoofing; expose engine source.
- **Verify:** Translator decision-matrix and disclosure-copy tests; device network observation if used as evidence.
- **Owner prompt:** 19.

## 9. Vault security controls absent or unreachable
- **Severity:** High — exposed secrets/lock bypass risk.
- **Evidence:** No `FLAG_SECURE` repo-wide; `VaultScreen.kt:1227-1237` autolock only on `ON_STOP`, skipped during SAF; `:140-141,249-271,308-352` biometric/password methods UI-dead; `:517-721` v3 export not consumable by exposed import; cloud VMK escrow documented in `docs/vault-security.md`.
- **Impact:** Screenshots/recents or long foreground sessions can expose plaintext; documented controls mislead users; not E2EE against operator.
- **Immediate containment:** Do not claim biometric/auto-lock/screenshot protection; mask TOTP.
- **Fix:** Lifecycle `FLAG_SECURE`, idle/picker lock, wire or remove biometric/password flows, v3 round-trip, accurate threat model in UI.
- **Verify:** Device screenshots/recents, autolock, encrypted export restore, no plaintext in logs/backups.
- **Owner prompt:** 11.

## 10. Token refresh race and raw error-body exposure
- **Severity:** High — false sign-outs and possible PII leakage.
- **Evidence:** `core/data/supabase/SupabaseAuthApi.kt:76-86,234-277` no refresh single-flight; `SupabaseRestClient.kt:390-399` embeds up to 500 chars raw body; `SupabaseAuthApi.kt:130-131,352-355`; `SupabaseSyncWorker.kt:35-36`; `SupabaseScreen.kt:528`.
- **Impact:** Concurrent refresh can invalidate sessions if rotation is enabled (remote unverified); raw server text can contain PII in UI/worker state.
- **Immediate containment:** Do not display raw errors; avoid concurrent sign-in/refresh during testing.
- **Fix:** Mutex single-flight + one 401 retry; hardened token storage; stable localized error categories; no raw-body persistence.
- **Verify:** Concurrent refresh test, session matrix, grep/CI check for sensitive values.
- **Owner prompt:** 16 (coordinate 17).

---

## Also high, but behind the top ten

| Issue | Evidence | Action |
|---|---|---|
| Data-bearing migrations 16→17 / 18→19 may cascade-delete payments | `Data.kt:1298-1315,1350-1377`; tests use empty DB | Add data-bearing tests; repair migration if needed; never rewrite shipped SQL (prompt 21) |
| SAF persistable-permission failures swallowed | `FilesScreen.kt:97-108`, `ContactsScreen.kt:297-301`, `ProfileScreen.kt:115-121` | Surface/reject; no broken links silently (prompts 08/09/16) |
| Unbounded file reads / zip entries | `FileStorageSync.kt:18-45`, `ContactImport.kt:16-27,157-167` | Bounded streaming + caps (prompts 08/09/17) |
| SQL drift: duplicate schemas, deletion RPC only in legacy file, policies omit `TO authenticated` | `docs/supabase-schema.sql`, `docs/supabase/profiles.sql`, `docs/supabase-wipe-state.sql` | Canonical schema + grants + staging smoke (prompt 17) |
| Hidden modules cannot be recovered in UI | `showAllModules()` at `MomentumSettingsRepository.kt:64-70` has no call site | Expose in Settings (prompt 15) |
| CI does not run connected tests/benchmarks; detekt baseline large | `.github/workflows/android.yml`, `config/detekt-baseline.xml` | Add jobs/artifacts/ratchet; report only executed results (prompt 22) |
