# Momentum vault security

## Threat model

The local vault protects credentials and notes in the app database and
password-protected export from someone who obtains those files without the
master password. It also limits accidental exposure through screenshots,
recents thumbnails, clipboard retention, and backgrounded UI. The cloud-linked
key behavior described below has a different threat boundary.

The current record scheme remains AES-256-GCM with random per-record nonces.
The master password is processed with PBKDF2-HMAC-SHA256 using 310,000
iterations and a random salt. New and migrated records are encrypted with
a random 256-bit Vault Master Key (VMK). The VMK is wrapped by the
password-derived key, so changing the master password re-wraps the VMK rather
than re-encrypting every record.

Optional biometric unlock uses an Android Keystore AES key requiring
authentication. The Keystore key is configured for strong biometrics on
supported Android versions and is invalidated when biometric enrollment
changes.

## Supabase key handling

Vault entry rows sync as ciphertext and nonce, but the app also stores the raw
VMK as Base64 text in the separate `momentum_vault_keys.key_material` row. RLS
limits ordinary API access to the signed-in account. A Supabase project
administrator or service-role holder can read that key and decrypt the synced
Vault entries. Account sign-in can retrieve the VMK without the master
password. On a new device, the app fetches the existing account VMK, validates
that it is exactly 256 bits, and performs a full Vault pull before exposing the
Vault. If the key fetch or sync fails, the in-memory key copy is cleared and
the Vault remains locked. A new account key is created only when there are no
local encrypted records and no remote Vault records; this prevents silently
replacing a key that may protect existing data. The app does not overwrite an
existing cloud key during account recovery.

This implementation is **not end-to-end encrypted against the cloud
operator**.

**Phase 2 decision (2026-11-13): retain the current account-only Vault unlock.**
No password-wrapped cloud-key migration or new-device master-password prompt
will be introduced in this phase. This preserves the existing cross-device
account-only experience and avoids changing key format or risking access to
existing Vault data. The cloud-operator limitation above remains an explicit
security trade-off; do not describe the Vault as end-to-end encrypted.

## Migration and recovery

On the first successful unlock of a legacy vault, Momentum writes an
app-private backup containing the existing encrypted record blobs, verifies
that every record decrypts, re-encrypts the records under a new VMK, and then
persists the password wrapper. The backup never contains decrypted values.

Version-2 encrypted Vault exports contain ciphertext, the salt and verifier,
and the VMK wrapped by the master-password key; they do not include the
master password or decrypted entries. Restoring that export requires the
original master password. Account-linked Vaults can also retrieve their VMK
through the signed-in Supabase account, subject to the cloud-key limitation
above. A vault that has neither a usable account link nor a matching backup
cannot be recovered after losing its key. Do not clear app data or delete
the migration backup while troubleshooting a Vault migration.

## What is protected

- Vault record ciphertext and metadata stored in Room.
- Password-derived VMK wrapper and master-password verifier.
- Optional biometric VMK wrapper in Android Keystore-backed encryption.
- Version-2 export keeps the VMK password-wrapped; the master password is not
  included.
- Screenshots and recents thumbnails while the vault is open.
- Clipboard values copied by the vault, when the clipboard is unchanged.
- Keys held by the vault session are cleared when the vault locks or the app
  goes into the background.

## What is not protected

- A live unlocked screen from a person already using the device.
- A rooted or compromised operating system, debugger, malicious accessibility
  service, keylogger, or modified Android framework.
- The Supabase project operator or anyone holding its service-role key, because
  the current account-recovery table stores the VMK in Base64 form.
- Plaintext while the user is actively viewing or editing a record.
- Clipboard content after another application replaces it, or on OEM builds
  that do not honor clipboard clearing.
- Password strength, reuse, or user mistakes.

## Operational guidance

Use a unique master password of at least 12 characters. Keep Android updated,
use a device screen lock, and enable strong biometrics only on trusted devices.
Test encrypted export/import and the master-password fallback before relying
on biometric unlock.
