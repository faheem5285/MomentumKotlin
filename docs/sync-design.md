# Phase 8 sync design

## Contract

Room remains the source of truth. The app reads and writes local Room data
without requiring a network or Supabase session. A unique device id identifies
the writer, while each local sync row keeps its existing UUID, `updatedAt`,
`deleted`, and `dirty` metadata. Sync is incremental and runs in a
network-constrained WorkManager job, after debounced local changes, or from
Settings with “Sync now”.

Each cloud table has `id`, `user_id`, `updated_at`, `deleted_at`, `device_id`,
and `version`, plus the entity fields. RLS is enabled on every table and every
policy compares `user_id` to `auth.uid()`. The Android app uses only the
publishable key; credentials remain user-level Gradle properties.

## Push, pull, and conflicts

Push sends dirty rows in bounded batches. A row is accepted only when its
`updated_at` is newer than the server row; an equal timestamp is resolved by
the lexicographically larger `device_id`. The server never clears a tombstone
because a client sends an older live row. Successful pushes clear the local
dirty bit only when the returned server version still matches the pushed
timestamp.

Pull fetches rows whose `updated_at` is newer than the per-table cursor. The
cursor is advanced only after the whole table page is applied in a Room
transaction. Last-writer-wins is applied locally using `(updated_at,
device_id)`; a deleted winner remains a tombstone. A local conflict counter is
incremented when both local and remote rows changed since the last successful
cursor. No cloud row can resurrect a local tombstone unless it wins by the
same explicit ordering rule. The Settings screen exposes pending rows, last
sync, errors, pause state, and conflicts.

The first authenticated sync exports the existing Phase 1 backup to app-private
storage before any push or pull. Sync never replaces local data silently:
remote winners are applied as a merge result with counts, and the UI presents
that result before destructive cleanup. Sign-out clears only the encrypted
session token; local Room data remains.

## Auth and vault

The refresh token is Keystore-protected. An access token is held in memory and
refreshed on expiry; failed refresh leaves the app usable offline. Vault rows
contain only the stable record id, nonce, ciphertext, timestamps, deletion
metadata, and version. The VMK never enters a request, database sync payload,
log, or backup. Vault titles, usernames, passwords, notes, folders, tags, and
TOTP values remain inside the encrypted ciphertext. A second device requires
the master password (or an optional offline recovery key) to unwrap its VMK.

## Safety limits and rollout

All requests use HTTPS, bounded pages, exponential retry, and no retry for
authentication or RLS failures. Delete-cloud-data requires an explicit
confirmation and never deletes local rows. The SQL in
`docs/supabase-schema.sql` must be applied before enabling sync. Until the
Settings sync UI and device tests are complete, sync remains opt-in and
disabled by default.
