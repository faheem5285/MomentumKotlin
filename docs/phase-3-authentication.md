# Phase 3 — Authentication and account management

> Authentication workstream for the current Zylo modernization. The existing `phase-3-reliability.md` and `phase-3-perf.md` remain separate and are not replaced.

## Safety rules

- Preserve account-specific Room database activation and account isolation.
- Keep refresh tokens encrypted with Android Keystore; do not persist access tokens.
- Do not clear local user data when authentication fails or the network is unavailable.
- Keep the existing account-only Vault unlock decision. No Vault key-format migration.
- Never commit Supabase credentials, service-role keys, passwords, tokens, or personal data.

## Audit findings

- Email/password sign-in and registration UI already existed.
- Supabase email confirmation is enabled.
- Password-reset request endpoint existed; reset-email validation was strengthened.
- Refresh tokens are encrypted by `SecureTokenStore`; session restore activates the matching account database.
- Sign-out clears the local token, in-memory access token, and account profile even when revocation cannot complete.
- Raw backend exception details were replaced with safe user-facing messages.
- Unverified sign-up display names are no longer written into the active account profile.

## Implementation checklist

- [x] Audit AuthScreen, SupabaseAuthApi, SecureTokenStore, SupabaseProvider, and startup session restoration.
- [x] Validate reset-email format before calling Supabase.
- [x] Replace raw backend/exception details in the UI with safe, actionable messages.
- [x] Avoid writing an unverified sign-up name into the currently active account profile.
- [x] Serialize concurrent refresh-token rotation and reuse a fresh session when another caller has already refreshed it.
- [x] Save a rotated refresh token before publishing the refreshed in-memory access token.
- [x] Add JVM tests for common authentication error mapping and safe fallback messages.
- [x] Extract deterministic session-expiry and invalid-session decisions and cover them with JVM tests.
- [x] User-confirmed configured-device testing: email confirmation.
- [x] User-confirmed configured-device testing: password reset.
- [x] User-confirmed configured-device testing: offline session restoration.
- [x] User-confirmed configured-device testing: sign-out.
- [x] User-confirmed configured-device testing: switching between two accounts.

## Verification record

### Automated CI

GitHub Actions run [#68](https://github.com/faheem5285/MomentumKotlin/actions/runs/43492423950) for commit `3c53475bb45787d9c2fd1a74d2932551f22f91a7` completed successfully on `agent/zylo-premium-modernization`.

- [x] Android lint — passed
- [x] Detekt — passed
- [x] Unit tests — passed
- [x] Debug build — passed
- [x] Release build — passed

### Configured-device checks

The user confirmed that all requested real-device flows passed: email confirmation, password reset, offline session restoration, sign-out, and switching between two accounts. These are recorded as user-reported manual test results; they are not represented as CI/instrumentation tests.

## Phase status

All listed Phase 3 implementation, automated CI, and user-reported device verification items are complete.
