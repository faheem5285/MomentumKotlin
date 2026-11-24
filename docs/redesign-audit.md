# Phase 0 — redesign audit

**Date:** 2026-11-24  
**Branch:** `feature/premium-redesign-profile-language`  
**Scope:** Read-only product and architecture audit; no feature code changed.

## Repository and navigation

- Android application namespace and `applicationId` are both `com.faheem.habits`; Kotlin source declares the same package. The project is a single app module with directories organized as `app`, `core`, and `features`, plus a `benchmark` module.
- `MainActivity` owns the signed-in gate and the app-level state/lifecycle work. `MomentumNavigationHost` in `app/.../app/Navigation.kt` uses Navigation Compose with a Home route and one parameterized module route; module selection is dispatched through `ModuleScreen`.
- Home's tile catalog is `AppModule` in `core/ui/Ui.kt`; Home rendering is `features/home/HomeScreen.kt`. Current routes include Habits, To-dos, Money, Loans, Contacts, Files, Life Rules, Screen time, Vault, Battery, Network, Supabase, and Settings. There are no Profile or Language routes/tiles.
- Gradle config confirms minSdk 26, compileSdk/targetSdk 34, Java 17. Dependency versions are centrally managed in `gradle/libs.versions.toml`.

## Existing design system

- Theme and glass palette: `core/ui/Theme.kt`; spacing/glass surface tokens and shared glass components: `core/ui/Glass.kt`; reusable hero, empty state, progress, and module tile primitives: `core/ui/Ui.kt`; Canvas charts: `core/ui/Charts.kt`.
- Existing reusable pieces include `GlassCard`, `PremiumCard`, `GlassSheet`, `ModuleHeader`, `SectionHeader`, `MetricTile`, `StatusPill`, `ErrorState`, `SkeletonLoader`, `ConfirmDialog`, `UndoSnackbar`, `ProgressRing`, and several Canvas chart types. The current component vocabulary is substantial, but screen-level styling still mixes components and bespoke Compose styling.
- `MomentumTokens` exists, but this audit found no unified documented token guide, explicit contrast guarantee, full semantic typography/spacing scale, or preview matrix matching the requested design foundation. Per-feature files still contain literal spacing, colors, and labels.

## Target area inventory

| Area | Files and state/data flow | Permissions/services | Current UX and duplication findings |
| --- | --- | --- | --- |
| Screen time | `features/screen_time/ScreenTimeScreen.kt`; local Compose state; reads Android usage stats and SharedPreferences. `ScreenTimeLimits.kt`, `UsageLimitWorker.kt`, `InstagramScrollCounterService.kt` hold limit/background behavior. | `PACKAGE_USAGE_STATS` and Usage Access special access; optional `BIND_ACCESSIBILITY_SERVICE` counter; optional `SYSTEM_ALERT_WINDOW` floating counter; usage limit worker/notifications. | Already has overview/apps/Reels tabs, permission messaging, chart-like summary, limits, and Reels counter. The large screen mixes system reads, settings writes, view state, and UI in one composable; permission education is feature-local and needs a clearer benefit/next action. Overview currently has today's total and weekly values but not an explicit today-vs-yesterday hero trend. |
| Supabase / Cloud & Sync | `features/supabase/SupabaseScreen.kt` directly reads sync state and invokes `SupabaseRestClient`, `SupabaseSyncRepository`, and `SupabaseSyncScheduler`; `core/data/sync/SupabaseSyncRepository.kt`, `SupabaseSyncWorker.kt`, `SyncModels.kt`, `FileStorageSync.kt`, `DeviceIdentity.kt`, `ProjectWipeManager.kt` own sync. `core/data/supabase/SupabaseProvider.kt`, `SupabaseRestClient.kt`, `SupabaseAuthApi.kt`, `SecureTokenStore.kt` own client/auth/session. | `INTERNET`; WorkManager with connected-network constraint; foreground/background sync is coordinated by unique work. | The screen already shows sync status, last success, record counts/storage and retry/delete actions, but is an oversized screen with ad hoc Compose state and no separate per-module toggles or recent event list. Several labels say Supabase, so the requested Cloud & Sync naming pass remains. The sync layer is mature and documented; preserve its conflict and retry contracts. |
| Battery | `features/battery/BatteryScreen.kt`, `BatterySessionHistory.kt`, `AppBatteryUsage.kt`, `BatteryChargeOverlay.kt`, `BatteryChargeAnimations.kt`, `BatteryChargingActivity.kt`, `BatteryChargingMonitorService.kt`; battery readings are broadcast-derived, UI state is local Compose state. | Optional `PACKAGE_USAGE_STATS` for app usage estimates; optional `SYSTEM_ALERT_WINDOW` charging overlay; notification permission; `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`; battery receiver and monitor service. | Rich live battery, history, usage, overlay controls, preview, and diagnostics are present. The screen is especially large and has diagnostic controls mixed into the main product surface. Battery percentage is not expressed in the requested ring-first hierarchy; unsupported properties and charging-time estimates require review. Runtime permission prompts are spread across UI callbacks. |
| Network | `features/network/NetworkScreen.kt`, `AppNetworkUsage.kt`, `NetworkIndicatorService.kt`; current/month usage read via `NetworkStatsManager`, screen state is local Compose state. | Optional `PACKAGE_USAGE_STATS` for usage history; notification permission and `FOREGROUND_SERVICE_DATA_SYNC` for the live indicator service. | The current hero is usage volume, not current Wi-Fi/mobile/offline connection state. Network report is manually refreshed/lifecycle-refreshed; there is no connectivity callback Flow or lightweight connection test. Indicator and alarm controls are present. `Unavailable` is used for unsupported stats, and styling is feature-local. |
| Settings | `features/settings/AppSettingsScreen.kt`, `SettingsViewModel.kt`, `PermissionsSetupScreen.kt`, `core/settings/MomentumSettingsRepository.kt`, `AppearancePreferences.kt`; ViewModel handles module visibility and permission-setup preference. | Links to permission/system settings, with separate reminder setup for notifications/exact alarms/battery optimization. | Existing areas include appearance, module visibility, data health/backup, preferences, permission setup, and About. It is a long, bespoke screen with sections but no account/Profile destination or Language destination. Security, data, and notification settings span other screens, and row/group styling is not consistently centralized. |

