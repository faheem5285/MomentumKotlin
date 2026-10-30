# Phase 2 — Data and release safety

## Decisions implemented

- Automatic Android backup is disabled. Momentum currently has no import/export or account recovery flow; this avoids transferring an encrypted vault database without a complete, user-tested recovery story. Revisit this when supported export/import exists.
- Database destructive migration fallback is removed. The known committed schema (v3) now has a forward migration to v4 and the app registers the existing migrations through v9. A missing future migration will fail visibly rather than silently erase all local records.
- Release builds use a dedicated `release` signing configuration. The debug signing key is no longer used for release artifacts.

## Release signing setup

Provide all four values through Gradle properties or environment variables. Keep the keystore and credentials outside version control.

| Gradle property or environment variable | Meaning |
| --- | --- |
| `RELEASE_STORE_FILE` | Path to the release keystore (relative paths are resolved from the project root) |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Signing key alias |
| `RELEASE_KEY_PASSWORD` | Signing key password |

For local builds, put these in the user-level Gradle properties file rather than committing them in this repository. CI should inject them as protected secrets. Release APK/AAB packaging checks these values and the keystore file and stops with a clear error if any are missing. Verify the resulting signature before publishing.

## Remaining data-safety work

- The database is currently schema version 10 with explicit migrations from v3 through v10. The v3→v4 migration is derived from the last committed v3 entities plus the current v4-era fields. Add Room migration fixtures and verify representative legacy data before shipping a release.
- There is no known schema history for versions earlier than v3. Such a database will now be preserved and rejected by Room instead of being wiped. If pre-v3 installs must be supported, recover their historical schemas and add explicit migrations.
- A versioned JSON export/import exists for non-secret records. Import validates the backup and merges records transactionally; vault entries and file contents are excluded. File references may not work on another device. Export reads all database tables in one Room transaction so edits cannot produce a mixed-time snapshot.
- Decide how to retain generated Room schema history. `exportSchema` is currently disabled, and no schema fixture directory is configured.
- The vault master password cannot be reset. Vault content is device-local and excluded from JSON backup; users must keep access to the original installation to access those entries.

## Verification status

Debug Kotlin compilation passed, and release Kotlin compilation passed before the release-signing gate. With no release credentials configured, `assembleRelease` now stops immediately with the four required property names. Reminder and vault crypto unit tests exist; Room migration, backup/restore, and broader domain tests remain outstanding.
