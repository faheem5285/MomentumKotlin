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

## Module review checklist

| Module | Current status | Remaining work |
|---|---|---|
| Home | In progress | Review information hierarchy, daily summary, quick actions, large fonts, and compact layouts. |
| Habits | In progress | Review Today, Library, Insights, and Manage workflows; verify completion, archive, freeze/skip, and reminder actions. |
| To-dos | In progress | Verify task create/edit/complete/delete flows; review calendar navigation and date/priority clarity. Drag, repeat, and subtask UI remain separate feature work. |
| Money | In progress | Review add/edit, budgets, categories, monthly charts, CSV export, and account workflows. |
| Loans | In progress | Review lend/borrow, settlement, due-date clarity, person profiles, and reminder actions. |
| Files | In progress | Review import, preview, permissions, metadata, and safe removal. Thumbnail and tag management remain separate feature work. |
| Vault | In progress | Review unlock, search, copy, edit, delete, recovery, and lock flows without changing cryptography or key formats. |
| Screen Time | Pending review | Review usage-access guidance, privacy messaging, limits, and notification behavior. |
| Battery | Pending review | Review permission/setup guidance, animation controls, and background behavior. |
| Network | Pending review | Review indicator status, permissions, and setup guidance. |
| Contacts | In progress | Verify search, empty states, contact editing, and attachment permissions. |
| Life Rules | In progress | Review pin, edit, archive/delete, and reading experience. |
| Notifications | In progress | Verify quiet hours, reminder channels, snooze/skip/complete actions, and delivery behavior. Per-reminder sound/vibration and evening nudges remain deferred. |
| Supabase | In progress | Review setup, auth, sync status, recovery guidance, and failure/retry messaging; configured-project integration testing remains required. |
| Settings | In progress | Review discoverability, permissions, backup/restore safety, appearance controls, and large-font behavior. |

## Verification still required

- [ ] Run the full Android CI suite after the current source batch.
- [ ] Test TalkBack labels and action order on a device.
- [ ] Test 1.3x–2.0x font scaling and narrow/landscape layouts.
- [ ] Verify predictive back and haptics on supported Android versions.
- [ ] Test reminders, permissions, background services, file access, and Vault recovery on physical devices.
- [ ] Complete a screen-by-screen visual comparison against the premium glass/neon design direction.

A successful CI run verifies code quality and buildability; it does not substitute for the device checks above.
