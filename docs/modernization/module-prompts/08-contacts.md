# Module Prompt 08 — Contacts

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 2, 6, 7 (S22), 9, 10 and this prompt. Contacts contain PII; never log or expose it. Report only tests actually executed.

## 1. Actual source files involved
- `features/contacts/ContactsScreen.kt` — list/search, add/edit, photo picker, import flow (`importContacts` `:75-110`), `phoneKey` dedupe (`:52-55`), photo grant handling (`:297-301`).
- `features/contacts/ContactImport.kt` — VCF/CSV `readText` (`:16-27`), XLSX `ZipInputStream`/`readBytes` (`:157-167`).
- `core/data/Data.kt` — `Contact` entity (`:146-159`) with name/phone/email/address/notes/photo URI and sync metadata; DAO deletes/upserts (`:772-773,780-781`).
- `core/data/sync/SupabaseSyncRepository.kt` — contact push/pull; `core/data/backup/MomentumBackup.kt:102-108` — plaintext PII export.
- `app/src/test/java/com/faheem/habits/features/contacts/ContactImportTest.kt` — existing parser tests.

## 2. Existing functionality (verified)
- Local contacts with search/organization, SAF-based VCF/CSV/XLSX import, optional photos via picker, dedupe by phone digits/email, sync and backup of contact fields.

## 3. Problems discovered
- No Android Contacts permission is used (privacy-positive) but SAF imports read whole files into memory; XLSX lacks zip-bomb/size guards (C1).
- Import inserts row-by-row without a transaction → partial import on failure (C2).
- Dedupe heuristics simplistic (C3); empty-contact rows possible.
- Persistable photo URI failures swallowed (C4/S22); photo can break after restart.
- Contact PII exported in unencrypted JSON and synced in plaintext rows; UI warning is broad (C4/S16).
- No UI tests for contacts flow.

## 4. Recommended UI improvements
- Easy search and organization; clear add/edit forms and photo handling.
- Explicit import preview (count, duplicates, errors) with cancel/confirm.
- Clear empty/error/partial-import states; accurate "linked from provider" wording if URI-only storage is retained.
- Warning at backup export that contacts/PII are included.

## 5. Recommended backend and architecture improvements
- Bounded streaming reader with file-size caps and XLSX entry/uncompressed byte limits.
- Wrap dedupe+insert loop in Room transaction; report rollback clearly.
- Improve dedupe: require at least one contact method; normalize phone numbers; distinguish duplicate-number vs duplicate-contact cases.
- Check `takePersistableUriPermission` result; warn or copy photo into app storage where feasible.

## 6. Useful features to add
- Merge-duplicate UI for contacts with same number/email.
- Export selected contacts to VCF (with explicit privacy warning).
- Tags/groups if consistent with other modules' organization model.

## 7. Unnecessary elements to simplify or remove
- Overly complex import format options with low usage; keep VCF/CSV/XLSX only if justified by tests.
- Redundant fields with no UI after data audit; do not delete persisted values silently.

## 8. Data safety requirements
- Preserve all existing contact rows and photo URI strings during refactor; no hard deletes in UI.
- Transactional imports must roll back fully on failure; no partial orphan rows.
- Preserve UUID/dirty/deleted metadata for sync and backup compatibility.

## 9. Security considerations
- Never log contact names/phones/emails/addresses or parser input.
- Contact data is synced to cloud when enabled and included in plaintext JSON backups; UI must state this accurately.
- Handle SAF permissions explicitly; revoke-aware states when provider access is lost.

## 10. Required tests
- Parser tests with malicious/huge fixtures, malformed VCF/CSV/XLSX, and dedupe edge cases.
- Import rollback test (failure mid-import leaves no rows).
- Photo URI permission-denied UI test.
- Sync/backup round-trip tests preserving contact fields and tombstones.
- Compose tests for search, add/edit, import preview, empty/error states.

## 11. Dependencies on other modules
- `core/data` DAO (prompt 21), sync (prompt 17), backup (prompt 18), design system (prompt 20), SAF/permission guidance (prompt 03), Files/loans attachment patterns (prompts 08/07).

## 12. Acceptance criteria
- No OOM on crafted import files; imports atomic; duplicate handling documented and tested.
- Persistable permission failures surfaced; PII never logged.
- All executed tests pass.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` shipped schema/migrations.
- `core/data/backup/MomentumBackup.kt` format version (prompt 18).
- `core/data/sync/*` conflict logic (prompt 17).
- `core/ui/Theme.kt` (prompt 20).