### Shared permission/service notes

The manifest declares Internet, usage stats, notifications, boot receiver, exact alarm, ignore-battery-optimization request, overlay, foreground service/data-sync/special-use, and `QUERY_ALL_PACKAGES`. The special-use battery service includes a subtype explanation. Screen time's AccessibilityService is exported only with `BIND_ACCESSIBILITY_SERVICE`. Network and battery services are not exported. These special accesses are user-controlled system settings; current setup routes are distributed between feature screens and `PermissionsSetupScreen`.

## Auth and cloud wiring

- `SupabaseProvider` exposes the configured URL and publishable key from `BuildConfig`; `app/build.gradle.kts` reads these from user Gradle properties or environment variables. The provider installs PostgREST. `SupabaseRestClient` is the app's REST/authenticated request path.
- `SupabaseAuthApi` implements email/password sign-up/sign-in, reset email, refresh/session restoration, and sign-out. The refresh token is persisted by `SecureTokenStore` as AES/GCM ciphertext under a non-exportable Android Keystore key; access token/session state is held in memory. `MainActivity` restores/refreshes session and currently gates access to modules behind sign-in (including an offline restored-session path).
- Current UI is `features/auth/AuthScreen.kt`; there is no Profile module, Google Credential Manager flow, user profile repository/table cache, edit-profile screen, password-change flow, or account-deletion UI. The Auth form is not yet separated into repository/ViewModel state contracts.
- Sync uses Room as local source of truth, WorkManager scheduling, per-table sync state/cursors, stable ids and last-write-wins ordering. Existing docs (`sync-design.md`, `phase-8-supabase-sync.md`) describe established behavior and server SQL. Do not replace this protocol as part of a UI redesign. Current local record behavior/sign-in gate in source and these docs conflicts with the pasted brief's statement that the app fully works offline without signing in; this needs a documented product decision and conservative implementation.

## Room and migration state

- `AppDatabase` is declared in `core/data/Data.kt` with `version = 20` and `exportSchema = true`.
- Explicit migrations exist from v3 through v20 (`MIGRATION_3_4` ... `MIGRATION_19_20`) and are registered individually. No destructive migration fallback is configured.
- The entity/DAO catalog is centralized in `core/data/Data.kt`, with domain records for habits/completions/reminders, expenses/budgets, loans/payments/attachments, contacts, files/life rules, todos, vault entries, and sync state. Battery history/profile/language tables are not currently present in the entities inspected.
- Migration instrumentation coverage is in `app/src/androidTest/.../AppDatabaseMigrationTest.kt`; backup round-trip and settings migration instrumentation tests also exist. Room schema export is enabled and Android test assets point to `app/schemas`; schema snapshots were not listed by the initial directory listing, so availability should be checked before any future schema edit.

## Risks and implementation considerations

- Several feature composables do work beyond rendering: system service queries, SharedPreferences access, async dispatch, permissions, and service start/stop calls. Target phases should move state/operations behind repositories and ViewModels incrementally while preserving behavior.
- Supabase, Battery, Settings, and Network screens are long, and Screen time is also a broad multi-tab screen. Shared components exist but do not yet prevent duplicated feature styling.
- Lifecycle refresh is handled through `LifecycleEventEffect` on several screens, but high-frequency network state observation is absent. Battery uses a registered receiver tied to composition and records history while open; background sampling has separate service logic.
- Permission denial and unsupported device data need explicit states. `QUERY_ALL_PACKAGES` and the special-use foreground service carry distribution-policy review implications documented in older project docs.
- Accessibility, contrast, TalkBack order, large font scaling, 360dp layouts, and reduced-motion behavior require device/UI verification. Current tests include Compose instrumentation, but no connected device was assumed in this static audit.
- Release builds deliberately require user-supplied release signing properties and reject debug signing. Do not print or commit local credentials during later phases.

## Existing guidance reviewed

All files under `docs/` were inventoried and reviewed for applicable policy: current roadmap, architecture, data safety, reminders, vault security, Supabase sync/schema/setup, release verification, and historical incident records. Older snapshots contain stale package/product/account statements; current source and the current roadmap/sync design are authoritative where historical notes conflict.
