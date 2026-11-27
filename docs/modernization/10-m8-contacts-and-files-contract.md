# Milestone 8 — Contacts and Files Contract

**Status:** Implemented on `impl/m1-data-safety`; JVM tests executed, instrumentation/device/remote behavior **Unverified**.
**Date:** 2026-11-26.

## 1. Bounded contact imports

`ContactImport.kt` keeps the 10 MB transfer cap and adds fixed limits: 5,000 contact rows, 32 columns
per row, 2,000 characters per field, 12 phone numbers per contact, 200 properties per vCard, and 200
XLSX zip entries. CSV parsing rejects an unclosed quoted field; malformed VCF/XLSX input produces a
fixed error instead of an empty successful import; legacy `.xls` is rejected with an upgrade message.
XLSX reading tracks remaining bytes across the shared-strings and first-worksheet entries, so a
crafted archive cannot buffer an unbounded worksheet. No parser error exposes a raw exception body or
file content.

## 2. Atomic, tombstone-aware import

`planContactImport` is pure and JVM-testable. It normalizes phone digits and emails, detects
duplicates inside the incoming batch, and includes tombstoned rows because the caller supplies
`ContactDao.getAllForBackup()` rather than the filtered `getAll()` flow. Therefore deleting a contact
and re-importing the same number or email does not recreate it.

`persistContactImportPlan` puts every insert in one Room transaction. A failure rolls back the whole
batch. `ContactsViewModel` owns a typed `ContactImportState` (`IDLE`, `READING`, `SAVING`, `SUCCESS`,
`FAILURE`) and shows fixed summary/failure copy; raw parser and database error text is never rendered
or logged. The document picker no longer offers the unsupported `.xls` MIME type.

## 3. File links and re-grant

`FileRepository.add/reconnect` validates a `FileLinkDraft` before persistence. A known size over
10 MB returns `TOO_LARGE`; an unreadable `content://` URI returns `ACCESS_DENIED`; neither result
writes a row. An unpersistable but currently readable URI is stored with `missing = true` so the UI
can warn that the link is temporary. `markMissing`/`markAvailable` update the existing `missing`
column, and reconnecting writes a readable URI, clears `missing`, refreshes metadata, and marks the
row dirty.

The library copy now says files stay with their provider and Zylo stores a link, shows folder, tags,
last-opened and re-grant state from existing columns, and uses fixed error/success messages. There is
no file-size column and no Room schema version change; only DAO queries were added.

## 4. Cloud transfer safety

`SupabaseRestClient.uploadStorageStream` pushes the SAF stream through `BoundedInputStream`, so an
oversized source fails instead of being fully buffered. `downloadStorageTo` writes a bounded response
to a `.tmp` file and renames it into place only after a complete download; a failed restore cannot
leave a partially written document. `FileStorageSync` isolates per-item upload/download failures,
records unreadable files as missing, and reports only an aggregate count with fixed copy.

Pulled `profile_image_path` values are stored as local `cloud:<path>` sentinels via
`profilePathToStoredUri`, never as local URIs. `shouldUploadProfileImage` prevents treating that
sentinel or a raw cloud path as a readable local file. Restore resolves the sentinel back to the
cloud object path and replaces it with a real local file URI when the download succeeds.

## 5. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**
- `.\\gradlew :app:compileDebugKotlin --no-daemon` — PASS.
- `.\\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 143 tests, 0 failures/errors/skips, 27
  suites. The first attempt hit the command timeout before producing a result; the rerun found one
  incorrect bounded-read test expectation, which was corrected, and the identical command passed.
- `.\\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS, 143 tests,
  0 failures/errors/skips, 27 suites; release signing remains unconfigured and no signed APK was produced.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).

New JVM tests cover tombstone-aware duplicate counters, batch duplicate counts, fixed summary copy,
malformed VCF/CSV/XLSX fixtures, row/column/field caps, cloud-sentinel mapping, exact and overflowing
bounded reads, and bounded upload-stream behavior. Instrumentation sources assert SAF denial without
persistence, size-cap rejection, missing-to-regrant persistence, all-or-nothing Room import rollback,
and exact denied-file UI copy.

**Unverified:** no connected/device/emulator execution; no real SAF grant/revoke flow, real
ContentResolver denial, or device OOM measurement; no real Supabase upload/download, RLS, storage
policy, Edge Function or restore run; no Compose visual review, TalkBack, 360dp/2× font, dark mode or
contrast check; no signed release, CI, benchmark or Play-policy check.
