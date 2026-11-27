# Milestone 9 — Vault and Screen Time Contract

**Status:** Implemented on `impl/m1-data-safety`; JVM tests executed, instrumentation/device/remote behavior **Unverified**.
**Date:** 2026-11-26.

## 1. Vault secret exposure

TOTP secrets use `SensitiveVaultValue`, which starts hidden behind a fixed eight-dot mask and exposes
separate reveal/copy actions. The fixed mask does not reveal the secret's length. Existing password
fields keep `PasswordVisualTransformation`. Unlocking and editing state lives in `VaultScreen`;
`lockVault()` now clears backup/recovery/legacy password fields, editor and dialog state, error and
success messages, and the in-memory session key before nulling it. No Vault code logs or prints
secret values; display errors go through `HttpErrorSanitizer.forDisplay(...)`.

The unlocked Vault sets `FLAG_SECURE` while it is composed and clears it on disposal. Idle autolock
uses elapsed realtime via `VaultSessionTimeout`, and stopping the screen locks it unless an external
file picker is open. The destructive QA reset remains gated by `VaultQaReset` to debug builds. These
are source-level guarantees; real screenshot, lifecycle and Keystore behavior remains **Unverified**.

## 2. Clipboard ownership and backup password

`SensitiveClipboardWorker` is the only clipboard-clearing mechanism. The duplicate
`Handler.postDelayed` clear was removed. `copySensitive` marks the clip sensitive on Android 13+,
stores only a SHA-256 hash of the copied value in worker input, and asks WorkManager to clear after
30 seconds. The worker compares the current clip with `MessageDigest.isEqual` and clears only when
it is unchanged, so a user's newer copy is not overwritten. The hash helper lives in
`VaultSensitivePolicy` and is JVM-testable.

The key scheme is unchanged; M9 adds no crypto migration. Version 3 encrypted export uses
`VaultCrypto.protectBackup`: a fresh salt, PBKDF2-HMAC-SHA256 derivation from a separate backup
password of at least 12 characters, AES-GCM encryption, and zeroing of the derived key. Opening a
v3 file uses the matching backup password; v1/v2 import and v2 master-password recovery remain
available through the wired Vault actions. Vault UI copy now distinguishes those flows.

## 3. Cloud-key threat model in the UI

`VaultThreatModelCard()` is shown in the normal Vault home list, not only in a QA screen. It states
that the vault key is stored with the signed-in account for multi-device unlock, that this is **not
end-to-end encryption**, that anyone who can read the account key and encrypted rows can decrypt
them, and that export files use a separate backup password. Cloud rows remain encrypted at rest;
the warning is about account-key escrow, not a claim that transport or database encryption was
removed.

## 4. Screen Time permissions and polling

`ScreenTimeEventPolicy` centralizes the pure debounce/package/overlay rules used by
`InstagramScrollCounterService`. Only `TYPE_VIEW_SCROLLED` events from Instagram are counted; events
must be at least 900 ms apart and cannot move the timestamp backwards. Overlay visibility follows
`TYPE_WINDOW_STATE_CHANGED` for the foreground package and ignores Zylo's own window events. No view
hierarchy, screen text or notification content is read by the service.

Usage-access loss is detected while the screen is visible. The overview and app list show fixed
limitation copy, revocation has distinct copy, the alarm button is disabled and labelled "Set alarm
(needs Usage access)", and the helper explains that alarms pause when access is revoked. Saved
settings are retained. Reels copy says the number counts scroll events, **not verified Reels plays**,
and explains that Accessibility and Usage access control different features. Settings overlay copy
now covers both the floating network indicator and the optional Instagram counter.

`UsageLimitSchedule.refresh()` runs at app startup and whenever an alarm is saved. It enqueues a
15-minute periodic `UsageLimitWorker` only when Usage access exists and an alarm is configured,
with `setRequiresBatteryNotLow(true)`, and cancels the work otherwise. The worker rechecks both
conditions before calling `checkScreenTimeAlarm`, so it early-returns if access was revoked or the
alarm was removed.

## 5. Play-policy justification (Unverified)

The optional scroll counter is opt-in, limited to package and scroll-event metadata, does not read
screen text/content, does not send counts to the cloud, and provides an assistive self-monitoring
feature with an alternative path: users can leave Accessibility off and continue using Usage-access
Screen Time without it. This is the rationale for using the Accessibility Service API. It is **not**
a Play approval: no store review, policy consultation or declaration was executed. If Play rejects
that use, the fallback is to disable the accessibility service/counter and retain the remaining
Screen Time behavior. The current implementation and its policy status are therefore recorded as
**Unverified**, not as policy-compliant.

## 6. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**
- `.\\gradlew :app:compileDebugKotlin --no-daemon` — PASS.
- `.\\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 157 tests, 0 failures/errors/skips, 29
  suites. The first run failed on one threat-model wording assertion; the copy was corrected and the
  identical command passed.
- `.\\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS, 157 tests,
  0 failures/errors/skips, 29 suites; release signing remains unconfigured and no signed APK was produced.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).

JVM tests cover v3 backup round-trip/wrong-password/tamper rejection, threat-model wording, clipboard
hash stability and non-disclosure, fixed masking, accessibility debounce boundaries/out-of-order and
foreign-package filtering, overlay actions, permission/alarm copy, and work-scheduling gating.
Instrumentation sources assert the visible threat model, masked TOTP with separate reveal/copy
actions, and usage-access revocation copy.

**Unverified:** no connected/device/emulator execution; no real clipboard worker, Keystore,
`FLAG_SECURE`/screenshot, autolock, accessibility-service, overlay, alarm or channel behavior; no
OEM battery/overlay/reboot/time-change check; no Play-policy decision; no Compose visual review,
TalkBack, contrast, 360dp/2× font, dark/reduced-motion/dynamic-accent check; no lint, detekt,
benchmark, CI, signed-release or R8 check.
