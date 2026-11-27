# Module Prompt 18 — Backup & Restore

Standalone OpenCode prompt. This is a high-risk data module. Implement on a dedicated branch with failing tests first. Read audit sections 6 (DB9/DB10), 7 (S15/S16), 10 (T5), 11, 12, 13 and this prompt. Never omit user-data tables silently, never apply a partially validated restore, and never export vault secrets. Report only tests actually executed.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/core/data/backup/MomentumBackup.kt` — format constants (`:19-24`), `export`/`exportBytes` (`:38-183`), sections (`:168-177`), restore (`:205-441`), parser/version checks (`:467-524`).
- `WeeklyBackupWorker.kt` — SAF folder/settings (`:24-43`), 7-day periodic job and pruning (`:45-83`).
- `core/settings/MomentumSettingsRepository.kt` — `MomentumSettingsBackup` key mapping/types (`:96-169`).
- `features/settings/AppSettingsScreen.kt` — SAF export/import UI and preview (`:92-172,285-296`).
- `core/data/Data.kt` — v22 schema (`:910-914`), four language tables (`:1396-1407`), `sync_state`.
- `core/data/sync/SupabaseSyncRepository.kt:526-529` — first-sync backup call.
- Tests: `MomentumBackupRoundTripTest.kt:183-193`, `MomentumSettingsMigrationTest.kt`.
- `AndroidManifest.xml` (`allowBackup`) and SAF/URI permission handling in `AppSettingsScreen.kt`.

## 2. Existing functionality (verified)
- Backup format is v3 and requires exact `schemaVersion == 20`; legacy v2 accepts 11/13 and has upgrade paths. Size guards are 10 MB and 10,000 rows per section.
- Export includes 16 user-data sections plus budgets/settings: habits, completions, skips, reminders, expenses, loans, files, to-dos, contacts, loan people/attachments/payments, category budgets, life rules/files, custom notifications.
- Explicitly excluded: vault entries/secrets, file contents, profile cache and `sync_state`; UI warns JSON is unencrypted.
- Restore runs in a Room transaction; `replace` deletes app tables, `merge` inserts only missing UUIDs. Settings are imported with two DataStore edits.
- Weekly backup worker can create a SAF document, prune to five copies and record `last_backup_at`, but no production UI enables it.
- The same exporter creates `filesDir/sync-first-local-backup.json` before first sync.

## 3. Problems discovered
- Backup schema is stale: Room is v22, but v3 requires schema 20; the round-trip test codifies schema 20. New backups can become invalid as schema evolves.
- Four language user-data tables (`vocabulary_words`, `grammar_notes`, `review_states`, `translation_history`) exist at v22 but are omitted from export and parser validation — silent study-data loss.
- JSON is plaintext and includes contacts, loan people and file URIs/PII; UI warning is general and does not say contacts are included.
- `replace` restore does not clear `sync_state`, so stale cursors/conflict counts can skip rows or misreport status after restore.
- `merge` only checks UUID existence; newer backup rows never update older local rows, and imported `dirty` flags can mark restored rows dirty.
- Settings import performs two separate DataStore edits inside the Room transaction; a failure can leave DB rows and settings/budgets inconsistent.
- Weekly backup is unreachable from the shipped UI.
- File URIs are references only; restore cannot recover file contents and must not claim it does.
- `sync-first-local-backup.json` is a durable plaintext PII copy with no user-facing deletion/expiry.
- Legacy budget key `budget` is exported with no reader (prompt 15).

## 4. Recommended UI improvements
- Add weekly-backup controls: SAF folder picker, enable/disable, last-backup time, retention count, permission-loss state.
- Show a per-section manifest before import: counts, format/schema version, included/excluded categories, language coverage, contacts/PII and plaintext warnings.
- Explain merge semantics (newer backup rows do not overwrite newer local rows today; change this with timestamp-aware merge) and replace semantics (`sync_state` reset).
- Offer optional backup encryption with explicit passphrase/recovery wording and legacy import support.
- Show backup failure/success states without raw exception messages; never imply vault or file contents are included.

## 5. Recommended backend and architecture improvements
- Introduce format v4 with a capability/compatibility matrix instead of exact Room-version equality; keep v1/v2/v3 readers importable.
- Include all four language tables in export/parser/validation; add regression tests tied to v22+.
- Clear/reset `sync_state` on replace (or include it with explicit semantics); coordinate with prompt 17.
- Replace UUID-existence-only merge with timestamp/tombstone-aware comparison shared with the sync resolver; never silently downgrade newer local data.
- Stage settings/budget import and apply after Room success, or make one DataStore update with a restore receipt; report partial failures explicitly.
- Wire weekly backup into Settings; handle SAF permission loss and cancel/reschedule jobs with wipe/reset flows (prompt 17).
- Encrypt/expire/delete the first-sync plaintext backup; expose a user-facing deletion control.
- Keep 10 MB/10 k row guards and validate UUIDs/fields before applying anything.

## 6. Useful features to add
- Encrypted backup option using a user passphrase or platform keystore with tested recovery.
- Checksum in the manifest and restore dry-run with conflict list.
- Differential/incremental export and optional cloud upload of the backup file, distinct from row sync.
- Restore receipt: what sections applied, skipped, failed and why.

## 7. Unnecessary elements to simplify or remove
- Dead weekly-backup configuration: wire it up (preferred) or remove it.
- Duplicate warning copy in `AppSettingsScreen.kt`; consolidate into one accurate manifest.
- Legacy V1/V2 readers only after a documented sunset window; keep them while supported installs can produce them.
- Legacy `budget` export key if no import reader remains (prompt 15).

## 8. Data safety requirements
- Never delete or overwrite records outside declared sections; `replace` must remain scoped to backup-supported tables.
- Pre-validate format, schema, UUIDs, row caps and field constraints before any mutation.
- Preserve IDs, UUIDs, `updatedAt`, `deleted`, `dirty` and minor-unit money values.
- Never export vault secrets; never claim file contents were restored; preserve language review state.
- On failure, leave DB and settings unchanged or explicitly report a partial restore with a receipt.
- No destructive Room migration fallback; schema changes require additive migrations and exported schemas.

## 9. Security considerations
- Backups are plaintext JSON with PII; document and optionally encrypt. Do not write backups to world-readable locations.
- SAF URI permissions can expire; surface stale grants instead of silently failing.
- Do not log backup contents, contact names, file URIs or vault data.
- Keep size/row caps to bound malicious-file denial of service.

## 10. Required tests
- v4/schema22 round-trip covering all 16 original sections plus four language tables and review state.
- Legacy v1/v2/v3 import and rejection of wrong format/schema.
- Replace semantics including `sync_state` reset; merge with older/newer `updatedAt` and tombstones.
- Failure injection: invalid UUID, oversize file, >10k rows, DataStore failure — assert no partial DB/settings apply.
- Weekly worker enable/schedule/prune-to-five, missing folder and SecurityException paths.
- First-sync backup marker created once, encrypted/expiry behavior if added, user deletion control.
- Assert vault plaintext/secrets never appear in exported bytes. No tests were executed in this audit.

## 11. Dependencies on other modules
- Prompt 17 sync (`sync_state`, first-sync backup, conflict semantics), prompt 21 Room schema, prompt 15 Settings UI/DataStore, prompt 19 language tables, prompt 11 vault exclusion, SAF permissions.

## 12. Acceptance criteria
- Backups include every user-facing local module, including language data; legacy backups still import.
- Replace leaves sync state consistent; merge is deterministic; no partial restore; settings and budgets restored atomically.
- Weekly backup reachable and functional; PII exposure documented; vault secrets never exported.
- All executed tests pass.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` shipped schema/migrations (prompt 21).
- `core/data/sync/SupabaseSyncRepository.kt` first-sync backup contract (prompt 17).
- `features/vault/VaultCrypto.kt` and vault records (prompt 11).
- `MomentumBackupRoundTripTest.kt` format assertion at `:183-184` — update only with an intentional v4 contract test.
- `MomentumSettingsRepository.kt` key names without migration tests.
