# Module Prompt 17 — Supabase Sync & Cloud Backend

Standalone OpenCode prompt. This is the highest-risk module. Implement only on a dedicated branch with a verified local backup of test devices. Read audit sections 6 (DB4–DB8), 7 (S1/S6/S14–S19), 8 (AU3–AU9), 10 (T2/T3/T10), 12, 13 and this prompt. Never overwrite dirty local edits, resurrect tombstones, delete other accounts' databases, or weaken RLS. Remote Supabase behavior is unverified until smoke-tested. Report only tests actually executed.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/core/data/sync/SupabaseSyncRepository.kt` — `sync()` `:109-304`, reset paths `:330-381`, upserts `:414-506`, `accept()` `:516-525`, JSON helpers `:703-709`, enqueue `:622-630`.
- `SyncModels.kt:22-24` (`winsOver`), `SyncModuleSettings.kt:9-39`, `SupabaseSyncWorker.kt:15-36`, `FileStorageSync.kt:18-89`.
- `LocalAppDataReset.kt:20-49`, `ProjectWipeManager.kt:20-55`, `DeviceIdentity.kt`.
- `core/data/supabase/SupabaseRestClient.kt` — profile `:52,71-75`, deletion RPC `:80-93`, generic upsert `:95-104`, pull `:106-129`, deletes `:131-143`, wipe state `:180-212`, Storage `:242-264`, error wrapper `:390-399`.
- `SupabaseAuthApi.kt:289-337`, `SecureTokenStore.kt`, `SupabaseProvider.kt`.
- `core/data/Data.kt` — v22 schema (`:910-914`), migrations (`:988-1442`), `@Insert(REPLACE)` sync upserts (`:392-399,569-572,705,...`), `SyncState`.
- Triggers: `MomentumApplication.kt:30-61`, `MainActivity.kt:184,298`, `features/supabase/CloudSyncRepository.kt:60`, `CloudSyncViewModel.kt:43-48`, `features/auth/AuthRepository.kt:58-61`.
- UI: `features/supabase/SupabaseScreen.kt:525-592,844-849`, `features/settings/AppSettingsScreen.kt`.
- Backend: `docs/supabase-schema.sql:8-39,421-501`, `docs/supabase-wipe-state.sql`, `docs/supabase/profiles.sql`, `docs/supabase/language.sql`, `supabase/functions/admin-wipe-cloud/index.ts:3-165`.
- Tests: `SyncModelsTest.kt:10-17`.

## 2. Existing functionality (verified)
- Sync covers `profiles` plus 17 `momentum_*` tables with nine module toggles, keyset pull (200/page) and push (100/batch), guarded by a process mutex.
- WorkManager triggers are one-shot: Room invalidation debounce, app start, session restore/sign-in, manual refresh, module toggle, queued module delete. No periodic sync worker exists.
- Conflict tie-break is newer `updatedAt`, then larger `deviceId`. Push marks rows clean only when local `updatedAt` is unchanged.
- A first-sync backup is written once to `filesDir/sync-first-local-backup.json`; wipe state is checked before sync.
- RLS ownership policies and a wipe guard are present in the canonical SQL; an Edge Function performs admin cloud wipes with JWT/role checks.
- Storage sync uploads file bytes and restores unreadable local URIs into `filesDir/synced-files`.

## 3. Problems discovered
- `accept()` receives `dirty` but never reads it, and hardcodes local `deleted=false` (`SupabaseSyncRepository.kt:516-525`): unsynced local edits can be overwritten by a newer clean remote row, and local tombstones can be resurrected.
- Sync upserts use `@Insert(REPLACE)` on parents with `ON DELETE CASCADE` children: remote habit/loan/life-rule updates can delete local completions, reminders, payments and attachments.
- `LocalAppDataReset.kt:38-41` recursively deletes `filesDir`, which contains `account-databases/`; a reset can irreversibly delete other accounts' databases. It also clears most SharedPreferences but not both DataStores by default.
- `queueModuleDelete` keys pending prefs by the *current* user id and drains using the current user again; cross-account sign-out/in can orphan or misapply deletes. `commit()` results are unchecked.
- `SupabaseSyncRepository.kt:703-709` accesses nullable JSON with `?.jsonPrimitive`; explicit JSON `null` can throw and fail sync repeatedly.
- `SyncRunResult.pending = 0` is hardcoded (`SupabaseSyncRepository.kt:304`) while UI reads separate `sync_state` counts; status can be false. Failed modules do not refresh state rows.
- Only one-shot work exists; remote-only changes can remain indefinitely until an app event or manual action. Docs claim periodic sync.
- `SupabaseRestClient.kt:95-104` sends `Prefer: resolution=merge-duplicates` without `on_conflict`; PostgREST behavior is unverified.
- `FileStorageSync.kt` reads whole files into memory, aborts the file phase on one unreadable URI, ignores 404 on download, and never deletes Storage objects for tombstones.
- `ProjectWipeManager` uses `firstInstallTime` as clean-install exemption and has unchecked `commit()` calls; restored pre-wipe backups may escape a wipe.
- Raw HTTP error bodies up to 500 characters are embedded in exceptions, worker state and UI (S17).
- SQL files are duplicated: wipe-state and profile schemas appear twice; `delete_my_account` only exists in legacy `profiles.sql`; owner policies omit `TO authenticated` and grants for most `momentum_*` tables. Admin Edge Function uses wildcard CORS.
- `docs/supabase/language.sql` defines language tables that the app does not sync.

