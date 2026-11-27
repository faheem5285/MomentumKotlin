# Module Prompt 03 — First-Install Onboarding & Permission Guidance

Standalone OpenCode prompt. This is a high-priority UX module. Read audit sections 4 (U1), 6/7 (permission findings), 8, 11, 13 and this prompt. Implement on a dedicated branch. Do not block app usage; never claim a permission is enabled without verifying it. Report only tests actually executed.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/app/MainActivity.kt` — `permissionSetupSeen` gate at `:164-165,265-273`.
- `app/src/main/java/com/faheem/habits/features/settings/PermissionsSetupScreen.kt` — `SetupPermission` model, `usageGranted()`, permission cards at `:107-210`.
- `app/src/main/java/com/faheem/habits/features/settings/SettingsViewModel.kt` — `permissionSetupSeen`, `markPermissionSetupSeen`, `clearForProjectWipe`.
- `app/src/main/java/com/faheem/habits/core/settings/MomentumSettingsRepository.kt` — `permission_setup_seen` preference.
- `app/src/main/AndroidManifest.xml` — permissions actually declared: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `FOREGROUND_SERVICE*`, `QUERY_ALL_PACKAGES`.
- `core/notifications/NotificationSystem.kt` — `canSchedulePrecise` and notification permission checks; `ZyloNotifications.kt` — channel/post guards; `features/screen_time/ScreenTimeScreen.kt:56-61` — usage access check; `features/screen_time/InstagramScrollCounterService.kt:147-151` — accessibility enabled check; `res/xml/instagram_scroll_accessibility_service.xml`.

## 2. Existing functionality (verified)
- First launch shows one permission setup screen; users can continue without granting anything; it is revisitable from Settings.
- Progress is recomputed live on resume; unsupported/manufacturer cards show N/A and are excluded from the count.
- Usage access verified via AppOps; notification/exact-alarm state reported; overlay description currently wrong (says "floating network indicator"; actually Instagram counter + battery animation).
- `Permission_setup_seen` is a DataStore boolean; system back on first run has no `BackHandler` to persist it.
- Signed-out local messaging exists in Profile but not on Home/Settings.

## 3. Problems discovered
- No card-based onboarding; no task-level progress; no remind-later/don't-show-again; no revisit dashboard.
- Tri-state missing → possible repeated gate flash (U1).
- Overlay permission description inaccurate (device audit F3); high-privilege permissions need clear justification.
- No permission-status refresh guarantee after returning from system settings beyond resume-based checks.
- No signed-out cloud-backup card with accurate local/cloud/sync distinction (requirement 7).

## 4. Recommended UI improvements
- Prioritized cards, shown a few at a time: Welcome to Zylo; Create first habit; Add first task; Track first expense; Explore features; Enable important permissions; Protect your data with cloud backup.
- Each card: clear title, short explanation, one primary action, secondary action where useful, dismiss/remind-later/don't-show-again where appropriate.
- Permission cards: explain why before opening settings; show verified status; refresh on `ON_RESUME`; never interrupt repeatedly; keep essential features usable when denied.
- Signed-out card text: "Local data is still available on this device"; "No cloud backup — local data may not be recoverable if device lost/app data erased"; "Sync unavailable — latest changes may not yet be synchronized"; "Backup failure — latest backup could not be completed."
- Revisit onboarding from Settings; do not block app access.

## 5. Recommended backend and architecture improvements
- Add local onboarding progress store (DataStore) with task ids, completion/dismiss/snooze timestamps and version. Store must be scoped to app install, not account, and preserved by account switches.
- Use tri-state `unknown/pending/done` loading to avoid gate flash; handle system back explicitly.
- Centralize permission checks in a repository/use-case returning sealed states (`Granted`, `Denied`, `NeedsSettings`, `Unsupported`) and reuse from Home/Settings/feature screens.

## 6. Useful features to add
- "Feature tour" card linking to key modules with deep links.
- Permission health summary in Settings ("N of M optional access enabled").
- Contextual limitation banners inside features whose permission is denied.

## 7. Unnecessary elements to simplify or remove
- Duplicate permission explanations scattered across screens; consolidate into shared cards.
- Manufacturer-specific cards under the main "Android access" section; split into separate "Manufacturer checks" with N/A explanation.
- Wrong overlay copy; replace with actual uses.

## 8. Data safety requirements
- Onboarding progress must never be reset by project wipe unless explicitly intended; coordinate settings clear with prompt 17.
- Do not create/sync records as a side effect of onboarding actions except the user's explicit "create first habit/task/expense" action.
- Local data remains available when sign-in is deferred; never imply data loss.

## 9. Security considerations
- Do not request permissions not used: no Contacts permission (contacts are SAF-imported), no `USE_EXACT_ALARM`, no unnecessary foreground service.
- Verify `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, accessibility service, and FGS special-use necessity; prepare Play declarations or plan removal (policy outcome unverified).
- Never claim POST_NOTIFICATIONS/exact alarms/usage access/accessibility enabled without checking actual OS state; detect revocation on resume.

## 10. Required tests
- DataStore onboarding persistence/dismiss/snooze unit tests.
- Compose tests for card prioritization, primary/secondary actions, dismiss/remind-later, and revisit flow.
- Permission-state tests (granted/denied/unsupported) with fakes; verify no repeated prompting.
- Device checklist: grant/deny/revoke each permission, return from system settings, system back on first run, large font/narrow screen.

## 11. Dependencies on other modules
- Prompt 01 app shell gate, prompt 02 Home card slots, prompt 15 Settings, prompt 14 notification permission APIs, prompt 12 screen time/accessibility, prompt 05 auth status, `core/ui` components.

## 12. Acceptance criteria
- Fresh install shows relevant cards progressively, never all at once; completed/dismissed cards hide appropriately.
- Permission status accurately verified; explanations precede system-settings navigation; no repeated interruptions.
- Signed-out messaging distinguishes local/cloud/sync/backup failures accurately.
- Onboarding is revisitable and non-blocking; all executed tests pass.

## 13. Files that must not be changed without justification
- `AndroidManifest.xml` permission set (verify necessity first; coordinate with prompts 10/14).
- `core/data/sync/*` and `core/data/Data.kt` (data reset semantics owned by prompts 01/17).
- Auth/session implementation (`core/data/supabase/*`, prompt 05/16).
- Notification scheduling internals (`core/notifications/NotificationSystem.kt`, prompt 14).
