# Phase 7 - New features per module

## Implemented in this slice

- Added Room v12 with explicit migration and exported schema.
- Habits now persist schedule modes (`specific_days`, `times_per_week`,
  `every_n_days`), quantities, units, timer duration, categories, and freeze
  counts. Completion rows persist notes, quantity, duration, and completion
  time. Skip-day records are stored separately.
- To-dos now have optional due time, repeat rule, project, labels, parent
  task, and stable reorder value.
- Expenses now persist account, recurrence metadata, and category presentation
  fields. Category budgets are stored by category and month.
- Loans now support due dates and payment history.
- Files now persist tags, folders, last access date, and missing-file state.
- Added tested shared logic for flexible habit scheduling, completion rates,
  best day/time patterns, natural-language quick-add, budget warnings, and
  loan remaining balances.

## Verification

- `gradlew.bat :app:compileDebugKotlin --no-daemon` passed.
- `gradlew.bat :app:testDebugUnitTest --tests com.faheem.habits.features.phase7.Phase7LogicTest --no-daemon` passed.
- `gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon` passed.
- Migration execution on a physical/emulator device remains pending.

## Deferred UI work

The existing screens still need to expose the new data: streak-freeze controls,
Glance widget, quantity/timer editors, completion notes, to-do subtasks and
drag/repeat controls, money charts/export/account selectors, loan payment and
reminder actions, file tags/thumbnails/missing state, screen-time limit
notifications, global search, optional app lock, localization, and About.
