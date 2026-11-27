# Module Prompt 16 — Authentication & Profile

Standalone OpenCode prompt. This is a high-risk module: sign-in is optional and local-only use must continue to work. Implement on a dedicated branch after data-safety fixes. Read audit sections 1, 6 (DB3/DB4), 7 (S9–S14), 8 (AU1/AU2/AU10), 10 (T3/T6), 12, 13 and this prompt. Never log tokens, passwords, emails or raw error bodies. Report only tests actually executed; remote Supabase configuration is unverified.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/features/auth/AuthScreen.kt` — duplicate/dead UI, validation `:167-170`, signup `:178-184`.
- `app/src/main/java/com/faheem/habits/features/profile/ProfileScreen.kt` — signed-in/out UI, recovery parser `:191-204`, deletion dialogs `:159-171`, avatar loading `:704-720`.
- `ProfileViewModel.kt:39-153`, `ProfileRepository.kt:17-79`, `ProfileValidation.kt:7-21`, `features/auth/AuthRepository.kt:18-60`.
- `core/data/supabase/SupabaseAuthApi.kt:34-358` — signup `:133-139`, recovery `:141-143,208-217`, refresh `:234-277`, sign-out `:289-320`, identity binding `:327-337`.
- `SecureTokenStore.kt:14-85` — Keystore AES/GCM refresh token; `SupabaseRestClient.kt:80-93` — account-deletion RPC.
- `app/MainActivity.kt:172-225,282-306` — session restore/sign-out gate; `app/Navigation.kt` — `momentum://auth/*` and profile routes.
- `core/data/Data.kt:939-976` — account DB switching (do not change here without prompt 21).
- `docs/supabase/profiles.sql:60-82` — only file containing `delete_my_account`; `docs/supabase-schema.sql` — canonical RLS/grants.

## 2. Existing functionality (verified)
- Two auth UIs exist. `AuthScreen` allows ≥6-character passwords; `ProfileValidation` requires email regex and ≥8 characters. `ProfileViewModel` uses the stricter path.
- All session restore branches in `MainActivity` set `isAuthenticated = true`; the `AuthScreen` branch is effectively unreachable. Local-only mode works with `NO_SESSION`.
- `SupabaseAuthApi.signUp()` discards the response body; both UIs fabricate confirmation messages.
- Password recovery uses `momentum://auth/reset`; `ProfileScreen` reads raw `access_token` from the fragment. No `type=recovery`, expiry, error or PKCE validation, and the intent is not cleared.
- Refresh token is AES/GCM-encrypted with an Android Keystore key, but storage is plain SharedPreferences with no AAD/user-auth binding or key versioning. No refresh single-flight was found.
- Sign-out is a best-effort logout POST, clears local token/prefs and emits an event; no production call site cancels WorkManager sync.
- Account deletion calls an RPC present only in the legacy `profiles.sql`; profile UI warns it may not exist. Local records are intentionally retained.

## 3. Problems discovered
- Unreachable duplicate auth UI with weaker validation; two divergent auth flows (AU1/AU2).
- Signup state ambiguity: confirmation required, session returned, error and already-existing account cannot be distinguished.
- Recovery link can be driven by a crafted/non-recovery token; token lingers in intent data (S11).
- Concurrent refresh-token rotation can invalidate peers if rotation is enabled on the remote project (S10; remote config unverified).
- Sign-out leaves queued sync work and server session in some conditions (S14).
- Profile "synced" state can be false: `pendingChanges=false` is set after local save without cloud confirmation (AU10).
- Account deletion RPC/storage cleanup divergence means the warning may not match deployed behavior (S13/S19; remote unverified).
- Raw HTTP error bodies up to 500 characters are surfaced in UI/worker output; possible PII leak (S17).
- SAF avatar persistable-permission failures are swallowed and avatar downloads have no byte/decode cap.
- Account switching/first sync touches the critical `activateAccount`/WAL and reset paths (DB3/DB4/T3).

## 4. Recommended UI improvements
- Keep one auth UI (prefer the Profile path) with unified email/password rules; remove or route `AuthScreen` in coordination with prompt 01.
- Add explicit states: confirmation required, resend, expired link, network failure, account exists, invalid credentials, session expired/re-auth.
- Validate recovery link (`type=recovery`, expiry, error params) before showing the password-change dialog; clear intent after handling.
- Make signed-out/local-only messaging explicit; show local saved / cloud synced / pending / failed separately.
- Account deletion warning must match actual RPC behavior, storage cleanup and the "local records remain" policy.

