# Zylo Modernization — Data Safety & Security Review

**Status:** Read-only audit product. No application source, Gradle, schema, auth behavior or backend was changed. No build, test, device or remote Supabase operation was executed.
**Scope:** All 25 logical modules, Room v22, Supabase SQL/Edge Function artifacts, backup/restore, auth/session, vault, notifications, permissions and logging paths.
**Verification legend:** **Verified** = directly confirmed in cited source. **Unverified** = runtime, device, build or remote-service behavior not established here.

---

## 1. Executive verdict

The app has a strong security foundation but several **critical data-loss paths** that must be fixed before any visual modernization:

- **Do not change UI first.** Account switching, full reset, sync conflict resolution, REPLACE upserts and the production vault QA reset can irreversibly destroy user data (verified in source).
- **Preserve the verified strengths:** no hardcoded production secrets, Keystore-wrapped refresh token, AES-256-GCM/PBKDF2 vault primitives, per-table RLS ownership policies, explicit Room migrations with exported schemas.
- **Do not trust current documentation:** biometric unlock, auto-lock, screenshot protection, periodic sync, "all Room records synchronized" and "account required" are contradicted by source.
- **Never expose user data in logs or raw error surfaces.** The dominant remaining leak vector is raw HTTP error bodies and worker state (S17), not direct `Log.*` calls in auth/sync paths.
- **Optional sign-in is already effectively implemented** (`MainActivity.kt:194`), but accidental; local-only use must remain a first-class acceptance gate.

**Counts from the audit:** 8 critical findings, 28 high, 34 medium, 12 low. No finding is marked resolved by this document; all require implementation and executed tests.

---

## 2. Verified strengths to preserve

| Control | Source evidence | Why it matters |
|---|---|---|
| No hardcoded production credentials | `app/build.gradle.kts:16-26`; publishable key + Google client ID from Gradle properties/env with empty defaults | Prevents secret exposure in VCS; keep this pattern. |
| Encrypted refresh token | `SecureTokenStore.kt:14-37` — AES/GCM with Android Keystore alias `momentum_supabase_refresh_v1` | Token ciphertext, not plaintext; hardening must preserve or safely migrate existing tokens. |
| Vault crypto primitives | `VaultCrypto.kt:77-265` — AES-256-GCM, PBKDF2-HMAC-SHA256 310k, random nonces, constant-time verifier, zeroization, Keystore biometric key | Strong baseline; never weaken parameters without a tested key migration. |
| Room migration discipline | `Data.kt:910-914,1422-1440`; schemas `3.json`–`22.json`; `AppDatabaseMigrationTest.kt:45-151`; no `fallbackToDestructiveMigration` | Existing v3→v22 chain is explicitly tested; keep append-only. |
| RLS ownership | `docs/supabase-schema.sql:421-476`; restrictive wipe guards `:446-448,492-501` | Per-table `auth.uid()` ownership prevents cross-account reads/writes; do not weaken. |
| Service-role confinement | `supabase/functions/admin-wipe-cloud/index.ts:49-110` | Service role exists only in Edge Function with JWT + role validation. |
| Non-exported receivers | `AndroidManifest.xml` and dynamic receiver registrations | Reduces external attack surface; verify before manifest changes. |
| No PII in audited auth/sync `Log.*` calls | Repo grep over auth/sync paths | Keep this property; raw HTTP bodies remain the gap. |
| SAFT/backup size guards | `MomentumBackup.kt:23-24` — 10 MB / 10,000 rows | Bounds malicious backup input; preserve while adding v4. |

---

## 3. Critical data-safety findings

### 3.1 WAL-pending account switch can lose committed local rows — Critical
**Source:** `Data.kt:939-976` (`AppDatabase.activateAccount`).
**Verified behavior:** Checkpoint is wrapped in `runCatching` and its result ignored; cold start can copy only the main DB file, delete WAL/SHM and overwrite the previous account copy non-atomically. Active-user preference is committed after file operations.
**Impact:** Irreversible loss of committed local data or overwrite of another account's database. Actual WAL contents at runtime are **unverified**.
**Required control:** Require successful checkpoint; use SQLite backup API or atomic temp-file replacement with rollback; keep DB file and active-user marker consistent; add two-account WAL-pending instrumentation tests before touching this code.

