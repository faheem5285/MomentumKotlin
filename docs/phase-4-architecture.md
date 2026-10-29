# Phase 4 — Package boundaries and navigation

## Structure now enforced by Kotlin packages

```text
com.faheem.habits.app
  MainActivity.kt                 Android entry point and app lifecycle
  Navigation.kt                   typed destinations and module screen routing
com.faheem.habits.core.data       Room entities, DAOs, database, date/currency helpers
com.faheem.habits.core.notifications
                                  reminder policy, alarm scheduling, receiver, settings UI
com.faheem.habits.core.ui         theme, shared Compose components, module presentation metadata
com.faheem.habits.features.<name> one Kotlin package per product area
```

Feature source files now declare a package matching their feature folder. Shared persistence, UI, and notifications also have dedicated packages. Feature files use explicit shared-package imports instead of depending on the former flat `com.faheem.habits` namespace. Manifest component names were updated for services and activities moved into feature packages.

## Navigation contract

`MomentumDestination` distinguishes Home from a selected module. `MomentumNavigationHost` owns animated route changes and dispatches module content. `MainActivity` owns Android lifecycle setup and passes navigation events through the host; it no longer selects or styles individual module screens.

## Boundaries still to strengthen

- ViewModels and storage operations remain colocated with some screen files. Extract repositories and feature state holders before introducing cross-feature data access elsewhere.
- The app remains a single Gradle module. Separate Gradle modules only when build isolation, ownership, or dependency enforcement justifies the extra build complexity.
- Home currently aggregates Room data across product domains. Move those summary queries behind a dedicated overview repository as the data layer is extracted.
- `AppModule` contains presentation metadata and lives with shared UI; a later navigation cleanup can replace its remaining use as both module metadata and a route identifier.

## Verification

- `:app:compileDebugKotlin` passed after the package and navigation changes.
- Existing icon and deprecated network API warnings remain; these are not introduced by this refactor.
