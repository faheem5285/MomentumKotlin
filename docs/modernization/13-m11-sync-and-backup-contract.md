# Milestone 11 — Supabase Sync and Backup Contract

**Status:** Implemented on `impl/m1-data-safety`; JVM tests executed, instrumentation/device/remote behavior **Unverified**.
**Date:** 2026-11-26.

## 1. Sync decisions never silently overwrite local data

`SyncModels` owns the reconciliation policy. A remote row is applied when no local row exists, when the
local copy is clean, or when the local copy is a synced tombstone that the remote row supersedes. A
dirty local edit and a local tombstone are never replaced by an ordinary remote edit. A remote
tombstone wins unless the local row is a strictly newer unsynced edit. Ties are decided by device id
so two clients converge deterministically.

`decideRemote` returns both the apply decision and a conflict flag. Conflicts are counted only for a
divergent remote row meeting an unsynced local edit/tombstone; an identical revision is not a
conflict. `SupabaseSyncRepository.apply*` records now persist the conflict count for the current run
instead of accumulating old conflicts forever. Pulled JSON uses null-safe accessors, so a `null`
column in Supabase becomes a safe default instead of crashing the worker.

## 2. Durable pending deletes and bounded periodic reconciliation

Module deletions queue table names in `PendingCloudDeleteStore`. The shared-preferences key is
`SHA-256(activeUserId)`, lowercased and trimmed before hashing; the raw account id is never written
into a preference name or value. The queue is deduplicated, survives local data reset, and is only
removed after the corresponding cloud delete succeeds. Vault keys use a separate sentinel entry.

`SupabaseSyncScheduler` adds a unique periodic worker (`momentum-sync-periodic`, 15 minutes) with
`CONNECTED` + battery-not-low constraints, and `MomentumApplication` configures it on app open.
Manual and event-driven enqueues use the same constraints and still append a follow-up run. All
scheduling remains opt-in: if there is no current Supabase user, no cloud work is enqueued and local-only
use is unaffected. `pendingCount` derives the count from real dirty rows, not from sync metadata.

## 3. Storage tombstones remove cloud objects without orphaning

`StorageTombstones.planStorageTombstoneDeletion` only selects rows that are deleted, clean and have a
nonblank cloud path, deduplicates by `(bucket, path)` and caps the batch at 1,000 paths. Dirty
tombstones remain queued until the row itself has been pushed. `FileStorageSync.deleteTombstonedObjects`
runs only after the pull phase and only when the Files module is enabled; a failure is surfaced so the
worker retries instead of marking the object removed.

`SupabaseRestClient.deleteStorageObjects(..., strict = true)` treats `404` as already removed and raises
a generic sanitized error for any other failure. `ProfileRepository` now uses strict deletion before
calling the account-deletion RPC. Uppercase `CLOUD:` sentinels are recognized as well as lowercase.

## 4. Status UI and error sanitization

`summarizeSyncStatus` excludes the synthetic `momentum_sync` row from pending/conflict/tracked-table
counts, reports error tables separately, and treats conflicts as needing attention. The UI shows
accurate Pending, Conflicts and Tracked tables values, and chooses "Needs attention" before
"Waiting to sync" so a failed table is not hidden by a queued run. Raw worker/HTTP text is passed
through `HttpErrorSanitizer.forDisplay` before it can be shown, including the expandable detail row.

The admin wipe Edge Function now maps internal failures to generic client messages and tells users to
consult function logs; it does not return raw Supabase error bodies. No client service-role credential
was introduced: the app continues to use the publishable key plus the signed-in user's JWT, so
Supabase row-level ownership remains server-enforced.

## 5. Backup v4 / Room schema 22

`MomentumBackup` now writes `formatVersion = 4`, `schemaVersion = 22` and includes the four language
study tables: `vocabularyWords`, `grammarNotes`, `reviewStates` and `translationHistory`. All 20
non-vault user-data table keys are declared once in `BackupFormatPolicy.TABLE_KEYS` and used for
export, preview, validation and tests. Vault plaintext is still never read or written by the generic
backup; vault records remain in their own encrypted vault export.

Legacy compatibility is preserved by a forward-only upgrade chain: v1 → v2 → v3 → v4, v2/schema 11 or
13 → v3 → v4, and v3/schema 20 → v4. Missing language arrays become empty arrays. Replace imports
delete existing backup-supported tables **and** `sync_state`, so a restored snapshot cannot be
silently overwritten by an older cloud cursor. Merge imports are idempotent: language rows are matched
by normalized natural keys (word+language, category+title, translation triple), review states are
upserted by resolved word id, and counts only increase for newly added rows.

## 6. Optional authenticated backup encryption

`PortableBackupCrypto` writes a portable envelope: magic `MOMENTUM-ENC`, format version, PBKDF2
iteration count, random salt and IV, then AES-256-GCM ciphertext with the whole header as AAD. The
default is 120,000 PBKDF2-HMAC-SHA256 iterations. The passphrase is held only for the export/import
operation and is never stored by the app. A wrong passphrase or tampered envelope produces the
sanitized message "The backup password is incorrect or the backup is damaged."

Settings exposes an optional masked backup password. It encrypts new manual exports and opens an
encrypted import; preview reports whether the file is encrypted. The screen explicitly states that
backups may contain contact details, that Vault secrets and file contents are excluded, that replace
resets sync cursors, and that automatic weekly copies remain **unencrypted JSON**. Weekly work now
also waits for battery-not-low.

## 7. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**
- `.\\gradlew :app:compileDebugKotlin --no-daemon` — PASS after two fix iterations (DAO method name and
  typed natural-key maps).
- `.\\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 187 tests, 0 failures/errors/skips, 38 suites.
  The first run found a JVM `DestroyFailedException` in key cleanup and a UUID-case mismatch in the
  pending-delete hash; both were corrected and the identical command passed.
- `.\\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS, 187 tests,
  0 failures/errors/skips, 38 suites; release signing remains unconfigured and no signed APK was produced.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).

New JVM tests cover sync conflict decisions, status summaries, pending-delete hashing/merge, storage
tombstone planning, null JSON columns, uppercase cloud sentinels, backup format support, and the
portable crypto round trip/tamper/password failures. Instrumentation sources cover v2/v3/v4 round trips,
language-table preservation, idempotent merge, and encrypted export/import, but were not executed.

**Unverified:** no connected/device/emulator execution; no real Supabase/Edge Function call, RLS smoke,
deployment migration, offline/reconnect run, cloud object deletion, SAF backup file, periodic-work
runtime or WorkManager constraint behavior; no real passphrase UX on device, real keystore behavior, or
remote account-deletion RPC. No claim is made that orphaned objects are impossible in production until
those remote checks are run.
