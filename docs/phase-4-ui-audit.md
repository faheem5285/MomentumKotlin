# Phase 4.1 — UI/UX audit and implementation plan

## Audit scope

Reviewed the current navigation host, app entry point, shared theme and glass primitives, Home screen, and existing Phase 4 documentation. The repository also contains dedicated feature screens for Habits, To-dos, Money, Loans, Files, Vault, Screen Time, Battery, Network, Contacts, Life Rules, Supabase, and Settings.

This is a source-level audit. Device screenshots, touch testing, font-scale testing, and reference-image comparison still require a configured Android device.

## Current foundations worth preserving

- `MomentumTheme` centralizes light/dark palettes, typography, shapes, dynamic wallpaper colors, and reduce-effects preference.
- `Glass.kt` provides shared glass cards, sheets, bars, spacing/radius tokens, and visual effects.
- `Ui.kt` provides shared hero, empty-state, and metric presentation components.
- `Navigation.kt` uses typed destinations and a single navigation host, with notification deep links and module-level accent theming.
- Home and feature screens already use Compose lifecycle-aware state collection in several places.
- The app has existing data-safety, authentication, reminder, Vault, and sync foundations that must not be changed as a side effect of visual work.

## Findings and priorities

### P0 — Consistency and accessibility

- A source scan of the current Habits, To-dos, Money, Loans, Files, Vault, Screen Time, Battery, Network, Contacts, Life Rules, Supabase, and Settings screens found no direct Material `Card(...)` calls. These screens use the shared `PremiumCard`/glass presentation layer rather than needing a bulk card migration.
- Apply shared spacing, typography, shape, and surface hierarchy consistently across feature screens.
- Audit icon-only actions, semantic labels, minimum touch targets, contrast, and large-font behavior.
- Ensure reduce-effects preference suppresses non-essential motion and expensive visual effects consistently.

### P1 — Navigation and interaction polish

- Keep the existing typed route contract and notification deep links intact.
- Review back navigation and predictive-back behavior before changing navigation dependencies or route ownership.
- Add subtle, preference-aware haptic feedback only to meaningful completion/confirmation actions.
- Standardize loading, empty, error, offline, and permission-required states using shared components.

### P2 — Screen-by-screen refinement

- Home: clarify the primary daily summary and quick actions; avoid increasing dashboard density.
- Habits and To-dos: prioritize quick completion and clear due/status information.
- Money and Loans: improve readability of totals, trends, and transaction/payment actions.
- Files and Vault: prioritize clear security/privacy messaging and safe destructive-action confirmation.
- Screen Time, Battery, Network, Contacts, Life Rules, Supabase, and Settings: make status, permissions, and next actions easy to understand.
- Preserve all existing feature behavior and data.

## Implementation sequence

1. Establish a shared component and accessibility checklist.
2. Refine shared primitives and interaction feedback.
3. Modernize Home as the reference screen.
4. Migrate feature screens in small, reviewable groups.
5. Add consistent state handling and accessibility refinements.
6. Batch related source changes, run automated checks after a substantial implementation change, and perform device review before declaring visual verification complete.

## Guardrails

- Do not alter Room schemas, migrations, account database activation, sync conflict policy, Vault cryptography/key format, authentication, or reminder scheduling as part of UI-only changes.
- Do not remove features or settings to simplify layouts.
- Keep changes small and reversible; update screenshots/reference notes when device review is available.
- Record only checks that actually ran. CI success is not a substitute for physical-device visual/accessibility verification.

## Implementation update — 2026-11-14

The current branch includes the following Phase 4 implementation slices:

- Shared glass surfaces suppress decorative aurora layers and reduce elevation when Reduce effects is enabled.
- Shared UI components apply the Reduce effects preference to decorative effects and animation timing.
- Home improves the Settings touch target and suppresses decorative bloom/shadow effects when Reduce effects is enabled.
- The To-do calendar removes its date-selection color transition when Reduce effects is enabled.
- The Vault lock action uses a 48dp touch target.
- Contacts now distinguishes a genuinely empty address book from a search with no matches, with clearer next-step guidance.
- Life Rules pin and delete actions use explicit 48dp touch targets.
- Settings navigation rows now have a larger minimum hit area, a larger icon surface, and subtitles that wrap instead of truncating at one line.
- Habit insights achievement cards support expanded text, and To-do calendar dates expose larger targets and date/task/selection semantics.

These are incremental improvements, not evidence that every feature screen has completed its full visual/accessibility review.

## Audit status

- [x] Source-level navigation and app-entry review.
- [x] Shared theme/glass/component review.
- [x] Home screen structure review.
- [x] Existing Phase 4 deferred-work review.
- [x] Reduced-effects behavior improved in shared surfaces, Home, and To-do calendar.
- [x] Selected touch targets, settings navigation rows, weekday labels, and empty-state messaging improved.
- [x] Complete an initial source-level scan of all feature screens and shared chart primitives.
- [ ] Complete per-screen visual consistency and accessibility verification on device.
- [ ] Large-font and responsive-layout review on device.
- [ ] Full device/reference-image review and interaction testing.


## Additional implementation update — 2026-11-14

- Shared filter chips now expose their selected state and checkbox role to accessibility services, retain a minimum 48dp interaction height, and respect Reduce effects for color transitions.
- Shared habit/to-do completion controls now expose a clear accessible label and checked state, provide a minimum 48dp touch target while preserving the compact visual mark, and suppress decorative glow/animation when Reduce effects is enabled.
- Shared status pills no longer draw decorative elevation when Reduce effects is enabled.

These shared changes improve interaction consistency across modules; they do not replace screen-reader, font-scale, contrast, or device interaction testing.


## Additional implementation update — 2026-11-14

- Contacts and Supabase now collect their database/work-status flows with lifecycle-aware Compose collection, avoiding unnecessary active collection while their screens are stopped.
- Habit tab transitions now honor Reduce effects, matching the shared control behavior.

The automated checks cover compilation and tests; TalkBack, dynamic font scaling, predictive back, haptics, and physical-device layout checks remain open.


## Additional implementation update — 2026-11-14 (continued)

- The shared bar chart now exposes its labels and formatted values in one screen-reader description instead of presenting only a visual chart.
- Bar chart entry animation now follows Reduce effects.
- Home module tiles can grow beyond their minimum height so larger text is less likely to be clipped.
- Battery orb animation now stops when Reduce effects is enabled, and its accessibility description reports battery level and charging state.
- Screen Time now catches usage-stat query failures and presents a retry action instead of silently leaving stale or empty values.

The code-level pass has improved shared accessibility, reduced-motion behavior, and error recovery. Real TalkBack, font-scale, narrow-screen, and device checks are still required.
