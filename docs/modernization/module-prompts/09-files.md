# Module Prompt 09 — Files Library

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 2, 6, 7 (S22), 9, 12 and this prompt. Files may be sensitive; preserve SAF links and synced metadata. Report only tests actually executed.

## 1. Actual source files involved
- `features/files/FilesScreen.kt` — list/folders/search/filter, SAF add (`takePersistableUriPermission` in empty catch at `:97-108`), previews (`:390-469`), "Stored on this device" copy (`:297`).
- `features/files/data/FileRepository.kt` — URI-string storage.
- `core/data/Data.kt` — `FileItem` entity (`:227-242`) with name, URI, mime, tags, folder, lastAccessed, `missing`, sync fields; DAO `:604,614`.
- `core/data/sync/FileStorageSync.kt` — whole-file `readBytes()` uploads (`:18-45`) and restore (`:47-89`).
- `core/data/backup/MomentumBackup.kt:131-135` — file metadata export; `docs/supabase-schema.sql` Storage policies.

## 2. Existing functionality (verified)
- Users link files from SAF providers, organize into folders, search/filter, preview images (downscaled), PDF first page, text (capped 1 MB).
- Metadata (tags, folder, last access, missing flag) is persisted/synced/backed up but not exposed in UI.
- Cloud sync uploads bytes to `momentum-files` Storage bucket under UUID paths; restores missing local files.

## 3. Problems discovered
- Persistable permission failures swallowed while URI still stored → broken links later (F1/S22).
- "Stored on this device" inaccurate for cloud-backed SAF providers.
- Preview decode catches `Exception` but not `OutOfMemoryError`; uploads read whole files without size caps (F2/P1).
- `missing`/`lastAccessed` never used; docs claim tags/thumbnails/missing UI (F3).
- Storage tombstone does not delete objects; restore 404 fails whole sync (prompt 17 scope).

## 4. Recommended UI improvements
- Safe file handling: explicit add success/failure, re-grant access flow, missing-file banner using existing `missing` flag.
- Search/organization by folder/tags; expose existing tag editing and last-access display or document as metadata-only.
- Accurate copy: "Linked from your device/provider" unless copied into app storage.
- Clear empty/error/preview-unavailable states; shared components; accessible previews.

## 5. Recommended backend and architecture improvements
- Check persistable URI grant result; reject or warn; optionally copy small files into app-private storage with explicit user choice and migration plan.
- Stream uploads/downloads with caps; atomic temp-file write + DB update; handle 404 as missing without failing core sync (coordinate prompt 17).
- Bounded bitmap/text decoding with `OutOfMemoryError` handling.
- Centralize URI permission checks in a repository.

## 6. Useful features to add
- Tag editor and folder picker using existing fields.
- Size/mime indicator and quota warning before cloud upload.
- "Open with" and share actions with clear permission implications.

## 7. Unnecessary elements to simplify or remove
- Duplicate file-add entry points; unused preview code paths after consolidation.
- Stale documentation claims about thumbnails if not implemented.

## 8. Data safety requirements
- Never delete user files from SAF providers; only delete app-created synced copies with explicit user action.
- Preserve URI strings and metadata for existing records; no destructive migration.
- Backups contain metadata/URIs, not file contents — state this clearly.

## 9. Security considerations
- Files may be sensitive: no logging of filenames/paths where avoidable; private notif/lockscreen handling.
- Storage RLS (`owner_id = auth.uid()`) must be preserved; URIs must not be shared across accounts.
- SAF grants can persist beyond app data clears; explain re-grant behavior.

## 10. Required tests
- SAF permission-denied/expired-grant UI tests with fakes.
- Large-file upload size-cap test; preview OOM guard test.
- Sync restore tests (404 tolerated, atomic write, dirty flag cleared only after success).
- Round-trip backup test preserving file metadata.
- Compose tests for add/search/folder/missing states; narrow-screen previews.

## 11. Dependencies on other modules
- `core/data` entity/DAO (prompt 21), sync/storage (prompt 17), backup (prompt 18), permissions (prompt 03), design system (prompt 20), loans/life-rule attachments (prompts 07/10).

## 12. Acceptance criteria
- Broken SAF links surfaced with recovery action; no OOM on large files.
- Metadata fields either exposed or documented as data-only; accurate storage copy.
- All executed tests pass; no user files deleted by app refactor.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` shipped schema/migrations.
- `core/data/sync/*` and Storage SQL policies (prompt 17).
- `MomentumBackup.kt` format (prompt 18).
- `core/ui/Theme.kt` (prompt 20).
