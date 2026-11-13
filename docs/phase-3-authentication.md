# Phase 3 — Authentication and account management

> This is the authentication workstream agreed for the current Zylo modernization.
> Existing `phase-3-reliability.md` and `phase-3-perf.md` remain separate and are not replaced.

## Safety rules

- Preserve account-specific Room database activation and the existing account-isolation behavior.
- Keep refresh tokens encrypted with Android Keystore; do not persist access tokens.
- Do not clear local user data when authentication fails or the network is unavailable.
- Keep the existing account-only Vault unlock decision. No Vault key-format migration.
- Never commit Supabase credentials, service-role keys, passwords, tokens, or personal data.

## Audit findings

- Email/password sign-in and registration UI already exist.
- Supabase email confirmation is enabled; the UI tells new users to confirm email before signing in.
- Password-reset request endpoint exists, but the UI previously checked only for a blank email.
- Refresh tokens are encrypted by `SecureTokenStore`; session restore activates the matching account database.
- Sign-out clears the local token, in-memory access token, and account profile even when revocation cannot complete.
- The auth UI previously displayed raw exception messages and wrote a display name into the active profile before a confirmed sign-in.
- No dedicated authentication unit or instrumentation tests were found in the current test tree.

## Implementation checklist

- [x] Audit AuthScreen, SupabaseAuthApi, SecureTokenStore, SupabaseProvider, and startup session restoration.
- [x] Validate reset-email format before calling Supabase.
- [x] Replace raw backend/exception details in the UI with safe, actionable messages.
- [x] Avoid writing an unverified sign-up name into the currently active account profile.
- [x] Serialize concurrent refresh-token rotation and reuse a fresh session when another caller has already refreshed it.
- [x] Save a rotated refresh token before publishing the refreshed in-memory access token.
- [x] Add JVM tests for common authentication error mapping and safe fallback messages.
- [x] Extract deterministic session-expiry and invalid-session decisions and cover them with JVM tests.
- [ ] Add configured-session integration coverage for refresh, sign-out, and account switching (requires a live Supabase test account/device).
- [ ] Verify registration, email confirmation, sign-in, reset email, session restore, offline startup, sign-out, and account switching on a configured Android device.
- [ ] Run unit tests, Android build, and CI; record actual results below.

## Verification record

CI verification is tracked per commit and must complete successfully before the phase is considered build-verified. Live Supabase/device checks remain pending until executed on a configured device.
