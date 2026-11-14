# Supabase setup for Momentum

This repository uses Supabase for automatic sync after local record changes
when a user account is connected. Room remains the source of truth. Read
[sync-design.md](./sync-design.md) before applying
the paste-ready schema in [supabase-schema.sql](./supabase-schema.sql).
The schema also creates a private `momentum-files` Storage bucket for file contents.

## 1. Add credentials locally (never commit them)

In your **user-level Gradle properties** file, not the repository, add:

```properties
SUPABASE_URL=https://YOUR_PROJECT_ID.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_SB_PUBLISHABLE_KEY
```

On Windows, this file is usually:
`C:\Users\YOUR_WINDOWS_USERNAME\.gradle\gradle.properties`

Use the API URL shown in your Supabase project dashboard and the **Publishable key**. Do not use a Secret key or the database password in the Android app. Do not paste credentials into GitHub files or chat.

You can alternatively provide `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` as environment variables for the Gradle process.

## 2. Sync and build

After adding the values, pull the latest `main` branch in VS Code and sync Gradle. If values are absent, the app can still build; `SupabaseProvider.isConfigured` will be false and `createClientOrNull()` will return null.

## 3. Before syncing personal data

Do not create permissive public policies. First configure Supabase Auth, then create tables with a `user_id` UUID referencing `auth.users(id)`, enable Row Level Security (RLS), and add policies that restrict each operation to `auth.uid() = user_id`.

Never put a service-role/secret key in an Android app. A publishable key is not a substitute for RLS.

## 4. Apply the schema

Open the Supabase SQL Editor, paste the complete contents of
`docs/supabase-schema.sql`, and run it while signed in as the project owner.
This creates the tables, RLS policies, and private Storage policies. Do not
make the bucket public.

## 5. Enable project wide cloud cleanup (optional)

The Supabase screen's **Delete all users’ cloud data** action clears Momentum
sync tables and every object in the private `momentum-files` bucket. It never
deletes `auth.users` or server-side sessions. A reset generation tells updated
app installations to clear their local database, preferences, and app-private
files and remove their local session when they next check the marker.
Offline devices are cleared only after reconnecting and opening the updated
app. Android-owned usage history and OS permission grants are outside the
app's control. This operation is project wide and cannot be authorized by the
mobile publishable key alone. The updated schema pauses authenticated app writes
through RLS while cleanup is running. The reset generation is advanced only
after all table rows and storage objects have been deleted successfully. If a
cleanup request fails, this device keeps its local data; cloud cleanup may be
partial and the write lock stays active, so fix the reported issue and retry.
The failed attempt leaves the lock ready for a safe retry. If the function is
terminated before it can report the failure, retry after the 10-minute
stale-lock window.
Update every device to a build containing the wipe-generation check before
running a wipe; older app versions cannot clear their local data and may sync
it back to Supabase.

Before deploying the updated function, apply the current
`docs/supabase-schema.sql` in the Supabase SQL Editor. It creates the
`momentum_wipe_state` marker table needed to trigger local device resets.
If the app reports `PGRST205` or says `public.momentum_wipe_state` is missing,
run `docs/supabase-wipe-state.sql` in the SQL Editor for the same project used
by the app. The repair script safely creates the marker if needed and reloads
the Data API schema cache. Then return to the app and retry. If the error
continues, verify the dashboard project reference matches the app's
`SUPABASE_URL`.

Deploy `supabase/functions/admin-wipe-cloud/index.ts` as the
`admin-wipe-cloud` Edge Function to the same project whose URL is in
`SUPABASE_URL`. The function validates the caller's access token and accepts
requests only when the caller has
`app_metadata.role = "admin"` or includes `"admin"` in `app_metadata.roles`.
Set that server-managed metadata for the intended admin account in the SQL
Editor (replace the email with your admin login):

```sql
update auth.users
set raw_app_meta_data = coalesce(raw_app_meta_data, '{}'::jsonb) || '{"role":"admin"}'::jsonb
where email = 'YOUR_ADMIN_EMAIL';
```

User-editable `user_metadata` is not checked. The Edge Function uses Supabase's
server-injected secret API key to perform the project-wide deletion. Never put
a secret/service-role key in Android, Gradle properties, or this repository.
The function supports both the legacy injected environment variables and the
newer `SUPABASE_PUBLISHABLE_KEYS` / `SUPABASE_SECRET_KEYS` variables.

Deploy with the Supabase CLI from the repository root. Install the CLI first
if needed. In the VS Code terminal opened at the repository root, initialize
the Supabase CLI config (the repo already has the `supabase/functions` folder),
then authenticate, link the right project, and deploy. Use `npx` because the
CLI was installed as this repository's npm dependency:

```text
npx supabase init
npx supabase login
npx supabase link --project-ref YOUR_PROJECT_REF
npx supabase functions deploy admin-wipe-cloud --remote YOUR_PROJECT_REF --yes
```

Find `YOUR_PROJECT_REF` in the project dashboard URL; it is the part between
`https://` and `.supabase.co`. Confirm the app's `SUPABASE_URL` uses that same
project. After deployment, reopen the app and sign in as the admin account.
Successful execution deletes all Momentum cloud records and stored file
objects across users, while leaving Auth accounts intact. Local data is cleared
when each device runs an app build containing the remote wipe-generation check
and reconnects to the project.

## Current scope

- Supabase Auth and PostgREST client dependencies are included.
- The app has Internet permission.
- A central client provider is included.
- Vault entry rows contain ciphertext and nonce. The separate
  `momentum_vault_keys.key_material` table stores a Base64-encoded account
  recovery key under per-user RLS; this does not provide end-to-end encryption
  against the Supabase project administrator. See [vault-security.md](./vault-security.md).
- All Room records are synchronized after authentication. File metadata is
  stored in Postgres and file bytes are stored in the private Storage bucket.
- App records are not uploaded successfully until the schema/policies have
  been applied and tested.
- The Supabase module shows connection status, account-visible row counts for
  the sync tables, and object/byte usage for the private `momentum-files`
  bucket. It does not show project billing quotas because those require the
  Supabase dashboard or a server-side management credential. The module
  displays this limitation in its storage usage card rather than showing an
  inaccurate fixed quota.
- The project-wide cleanup action is protected by the deployed admin-only Edge
  Function and retains Auth accounts and sessions.
