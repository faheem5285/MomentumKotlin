# Module Prompt 13 — Battery, Network & Device Features

Standalone OpenCode prompt. Implement on a dedicated branch. Read `docs/modernization/00-complete-repository-audit.md` sections 2, 7 (S18/S20), 9 (P5/P6), 10 (T8), 12 and this prompt. Device, OEM, Play-policy and background-execution behavior is **unverified** until tested on real devices. Report only tests actually executed.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/features/battery/BatteryScreen.kt` — dashboard and toggles (`:137-236`), preference writes (`:192-205`), permission launchers (`:167-170,217-223`), estimator (`:926-1022`).
- `BatteryViewModel.kt`, `BatteryRepository.kt` — lifecycle-aware state (`:12`) and dynamic power receiver (`:31-84`).
- `BatteryChargingMonitorService.kt` — foreground service, dynamic receiver, `START_STICKY`, channel creation, `setEnabled()` (`:22-107`).
- `BatteryChargingActivity.kt`, `BatteryChargeOverlay.kt` — full-screen animation, overlay window (`:222-302`), notification fallback (`:305-347`), manifest receiver and shared power handler (`:83-130`).
- `BatterySessionHistory.kt` (`:27-33`, dedupe), `AppBatteryUsage.kt` (`:43-48,115-184`, estimates and usage stats), `BatteryChargeAnimations.kt` (`:27-41`).
- `app/src/main/java/com/faheem/habits/features/network/NetworkScreen.kt` — indicator UI/toggles (`:102-223,317-329,536-538`).
- `NetworkIndicatorService.kt` — network FGS (`:35-216`), 3s sampling (`:60-90`), threshold alert burst loop (`:105-144,216`).
- `NetworkRepository.kt`, `NetworkViewModel.kt`, `AppNetworkUsage.kt`, `NetworkLimitPeriod.kt`.
- `app/src/main/java/com/faheem/habits/core/notifications/ZyloNotifications.kt:25-133` and `UsageLimitNotifications.kt:9-53` — shared posting policy versus direct posts.
- `app/src/main/java/com/faheem/habits/app/MainActivity.kt:250-263` — shell power receiver.
- `app/src/main/AndroidManifest.xml:5-27,68-80,93-119` — permissions, FGS declarations, receivers.
- `core/ui/AppIdentity.kt` — installed-app labels via `QUERY_ALL_PACKAGES`.

## 2. Existing functionality (verified)
- Battery screen shows charging monitor/animation status, session history, app-energy estimates and battery settings; preferences live in SharedPreferences `momentum_battery` (`charge_animation_style`, `charge_animation_auto_show`, `last_auto_show_event`, `background_listener_enabled`).
- Charging monitor uses `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+ with a manifest `specialUse` subtype; overlay uses `TYPE_APPLICATION_OVERLAY` and falls back to a notification when screen is off, keyguard is locked or overlay is unavailable.
- Network indicator is user-enabled, runs as a `dataSync` foreground service, refreshes about every 3 seconds and keeps an ongoing notification.
- App battery values are explicitly estimates from measured drop or a screen-on model; usage stats are used for app attribution.
- Power events are deduped in two places, but several components observe them independently.

## 3. Problems discovered
- Four power-event owners exist: manifest receiver, monitor-service receiver, `MainActivity` receiver and `BatteryRepository`/activity receivers. Diagnostics can be overwritten and wakeups duplicated (audit P6).
- Battery FGS uses `START_STICKY` plus `stopWithTask=false`; background-start, OEM autostart and Play special-use acceptance are unverified.
- Overlay copy in `PermissionsSetupScreen.kt:124` says "floating network indicator"; overlay is actually used for charging animation and the screen-time scroll counter.
- `NetworkIndicatorService.kt:105-144` can post up to 64 high-importance threshold alerts per check; `UsageLimitNotifications.kt:36-52` posts directly without `POST_NOTIFICATIONS` guard or quiet-hour policy.
- Notification channels `background_charging_listener`, `charging_animation` and `persistent_network_indicator` are created in both feature code and `ZyloNotifications.kt`; channel importance is effectively permanent once created.
- Battery/network data are personal and local-only by design; no explicit limits on diagnostic export or package-label logging are present.
- Battery/network state copy should not imply exact Android battery attribution; estimates must remain labelled as estimates.

