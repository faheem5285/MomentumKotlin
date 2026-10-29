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

- The v3→v4 migration is derived from the last committed v3 entities plus the current v4-era fields. Add Room migration fixtures and verify representative legacy data before shipping a release.
- There is no known schema history for versions earlier than v3. Such a database will now be preserved and rejected by Room instead of being wiped. If pre-v3 installs must be supported, recover their historical schemas and add explicit migrations.
- Add versioned export/import with validation before re-enabling backup. Decide separately whether encrypted vault data participates and document what happens when a user forgets the master password.
- Configure a Room schema export location and retain schema JSON for migration review.

## Verification status

Debug Kotlin compilation passed, and release Kotlin compilation passed before the release-signing gate. With no release credentials configured, `assembleRelease` now stops immediately with the four required property names. Automated migration, domain, and crypto tests remain planned work; they have not been added in this change.
