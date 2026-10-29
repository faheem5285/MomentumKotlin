# Supabase setup for Momentum

This repository is prepared to use Supabase, but it does not upload or synchronize any app data yet. Room remains the existing local database.

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

## Current scope

- Supabase Auth and PostgREST client dependencies are included.
- The app has Internet permission.
- A central client provider is included.
- No app records are migrated, uploaded, or deleted yet. Data sync must be implemented per module with ownership rules and retry/conflict handling, after authentication and database policies are tested.
