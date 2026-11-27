# Module Prompt 05 — To-dos

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 2, 4, 6 (DB5/DB6), 8, 10 and this prompt. Preserve all task data, subtasks, recurrence and reminder metadata. Report only tests actually executed.

## 1. Actual source files involved
- `features/todos/ToDoScreen.kt` — list, filters, grouping, `ToDoEditor` at `:531`, reminder interactions.
- `features/todos/data/ToDoRepository.kt` — CRUD/soft-delete/sync helpers.
- `core/data/Data.kt` — `ToDoItem` entity (`:259-280`), DAO `upsertForSync` (`:669`), `getItemsForScheduling` (`:676`), indexes/tombstone fields.
- `core/notifications/NotificationSystem.kt` — `ToDoReminderScheduler.deliver` (`:451-494`) and rescheduling (`:447-449`).
- `core/data/sync/SupabaseSyncRepository.kt` — `applyTodo` (`:483`), conflict resolver.
- `app/Navigation.kt` — `itemId`/`requestToken` args for opening a task.

## 2. Existing functionality (verified)
- Task creation/editing with title, notes, due day/millis, category, priority, reminder time, repeat rule, project, labels, subtasks (`parentId`), sort order.
- Date grouping, filters, completion, soft delete with UUID/dirty/deleted metadata, sync and reminders.

## 3. Problems discovered
- No unit tests for to-do repository/UI logic.
- `ToDoReminderScheduler.deliver` clears `reminderHour/reminderMinute` and cancels even when notification suppressed by quiet hours/permission (N3).
- Stale alarms for completed/deleted tasks remain registered; `deliver()` can mutate completed rows (N4).
- Sync resolver ignores dirty/deleted state; REPLACE-style upserts risk child/label data (DB5/DB6).
- Non-lifecycle flow collection not the main issue here but check when refactoring.

## 4. Recommended UI improvements
- Fast add form (title + due date + priority first, advanced options collapsible).
- Clear priority/due-date chips, simple organization by project/labels, subtask checklist with minimal friction.
- Useful recurring-task configuration with plain-language next-occurrence summary.
- Simplify filters: keep only high-value views (Today, Upcoming, Completed, All); avoid complicated workflows.
- Shared empty/error/loading states and accessible task actions.

## 5. Recommended backend and architecture improvements
- Wrap multi-step updates (subtasks, reminders) in Room transactions; suppress sync until commit.
- Persist blocked-reminder state and reschedule to end of quiet hours instead of clearing it.
- Add global alarm-cancel pass for ineligible task ids; use deterministic request codes.
- Consider a ViewModel for list/filter state to enable unit testing.

## 6. Useful features to add
- Natural-language quick parsing (coordinate with `Phase7Logic.parseQuickTask`, currently unused).
- "Next 3 tasks" dashboard quick action (prompt 02).
- Task templates for recurring chores.

## 7. Unnecessary elements to simplify or remove
- Low-value filters/duplicated sort controls; unused legacy fields only after data audit and migration plan.
- Duplicate reminder cancellation mechanisms after notification module fixes.

## 8. Data safety requirements
- Soft-delete with tombstones; never hard-delete tasks or subtasks in UI flows.
- Preserve UUIDs, `updatedAt`, parent relationships, recurrence metadata across refactors.
- Migration changes must be explicit/exported/tested; no destructive fallback.

## 9. Security considerations
- Task notes may be sensitive: no logging of task content; notification previews should respect private visibility.
- Deep links must not expose task details to other apps; keep exported intent handling validated.

## 10. Required tests
- Repository unit/instrumentation tests for create/edit/complete/soft-delete with subtasks.
- Reminder tests: suppression preserves reminder, snooze, stale alarm cancellation, recurrence next occurrence.
- Sync tests: dirty local edit not overwritten; deleted task not resurrected; subtasks preserved.
- Compose tests for quick add, filters, completion, empty/error states; 360dp/large font previews.

## 11. Dependencies on other modules
- `core/data` DAO (prompt 21), sync resolver (prompt 17), notifications (prompt 14), Home quick actions (prompt 02), design system (prompt 20), Phase7 quick-task helper.

## 12. Acceptance criteria
- Reminders not silently lost due to quiet hours or permission loss.
- Recurring/subtask behavior tested and documented; no partial writes.
- All executed tests pass; no existing task data lost.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` shipped migrations and entity schema (add new migration).
- `core/data/sync/*` (prompt 17 owns conflict logic).
- `core/notifications/NotificationSystem.kt` (prompt 14).
- `app/Navigation.kt` (prompt 01 owns route contract).
