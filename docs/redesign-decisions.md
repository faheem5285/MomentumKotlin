# Redesign decisions

## Phase 0

- **Current login behavior conflicts with the brief.** The current `MainActivity` gates access to modules behind Supabase Auth, while the chosen direction says sign-in remains optional and local/offline use is complete. Treat the explicit product direction in the new brief as the target, but preserve local data and existing sync/session behavior while the architecture is audited. The transition must be planned and tested before changing the gate; do not silently invalidate existing signed-in sessions or data.
- **Existing sync is already implemented.** Keep the current WorkManager, merge ordering, conflict handling, and local-first persistence contracts. Cloud & Sync work should be presentation and explicitly scoped capability additions, not a protocol rewrite.
- **Cloud naming.** Use “Cloud & Sync” in user-facing UI while retaining existing Supabase names in APIs, configuration, and technical diagnostics where useful.
- **Build baseline blocker.** Both requested baseline tasks cannot configure the project because Gradle cannot resolve the configured Android Gradle Plugin. Record task results and proceed with source review; do not alter dependency versions or use a build workaround that hides this failure.
- **Room.** Current schema is v20 with explicit v3→v20 migrations. Any future profile/language cache schema addition requires a real migration, schema export, and existing instrumentation migration-test coverage.
- **Network indicator position.** The existing indicator is a persistent Android notification. Android controls where ongoing notifications appear, so the app will explain this as system-controlled instead of exposing a nonfunctional placement selector. The user can still enable or disable it.

## Phase 7

- **Optional sign-in and local data.** Users without a valid saved session open the app in local mode. On a first account sign-in, the unassigned legacy database is associated with that account if no account database already exists; established accounts remain isolated in their own database files.
- **Signed-out Vault access.** The encrypted Vault continues to use its last local account binding after sign-out; only the active Auth identity is cleared. This keeps the local encrypted store reachable while preventing signed-out sessions from scheduling cloud sync.
- **Profile photos.** Photos selected through the system photo picker stay in app storage through the persisted URI grant. No Storage bucket name exists in project configuration, so photo uploads remain disabled until one is configured.
- **Account deletion.** The UI requires two confirmations. The repository calls `delete_my_account` only when the Supabase RPC is installed; no SQL function is included because deletion authority and cloud record retention need server-side policy.
