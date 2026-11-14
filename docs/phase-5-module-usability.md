# Phase 5 — Module-by-module usability and workflow

This is the UI/UX and workflow companion checklist for the current modernization plan. The repository roadmap separately tracks the reminder-reliability milestone; this document tracks the wider per-module refinement requested for the app.

## Guardrails

- Preserve existing features, Room data, migrations, authentication, sync conflict rules, and Vault encryption.
- Keep the existing glass/neon identity, but prioritize clarity, readable hierarchy, predictable actions, and accessible touch targets.
- Destructive actions must be explicit and confirmed. Reminder alarms must only be cancelled when the underlying task is actually deleted or completed.
- Mark a module complete only after source review, automated checks, and device verification appropriate to that module.

## Verified implementation already present

- [x] Habits and To-dos share completion controls with explicit checkbox semantics and a minimum 48dp interaction target.
- [x] Shared filter chips expose selection semantics, meet a 48dp minimum height, and honor Reduce effects.
- [x] Habit tab transitions honor Reduce effects.
- [x] To-do deletion asks for confirmation in Tasks, Calendar, and Done views before deleting the record and cancelling its reminder.
- [x] Money expense deletion already uses a confirmation dialog.
- [x] Loans and person-profile deletion already use confirmation dialogs.
- [x] File-library removal already uses a confirmation dialog.
- [x] Life Rules deletion now asks for confirmation before removing an entry.
- [x] Vault deletion already uses a confirmation dialog.
- [x] Quiet-hours reminder deferral is shared by habit and to-do delivery; reminders are deferred to the next quiet-hours end instead of being silently skipped.
- [x] Contacts and Supabase screen flows use lifecycle-aware collection.
- [x] Screen Time usage-stat failures now surface a retry action.
- [x] Battery animations respect Reduce effects and expose the battery state to screen readers.
- [x] Home module tiles can expand for larger text.
- [x] Shared bar charts expose their data labels and values to accessibility services and respect Reduce effects.
- [x] Habit and task completion controls provide subtle system haptic feedback.
- [x] Vault password buffers are cleared in a finally block during setup, derived key material is cleared even when setup fails, and lockout waiting is coroutine-cancellable.

## Module review checklist

| Module | Current status | Remaining work |
|---|---|---|
| Home | Source pass complete | Large-text tile sizing improved; device review of information hierarchy, quick actions, and compact layouts remains. |
| Habits | Source pass complete | Verify Today, Library, Insights, and Manage workflows on device; freeze/skip and reminder actions remain. |
| To-dos | Source pass complete | Verify create/edit/complete/delete and calendar flows on device; drag, repeat, and subtask UI remain separate feature work. |
| Money | Source pass complete | Verify add/edit, budgets, categories, monthly charts, CSV export, and account workflows on device. |
| Loans | Source pass complete | Verify lend/borrow, settlement, due-date clarity, person profiles, and reminder actions on device. |
| Files | Source pass complete | Verify import, preview, permissions, metadata, and safe removal on device; thumbnail and tag management remain separate feature work. |
| Vault | Source pass complete | Verify unlock, search, copy, edit, delete, recovery, and lock flows on device; cryptography and key format preserved. |
| Screen Time | Source pass complete | Query failures now show retry guidance; verify usage-access, privacy, limits, and notifications on device. |
| Battery | Source pass complete | Reduced-motion and screen-reader support improved; verify permissions, animation styles, and background behavior on device. |
| Network | Source pass complete | Verify indicator status, permissions, usage totals, and setup guidance on device. |
| Contacts | Source pass complete | Lifecycle-aware collection and empty-state guidance improved; verify search, editing, and attachment permissions on device. |
| Life Rules | Source pass complete | Delete confirmation added; verify pin, edit, archive/delete, and reading experience on device. |
| Notifications | Source pass complete | Quiet-hours deferral is shared by habit and task reminders; verify channels, snooze/skip/complete, and real delivery on device. Per-reminder sound/vibration and evening nudges remain deferred. |
| Supabase | Source pass complete | Lifecycle-aware state and existing error/retry guidance reviewed; configured-project integration and physical-device sync tests remain. |
| Settings | Source pass complete | Permission setup, appearance, backup/restore, and back handling reviewed; verify large-font and device behavior. |

## Verification still required

- [ ] Run the full Android CI suite for the final consolidated source batch.
- [ ] Test TalkBack labels and action order on a device.
- [ ] Test 1.3x–2.0x font scaling and narrow/landscape layouts.
- [ ] Verify predictive back and haptics on supported Android versions.
- [ ] Test reminders, permissions, background services, file access, and Vault recovery on physical devices.
- [ ] Complete a screen-by-screen visual comparison against the premium glass/neon design direction.

The initial source-level review is complete. A successful CI run verifies code quality and buildability; it does not substitute for the device checks above.
