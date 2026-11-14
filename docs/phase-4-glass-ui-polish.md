# Phase 4 - Glass UI polish

## What changed

- Added centralized glass tokens for surface levels, blur, borders, highlights,
  radii, spacing, and motion durations in `core/ui`.
- Added reusable `GlassCard`, `GlassSheet`, `GlassTopBar`, `GlassBottomBar`,
  `StatTile`, `ErrorState`, `SkeletonLoader`, `ConfirmDialog`, and
  `UndoSnackbar` primitives.
- Updated the existing hero cards and form sheets to use the shared glass
  primitives.
- Added light/dark dynamic wallpaper color support through `MomentumTheme`.
- Added an API 31+ small-surface blur with a no-blur fallback on API 26-30 and
  a reduce-effects composition local.
- Exposed the wallpaper-based dynamic-colour setting in Appearance, persisted
  it with the first-frame preferences, and applied it through `MomentumTheme`.
- Preserved existing navigation and feature behavior.

## Verification

`gradlew.bat :app:compileDebugKotlin --no-daemon` passes after adding the
dynamic-colour preference.

Detekt passes after suppressing the existing composable naming false positive
for the new shared component APIs. Unit tests and `assembleDebug` were blocked
by the existing release-signing guard because this environment has no
`RELEASE_*` user properties.

Run the full checks from the repository root after configuring signing:

```powershell
.\gradlew.bat test detekt assembleDebug --no-daemon
```

On a phone, also check API 26-30, API 31+, dark mode, large font scale,
landscape orientation, keyboard opening in forms, and battery saver behavior.

## Known limits and deferred work

- Dynamic colours are available only on Android 12+; older versions retain the
  existing Momentum palette.
- Most feature-local `Card` calls remain and should be migrated incrementally
  after visual review; replacing them blindly would risk spacing and behavior.
- Predictive back, haptic check-off feedback, and per-screen loading/error
  states require feature-specific changes and remain for the next UI pass.
- Device measurements against the seven reference images were not possible in
  this environment; the references are present in `app/Assets`.