## 5. Recommended backend and architecture improvements
- Extract an explicit app/auth state machine from `MainActivity`; keep local-only first-class, never force sign-in for existing data.
- Single auth repository/validation source; parse signup responses instead of fabricating status.
- Add refresh single-flight (mutex/`Mutex`-backed defer) with one retry on 401; never log tokens.
- Harden `SecureTokenStore` via EncryptedSharedPreferences/MasterKey, AAD binding, key versioning and a safe migration/rotation plan.
- Deterministic sign-out: cancel/await relevant WorkManager work, mark pending logout when offline, clear local binding only after decision; preserve local data.
- Canonicalize SQL schema with profile table, RLS/grants and `delete_my_account` RPC; add server-side storage cleanup; verify deployment.
- Cap avatar download/decode sizes; reject failed SAF persistable grants visibly.
- Coordinate any `activateAccount`/first-sync change with prompts 17/21 and data-bearing tests.

## 6. Useful features to add
- Safe account-linking/first-sync flow: snapshot local DB, explicit "claim/merge local data" choice, deterministic handling of `unassigned-legacy-database.db`.
- Session expiry/re-auth prompt that does not discard unsynced local edits.
- Profile/cloud status card in Settings/Home with accurate local/cloud wording (prompt 15).

## 7. Unnecessary elements to simplify or remove
- `AuthScreen.kt` duplicate path after unification.
- Implicit re-sign-in password-change path; use session-based update with re-auth.
- Raw error body display and swallowed SAF failures.
- Unused `SupabaseProvider.createClientOrNull()` if no call site remains.

## 8. Data safety requirements
- Signed-out users must retain and use local data; never wipe or lock local records on session failure.
- No account switch, sign-in or first sync may overwrite dirty local rows or WAL-pending committed data; add failing multi-account tests before touching `Data.kt`.
- Account deletion must state clearly that local records remain unless a tested local-deletion policy is introduced.
- Preserve `account_profile`, `local_account_id`/`local_email` binding semantics in `clearAuthIdentity()`.
- Do not change vault key/crypto behavior here (prompt 11).

## 9. Security considerations
- Validate deep links and clear intent data; use PKCE where supported by the deployed provider.
- Never log tokens, passwords, emails, recovery tokens or raw error bodies; map to stable localized messages.
- Do not add service-role keys to the app; publishable key plus bearer token only.
- Token rotation, OAuth/Google config, RLS, RPC deployment and storage cleanup are remote-unverified.

## 10. Required tests
- Session restore matrix: authenticated, no session/local-only, temporary failure, wipe event, cold/warm deep links.
- Shared validation tests; signup response/confirmation/resend/expiry states; recovery link validation and intent clearing.
- Concurrent refresh single-flight and one 401 retry; network failure preserves encrypted token.
- Sign-out test asserting WorkManager cancellation/pending-logout behavior and retained local data.
- Account deletion RPC smoke test against staging plus local-data retention assertion.
- WAL-pending and multi-account/account-link tests before any `Data.kt` change.
- SAF denial and oversized-avatar tests. No tests were executed in this audit.

## 11. Dependencies on other modules
- Prompt 01 app shell/deep links, prompt 15 Settings status, prompts 17/21 sync/data, prompt 18 backup, prompt 11 vault key behavior, backend SQL deployment.

## 12. Acceptance criteria
- Single auth UI; local-only mode uninterrupted in all tested restore outcomes.
- Signup/recovery/session errors explicit; no token/PII leakage; deletion behavior matches the warning.
- No local data loss on account switch or first sync; all executed tests pass.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` migrations/`activateAccount` (prompt 21).
- `core/data/sync/LocalAppDataReset.kt`, `ProjectWipeManager.kt`, `SupabaseSyncRepository.kt` (prompt 17).
- `docs/supabase-schema.sql`, `docs/supabase/profiles.sql`, RLS policies without deployment verification.
- `AndroidManifest.xml` auth deep-link declarations (prompt 01).
- `SecureTokenStore.kt` key alias without a migration/rotation plan.
