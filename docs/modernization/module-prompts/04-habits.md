# Module Prompt 04 — Habits

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 2, 6 (DB1/DB5/DB6), 8 (AU3), 12 and this prompt. Preserve all habit/completion/skip/reminder rows and sync metadata. Report only tests actually executed.

## 1. Actual source files involved
- `features/habits/HabitScreen.kt` (tab host), `HabitsToday.kt`, `HabitEditor.kt`, `HabitLogic.kt`, `HabitTemplates.kt`, `HabitsTemplatesScreen.kt`, `HabitsInsightsScreen.kt`, `HabitsManageScreen.kt`, `HabitReminderManager.kt`.
- `features/habits/data/HabitRepository.kt` — `saveWithReminders`, delete paths.
- `core/data/Data.kt` — `Habit`, `Completion`, `HabitSkip`, `HabitReminder` entities, DAOs, `upsertHabit` (`@Insert(REPLACE)` at `:392-399`), FK cascades (`:45-50,69,85-90`).
- `core/notifications/NotificationSystem.kt` — `HabitReminderScheduler.deliver/rescheduleAllNow`; `ReminderTimeCalculator.kt`.
- `core/data/sync/SupabaseSyncRepository.kt` — `applyHabit`, conflict resolver.
- `features/phase7/Phase7Logic.kt` — `isHabitDue`, `completionRate`, `bestCompletionDay`, `timeOfDayPattern` (currently unused).

## 2. Existing functionality (verified)
- Full CRUD, scheduling (daily/weekly/custom days), quantity/timer, categories, archive, streak freezes/skips, templates, insights.
- Reminders scheduled through `HabitReminderManager` + `HabitReminderScheduler`; exact alarm fallback implemented.
- Sync uses UUID/dirty/deleted metadata; completions/skips/reminders have FK cascade to habits.

## 3. Problems discovered
- `saveWithReminders` updates habit, cancels/deletes reminders, inserts new ones outside a transaction (`HabitRepository.kt:31-64`); deletion multi-step without transaction (`:118-125`).
- Sync parent upsert uses REPLACE → can cascade-delete completions/skips/reminders (DB5).
- Conflict resolver ignores dirty/local deletion (DB6/AU3).
- Quiet hours/permission loss disable one-time habit reminders even when not delivered (notification audit N3).
- `getRemindersForScheduling()` filters out stale alarms; no global cancel pass (N4).
- Phase7 helpers have no production call sites; docs claim shared logic coverage.

## 4. Recommended UI improvements
- Simple habit creation form with clear schedule options; preserve template library but reduce duplicated entry points.
- Clear streak/progress explanations and completion affordance; insights with meaningful charts and accessible summaries.
- Empty/error states for no habits, filtered-out habits, and permission-denied reminders.
- Use shared components/tokens; avoid fixed font sizes and hardcoded colors.

## 5. Recommended backend and architecture improvements
- Wrap habit+reminder writes and soft-deletes in `db.withTransaction`; schedule alarms only after commit.
- Change sync upserts to `@Upsert`/`UPDATE` preserving child rows; add child-count tests.
- Route Phase7 pure helpers into insights/streak logic or delete/mark experimental.
- Move UI-side logic out of giant composables into a ViewModel where tests require it.

## 6. Useful features to add
- Flexible schedules (intervals, every-N-days, skip weekends) with explicit next-occurrence preview.
- Habit pause/resume and streak-freeze transparency.
- Insight: best time-of-day and completion rate by weekday using existing helpers.

## 7. Unnecessary elements to simplify or remove
- Duplicate "new habit" entry points if templates and editor can be unified.
- Dead/unused reminder fields or helpers after audit; do not remove persisted data without migration/documentation.

## 8. Data safety requirements
- Never delete completions/history on habit edit; soft-delete with tombstones and dirty flags.
- Any migration must be additive and tested with existing rows; never use destructive fallback.
- Preserve UUIDs and `updatedAt` for sync; do not reset streak history during UI refactor.

## 9. Security considerations
- Reminder text/notes may be personal: no logging of habit names/notes.
- Notification content should respect private visibility (`ZyloNotifications` channels are private); do not expose details on lockscreen.

## 10. Required tests
- `HabitLogic.computeStreaks` unit tests (currently missing; README falsely claims them).
- Repository transaction tests: partial failure leaves no half-updated reminders.
- Sync tests: remote habit update preserves completions/skips/reminders; dirty local edit never overwritten.
- Reminder tests: quiet-hours deferral, stale alarm cancellation, schedule next-occurrence.
- Compose editor/today tests with stable tags; large-font/narrow-screen previews.

## 11. Dependencies on other modules
- `core/data` DAO/upsert changes (prompt 21), sync resolver (prompt 17), notification scheduling (prompt 14), design system (prompt 20), Phase7 logic, Home quick actions (prompt 02).

## 12. Acceptance criteria
- All existing habit data preserved in tests; edits do not delete completions.
- Reminders survive quiet hours appropriately; schedules fire on selected days.
- Streak/insight logic tested; all executed tests pass.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` migrations already shipped (add new migration instead).
- `core/data/sync/*` resolvers (prompt 17 owns them).
- `core/notifications/NotificationSystem.kt` (prompt 14 owns delivery semantics).
- `core/ui/Theme.kt` (prompt 20).
