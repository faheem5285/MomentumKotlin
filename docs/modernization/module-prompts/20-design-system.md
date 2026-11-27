# Module Prompt 20 — Design System, Theme & Accessibility Primitives

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 4 (U3–U5), 9 (P4), 10 (T9), 11, 12, 13 and this prompt. Refine the existing glass/neon identity; do not replace it with a generic Material theme. Visual contrast, TalkBack and device behavior remain unverified until actually tested. Report only tests actually executed.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/core/ui/Theme.kt` — semantic colors (`:23-42`), light/dark schemes (`:58-124`), `GlassPalette` (`:130-180`), shapes/typography (`:191-280`), composition locals and `MomentumTheme` (`:282-323`).
- `Glass.kt` — `MomentumTokens` (`:81-120`, duplicated spacing scales), `ScreenBackground`, `GlassCard`, `PremiumCard`, `GlassSheet`, `GlassTopBar`, `MomentumTabs`, buttons/fields/chips/FAB/status/dialogs (`:201-1216`).
- `Ui.kt` — `HeroCard` (`:44-154`), `ProgressBar` (`:355`), `ProgressRing` (`:401-405`), hardcoded module gradients/identity (`:491-512`).
- `DesignComponents.kt` — `HeroMetricCard`, `MiniBarChart`, `SettingsRow`, `PasswordField`, `passwordStrength`, `StrengthMeter` (`:49-218`).
- `Charts.kt` — chart palettes/semantics (`:65-76,163,267-339,421-430`).
- `AppIcon.kt`, `AppIdentity.kt`, `DesignSystemPreview.kt:18-56`.
- `core/settings/AppearancePreferences.kt:26-79` and `app/MainActivity.kt:103-115` — four appearance flows.
- Consumer screens with literal colors/fonts (Battery, Loans, Expenses, Home) and giant composables (Language, Vault, Supabase).

## 2. Existing functionality (verified)
- Material3 theme plus a substantial glass/neon component library, semantic colors, light/dark schemes, module gradients, glass intensity, dynamic accent (Android S+) and a reduce-effects composition local.
- `MainActivity` wires `theme_mode`, `reduce_motion`, `dynamic_accent`, `glass_intensity` into `MomentumTheme` with lifecycle-aware collection.
- Shared components already include cards, sheets, tabs, buttons, fields, chips, FAB, status pills, toggles, dialogs and snackbars.
- Existing previews cover light, dark and large text only.

## 3. Problems discovered
- `MomentumTokens` has two parallel spacing scales (`space4dp`…`space32dp` and `space1`…`space6`).
- Main source contains ~136 raw `Color(0xFF…)` literals and many one-off `RoundedCornerShape` values; dynamic accent changes the Material scheme but not glass halos/module gradients.
- `LocalReduceEffects` is honored in some components but ignored by `Ui.ProgressBar`, `Charts.BarChart`, chip/toggle animations, language tabs, battery orb and habit crossfade.
- Charts expose generic point counts rather than data summaries; password-strength and chart empty-state strings are hardcoded; only two `stringResource` calls exist in `Ui.kt`.
- HeroCard white text on bright gradients may fail contrast; actual ratios are unverified.
- `DesignSystemPreview` lacks reduce-motion, dynamic-accent and glass-intensity variants.
- Some rows use bare `clickable` without role/action labels; TalkBack reading order is unverified.

## 4. Recommended UI improvements
- Consolidate spacing/radius/motion into one authoritative token set; deprecate duplicate families before removal.
- Derive glass palette, chart palette and module accents from the active scheme where appropriate; preserve intentional brand overrides explicitly.
- Introduce `animationSpecFor(LocalReduceEffects)` and make every animated component use it; optionally respect system animation scale.
- Add shared loading/empty/error/form/dialog/sheet components and migrate Home and Settings first, then high-traffic screens incrementally.
- Improve semantics: chart value summaries, progress range info, `Role.Button`/`onClickLabel` for rows, decorative icons `contentDescription = null`.
- Extract user-visible strings to resources without changing meaning or lying about data location.
- Add previews at 360dp, 2× font, dark, reduce-motion on/off, dynamic accent and glass intensity extremes.

## 5. Recommended backend and architecture improvements
- Keep theme preferences as the single source; coordinate prompt 15 appearance DataStore migration so no keys or startup behavior are lost.
- Avoid behavior changes in data/sync modules here; this prompt owns visual primitives only.
- Split giant feature composables into smaller sections only when behavior and state are preserved and tests exist.

## 6. Useful features to add
- Contrast-safe content color helper for gradient cards (luminance-based).
- Reusable `AsyncImage`/avatar wrapper with size caps and privacy-safe placeholders.
- Token documentation/component catalog in previews for reviewers.

## 7. Unnecessary elements to simplify or remove
- Duplicate spacing tokens and one-off color/shape literals after migration.
- Fixed-duration `tween` calls that ignore user preference.
- Unused `Window` import/helper in Vault if no `FLAG_SECURE` decision (coordinate prompt 11).
- Hardcoded strength/empty strings not in resources.

## 8. Data safety requirements
- Previews and screenshots must not contain real user data; use fixture data only.
- Do not log, sync or persist user content from design tests.
- No schema, backup or auth changes in this module.

## 9. Security considerations
- Do not surface sensitive previews (vault labels, contact names, financial details) in screenshots or diagnostics.
- Do not weaken `VISIBILITY_PRIVATE` notification settings (prompt 14) while changing visual styling.
- Contrast/TalkBack/dynamic-accent behavior must be device-verified before claims.

## 10. Required tests
- Unit tests for token coercion, glass-intensity bounds and theme-mode selection where extractable.
- Compose semantics tests for charts, progress indicators, settings rows, buttons and password visibility toggle.
- Preview/screenshot checks for light/dark, large font, reduce motion, dynamic accent and glass intensity.
- Manual device checks: 360dp, 2× font, TalkBack focus order, contrast, reduced motion. None were run in this audit.

## 11. Dependencies on other modules
- Prompt 15 appearance key migration, prompts 01/02 app shell and Home, prompt 11 Vault `FLAG_SECURE` decision, all consumer screens, prompt 22 CI checks.

## 12. Acceptance criteria
- One authoritative token API; reduced motion honored across migrated components; dynamic accent reflected in glass/chart/module colors or documented override.
- No sensitive fixture data; semantics and previews expanded; all executed tests pass; device claims only after device verification.

## 13. Files that must not be changed without justification
- `AppIcon.kt` / `AppIdentity.kt` identity behavior and `KNOWN_APP_LABELS` fallback.
- `ChartPalette` color meaning without legend/semantics updates.
- `AppearancePreferences.kt` keys without migration tests.
- `core/data/Data.kt`, auth/sync/backup files (other prompts own them).
