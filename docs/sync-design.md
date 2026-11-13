# Phase 8 sync design

## Contract

Room remains the source of truth. The app reads and writes local Room data
without requiring a network or Supabase session. A unique device id identifies
the writer, while each local sync row keeps its existing UUID, `updatedAt`,
`deleted`, and `dirty` metadata. A central Room invalidation observer watches
all synced entity tables. It debounces committed changes for 750 ms, then checks
for dirty rows before scheduling the unique `momentum-sync` WorkManager chain.
The same dirty-row check runs at application startup to recover a local edit if
the process stopped before its debounce finished.
This second check ignores clean rows written while applying a pull or clearing a
successful push, preventing sync from scheduling itself. The Supabase screen's
retry action uses the same worker, so foreground and background sync share one
coordinator. A process-wide mutex also serializes Vault key-guided syncs with
the main worker. WorkManager requires a connected network and uses exponential
backoff. It retries I/O and timeout failures, HTTP 408/429, and HTTP 5xx;
authentication, permission, and other HTTP 4xx failures stop and require
attention. Invalid/expired sessions are not treated as offline errors; the
screen asks the user to sign in again before retrying.

Each cloud table has `id`, `user_id`, `updated_at`, `deleted_at`, `device_id`,
and `version`, plus the entity fields. RLS is enabled on every table and every
policy compares `user_id` to `auth.uid()`. The Android app uses only the
publishable key; credentials remain user-level Gradle properties.

## Push, pull, and conflicts

Dirty rows are pushed in batches of 100; each batch is an idempotent upsert.
After a successful request, the local dirty bit is cleared inside a Room
transaction only if both UUID and `updatedAt` still match the pushed snapshot.
An edit made during the request therefore remains dirty for the follow-up run.
User edits and notification actions that change synced fields set `dirty`,
advance `updatedAt`, and retain their tombstones; local-only file restoration
updates remain clean.
A row is accepted only when its
`updated_at` is newer than the server row; an equal timestamp is resolved by
the lexicographically larger `device_id`. The server never clears a tombstone
because a client sends an older live row. Successful pushes clear the local
dirty bit only when the local version still matches the pushed timestamp.

Pull fetches pages of 200 in `(updated_at, id)` order. The cursor stores both
values and uses a keyset filter, so rows sharing a timestamp are not skipped at
page boundaries. Existing timestamp-only cursors are read inclusively once to
recover any tied rows. The cursor is persisted after all fetched rows for that table have been applied;
individual row applications are not wrapped in one transaction, so a retry may
apply already-seen rows again. Last-writer-wins is applied locally using `(updated_at,
device_id)`; a deleted winner remains a tombstone. A local conflict counter is
incremented when both local and remote rows changed since the last successful
cursor. No cloud row can resurrect a local tombstone unless it wins by the
same explicit ordering rule. The Supabase screen exposes aggregate pending rows, last
successful sync time, conflicts, and errors. Its status is Waiting to sync,
Syncing, Synced, or Needs attention. Retryable errors remain queued with
WorkManager backoff; permanent errors expose a short explanation and expandable
technical detail. Per-table cursors and errors remain separate; the
`momentum_sync` state row records the last fully successful run.

Parent records are processed before their dependents: habits before completions,
skips, and reminders; loan people before loans, then attachments/payments; and
life rules before life-rule files. File objects are uploaded before their
metadata. Independent tables retain separate cursors and are not rewritten when
they have no dirty rows; pulls remain incremental per table.

The first authenticated sync exports the existing Phase 1 backup to app-private
storage before any push or pull. Sync merges remote winners into Room according
to the ordering rule. The current UI does not show a per-run merge summary.
Sign-out clears the local encrypted session token; local Room data remains.

## Auth and vault

The refresh token is Keystore-protected. An access token is held in memory and
refreshed on expiry; failed refresh leaves local app data available offline.
Vault entry rows contain the stable record id, nonce, ciphertext, timestamps,
deletion metadata, and version. Vault titles, usernames, passwords, notes,
folders, tags, and TOTP values remain inside that ciphertext.

**Current Vault cloud-key limitation:** the app separately sends the raw VMK,
Base64-encoded, to `momentum_vault_keys.key_material`. RLS limits normal API
access to the signed-in user's row, but a Supabase project administrator or
service-role holder can read that key and decrypt the Vault rows. Account sign-in
can therefore restore the Vault without asking for the master password. This
is encrypted record storage, but it is not end-to-end encryption against the
Supabase project operator. The exported version-2 Vault backup instead includes
encrypted records and a password-wrapped VMK; restoring that file requires the
original master password. A move to password-wrapped cloud key material would
change cross-device unlock and needs a deliberate migration plan.

## Safety limits and rollout

All requests use HTTPS. Sync pushes are bounded and pulls are keyset-paged.
Cloud diagnostics use Supabase's estimated count mode and run table counts
concurrently. Storage usage scans at most 5,000 objects and labels the result as
a lower bound when the scan is capped. WorkManager uses exponential backoff;
authentication and RLS errors are recorded as failures rather than treated as
transient network errors. Per-table cloud deletion removes cloud
rows only. Project-wide cleanup is a separate admin-only Edge Function that
deletes Momentum cloud rows and stored file objects, then advances the wipe
generation so updated app installations clear local app data. Auth accounts
are preserved. While cleanup runs, the schema's RLS write guard pauses app
writes; the generation advances only after table and storage cleanup succeeds.
Apply `docs/supabase-schema.sql` before sync or project cleanup is enabled.