## 4. Recommended UI improvements
- Per-module last successful sync, pending/conflict counts and queued-deletion state sourced from the same persisted state used by the worker.
- Distinguish waiting-for-network, blocked-by-wipe, local-only/signed-out, and sanitized failure states; no raw server text.
- Warn when a queued module delete targets a different signed-in account and require explicit confirmation.
- Add storage health row: unreadable local URIs, missing remote objects, tombstoned objects pending deletion.
- Manual "check for remote changes" action until periodic sync is implemented.

## 5. Recommended backend and architecture improvements
- Pass real local `dirty` and `deleted` into the resolver; define and test tombstone policy: deletes win over clean rows, strictly newer dirty edits win over tombstones, explicit conflict record otherwise.
- Replace `REPLACE` sync upserts with targeted update-then-insert/`@Upsert` semantics that preserve local PKs, UUIDs, child rows and FK identity.
- Scope reset/wipe to active account: never recursively delete all `filesDir`; clear both DataStores; cancel/reschedule WorkManager; close DB before file operations.
- Key pending deletes by immutable originating account id, verify identity before draining, check all commits, and clear on account switch.
- Add bounded periodic reconciliation with network/battery constraints while retaining one-shot triggers.
- Add per-table `on_conflict` targets or a documented single conflict target; handle explicit `JsonNull`; recompute pending/conflict counts.
- Stream Storage uploads with size caps, isolate per-file failures, tolerate 404, delete tombstoned objects.
- Consolidate to one canonical SQL schema with `TO authenticated`, explicit grants/revokes, deletion RPC, storage cleanup and restricted admin CORS. Verify against a staging project; keep deployment out of public PR CI.
- Sanitize all error bodies; persistence should contain stable categories only.

## 6. Useful features to add
- Field-level conflict report with keep-local/keep-cloud actions.
- Wi-Fi-only sync option, per-file skip list and sync audit-log export (sanitized).
- Verify-and-repair tool comparing local UUIDs, cloud rows and Storage objects.
- Periodic sync interval setting with accurate docs.

## 7. Unnecessary elements to simplify or remove
- Duplicate SQL in `docs/supabase-wipe-state.sql` and `docs/supabase/profiles.sql` after canonicalization.
- `docs/supabase/language.sql` if language remains local-only.
- Unused `deleteCloudDataUpdatedToday`/`deleteCloudTableUpdatedToday` helpers after call-site verification.
- Raw error-body logging and indefinite `sync-first-local-backup.json` retention (coordinate prompt 18).

## 8. Data safety requirements
- Never push a local tombstone as undeleted or pull a cloud tombstone over a newer dirty local edit.
- Preserve child rows on parent updates; any destructive local operation must be transactional, account-scoped, explicitly opt-in and reversible.
- Create the first-sync backup before any cloud write that can overwrite local data; encrypt/expire it and expose deletion (prompt 18).
- Apply wipe before sync resumes; cloud wipe must preserve auth users.
- Keep vault ciphertext-only sync; never log plaintext vault payloads, tokens or response bodies containing PII.
- Add failing tests before fixing; never edit shipped Room migration SQL.

## 9. Security considerations
- Preserve `auth.uid()` RLS ownership per table plus restrictive wipe guards; add `TO authenticated`, grants and anon revokes after verification.
- Service-role key must remain inside the Edge Function only; keep JWT + role validation; narrow CORS.
- Storage prefix/owner policies must be verified remotely (`loan-people/`, `contacts/`, `loan-attachments/`, `life-rule-files/`).
- Token refresh race belongs to prompt 16; coordinate but do not weaken encryption here.
- Remote deployment, RLS behavior and PostgREST semantics are unverified until smoke-tested.

## 10. Required tests
- Resolver matrix: dirty local vs newer clean remote, local tombstone, remote tombstone, equal timestamp, null columns, duplicate/idempotent pull, cursor advance only on success.
- Parent-child preservation tests for habits, loans, life rules and to-dos under remote updates.
- Two-account reset and queued-delete tests; account switch while delete pending.
- Wipe state transitions, clean-install exemption, failed `commit()`, DataStore clearing and DB closed before delete.
- Storage tests: unreadable URI, oversized file, 404, tombstone object cleanup.
- WorkManager tests: periodic enqueue/dedup, one-shot triggers, sanitized error bodies, wipe-in-progress retry.
- Remote staging smoke: RLS select/insert/update/delete by owner and non-owner, grants, deletion RPC, admin 401/403/409/CAS. None were run in this audit.

## 11. Dependencies on other modules
- Prompt 18 backup/first-sync contract, prompt 21 Room schema/DAOs, prompt 16 auth/account identity, prompt 11 vault ciphertext/key, prompts 06–12 module schemas, prompt 15 settings toggles/wipe clearing.

## 12. Acceptance criteria
- Conflicts preserve dirty edits and tombstones; parent updates preserve children; reset is account-scoped; queued deletes reach the originating account.
- Periodic reconciliation works; status counts match persisted state; files are bounded and tombstoned objects cleaned.
- One canonical SQL set with verified RLS/grants; no raw error bodies or PII in logs/UI.
- All executed tests pass; remote claims marked verified only after staging smoke test.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` shipped schema/migrations (prompt 21).
- `core/data/backup/MomentumBackup.kt` (prompt 18).
- `docs/supabase-schema.sql`, `docs/supabase-wipe-state.sql`, `supabase/functions/admin-wipe-cloud/index.ts` — deployed backend contract; change atomically with client.
- `features/vault/VaultCrypto.kt` and vault payload builders (prompt 11).
- `core/data/supabase/SecureTokenStore.kt` key format without migration (prompt 16).
