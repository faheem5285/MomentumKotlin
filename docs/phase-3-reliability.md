# Phase 3 — Reminder reliability baseline

## Changes

- Extracted habit and to-do next-fire calculations into a pure calendar helper and added unit coverage for weekly weekday masks, daily rollover, one-date reminders, disabled reminders, completed tasks, and overdue tasks.
- Changed task reminder restoration to expose a suspend `rescheduleAllNow` path. The boot/time-change/package-replaced receiver now waits for task alarms to be restored before completing its `goAsync()` work, instead of launching detached work that could be cut off when the receiver finishes.
- Refreshes reminder schedules when the reminder settings screen resumes. This reconciles alarms after returning from Android notification or exact-alarm settings, including a newly granted exact-alarm permission.

## Verification

- `:app:testDebugUnitTest` — passed.
- `:app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.faheem.habits.core.data.AppDatabaseMigrationTest"` — passed all five Room migration/backup tests on Android 16. The v3→v20 chain, every individual migration, money conversion, and representative row preservation are covered.
- `VaultCryptoTest` covers encryption round-trip, wrong-key rejection, ciphertext tamper rejection, verifier checks, and key rewrapping.
- Android alarm delivery, reboot restoration, and notification display still require device-level checks.

## Device verification checklist

1. Schedule a habit reminder and a to-do reminder, reboot, and verify both are restored.
2. Change device time and timezone, then confirm schedules use the device's current local time.
3. Deny notification permission, schedule a reminder, grant permission in system settings, return to Momentum, and confirm reminders are active.
4. Deny exact-alarm access, confirm the UI describes approximate delivery, grant it, return to settings, and confirm the schedule is reconciled.
5. Disable notifications globally and per channel; verify Android settings explain the block and unrelated app modules remain usable.
6. Edit, complete, and delete reminders/tasks; verify old alarms do not fire.

## Remaining Phase 3 work

- Add focused loan aggregation/domain tests; money conversion and Vault crypto have unit coverage.
- Exercise service and permission-denial flows on representative Android versions and OEM battery policies.
- Re-run the complete instrumentation suite after Android permits test APK installation; the last UI run was blocked by `INSTALL_FAILED_USER_RESTRICTED`.
