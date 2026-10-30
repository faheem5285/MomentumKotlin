# Phase 5 - Reminders that always fire

## What changed

- Kept one central alarm policy in `MomentumReminderScheduler`: stable
  immutable PendingIntents, exact alarms when permitted, and inexact
  `setAndAllowWhileIdle` fallback when exact-alarm access is unavailable.
- Preserved idempotent rescheduling on boot, package replacement, date/time
  changes, timezone changes, app resume, and existing habit/to-do edits.
- Added a unique six-hour `ReminderVerificationWorker` safety net.
- Added Done, Snooze (5/15/60 minutes), and Skip notification actions for
  habits and to-dos. Actions update Room and cancel/reschedule alarms without
  opening the app.
- Added quiet-hours settings, completion-aware habit delivery, module grouping,
  battery-optimization status/request, OEM guidance, next-ten previews, and a
  ten-second test reminder to the reminder settings screen.
- Added `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and retained
  `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, and
  `SCHEDULE_EXACT_ALARM` permissions.

## Verification

- `gradlew.bat :app:testDebugUnitTest --no-daemon` passed.
- `gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon` passed.
- `gradlew.bat :app:compileDebugKotlin --no-daemon` passed.
- Detekt passes after the final source updates.
- The boot receiver test is written but requires a connected Android device.

## Known limits

- Android does not expose a reliable API to enumerate all scheduled alarms;
  the health list is derived from Room and the same next-trigger calculator
  used by scheduling.
- Per-habit sound/vibration needs persisted reminder/channel policy and remains
  deferred; users can control module channels through Android settings.
- Evening gentle nudges and vendor-specific deep-link screens remain deferred;
  the health screen provides generic Xiaomi/Oppo/Vivo/Samsung/Huawei guidance.
- Exact delivery, reboot behavior, Doze behavior, and OEM battery policies
  still require physical-device testing.

## Device verification

1. Grant notification and exact-alarm permissions, schedule habit and to-do
   reminders, and use the ten-second test.
2. Deny exact alarms and confirm the fallback reminder still appears.
3. Reboot and verify alarms return.
4. Change time and timezone, then verify the next-ten list and delivery times.
5. Complete a habit from its notification and confirm no further notification
   is shown for that day.
6. Exercise each snooze duration and Skip without opening the app.
7. Test quiet hours, Doze, battery saver, and OEM autostart/battery cleanup.