## 4. Recommended UI improvements
- Add a single "Device status" section: power receiver owner, FGS state, overlay/notification/usage-access status, last event, last update time and fallback reason.
- Correct overlay permission explanation: charging animation and screen-time counter, not network indicator only.
- Explain why a notification was used instead of the overlay (locked, screen off, permission denied, notification blocked).
- Label app battery numbers as estimates and surface confidence/source; keep current uncertainty copy.
- Collapse network threshold bursts into one summary notification with a capped count and a "view details" action.

## 5. Recommended backend and architecture improvements
- Choose one power-event owner and expose `Flow<PowerEvent>`; remove redundant receivers only after the single owner is verified with boot/unplug/replug tests.
- Route every notification creation and post through `ZyloNotifications`; remove duplicate channel definitions from feature code.
- Make FGS lifecycle explicit: start only for user-enabled monitors, stop on toggle/disable, document `START_STICKY` or remove it if unnecessary.
- Cap and coalesce network alerts; add quiet-hour and permission decisions before posting.
- Keep battery/network, usage and diagnostics local-only; do not add cloud sync in this module.
- Verify necessity of `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SPECIAL_USE` and Play declarations; do not add new permissions without justification.

## 6. Useful features to add
- Charge-session trends, weekly charging summary, last full/ETA with uncertainty.
- Network daily/weekly totals and threshold configuration with a summary alert instead of floods.
- Redacted diagnostics export with explicit user consent.

## 7. Unnecessary elements to simplify or remove
- Redundant `MainActivity` power receiver after single-owner migration.
- Duplicate notification channel creation in Battery and Network code.
- Direct `UsageLimitNotifications.notify()` calls; replace with guarded posting.
- `QUERY_ALL_PACKAGES` if narrower `<queries>` plus fallback labels are sufficient (policy outcome unverified).

## 8. Data safety requirements
- Preserve `momentum_battery`, `momentum_battery_diagnostics`, `momentum_battery_sessions` and `momentum_battery_usage` keys; no destructive settings clears.
- Do not log package names, labels, network thresholds or diagnostic details into cloud/error surfaces.
- Permission denial must degrade to "unavailable" or fallback, never fabricated zero usage.
- No change to Room schema, sync or backup in this module.

## 9. Security considerations
- Keep receivers non-exported and overlay `TYPE_APPLICATION_OVERLAY`/`FLAG_NOT_FOCUSABLE` behavior; verify input passthrough on device.
- Verify `POST_NOTIFICATIONS` before fallback posts; keep private lock-screen visibility.
- Do not assume Android 12+ foreground-start or OEM autostart behavior; label unverified until device-tested.
- Play policy outcomes for special-use FGS and overlay remain unverified.

## 10. Required tests
- Unit tests for battery estimator bounds/rate, session dedupe, network threshold progression and alert cap.
- Instrumentation tests for receiver registration/unregistration, FGS start/stop, overlay granted/denied/revoked, notification fallback with permission denied, boot and keyguard/screen-off behavior.
- Compose tests for status cards, fallback reasons, permission-denied states and estimate copy.
- Device checklist: OEM autostart, battery optimization, overlay revocation, Android 12+ FGS start, reboot, lock screen. None were run in this audit.

## 11. Dependencies on other modules
- Prompt 14 notification channels/policy, prompt 03 permission guidance, prompt 12 screen-time accessibility/overlay, prompt 15 Settings, prompt 01 app shell, prompt 20 design system, `core/ui/AppIdentity.kt`.

## 12. Acceptance criteria
- One power-event owner with no duplicate diagnostics writes.
- No unguarded notification posts; channel definitions centralized.
- Permission and overlay copy matches actual behavior; alerts are capped.
- All executed tests pass; device-dependent claims are marked verified only after device testing.

## 13. Files that must not be changed without justification
- `app/src/main/AndroidManifest.xml` permission/FGS/receiver declarations (verify policy and behavior first).
- `core/notifications/ZyloNotifications.kt` (owned by prompt 14).
- `core/data/Data.kt` and `core/data/sync/*` (owned by prompts 21/17).
- `features/settings/PermissionsSetupScreen.kt` copy (coordinate with prompt 03).
- `res/xml/instagram_scroll_accessibility_service.xml` (owned by prompt 12).
