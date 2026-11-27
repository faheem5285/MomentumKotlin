# Module Prompt 01 — App Shell, Session Gate & Navigation

Standalone OpenCode prompt. Work only on an implementation branch created for this module; never commit to `main`. First read `docs/modernization/00-complete-repository-audit.md` sections 1, 2, 5, 8 and the relevant rows of sections 14–15. Do not modify application code until you have read the files below. Never delete user data or use destructive Room migration fallbacks. Run tests; report only tests actually executed.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/app/MainActivity.kt` — `MainActivity`, `MomentumApp`, `RestoringSessionScreen`, `notificationTargetFrom`, `scheduleRemindersSafely`.
- `app/src/main/java/com/faheem/habits/app/Navigation.kt` — `MomentumDestination`, `MomentumNavigationHost`, `ModuleScreen`, `route()`, `matchesDestination`.
- `app/src/main/java/com/faheem/habits/app/MomentumApplication.kt` — application-level observers/work enqueue.
- `app/src/main/AndroidManifest.xml` — launcher and `momentum://module` / `momentum://auth` intent filters, receivers/services.
- `core/di/AppModule.kt`; `core/ui/AppIdentity.kt` (module labels/routes); `features/settings/SettingsViewModel.kt` (permission gate state).

## 2. Existing functionality (verified)
- `MainActivity` applies theme from `AppearancePreferences`, restores Supabase session, applies admin wipe checks, shows notices, runs permission setup gate, then hosts navigation.
- All session restore branches set `isAuthenticated = true` (`MainActivity.kt:182,194,199,209,217,296`); `AuthScreen` at `:285-306` is effectively unreachable. Local-only use works offline.
- Navigation has only `home` and `module/{module}?itemId={itemId}&requestToken={requestToken}`; module token is `AppModule.name`; deep links decode enum name case-insensitively. Hidden modules remain deep-link reachable.
- `momentum://auth/*` is parsed in `MainActivity.kt:125-137` outside NavHost; notification taps synthesize a destination with a request token.
- Boot/time-change reminder rescheduling and power-event receivers are wired in the shell.

## 3. Problems discovered
- Implicit auth state machine with unreachable AuthScreen and duplicated auth UIs (audit A1, AU1).
- Tri-state missing: `permissionSetupSeen` defaults false before DataStore emits → possible gate flash; system back can bypass marking it seen (U1).
- Deep-link contract undocumented and split across Activity/NavHost/Profile; hidden modules bypass visibility (U8, F2/F3).
- Business logic (session restore, wipe checks, reminders, power events) colocated in one composable (A3).
- `isAuthenticated` is never set false; sign-out notices handled as strings (AU1).

## 4. Recommended UI improvements
- Extract explicit `AppState` (Restoring / LocalOnly / SignedIn / WipeApplied / SigningOut) with typed notices instead of free-form strings.
- Show a neutral restoring shell until DataStore/auth state is known.
- Centralize deep-link validation at one boundary; document route constants; add hidden-module guard if hiding means inaccessible.
- Keep Home-as-hub unless a persistent nav decision is explicitly made; if changed, update all module callbacks and deep links together.

## 5. Recommended backend and architecture improvements
- Move session restore/wipe orchestration to a repository/use-case invoked from the Activity, not composable `LaunchedEffect`.
- Single auth entry point for sign-in/up; coordinate removal of `AuthScreen` with prompt 16.
- Use `collectAsStateWithLifecycle` for all flows; keep `FragmentActivity`/Hilt injection as-is unless a tested replacement is justified.

## 6. Useful features to add
- Typed deep-link intent parser with unit tests and documented URL map.
- Central `AppNotice` snackbar/banner with severity and actions.
- Boot recovery status diagnostics for reminder scheduling.

## 7. Unnecessary elements to simplify or remove
- Dead `AuthScreen` branch after auth unification (coordinate with module 16).
- Duplicate `momentum://auth` handling paths; decide one owner.
- Unused imports/helpers in shell after refactor.

## 8. Data safety requirements
- Session restore failure must keep local data available; never wipe on `TEMPORARY_FAILURE`.
- Wipe handling must continue to clear settings through `settingsViewModel.clearForProjectWipe()`; do not bypass DataStore clearing.
- Do not change `AppDatabase.activateAccount` or reset logic in this module (owned by prompts 01/17); coordinate any call-site change.

## 9. Security considerations
- Do not log auth tokens/intent data with tokens; clear password-recovery intent data after consumption (coordinate with prompt 16).
- Maintain non-exported receivers and exported-activity intent filters as declared; verify before changing manifest.
- Do not add `USE_EXACT_ALARM`; `SCHEDULE_EXACT_ALARM` is correct for a general productivity app.

## 10. Required tests
- Unit tests for deep-link parsing/matching, destination routing, and notice mapping.
- Compose test for tri-state startup (restoring → local mode) using injected/faked state.
- Instrumentation smoke through real `MainActivity` startup (not `setContentView` shortcut) verifying local-only entry and module navigation.
- Verify on device: notification-tap deep link, `momentum://module`, `momentum://auth` cold start and warm `onNewIntent`.

## 11. Dependencies on other modules
- `core/settings` (`SettingsViewModel`, DataStore), auth/session APIs (`core/data/supabase`), `core/notifications` scheduling, `core/ui` theme/components, all feature screens dispatched by `ModuleScreen`.

## 12. Acceptance criteria
- Local-only users reach Home without sign-in in all tested restore outcomes.
- No gate flash for returning users; system back during first-run gate persists completion.
- All documented deep links open correct modules; hidden-module policy explicit.
- App shell compiles and all executed tests pass; no sensitive data in logs.

## 13. Files that must not be changed without justification
- `app/src/main/AndroidManifest.xml` (permission/FGS intent-filter contract).
- `core/data/Data.kt` and `core/data/sync/*` (data safety owned by prompts 01/17).
- `core/ui/Theme.kt` (design system owned by prompt 20).
- Supabase SQL/Edge Function files (prompt 17).
