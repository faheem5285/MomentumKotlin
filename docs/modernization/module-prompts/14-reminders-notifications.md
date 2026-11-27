# Module Prompt 14 — Reminders & Notifications

Standalone OpenCode prompt. This is a critical reliability module. Implement on a dedicated branch after/conflicts with milestone 1 data-safety work. Read audit sections 2, 6 (DB-related reminder state), 7 (S2/S17), 9 (P5), 10 (T1) and this prompt. Never discard a user's scheduled reminder silently. Report only tests actually executed; alarm/channel/OEM behavior is unverified until device-tested.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/core/notifications/NotificationSystem.kt` — settings/preferences (`:50-147`), habit scheduler (`:149-401`), to-do scheduler (`:403-518`), receiver (`:520-634`), quiet hours (`:122-140`).
- `CustomNotificationScheduler.kt` — custom scheduling/cancel/deliver/snooze (`:20-184`); one-time cancellation at `:118-131`.
- `CustomNotificationTimeCalculator.kt` — weekly/daily/monthly/once rules (`:8-54`); Monday-first bit mapping at `:30-37`.
- `CustomNotificationScreen.kt` — day chips and summary (`:67-70,260-270,329-348`); weekday bits at `:264`.
- `ZyloNotifications.kt` — channels/groups/private visibility/post helpers (`:25-139`).
- `UsageLimitNotifications.kt:9-53`, `ReminderSettingsScreen.kt:40-347`, `ReminderTimeCalculator.kt:8`, `ReminderVerificationWorker.kt:7-13`.
- `app/src/main/AndroidManifest.xml:93-110` — reminder receiver and boot/time actions.
- `core/data/Data.kt:325-345` — `CustomNotification.daysMask` contract (bit 0 = Monday).
- Features: `features/habits/HabitReminderManager.kt`, `features/todos/ToDoScreen.kt`, `features/home/HomeScreen.kt`; app shell `MainActivity.kt:250-263`.

## 2. Existing functionality (verified)
- Preferences are in `momentum_notifications`: `notifications_enabled` (default false), `enabled_*`, `hour_*`, `minute_*`, `weekday_*`, `quiet_start=22`, `quiet_end=7`.
- Habit/todo/custom reminders use exact/alarm scheduling where available, grouped notifications, and Done/Snooze/Skip actions; a safety-net worker runs about every 6 hours and boot/time/date changes trigger rescheduling.
- Custom notifications can be silent during quiet hours; channels and lock-screen visibility are centralized in `ZyloNotifications`.
- `ReminderTimeCalculatorTest.kt` has meaningful coverage for weekly/daily/once/to-do rules; `CustomNotificationTimeCalculator` has no equivalent tests.

## 3. Problems discovered
- **One-time custom notifications are posted then cancelled** (`CustomNotificationScheduler.kt:118-131`): `cancel()` cancels the alarm, snooze and the just-posted notification. The row is also disabled and marked dirty. This is the highest-priority bug in this module.
- **Weekly day mismatch**: UI maps `Calendar` day constants to `1 shl (day - 1)` (`CustomNotificationScreen.kt:67-70,264`), while calculator uses `dayOfWeek.value - 1` (`CustomNotificationTimeCalculator.kt:30-37`) with Monday = bit 0. Sunday/Monday can fire on the wrong day; stored masks may need an additive migration.
- **Quiet hours silently discard**: habit delivery disables one-time reminders when quiet hours block posting (`NotificationSystem.kt:341-378`); to-do delivery clears reminder fields (`NotificationSystem.kt:454-493`). Users lose reminders instead of deferral.
- **Stale alarms**: disable/no-next paths cancel only the current pending intent; snooze uses a separate request code (`80_000 + id`), and no global alarm registry was found.
- **Unguarded posts**: `UsageLimitNotifications.kt:36-52` creates a channel and calls `notify()` without `POST_NOTIFICATIONS` guard; Network battery code duplicates channel creation.
- `CustomNotificationScreen.kt:78` uses `collectAsState()` rather than lifecycle-aware collection.
- `MomentumReminderReceiverTest.kt:11-15` has no assertion; critical reminder bugs are undetected.
- Notification text is personal; no assurance that message/title is kept out of logs or worker error output.

