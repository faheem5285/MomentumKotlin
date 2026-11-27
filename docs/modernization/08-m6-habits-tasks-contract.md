# Milestone 6 — Habits and Tasks Contract

**Status:** Implemented on `impl/m1-data-safety`; JVM tests executed, Compose/device behavior **Unverified**.
**Date:** 2026-11-26.

## 1. Pure schedule and streak math

`HabitScheduleLogic.kt` is a JVM-safe file containing `isScheduled(mask, epochDay)` and
`computeStreaks(days, mask, today)`. `HabitLogic.kt` now consumes them from that file instead of
owning calendar/streak math.

The streak definition is explicit:

- Only scheduled days advance or break a streak.
- A scheduled day that is not completed breaks a run, except today.
- Today remains open until the day ends, so an unchecked habit today does not prematurely break
  yesterday's streak.
- `best` is the longest completed scheduled run; `current` walks backwards from today while
  skipping unscheduled days.

`HabitsToday.kt` and `HabitsInsightsScreen.kt` show that explanation and use the shared
`isHabitDue(habit, epochDay, completedDays)` helper. `HabitLogic.kt` uses Phase7 `ratePercent`
for weekly completion, so the shared helper has a production caller. The prior loose
"same percentage" caption in insights was replaced with a description of what is counted.

## 2. Transaction boundaries and alarm ordering

`HabitRepository` writes habit details, old reminder tombstones, and replacement reminders inside
one `Room.withTransaction`. Alarm cancellation/scheduling happens only after the transaction
commits. The same pattern applies to add/update/delete, reminder CRUD, toggle, skip/unskip, and
archive changes. `ToDoRepository.save` and `delete` are transactional; deleting a parent also
tombstones its subtasks through the new `ToDoDao.deleteChildren` query. `ToDoViewModel` cancels the
alarm only after a successful repository delete.

Repositories resolve `AppDatabase.get(context.applicationContext)` at call time so transaction
scope follows the active account database after M1 account switches. The Room version remains 22;
only a DAO query was added.

## 3. Reminder delivery policy

`ReminderDeliveryPolicy` is a pure decision layer used before any notification is posted or
reminder state is changed:

- Quiet hours: do not post, do not consume, defer to the end of quiet hours.
- Habit delivered: recurring reminders are rescheduled; one-time reminders are consumed only
  after the notification is posted.
- Habit blocked (archived/disabled/permission off/completed): nothing is consumed.
- To-do delivered: clear the scheduled hour/minute only after posting.
- To-do quiet hours: preserve hour/minute and defer.

`deferUntilQuietEnds` computes the next strictly-future quiet-hours exit from persisted
settings. One-time habit reminders and to-do reminder times are never silently consumed when a
notification is blocked. Actual alarm/channel behavior remains **Unverified** without a device.

## 4. Quick-task wiring

Phase7 `parseQuickTask` now has an explicit `hasQuickTaskCue` gate. `ToDoEditorSheet` previews the
parsed title/date/time only for new tasks containing "today", "tomorrow", or a valid `HH:mm`
time, and applies the parsed values only when the user taps "Apply quick date/time". Manually
chosen dates are therefore never overwritten.

## 5. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**
- `.\\gradlew :app:compileDebugKotlin --no-daemon` — PASS (final M6 sources).
- `.\\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 113 tests, 0 failures/errors/skips, 23 suites.
- `.\\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS; release
  signing remains unconfigured and no signed APK was produced.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).

New JVM tests cover empty/missed/unscheduled/boundary streaks, quiet-hour deferral and state
preservation, delivered/blocked habit decisions, to-do state preservation, quiet-hours exit
arithmetic, and the explicit quick-task cue gate.

**Unverified:** Compose editor interaction, 360dp/two-column behavior, alarm posting/cancellation,
OEM battery behavior, reboot/time-zone changes, quiet-hours retry on a real device, notification
channel state, and any sync/remote behavior. Instrumentation and Compose tests were compiled but
not executed.
