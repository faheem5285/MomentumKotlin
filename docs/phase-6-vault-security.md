# Phase 6 - Vault security

## What changed

- Added a random 256-bit VMK. New vault records are encrypted with the VMK;
  the VMK is wrapped by the PBKDF2-derived master-password key.
- Legacy vaults migrate after an app-private encrypted backup is written and
  every legacy record successfully decrypts. Records are then re-encrypted with
  the VMK.
- Added Android Keystore biometric VMK wrapping with authentication required
  and biometric-enrollment invalidation. The master password remains available.
- Added master-password re-wrapping, increasing failed-unlock delays, secure
  recents handling, and process/background key clearing.
- Added sensitive clipboard marking and a WorkManager cleanup fallback after
  30 seconds.
- Added local password-health reporting, folders, tags, favorites, optional
  TOTP storage, secure notes, and the existing password generator.

## Verification

- `gradlew.bat :app:compileDebugKotlin --no-daemon` passed.
- Focused `VaultCryptoTest` passed, including round-trip, wrong-password,
  GCM tamper rejection, VMK re-wrap, and legacy-record migration coverage.
- Detekt and full debug build should be run sequentially after final edits.
- BiometricPrompt, Keystore invalidation, clipboard cleanup after process death,
  and migration against a real legacy database require a device.

## Known limits

- The current vault UI still locks immediately on background; timeout choices
  and a user-facing auto-lock setting remain deferred.
- Encrypted import and a complete change-password screen are not wired into the
  navigation UI yet; the VMK re-wrap operation is implemented.
- Android Keystore behavior varies by device and must be tested with strong
  biometrics and enrollment changes.