### 3.2 Full reset deletes every account database — Critical
**Source:** `LocalAppDataReset.kt:20-49`.
**Verified behavior:** `clearAllTables()`, most SharedPreferences cleared, then recursive deletion of `filesDir`, `cacheDir`, `noBackupFilesDir` and external dirs. `filesDir/account-databases/` contains other accounts' DB copies. Default callers do not clear both DataStores; weekly backup prefs can be cleared while WorkManager work remains.
**Impact:** Cross-account irreversible data loss; settings/work desynchronization. Runtime frequency is **unverified**.
**Required control:** Scope deletion to active account + explicitly synced files; clear both DataStores; close DB before file deletion; cancel/reschedule backup and sync work; add multi-account reset test.

### 3.3 Sync resolver ignores dirty state and local deletion — Critical
**Source:** `SupabaseSyncRepository.kt:516-525`; `SyncModels.kt:22-24`.
**Verified behavior:** `accept()` takes `dirty` but never reads it; local candidate hardcodes `deleted=false`. Tie-break is newer `updatedAt`, then larger `deviceId`.
**Impact:** Unsynced local edits can be overwritten by a newer clean remote row; local tombstones resurrect. Remote behavior is **unverified**.
**Required control:** Pass real `dirty`/`deleted`; define delete-wins/dirty-wins rules; record explicit conflicts; add dirty/tombstone/null-column tests; never advance cursors on failed pulls.

### 3.4 REPLACE upserts cascade-delete child records — Critical
**Source:** `Data.kt:392-399,569-572,705` and other `@Insert(REPLACE)` DAOs; FK `ON DELETE CASCADE` at `:45-49,66-69,85-89,187,206-210,303`.
**Verified behavior:** SQLite `REPLACE` deletes then reinserts parent rows; remote parent updates can remove completions, skips, reminders, payments, attachments and life-rule files.
**Impact:** Silent loss of child user data on sync. Runtime occurrence is **unverified**.
**Required control:** Use targeted update-then-insert/`@Upsert` preserving local PK/UUID/FK identity; add child-count regression tests for every affected parent.

### 3.5 Production QA reset in Vault UI — Critical
**Source:** `VaultScreen.kt:506-515,1181-1214`; no `BuildConfig.DEBUG` guard found.
**Verified behavior:** "Reset Vault QA data" deletes cloud vault tables/key and local vault state; vault rows are excluded from normal backup.
**Impact:** Irreversible vault loss from a QA affordance. Whether a release build exposes it is **unverified only in the sense that no guard was found in source**.
**Required control:** Remove or gate to debug/internal; require re-auth and verified encrypted export before any deletion; add release-configuration test.

### 3.6 Backup is stale and incomplete — High/Critical impact
**Source:** `MomentumBackup.kt:19-24,168-177,467-479`; `Data.kt:1396-1407`.
**Verified behavior:** Format v3 requires exact schema 20 while Room is v22; four language tables are excluded; `sync_state` survives replace; JSON is plaintext PII.
**Impact:** Language study data silently omitted from backups; stale cursors after restore; exports expose contacts/PII.
**Required control:** Backup v4 with compatibility reader, schema capability matrix, all user-data tables, sync-state reset on replace, optional encryption, partial-failure receipt.

### 3.7 Notification defects cause missed/wrong-day reminders — High/Critical impact
**Source:** `CustomNotificationScheduler.kt:118-131`; `CustomNotificationScreen.kt:264`; `CustomNotificationTimeCalculator.kt:30-37`; `NotificationSystem.kt:341-378,454-493`.
**Verified behavior:** One-time notification is posted then cancelled; weekly UI bit mapping disagrees with calculator; quiet hours silently clear one-time/to-do reminder state.
**Impact:** Missed or wrong-day reminders and silent loss of scheduled state. Device delivery is **unverified**.
**Required control:** Keep notification until user action; single weekday mapping with additive stored-mask repair; defer rather than discard; guarded posting via `ZyloNotifications`.

### 3.8 Cloud-first translation mislabeled private/on-device — High
**Source:** `LanguageTranslator.kt:37-54,92-104`; `LanguageScreen.kt:1761-1784,2229`.
**Verified behavior:** Text is sent to Google endpoint first with a spoofed browser UA before ML Kit fallback; UI claims on-device/private/offline.
**Impact:** Privacy misrepresentation and undisclosed data egress. Provider availability/ToS is **unverified**.
**Required control:** Offline-first with explicit opt-in, or prominent disclosure; remove UA spoofing; expose engine source; truthful copy.

