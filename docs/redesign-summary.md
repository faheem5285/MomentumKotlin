# Premium redesign summary

## Work completed

- **Screen time:** daily insight, yesterday comparison, seven-day chart, clearer permission flow.
- **Battery:** live state and concise status summary.
- **Network:** live connection state and connection test.
- **Cloud & Sync:** module controls and activity history.
- **Settings:** grouped searchable preferences, appearance/glass controls, module list.
- **Profile:** optional local use, email/Google auth flow, editable cached profile, migration and setup guides.
- **Language:** Room-backed vocabulary and grammar note CRUD, review schedule logic, Home tile and Settings entry. Full Language feature is incomplete; see below.

## Dependencies, storage and permissions

- Added AndroidX Credential Manager and Google ID dependencies for Google sign-in.
- Added ML Kit Translate `17.0.3` and Language ID `17.0.6`; their runtime UI and model lifecycle are not yet implemented.
- Room was v20 before Profile and is v22 in the current source. Migrations 20→21 and 21→22 are present. Room schema v22 has not been exported because Gradle/KSP could not run.
- No new Android permissions were added for the language UI. Existing internet permission supports existing network/auth behavior.

## Tests and verification

- Added Profile validation tests, a Profile migration test update, and a Leitner scheduling unit test.
- Test totals and build status are unavailable: Gradle cannot resolve `com.android.application:8.5.2`, so no compile, test, or lint task reached project code. See [redesign-verification.md](redesign-verification.md).

## Manual setup

- Apply `docs/supabase/profiles.sql` for profile rows and the self-delete RPC; follow `docs/supabase/google-signin-setup.md` and set user-level `GOOGLE_WEB_CLIENT_ID` for Google sign-in.
- `docs/supabase/language.sql` is optional schema guidance only; the application does not sync Language data.
- Configure an avatar Storage bucket separately to enable uploads; photos currently remain local.

## Incomplete work and next steps

- Language still needs dictionary lookup and pronunciation, full vocabulary/grammar editing and filters, quiz/session flow, daily due/streak/reminder experience, ML Kit translation with model management, opted-in MyMemory fallback, translation history UI, and validated JSON/CSV import/export.
- Generate and commit the Room v22 schema, then compile and run migration/unit tests after Android Gradle Plugin resolution is restored. Resolve any source or lint issues those tasks expose.
- Phase 9 review and final gates remain blocked by plugin resolution. ADB was also unable to initialize its home, so device availability and installation were not verified.

## Phone QA checklist

- Open Home and confirm Profile and Language tiles navigate.
- Use the app signed out and offline; confirm local data remains available.
- Add, search, delete and review vocabulary; add, search and delete grammar notes.
- Check 360dp width, TalkBack labels, and 1.3× / 2× font scale.
- After Language completion, check dictionary errors, model downloads/offline translation, privacy opt-in fallback, favorites/history, and import preview.
