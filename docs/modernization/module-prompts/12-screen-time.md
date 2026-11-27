# Module Prompt 12 — Screen Time & Accessibility Counter

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 2, 6/7 (permissions/accessibility findings), 9, 10 and this prompt. Do not assume permission behavior; verify against Android APIs and device tests. Report only tests actually executed.

## 1. Actual source files involved
- `features/screen_time/ScreenTimeScreen.kt` — usage lists, limits UI, Reels section, `hasUsageAccess` (`:56-61`), disclosure (`:529-544`).
- `ScreenTimeRepository.kt`, `ScreenTimeViewModel.kt`, `ScreenTimeLimits.kt` (guard `:34-43`, notifications `:54-59`), `UsageLimitWorker.kt`.
- `InstagramScrollCounterService.kt` — accessibility events (`:39-62`), overlay (`:73-113`), `isEnabled` (`:147-151`).
- `res/xml/instagram_scroll_accessibility_service.xml` — event types, `canRetrieveWindowContent="false"`.
- `AndroidManifest.xml` — `PACKAGE_USAGE_STATS`, accessibility service, overlay/FGS permissions.
- `core/notifications/UsageLimitNotifications.kt`, `features/settings/PermissionsSetupScreen.kt`.
- `core/ui/AppIdentity.kt` — app label/icon resolution via `QUERY_ALL_PACKAGES`.

## 2. Existing functionality (verified)
- Usage stats via `UsageStatsManager` with AppOps verification; per-app limits and notifications; daily Instagram scroll counter via minimal accessibility service + optional floating overlay.
- Service filters only `com.instagram.android`, subscribes to `TYPE_WINDOW_STATE_CHANGED`/`TYPE_VIEW_SCROLLED`, debounces 900 ms, does not read window text; UI disclosure matches config.
- Denied usage access yields unavailable state rather than fabricated data.

## 3. Problems discovered
- Accessibility service is high-privilege and Play-sensitive (A1/U2 audit); Play acceptance unverified.
- Overlay permission description in Settings wrong (prompt 03); overlay used for counter and battery animation, not network.
- Usage-limit notifications bypass guarded posting/permission checks (`UsageLimitNotifications.kt:36-52`) → possible SecurityException on API 33+ (N5).
- Usage access revocation while monitoring continues yields null totals; not clearly communicated.
- No tests for debounce/package filtering/limit behavior; battery impact unverified.

## 4. Recommended UI improvements
- Clear permission guidance with verified status and feature limitations when denied.
- Useful stats (daily/weekly totals, top apps, trends) with accessible chart summaries.
- Plain explanation that scroll counting is a best-effort proxy, not verified Reels plays (current copy already does this — preserve).
- Reduce battery cost: avoid unnecessary polling; show last-updated time and permission state.

## 5. Recommended backend and architecture improvements
- Route all usage-limit notifications through guarded `ZyloNotifications.post()` with permission/quiet-hours policy (coordinate prompt 14).
- Detect usage-access revocation on resume and surface limitation; stop dependent monitoring where appropriate.
- Centralize permission state checks for usage/accessibility/overlay; refresh on return from settings.
- Add debounce/package-filter tests; decide Maintain vs replace accessibility service with Usage Stats foreground-time proxy; prepare Play justification if retained.

## 6. Useful features to add
- Weekly comparison and app-limit insights using existing repository.
- Scheduled "screen-free" time aligned with habit reminders.
- Export of usage summary (privacy warning; no raw app identifiers without labels).

## 7. Unnecessary elements to simplify or remove
- Accessibility service if scroll counting is not essential (high policy/privacy cost); replacement must be documented.
- Overlay counter if notification/badge suffices; reduce `SYSTEM_ALERT_WINDOW` reliance.
- `QUERY_ALL_PACKAGES` if `<queries>` + fallback labels are adequate (policy benefit).

## 8. Data safety requirements
- Usage data is personal: no logging of package names/timestamps; no cloud sync unless explicitly designed (currently local-only).
- Preserve existing limit settings/preferences during refactor; no destructive resets.
- Accessibility service must not collect window text or broader events than currently configured.

## 9. Security considerations
- Keep `canRetrieveWindowContent="false"` and minimal event types; keep service bound with `BIND_ACCESSIBILITY_SERVICE`.
- Verify notification permission before posting; treat quiet hours consistently; do not claim enabled without checking.
- Verify Android 12+ FGS background-start and OEM battery behavior on device; mark unverified until then.

## 10. Required tests
- Unit tests for debounce/package filtering and limit period calculations.
- Permission-denied/revoked UI tests using fakes.
- Instrumentation test for notification posting guard (POST_NOTIFICATIONS denied).
- Device tests: grant/revoke usage access, accessibility service toggle, overlay grant/denial, reboot recovery, battery-optimization impact.
- Compose tests for stats/limits with stable tags.

## 11. Dependencies on other modules
- `core/notifications` (prompt 14), permissions onboarding (prompt 03), settings (prompt 15), `core/ui` charts/identity (prompt 20), battery overlay (prompt 13).

## 12. Acceptance criteria
- Permission limitations explained and verified; no unguarded notification posts.
- Accessibility service scope unchanged or replaced with documented lower-privilege approach.
- All executed tests pass; device-dependent items labelled verified only after testing.

## 13. Files that must not be changed without justification
- `res/xml/instagram_scroll_accessibility_service.xml` event/content scope without privacy review.
- `AndroidManifest.xml` permission/service declarations without policy review.
- `core/notifications/ZyloNotifications.kt` (prompt 14 owns channels).
- `core/data/Data.kt` (prompt 21).