---

## 4. Security findings by domain

### 4.1 Vault
- **S2/S3/S4/S5 (High):** No `FLAG_SECURE`; autolock only on `ON_STOP` and skipped during SAF picker; no idle timeout; biometric/password methods UI-dead; v3 encrypted export cannot be consumed by the exposed import path.
- **S6 (High):** VMK is escrowed in `momentum_vault_keys`; account unlock works without password. This is documented but means **not E2EE against a Supabase operator/admin**.
- **S7/S8 (Medium):** TOTP secret shown plainly; duplicate clipboard clearing (Worker + Handler).
- **Preserve:** AES-256-GCM/PBKDF2 parameters, zeroization, ciphertext-only Room storage and clipboard hash behavior.
- **Action:** Debug-gate QA reset; lifecycle `FLAG_SECURE`; idle/picker-close lock; wire or remove biometric/password flows; fix v3 round-trip; mask TOTP; single clipboard mechanism; show accurate threat model in UI.

### 4.2 Authentication and session
- **S9/S10 (High):** Refresh token in plain SharedPreferences with Keystore-wrapped ciphertext, no AAD/key version; no refresh single-flight.
- **S11–S14 (Medium/High):** Raw recovery token without validation/intent clearing; divergent password rules/signup response handling; account-deletion RPC only in legacy SQL; sign-out does not cancel WorkManager sync.
- **Action:** EncryptedSharedPreferences/MasterKey + AAD/rotation; mutex refresh + one 401 retry; unified validation; validated recovery/PKCE; deterministic sign-out; canonical deletion RPC + storage cleanup; no raw error bodies.
- **Preserve:** Optional sign-in; local data available with `NO_SESSION` (`MainActivity.kt:194`).

### 4.3 Sync and backend
- **S15/S17/S18/S19/S20 (Medium/Low):** Durable plaintext first-sync backup, raw server error text in UI/worker, RLS policies omit `TO authenticated`/grants, duplicate SQL schemas, wildcard CORS on admin function.
- **AU4/AU5/AU7/AU9 (High/Medium):** Null JSON access may throw; generic upsert lacks `on_conflict`; file reads unbounded and tombstones leave Storage objects; wipe-in-progress returns permanent failure and DataStore not cleared.
- **Action:** See §3.3 plus canonical SQL with explicit grants/revokes, sanitized errors, bounded streamed file sync, account-scoped pending deletes, periodic constrained reconciliation, staging RLS smoke test.
- **Preserve:** `auth.uid()` ownership, service-role confinement, JWT/role admin guard.

### 4.4 Backup and local storage
- Backups include contacts/loan people/file URIs in cleartext (`MomentumBackup.kt:93-108,131-135`); UI warning at `AppSettingsScreen.kt:296` is generic.
- Weekly backup worker exists but cannot be enabled from shipped UI; first-sync backup persists indefinitely.
- **Action:** Explicit contacts/PII warning, optional authenticated encryption, checksum, expiry/deletion of first-sync copy, tested legacy import, no partial settings apply.

### 4.5 Permissions and device features
- Declared: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `FOREGROUND_SERVICE*`, `QUERY_ALL_PACKAGES`.
- No Contacts permission (contacts are SAF-imported); no `USE_EXACT_ALARM` — correct for a general productivity app.
- Accessibility service uses minimal events and `canRetrieveWindowContent="false"` (verified).
- **Unverified:** Play policy acceptance for `QUERY_ALL_PACKAGES`, overlay, accessibility, special-use FGS; OEM behavior for exact alarms, background FGS, clipboard clearing, SAF persistence.
- **Action:** Verify necessity, avoid claiming status without OS checks, refresh on resume, prepare policy declarations or remove high-privilege paths.

### 4.6 Logging and error handling
- No `Log.*` call in audited auth/sync paths logs passwords, tokens, emails or plaintext vault content (verified by grep).
- **Remaining leak vectors:** `SupabaseRestClient.kt:390-399` embeds up to 500 chars of raw body; `SupabaseAuthApi.kt:130-131,352-355` surfaces raw messages; `SupabaseSyncWorker.kt:35-36` persists `cause.message`; `SupabaseScreen.kt:528` displays it.
- **Action:** Map to stable localized error categories; never persist raw response bodies; add CI grep checks; extend to notification text, backup content, package labels and user content.

