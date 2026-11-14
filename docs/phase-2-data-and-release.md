# Phase 2 — Data and release safety

## Decisions implemented

- Android Auto Backup is disabled. User-selected backup is handled separately
  by the in-app versioned JSON export/import flow and optional weekly local
  backup.
- Database destructive migration fallback is removed. Explicit Room migrations
  are registered from schema v3 through v20; exported Room schemas are kept in
  `app/schemas/`.
- Release builds use a dedicated `release` signing configuration. The debug
  signing key is not used for release artifacts.

## Backup behavior and disclosure

- JSON backups are plaintext and can contain personal records. Users choose
  where to save or restore them; they should use a trusted destination.
- The export/import format is versioned, validates input, and merges records
  transactionally. It excludes Vault entries and file contents. File metadata
  and URI references are included, but referenced files may not be available
  on another device.
- Export/import is limited to 10 MB and 10,000 rows. The optional weekly backup
  writes to a user-selected folder and retains the five newest matching copies.
- Vault entries remain device-local and are not recoverable from JSON backup.
  The Vault master password cannot be reset.

## Release signing setup

Provide all four values through Gradle properties or environment variables.
Keep the keystore and credentials outside version control.

| Gradle property or environment variable | Meaning |
| --- | --- |
| `RELEASE_STORE_FILE` | Path to the release keystore (relative paths are resolved from the project root) |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Signing key alias |
| `RELEASE_KEY_PASSWORD` | Signing key password |

For local builds, put these in the user-level Gradle properties file rather
than committing them in this repository. CI should inject them as protected
secrets. Release APK/AAB packaging checks these values and the keystore file
and stops with a clear error if any are missing. Verify the resulting
signature before publishing.

## Remaining data-safety work

- There is no known schema history for versions earlier than v3. Such a
  database will be rejected by Room rather than silently wiped. If pre-v3
  installs must be supported, recover their historical schemas and add
  explicit migrations.
- Device-level backup/restore behavior and the weekly worker still need
  representative Android/OEM verification.

## Verification status

Room migration tests now cover each supported step through v20, a full v3→v20
chain, representative row preservation, and money conversion. Backup
round-trip, merge idempotency, legacy-format compatibility, and failed-replace
safety have instrumentation coverage. Final results are recorded in the
Phase 3 reliability checklist after the connected test run.
