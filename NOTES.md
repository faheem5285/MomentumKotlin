# Momentum current state

This note reflects the repository at Phase 9. The app is a native Android
single-module project built from VS Code with Kotlin, Compose, Room, Hilt,
ViewModel, WorkManager, DataStore, and Supabase Auth/PostgREST.

## Database and data safety

- Room schema version: **13**.
- Explicit migrations cover versions 3 through 13.
- Migration JSON files are exported under `app/schemas/`.
- Destructive migration fallback is not used.
- Local Room data remains the source of truth.
- Versioned local backup/export exists; vault plaintext is excluded.

## Modules

Habits, to-dos, money/expenses, loans, files, vault, screen time, home,
settings, auth, network indicator, and battery charging overlay are present.
Reminder scheduling and notification actions are implemented with exact-alarm
fallbacks and WorkManager verification.

## Vault

The vault uses PBKDF2-HMAC-SHA256 with 310,000 rounds and AES-256-GCM.
Records use a random VMK; the VMK is wrapped by the master-password key and
an Android Keystore biometric key. Screenshots are blocked while open,
background locking is enabled, and copied secrets are cleared on a timer.
Lost master passwords cannot be recovered.

## Sync

Supabase sync is opt-in. The design and RLS SQL are in
[docs/sync-design.md](docs/sync-design.md) and
[docs/supabase-schema.sql](docs/supabase-schema.sql). The current engine
contains sync state, WorkManager scheduling, first-sync local backup,
conflict ordering, and the ciphertext-only vault transport. Non-vault
adapters and the Settings sync UI remain deferred.

## Verification

Use `.\gradlew.bat :app:testDebugUnitTest`, `.\gradlew.bat detekt`, and
`.\gradlew.bat :app:assembleDebug`. Physical device checks are still required
for biometric behavior, alarms, migrations, SAF permissions, and Supabase
end-to-end sync.
