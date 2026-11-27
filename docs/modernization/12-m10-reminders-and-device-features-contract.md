# Milestone 10 — Reminders and Device Features Contract

**Status:** Implemented on `impl/m1-data-safety`; JVM tests executed, instrumentation/device behavior **Unverified**.
**Date:** 2026-11-26.

## 1. One posting and channel policy

`ZyloNotifications` owns channel creation, grouping, summaries and permission-aware posting. Screen-time
and network alerts now use the shared silent `usage_limits_v2` channel instead of creating an
`IMPORTANCE_HIGH` channel in `UsageLimitNotifications`; the old channel id is left unused because
Android does not allow an existing channel's importance to be lowered. Alerts are grouped under
`zylo.usage`, cancelled only when notifications are allowed, and summarized through the same helper
used by reminders.

The M1 `ReminderDeliveryPolicy` remains the only quiet-hours decision path: habit and to-do reminders
are deferred when quiet hours suppress them, one-time state is not consumed without a post, and
blocked deliveries do not silently discard the reminder. The quiet-hours screen now says this
explicitly. Snooze requests are still bounded (habits/to-dos 5–60 minutes, custom 5–120 minutes),
and stale alarms are cancelled whenever a schedule is disabled, deleted or has no next occurrence.

## 2. Bounded network alerts

`NetworkAlertPolicy.planNetworkAlerts` is pure and JVM-testable. It evaluates crossed 1.5× thresholds
in order, displays at most three per check, counts any older crossed thresholds, and advances the
persisted cursor to the last crossed threshold. A long gap or a period/limit change therefore cannot
post 64 notifications or replay old thresholds on the next refresh. The collapsed count is included
in the last alert body; screen-time and network copy share the same low-priority presentation.

## 3. Explicit foreground services and one power-event owner

`NetworkIndicatorService` declares `FOREGROUND_SERVICE_DATA_SYNC` in the manifest and starts with
`ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` on Android 14+. `BatteryChargingMonitorService`
keeps its `specialUse` declaration and subtype property. Both use the shared notification builders
and channels.

`BatteryChargingReceiver` in the manifest is now the only owner of `ACTION_POWER_CONNECTED` and
`ACTION_POWER_DISCONNECTED`. The duplicate dynamic receivers in `MainActivity` and the foreground
service were removed. `BatteryPowerEventPolicy` debounces repeated connect events over elapsed
realtime (2 seconds) so a wall-clock change cannot collapse or duplicate an animation. The battery
UI now describes the background listener as a status notification while Android delivers power
events; it does not promise that OEM background restrictions are bypassed.

## 4. Boot and time recovery where permitted

`DeviceMonitorBootReceiver` handles `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` for the persisted
network indicator and battery listener. `DeviceMonitorRecoveryPolicy` returns `START` only when a
foreground start is allowed; on Android 12+ it records a pending resume and posts a low-priority
status notification telling the user to open Zylo. `MomentumApplication` retries pending resumes when
the app opens and clears the flag only after a successful start. Reminder receivers already handle
`TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`, boot and package replacement by rescheduling every
source; no duplicate time-change receiver was added.

## 5. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**
- `.\\gradlew :app:compileDebugKotlin --no-daemon` — PASS.
- `.\\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 173 tests, 0 failures/errors/skips, 32
  suites. Two attempts failed on incorrect expectations in the new threshold test; the assertions
  were corrected and the identical command passed.
- `.\\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS, 173 tests,
  0 failures/errors/skips, 32 suites; release signing remains unconfigured and no signed APK was produced.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).

JVM tests cover threshold order/overflow/no-replay, burst collapsing, alert copy, quiet-hours copy,
monitor boot/recovery decisions, and power-event debounce. Instrumentation sources assert that the
usage channel is created at low importance and that quiet-hours/restart limitations are visible.

**Unverified:** no connected/device/emulator execution; no real alarm, exact-alarm permission,
notification-channel, foreground-service, boot/restart, time/date/timezone, power-event, overlay or
battery-optimization behavior; no OEM matrix; no Compose visual review, TalkBack, 360dp/2× font, dark
mode or contrast check; no lint, detekt, benchmark, CI, signed-release or R8 check.
