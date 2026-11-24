# Design system guide

The design system lives in `core/ui/Theme.kt`, `Glass.kt`, `Ui.kt`,
`Charts.kt`, and `DesignComponents.kt`. Add shared visual behavior there so
feature screens consume the same tokens and components.

## Tokens

- Spacing: 4, 8, 12, 16, 24, and 32dp (`MomentumTokens.space4dp` through
  `space32dp`). Existing aliases (`space1` through `space6`) remain available.
- Radius: `radiusSmall`, `radiusMedium`, `radiusLarge`, and `radiusXLarge`.
- Elevation: `elevationLow`, `elevationMedium`, and `elevationHigh`.
- Glass: `glassSurface`, `glassRaised`, and `glassOverlay` map to the shared
  `GlassLevel` values. Glass cards use a light edge and a background surface
  that keeps text legible.
- Semantics: `LocalSemanticColors.current` supplies success, warning, danger,
  and info foreground colors adjusted for the active theme.
- Type: Material typography provides display, headline/metric, title, body,
  and label styles from the single `MomentumTheme` definition.
- Motion: `motionShort`, `motionMedium`, and `motionLong` are 140, 260, and
  320ms. Honor `LocalReduceEffects` for nonessential movement.

## Components

- `GlassCard` is the default grouped surface; `PremiumCard` is for a tappable
  card or an optional accent rail.
- `HeroMetricCard(value, label, trend)` gives a screen one concise primary
  metric. Put detailed controls below it.
- `SectionHeader` names content groups. `MetricTile` is for a secondary
  measurement, not another hero.
- `StatusPill` combines a label and icon; never communicate state through hue
  alone.
- `ProgressRing` and `MiniBarChart` expose progress/selection semantics.
  MiniBarChart accepts up to seven values and routes taps through `onSelect`.
- `SettingsGroup` contains related `SettingsRow`s. Use the supporting text to
  explain an effect or permission before offering a toggle.
- `PermissionCard` explains the benefit before invoking the platform settings
  route supplied by the caller.
- `EmptyState`, `ErrorState`, `SkeletonLoader`, `ConfirmDialog`, and
  `UndoSnackbar` share the app's empty, failure, loading, and confirmation
  language.
- `PasswordField` owns show/hide state; `StrengthMeter` displays a short,
  non-color-only strength label. Validation and password policy remain in the
  ViewModel/repository.

Example:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(MomentumTokens.space12dp)) {
    HeroMetricCard(value = "2h 14m", label = "Screen time today", trend = "18 min less than yesterday")
    SectionHeader("Your apps")
    SettingsGroup("Preferences") {
        SettingsRow(
            title = "Daily limit",
            supportingText = "Get a gentle reminder when you reach your goal",
            value = "3 hours",
            onClick = onEditLimit
        )
    }
}
```

## Accessibility and layout

- Give actions at least a 48dp target and meaningful content descriptions for
  icon-only actions. Composite settings rows merge their child semantics.
- Keep the primary text on the theme's opaque/legible surface or provide a
  scrim when placing it over imagery. Do not assume translucent white text has
  adequate contrast over arbitrary module gradients.
- Support font scaling by allowing text to wrap and cards to grow vertically.
  Keep charts decorative only when their values are also available through
  labels and content descriptions.
- Keep feature composables stateless where practical and route business
  decisions through ViewModels and repositories.
