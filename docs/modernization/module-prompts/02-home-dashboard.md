# Module Prompt 02 — Home Dashboard

Standalone OpenCode prompt. Implement on a dedicated branch. Read `docs/modernization/00-complete-repository-audit.md` sections 2, 4 (U2/U3/U4), 5, 11, 13 and this prompt before changing code. No destructive data operations. Report only tests actually executed.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/features/home/HomeScreen.kt` — `HomeScreen` and all dashboard tiles/rows/sections.
- `app/src/main/java/com/faheem/habits/features/home/HomeRepository.kt` — stat aggregation and device insights.
- `app/src/main/java/com/faheem/habits/core/ui/Ui.kt`, `Glass.kt`, `Charts.kt`, `DesignComponents.kt` — shared styling/components.
- `app/src/main/java/com/faheem/habits/app/Navigation.kt` — `onOpenModule` callback and deep-link args.
- `core/data/Data.kt` DAOs, `features/settings/SettingsViewModel.kt` (module visibility), `core/data/supabase` auth state (for cloud/backup status).

## 2. Existing functionality (verified)
- Home is the only hub: module tiles (Habits, To-dos, Money, Loans, Contacts, Files, Life rules, Vault, Cloud & Sync), device tool rows (Screen time, Battery, Network, Language, Profile), header/settings entries.
- Statistics default to zero in `HomeState()`; device insights use `runCatching` and fall back to empty.
- Tiles expose `Role.Button`, `onClickLabel`, composite `contentDescription` (good baseline).
- Fixed two-column tile grid, `142.dp` height, some `maxLines=1`/`softWrap=false`.

## 3. Problems discovered
- Zero/no-permission/error indistinguishable; no loading/error/retry state (U2).
- No quick actions; fresh install feels empty (audit executive summary, U2).
- Truncation risk at 360dp and large fonts; hardcoded colors/font sizes (U2/U3).
- No onboarding/permission/backup cards on Home (owned by prompt 03; provide slots here).
- Header copy "tables healthy" uses internal jargon (U6).

## 4. Recommended UI improvements
- Add `isLoading`/`error`/`retry` to `HomeRepository`/state; use shared `ListLoadingState`/`ListErrorState`.
- Add adaptive grid/single-column at compact widths; remove fixed tile heights and `softWrap=false`; use `MaterialTheme.typography` and tokens.
- Add 2–3 contextual quick actions (new habit/task/expense) with deep-link args; coordinate navigation contract with prompt 01.
- Provide well-defined slots for onboarding, permission, and signed-out backup cards from prompt 03 without owning their business logic.
- Add chart summaries for TalkBack; derive text color from gradient luminance.

## 5. Recommended backend and architecture improvements
- Move device-insight reads out of `LaunchedEffect` into a `HomeViewModel`/repository with lifecycle-aware collection.
- Keep aggregation queries efficient; do not load all rows into memory for counts.
- Derive module visibility from settings repository; do not duplicate hidden-module logic.

## 6. Useful features to add
- Recent activity list (last completions/expenses/tasks) with stable navigation targets.
- Cloud/local status pill using auth+sync state (accurate wording from prompt 03/17).
- "Continue where you left off" based on last opened module.

## 7. Unnecessary elements to simplify or remove
- Duplicate settings entry points (header and row) if one suffices.
- Internal jargon in status copy; hardcoded color/spacing literals.

## 8. Data safety requirements
- Never mark records complete/synced from Home without repository verification.
- Do not clear or mutate module data from dashboard actions.
- Device permission failures must degrade to "unavailable", not fabricated zeros.

## 9. Security considerations
- Do not surface PII (contact names, vault labels, financial detail) in dashboard previews without user intent.
- Quick-action deep links must not bypass auth/permission gating.

## 10. Required tests
- Unit tests for `HomeRepository` aggregation/state mapping including loading/error/permission-denied.
- Compose tests with test tags for tiles, quick actions, onboarding slots, empty/error states.
- Previews at 360dp, fontScale 2f, dark theme, reduced motion; TalkBack/contrast device verification.

## 11. Dependencies on other modules
- `core/ui` components/tokens (prompt 20), prompt 01 navigation, prompt 03 onboarding cards, auth/sync status (prompts 05/17), settings visibility, all module screens for quick links.

## 12. Acceptance criteria
- Empty, loading, error and permission-denied states are visually distinct with retry where applicable.
- Layout readable at 360dp and 2× font; quick actions open correct editors.
- No misleading local/cloud claims; all executed tests pass.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` schema/DAOs (prompt 21).
- `core/data/sync/*`, `core/data/backup/*` (prompts 17/18).
- `app/Navigation.kt` (prompt 01) — coordinate quick-action args.
- `core/ui/Theme.kt` (prompt 20).
