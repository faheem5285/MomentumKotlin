# Momentum vault security

## Threat model

The vault is designed for an offline personal device. It protects stored
credentials and notes from someone who copies the app database or backup files
without the master password. It also limits accidental exposure through
screenshots, recents thumbnails, clipboard retention, and backgrounded UI.

The current record scheme remains AES-256-GCM with random per-record nonces.
The master password is processed with PBKDF2-HMAC-SHA256 using 310,000
iterations and a random salt. New and migrated records are encrypted with a
random 256-bit Vault Master Key (VMK). The VMK is wrapped by the
password-derived key, so changing the master password re-wraps the VMK rather
than re-encrypting every record.

Optional biometric unlock uses an Android Keystore AES key requiring
authentication. The Keystore key is configured for strong biometrics on
supported Android versions and is invalidated when biometric enrollment
changes. The master password remains the recovery path for a valid vault.

## Migration and recovery

On the first successful unlock of a legacy vault, Momentum writes an
app-private backup containing the existing encrypted record blobs, verifies
that every record decrypts, re-encrypts the records under a new VMK, and then
persists the password wrapper. The backup never contains decrypted values.

There is no recovery for a lost master password. Do not clear app data or
delete the migration backup while troubleshooting a vault migration.

## What is protected

- Vault record ciphertext and metadata stored in Room.
- Password-derived VMK wrapper and master-password verifier.
- Optional biometric VMK wrapper in Android Keystore-backed encryption.
- Screenshots and recents thumbnails while the vault is open.
- Clipboard values copied by the vault, when the clipboard is unchanged.
- Keys held by the vault session are cleared when the vault locks or the app
  goes into the background.

## What is not protected

- A live unlocked screen from a person already using the device.
- A rooted or compromised operating system, debugger, malicious accessibility
  service, keylogger, or modified Android framework.
- Plaintext while the user is actively viewing or editing a record.
- Clipboard content after another application replaces it, or on OEM builds
  that do not honor clipboard clearing.
- Password strength, reuse, or user mistakes.

## Operational guidance

Use a unique master password of at least 12 characters. Keep Android updated,
use a device screen lock, and enable strong biometrics only on trusted devices.
Test encrypted export/import and the master-password fallback before relying on
biometric unlock.