## 4. Recommended UI improvements
- Share one weekday enum/bit mapping between UI, calculator, backup and tests; display selected weekdays by name from that mapping.
- Add reminder health: exact-alarm permission, `POST_NOTIFICATIONS`, channel blocked state, next ten deliveries and last delivery result.
- Make quiet-hour behavior explicit: "silence now, deliver later" or "suppressed"; copy must match implemented policy.
- Show one-time notifications as active until the user acts (Done/Snooze/Dismiss), not immediately after delivery.
- Use lifecycle-aware collection and accessible labels for day chips/actions.

## 5. Recommended backend and architecture improvements
- Introduce a single posting policy: permission check → quiet-hour decision → channel choice → grouped summary → delivery state. All features, including usage/network alerts, must use it.
- Persist deferrals for blocked reminders instead of clearing one-time or to-do state; retry after quiet hours with a bounded delay.
- Fix `ONCE` delivery: keep the notification posted, persist `lastDeliveredEpochMillis`, mark appropriate dirty state, and cancel only on user action or explicit expiry.
- Add an alarm registry/cancellation helper covering main, snooze and action pending intents; reconcile on boot/time/date change.
- Centralize channel creation/importance in `ZyloNotifications`; do not delete/recreate existing channels casually because user settings are permanent.
- If stored masks need correction, add a new additive migration/repair path; never rewrite shipped migration SQL.

## 6. Useful features to add
- Notification history/inbox for delivered and deferred reminders.
- Snooze manager with explicit "next delivery" display.
- Test-notification diagnostics showing permission/channel/alarm state (no personal text in logs).

## 7. Unnecessary elements to simplify or remove
- Direct notification posts outside `ZyloNotifications`.
- Duplicate channel creation in Battery/Network/Usage-limit code.
- Duplicate or redundant alarm cancel paths after a registry is introduced.
- `collectAsState()` in `CustomNotificationScreen.kt`.

## 8. Data safety requirements
- Preserve `CustomNotification` rows, `daysMask`, `lastDeliveredEpochMillis`, `dirty` semantics and habit/to-do reminder fields. No destructive table clears.
- Weekday-mask repair must not silently shift a user's selected days; back up/document mapping and test every bit.
- Do not log notification titles/messages; they can contain personal text.
- Do not change Room schema without additive migration, exported schema and data-bearing tests.

## 9. Security considerations
- Verify `POST_NOTIFICATIONS` and exact-alarm permission before claiming scheduling is healthy.
- Keep `PendingIntent.FLAG_IMMUTABLE` and `VISIBILITY_PRIVATE`; avoid sensitive reminder content on lock screens.
- Channel changes, OEM alarm restrictions and quiet-hour runtime behavior remain unverified until device-tested.

## 10. Required tests
- Unit tests for all seven weekdays, legacy stored masks, one-time retention, quiet-hour deferral, snooze bounds, monthly/day rollover.
- Replace the no-assertion `MomentumReminderReceiverTest` with alarm/row assertions: set, cancel, reboot, timezone/date change, snooze, Done/Skip, grouped notification remains visible.
- Instrumentation for `POST_NOTIFICATIONS` denied, exact alarms denied, channel blocked, one-time delivery and quiet-hour retry.
- Compose tests for day chips, health status, lifecycle collection and accessible labels.
- Device checklist: OEM battery optimization, Doze, reboot, language/24-hour changes. None were run in this audit.

## 11. Dependencies on other modules
- Prompt 13 Battery/Network posting paths, prompt 12 Screen Time usage alerts, prompts 04/05 habit and to-do reminders, prompt 15 Settings/quiet hours, prompt 01 boot/time receiver shell, prompt 21 Room schema, prompt 20 design components.

## 12. Acceptance criteria
- One-time notifications remain visible until user action.
- Weekly reminders fire on the selected weekday for all seven days, including stored legacy masks.
- Quiet hours never silently discard a reminder; no unguarded posts or duplicate channel definitions.
- All executed tests pass; device-dependent claims labelled verified only after device testing.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` shipped schema/migrations (prompt 21).
- `AndroidManifest.xml` receiver declarations (prompt 01).
- `core/notifications/ZyloNotifications.kt` is owned here but shared; coordinate before other modules edit it.
- `core/notifications/UsageLimitNotifications.kt` is shared with prompts 12/13.
- `features/habits/HabitReminderManager.kt` and to-do reminder call sites (coordinate with prompts 04/05).
