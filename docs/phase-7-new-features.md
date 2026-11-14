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

Implemented in this follow-up: habit streak-freeze credits can be configured,
applied to a missed scheduled day, synced through the existing skip record,
and counted as neutral days in current/best streak calculations.

Remaining UI work: Glance widget, quantity/timer editors, completion notes,
to-do subtasks and drag controls, loan reminder actions, file tags/thumbnails/
missing state, screen-time limit notifications, global search, optional app
lock, localization, and About.

- Expenses now let users assign Cash, Bank, Card, or Wallet to each new record;
  the selected account is retained in Room and included in CSV exports.
- Loan details now show payment history and remaining balance, allow partial
  payments, reject zero/negative/over-balance payments, and mark a loan settled
  when its full amount has been paid.

### To-do recurrence implementation

- Added Never, Daily, Weekly, Monthly, and Yearly choices to the task editor.
- Repeating tasks advance to the next future occurrence when completed or
  skipped, including notification actions; the saved reminder time is retained.
- A reminder that fires for a repeating task schedules the next occurrence
  rather than clearing the reminder fields.
- Added unit coverage for month-end dates, overdue recurrence catch-up, skip,
  completion, and reminder rescheduling.
