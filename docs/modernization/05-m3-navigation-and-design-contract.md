# Milestone 3 — Navigation, Deep Links, and Shared Design Contract

**Status:** Implemented on `impl/m1-data-safety`; JVM tests executed, device-dependent checks **Unverified**.
**Date:** 2026-11-26.

This document records the M3 contract introduced by `app/src/main/java/com/faheem/habits/app/MomentumRoutes.kt`,
`Navigation.kt`, `MainActivity.kt`, and the shared UI primitives under `core/ui`.

---

## 1. Routes and arguments

`MomentumRoutes` is the single source of truth. Raw route strings were removed from `Navigation.kt`
and `MainActivity.kt`.

| Constant | Value | Purpose |
|---|---|---|
| `HOME` | `home` | Start destination and hub |
| `MODULE_PATTERN` | `module/{module}?itemId={itemId}&requestToken={requestToken}` | Compose `NavHost` route |
| `MODULE_PREFIX` | `module/` | Route family detection |
| `ARG_MODULE` | `module` | `AppModule.name`, case-insensitive on input |
| `ARG_ITEM_ID` | `itemId` | Optional positive row id |
| `ARG_REQUEST_TOKEN` | `requestToken` | Optional one-shot nonce for notification/action deep links |

Builders:
- `MomentumRoutes.module(module, itemId?, requestToken?)` → internal route; omits non-positive args.
- `MomentumRoutes.moduleDeepLink(module, itemId?, requestToken?)` → external `momentum://` URI.
- `MomentumRoutes.parseModule(value)` → `AppModule?`; trims and ignores case; returns `null` for blank/unknown values.

## 2. Deep-link contract

Manifest filters are unchanged (intent filters cannot reference Kotlin constants):

| URI | Behavior |
|---|---|
| `momentum://module/{module}` | Opens the module if visible, otherwise returns Home |
| `momentum://module/{module}?itemId={itemId}` | As above, with an item target where the module supports it |
| `momentum://module/{module}?itemId={itemId}&requestToken={requestToken}` | As above, with a one-shot token |
| `momentum://auth` | Opens Profile via `AppModule.Profile` (account attention path) |

Unknown module names and invalid item/token values are ignored; the user lands on Home rather than
an unknown-route dead end. Notification extras (`ZyloNotifications.EXTRA_TARGET_MODULE`,
`EXTRA_TARGET_ITEM_ID`) take precedence over URI values, preserving existing notification behavior.

## 3. Hidden-module guard

`isModuleEntryAllowed(module, isVisible, source)` is pure and JVM-testable.

| Source | Rule |
|---|---|
| `NORMAL` (tile tap), `DEEP_LINK`, `NOTIFICATION` | Deny hidden modules; always allow `Profile` and `Settings` |
| `SYSTEM` | Allow (internal/system-driven entry that must not be locked out) |

Visibility comes from `SettingsViewModel.moduleVisibility` / `MomentumSettingsRepository`
(`module_enabled_*` DataStore keys). `MainActivity` applies the guard to Home tile entries and
notification targets; `MomentumNavigationHost` applies it again when a module destination is
entered, so a hidden module opened by a deep link redirects to Home. Profile and Settings stay
reachable so users can sign in and restore module visibility.

## 4. Shared design primitives

- `MomentumTokens`: `space1..space6` for new spacing; `screenPadding` and `cardPadding` are both
  `18.dp`; prefer `radius*` tokens; legacy `space*dp` aliases are deprecated, not deleted.
- `Motion.kt`: `animationSpecFor` / `rememberAnimationSpecFor` return `snap()` when
  `LocalReduceEffects` is on. Shared progress bars/rings, chips, completion toggles, and charts
  now use them instead of fixed `tween`s.
- `Accessibility.kt`: `momentumClickable` supplies a label and explicit `Role`; `progressSemantics`
  supplies range info plus a description; `chartSummaryDescription` gives charts a short TalkBack
  summary.
- `HeroCaption` replaces 11 duplicated 13sp/84%-white caption sites; `SectionLabel`, `FormSheet`,
  `BigButton`, `ErrorState`, and `SkeletonLoader` are deprecated aliases/variants awaiting a
  follow-up migration pass. No global shape or glass-palette changes were made (deliberately out of scope).
- Previews added at 360dp, 2× font, dark + reduced motion, dynamic accent, and glass intensity
  0.88f / 1.12f.

## 5. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**
- `.\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 75 tests, 0 failures/errors/skips.
- `.\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS,
  75 tests, 0 failures/errors/skips. Release signing remains unconfigured; no APK was produced.
- `.\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).
- New JVM tests: `MomentumRoutesTest` (routes, parsing, guard matrix) and `MotionAndTokenTest`
  (snap-vs-tween, token values, chart summary) executed in both variants above.

**Added but NOT executed (no device/emulator run):**
- `DesignSystemUiTest` — shared component semantics under reduced motion / glass intensity.
- `HiddenModuleDeepLinkTest` — hidden-module redirect through `MomentumNavigationHost`.

**Unverified (device-dependent):** TalkBack focus order and spoken labels, contrast of text on
gradient HeroCards, 360dp and 2× font visual review, dynamic-accent and glass-intensity visual review,
reduced-motion animation behavior on device, and real intent delivery from an external launcher.
These must be checked on a device before M3 acceptance is marked fully complete; no such claim is made here.
