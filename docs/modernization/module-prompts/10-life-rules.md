# Module Prompt 10 — Life Rules

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 2, 6, 9, 11 and this prompt. Preserve user-written rules and attachments. Report only tests actually executed.

## 1. Actual source files involved
- `features/liferules/LifeRulesScreen.kt` — list/pinned/editor, attachment picker, `folderPath` handling at `:409,421-428`, "stored on this device" copy at `:228-233`.
- `features/liferules/data/LifeRuleRepository.kt` — CRUD/soft delete.
- `core/data/Data.kt` — `LifeRule` (`:282-298`), `LifeRuleFile` (`:300-317`) with FK cascade, `upsertForSync` (`:705`).
- `core/data/sync/SupabaseSyncRepository.kt` — rule/file sync (`:264-277,489,670-671`); `FileStorageSync.kt:40-43,74-78` — attachment bytes upload/restore.
- `core/data/backup/MomentumBackup.kt` — rule/file export.

## 2. Existing functionality (verified)
- User-written rules/notes with kind, pinned flag, files/folder metadata; encryption not applied; rules and file metadata sync to cloud and backups; attachment bytes uploaded to Storage.

## 3. Problems discovered
- "Stored on this device" copy misleading when signed in/sync enabled (LR1).
- Editing a rule resets `folderPath` to empty; new attachments get empty folder while existing keep theirs (LR2).
- Sync REPLACE of life rule can cascade-delete attached files (DB5).
- No dedicated tests; Phase7 docs overstate shared logic coverage (LR4).

## 4. Recommended UI improvements
- Simple list with pin/search, readable detail view, focused editor with clear attachment management.
- Folder grouping or per-attachment folder editing consistent with stored metadata.
- Accurate storage copy derived from sync module state: "Stored locally; included in cloud sync when enabled."
- Shared empty/error states and accessible attachment actions.

## 5. Recommended backend and architecture improvements
- Initialize editor folder from existing attachment metadata; avoid resetting on edit.
- Wrap rule + file updates in transactions; suppress sync until commit.
- Change sync upsert to non-destructive update preserving child files.
- Move complex editor state into a ViewModel/state holder for testability.

## 6. Useful features to add
- Rule templates/categories consistent with habit templates.
- Attachment preview/size limits aligned with prompt 09.
- Search across rule title/body via global search (prompt 22/15).

## 7. Unnecessary elements to simplify or remove
- Unused folder fields if no grouping UI is planned (document data-only state instead).
- Duplicate file-picker entry points; stale "device-only" copy.

## 8. Data safety requirements
- Preserve existing rule bodies and attachments; soft-delete with tombstones.
- No destructive migrations; preserve UUID/`updatedAt` for sync.
- Backups must keep rule/file sections compatible with current format.

## 9. Security considerations
- Rules may contain personal information; no logging of content.
- Attachment URIs are account-scoped via sync; do not leak across account switches.
- Accurately disclose cloud upload of attachments.

## 10. Required tests
- Repository transaction tests for rule+file edits.
- Sync test: remote rule update preserves attachments.
- Editor tests for folder initialization and attachment add/remove.
- Backup round-trip with rule files; Compose empty/error tests.

## 11. Dependencies on other modules
- `core/data` (prompt 21), sync/Storage (prompt 17), backup (prompt 18), Files attachment patterns (prompt 09), design system (prompt 20).

## 12. Acceptance criteria
- Editing doesn't drop folder metadata or attachments; sync updates preserve children.
- Storage copy accurate; all executed tests pass.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` shipped migrations; `app/schemas/*`.
- `core/data/sync/*` (prompt 17), `MomentumBackup.kt` (prompt 18).
- `app/Navigation.kt` (prompt 01), `core/ui/Theme.kt` (prompt 20).
