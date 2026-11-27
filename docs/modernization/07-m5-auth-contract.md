# Milestone 5 — Authentication and Account Experience Contract

**Status:** Implemented on `impl/m1-data-safety`; JVM tests executed, device/remote checks **Unverified**.
**Date:** 2026-11-26.

## 1. Explicit signup states

`SupabaseAuthApi.signUp` now parses the response instead of ignoring it. `SignUpResponseParser`
produces one closed set of states: `CONFIRMATION_REQUIRED`, `SESSION_STARTED`, `ACCOUNT_EXISTS`,
`RATE_LIMITED`, `SERVICE_UNAVAILABLE`, `NETWORK_UNAVAILABLE`, `REJECTED`. Raw bodies and status
codes are discarded after parsing; no provider text reaches the UI, logs, or worker state.

When Supabase returns a complete session (email confirmation disabled), the session is applied only
after the account-link snapshot hook succeeds. Confirmation-required state offers a resend action;
`ACCOUNT_EXISTS` routes the user to sign-in/reset copy instead of claiming success.

## 2. Recovery deep links

`RecoveryLinkParser` accepts only `momentum://auth/reset` with `type=recovery`, no provider error, a
bounded access token, and a parsed `expires_in`/`expires_at` in the future. Cold and warm links use
the same parser. `ProfileScreen.consumeRecoveryLink` clears only the activity intent's `data` after
parsing, leaving notification extras intact. Invalid, expired, wrong-type, and error links produce
fixed sanitized messages and no dialog.

## 3. Password change/recovery

`SupabaseAuthApi.changePassword` verifies the current password with the token endpoint without
activating an account, saving a second refresh token, or replacing local Room files. It then updates
the password with the existing session token and the user id must match the signed-in account.
`passwordChangeError` requires reauth, matching passwords, minimum length, and a different new
password. Recovery uses the validated link token only in memory.

## 4. Safe account linking

Any path that activates an account (email sign-in, session-returning signup, Google sign-in) first
runs `AppDatabase.snapshotForAccountLink`: it checkpoints and atomically copies the current database
into `filesDir/account-link-snapshots` (at most three snapshots). If the snapshot fails, activation
does not proceed. `AccountLinkResult` reports deterministically whether local records were linked
to a new account or an existing account's records were loaded; the UI shows the matching notice.

When local records exist, `ProfileViewModel` requires an explicit confirm/cancel dialog before any
network or database activation. Signed-out use remains untouched on cancel. Account switching uses
distinct wording and still preserves the outgoing account's data through the existing
`unassigned-legacy-database.db`/account-file logic plus the new snapshot.

## 5. First-sync backup and deletion

`SupabaseSyncRepository.createFirstSyncBackup` writes one bounded (10 MB) backup per account id,
encrypted with a non-exportable Keystore AES-GCM key and AAD via `LocalBackupCrypto`. Account ids are
hashed in the filename. The legacy plaintext `sync-first-local-backup.json` is deleted on sight.
The backup is created inside sync, i.e. only after account linking has completed.

Deletion removes known cloud object paths from this device (`FileStorageSync.storagePathsForAccount`,
capped at 1,000), then calls the canonical `delete_my_account` RPC. Storage failures are counted
without raw server text and do not block the RPC; the RPC still governs cloud account/record
deletion. The two confirmation dialogs now state exactly what is local, what is removed, and what
requires a server RPC. Local Room/DataStore records are intentionally not erased.

## 6. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**
- `.\\gradlew :app:compileDebugKotlin --no-daemon` — PASS.
- `.\\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 102 tests, 0 failures/errors/skips.
- `.\\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS,
  102 tests, 0 failures/errors/skips.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).

New JVM tests cover signup state parsing, UI message matrix, account-link decisions/copy, recovery
link cold/warm/reject cases, password reauth validation, deletion wording, sanitized errors, and
account-scoped encrypted backup naming. `LocalBackupCryptoTest` is compiled but not executed.

**Unverified:** real Supabase signup/resend/recovery/RPC responses, email confirmation behavior,
Keystore availability on a device, actual Room snapshot/restore, deep-link delivery from a mail
client, intent clearing on warm start, storage object deletion, RLS ownership, and all dialog/visual
behavior. No device, emulator, or remote project was used.