---

## 5. Threat model summary

| Threat | Current posture | Required mitigation |
|---|---|---|
| Accidental cross-account overwrite | Weak: WAL switch and all-directory reset (§3.1/3.2) | Account-scoped atomic operations, multi-account tests |
| Local edit lost during sync | Weak: dirty ignored, REPLACE cascades (§3.3/3.4) | Real conflict candidates, child-preserving upserts |
| Device lost / unlocked | Mixed: vault ciphertext strong; no `FLAG_SECURE`, idle autolock or complete lock helper | Lifecycle protection + tested lock states |
| Cloud operator reads vault | Known escrow, documented; not E2EE | Accurate UI threat model; consider password-wrapped VMK |
| Malicious/oversized backup | Partial: size/row caps; plaintext PII; stale schema | v4 validation, timestamp-aware merge, optional encryption |
| Token theft / session fixation | Partial: Keystore ciphertext; no AAD/user binding or refresh single-flight | Hardened storage + rotation + recovery validation |
| Privacy leakage to third parties | Weak: cloud-first translation mislabeled | Explicit consent/disclosure, engine indicator |
| Log/error PII exposure | Partial: no direct sensitive logs; raw bodies remain | Sanitization + CI checks |
| Permission abuse / Play policy | Unverified high-privilege paths | Necessity review, minimal scope, policy evidence |

---

## 6. Non-negotiable safeguards for implementation

1. **No destructive Room migration fallback.** Additive migrations only; export schemas; data-bearing tests.
2. **No shipped-schema rewrite.** Never edit migration SQL for already-shipped versions; add repair migrations.
3. **No account-wide or directory-wide deletes.** Every deletion must be scoped, transactional where possible, explicitly opt-in and recoverable.
4. **No dirty local edits overwritten.** Conflict resolver must receive real `dirty` and `deleted` state.
5. **No secrets/PII in logs, CI, UI error text, backups or worker state.**
6. **No auth requirement for local features.** Signed-out mode remains fully usable.
7. **No RLS weakening.** Preserve per-table ownership and service-role confinement; verify remotely before closing sync work.
8. **No "passed" claim without actual execution.** Tests, CI, device and remote smoke results must be recorded when run.
9. **No direct commits to `main`.** Work on dedicated branches from the agreed integration branch.
10. **No visual-first changes while critical data paths are open.**

---

## 7. Security acceptance gates

| Gate | Must be true before milestone closure |
|---|---|
| Data preservation | All reproduction tests for WAL, reset, upsert children, dirty/tombstone, backup and migrations pass when actually executed. |
| Vault | QA reset absent in release; `FLAG_SECURE`, idle/picker lock and encrypted export round-trip verified; no plaintext in Room/backups/logs. |
| Auth | Local-only mode works; signup/recovery/session states explicit; token rotation single-flight; no token/PII logs. |
| Sync/backend | One canonical SQL; RLS/grants verified on staging; account-scoped deletes; no raw error bodies. |
| Backup | v4 covers all user-data tables incl. language; legacy import works; replace leaves consistent sync state; PII warning explicit. |
| Privacy | Translation behavior matches copy; no hidden cloud transmission; permission claims verified against OS state. |
| Quality | No new secrets; detekt baseline ratchets; connected tests in CI; device/remote results labelled with evidence/date. |

---

## 8. Unverified items

- Actual WAL contents during cold-start account switch and runtime `PRAGMA foreign_keys` semantics in migrations.
- Whether the release build actually exposes the vault QA reset (no guard was found in source).
- Remote Supabase project configuration: applied SQL, token rotation, grants, RLS behavior, RPC/Edge Function deployment, Storage policies.
- Google Play policy decisions for high-privilege permissions and foreground service types.
- OEM behavior for exact alarms, background FGS, clipboard clearing and SAF persistence.
- Visual contrast, TalkBack and large-font behavior without device runs.
- All build, lint, detekt, unit, instrumentation, benchmark and device outcomes for this branch — none were executed during this audit.
