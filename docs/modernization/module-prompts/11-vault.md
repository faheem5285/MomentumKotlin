# Module Prompt 11 — Vault

Standalone OpenCode prompt. This is a security-critical module. Implement on a dedicated branch after data-safety fixes. Read audit sections 6 (DB7), 7 (S1–S8, S15), 12, 13 and this prompt. Never log plaintext secrets or keys; never weaken existing AES-GCM/PBKDF2 controls. Report only tests actually executed.

## 1. Actual source files involved
- `features/vault/VaultCrypto.kt` — AES-256-GCM, PBKDF2-HMAC-SHA256 310k, VMK wrapping, biometric Keystore key, zeroization (`:77-265`).
- `features/vault/VaultScreen.kt` — gate/home/detail/editor, account unlock (`:143-170`), dead password/biometric methods (`:140-141,249-271,308-352`), QA reset (`:506-515,1181-1214`), encrypted export/import/restore (`:517-721`), autolock (`:1227-1237`), TOTP display (`:1735`), clipboard (`:1857-1883`), unused `findActivity` (`:1885-1889`).
- `features/vault/data/VaultRepository.kt`, `SensitiveClipboardWorker.kt`.
- `core/data/Data.kt` — `VaultRecord` (`:244-257`) ciphertext/nonce only.
- `core/data/supabase/SupabaseRestClient.kt` — `fetchVaultKey`/`saveVaultKeyIfAbsent` (`:305-358`).
- `core/data/sync/SupabaseSyncRepository.kt` — vault key checks (`:71,284-296,508-514`), `resetModule`.
- `docs/vault-security.md`, `docs/sync-design.md:88-97`.

## 2. Existing functionality (verified)
- Strong crypto primitives: random nonces, PBKDF2 310k, constant-time verifier, zeroization, biometric Keystore key requiring user auth with invalidation on enrollment change.
- Current UI unlocks via account-fetched VMK only; ciphertext synced; clipboard cleanup uses SHA-256 hash in WorkManager + Handler.
- `exportEncrypted()` writes v3 password-protected payload containing raw VMK; `restoreEncryptedBackup()` accepts v2–3 but is not wired into UI; `importEncrypted()` accepts only v1–2.

## 3. Problems discovered
- Production "Reset Vault QA data" button deletes cloud vault + key and local vault prefs irreversibly (S1).
- `FLAG_SECURE` absent despite docs claiming screenshot/recents protection (S3).
- Biometric/password setup/unlock/change-password methods are UI-dead (S4).
- Autolock only on `ON_STOP`, skipped while SAF picker open; no idle timeout (S2).
- v3 export cannot be consumed by exposed import path; restore UI not wired (S5).
- Cloud VMK is recoverable by Supabase operator/admin — documented but serious (S6/V1).
- TOTP secret shown plainly; clipboard duplicate mechanisms (S7/S8).
- Dirty local vault records that don't match fetched key are silently skipped and remain pending (audit F16).

## 4. Recommended UI improvements
- Clear gate explaining account unlock vs password/biometric (depending on chosen design) and the cloud-key threat model.
- Mask TOTP by default with reveal/copy; add editor field if feature retained.
- Wire explicit "Export encrypted backup" and "Restore encrypted backup" actions; remove stale/no-op text.
- Show pending/unreadable record diagnostics without exposing plaintext.
- Consistent autolock indicator and user-configurable timeout.

## 5. Recommended backend and architecture improvements
- Remove/gate QA reset behind debug/internal build; require re-auth and verified export before any vault deletion.
- Add lifecycle-scoped `FLAG_SECURE` while session key non-null; clear on lock and on picker completion.
- Implement idle autolock via settings timeout + single `lockVault()` helper clearing key/items/editor state.
- Either wire BiometricPrompt setup/unlock/change-password or remove dead methods and doc claims; keep reset key clearing.
- Fix v3 import compatibility; align export/restore version handling; add round-trip tests.
- Long-term: evaluate password-wrapped VMK with explicit cross-device flow; if kept, surface accurate threat model in UI.
- Single clipboard mechanism with WorkManager fallback; cancel duplicate work.

## 6. Useful features to add
- Vault item categories/tags search (without plaintext logs).
- Recovery-code flow if changing key management.
- Lock timeout setting under Privacy & Security (prompt 15).

## 7. Unnecessary elements to simplify or remove
- QA reset in release UI; duplicate clipboard cleanup; unused `findActivity`/`Window` imports if no FLAG_SECURE decision; dead biometric methods if not wired.

## 8. Data safety requirements
- Never delete vault records in normal UI flows; all export/import paths must be reversible with verified backup.
- Preserve ciphertext/nonce/UUID/`updatedAt`/`deviceId` exactly; no plaintext write to Room/prefs/backups/logs.
- Any key migration must support existing records; no destructive fallback; test with real encrypted fixtures.
- Do not clear local vault on sign-out (preserve encrypted local records).

## 9. Security considerations
- Preserve AES-256-GCM, PBKDF2 310k, Keystore biometric gating, zeroization and private clipboard hash behavior.
- Do not log keys, plaintext, ciphertext, passwords, or biometric results.
- Document and display: cloud account unlock means key escrow, not E2EE against Supabase operator/admin.
- Device-verify screenshots/recents, autolock, clipboard clear before marking complete.

## 10. Required tests
- Existing `VaultCryptoTest` must still pass; add malformed ciphertext/zeroization/biometric wrap tests where feasible.
- Encrypted export v3 → restore v3 round-trip test; legacy v1/v2 compatibility test.
- Lifecycle tests for `FLAG_SECURE` and autolock (instrumentation where needed).
- QA-reset gating test (absent in release configuration).
- Sync tests: dirty unreadable records not silently lost; key mismatch produces clear pending state.
- Compose tests for gate/detail/editor with test tags, TOTP masking, empty/error states.

## 11. Dependencies on other modules
- `core/data/Data.kt` (prompt 21), sync/key REST (prompt 17), backup format (prompt 18), settings timeout (prompt 15), permissions/SAF (prompts 03/09), design system (prompt 20).

## 12. Acceptance criteria
- No destructive QA path in release UI; vault plaintext never visible in logs/UI when locked or in screenshots while unlocked.
- Export/restore round-trip passes; autolock verified on device; cloud-key threat model documented in UI.
- No existing ciphertext lost; all executed tests pass.

## 13. Files that must not be changed without justification
- `VaultCrypto.kt` crypto primitives/parameters without migration and tests.
- `core/data/Data.kt` shipped vault schema/migrations.
- `core/data/supabase/SupabaseRestClient.kt` key storage behavior (coordinate with prompt 17).
- `core/data/sync/SupabaseSyncRepository.kt` conflict/key logic (prompt 17).
- `docs/vault-security.md` threat model without preserving accurate history.
