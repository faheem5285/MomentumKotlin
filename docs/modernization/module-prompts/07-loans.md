# Module Prompt 07 — Loans, Payments & Attachments

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 2, 6 (DB1/DB2/DB5), 9, 10, 12 and this prompt. Loan/payment records are financial data; never delete them without explicit migration and test evidence. Report only tests actually executed.

## 1. Actual source files involved
- `features/loans/LoansScreen.kt` — people, loan cards, details, editors, attachments, totals at `:227-229,458-459,553`.
- `features/loans/data/LoanRepository.kt` — `resolvePersonId` (`:73-78`), multi-step deletes (`:103-108`).
- `core/data/Data.kt` — `LoanPerson`, `Loan`, `LoanPayment`, `LoanAttachment` entities (`:131-225`), `getPaidAmount` (`:593`), `upsertLoan/upsertPayment` REPLACE (`:569-572`), FK cascades, migrations 16→17 (`:1298-1315`) and 18→19 (`:1350-1377`).
- `features/phase7/Phase7Logic.kt` — `loanRemaining` (`:89-90`) currently unused and does not filter deleted payments.
- `core/data/sync/SupabaseSyncRepository.kt` — `applyPerson`, `applyLoan`, `applyPayment`.
- `app/Navigation.kt` — loan `itemId`/`requestToken` deep links.

## 2. Existing functionality (verified)
- Borrow/lend records with person, amount minor units, due date, status, notes, attachments, payment history.
- People are resolved by case-insensitive name scan and inserted if missing; soft deletes with tombstones; sync/backups include loans/payments/attachments metadata.

## 3. Problems discovered
- UI "outstanding balance" is sum of principal only; `getPaidAmount` and `loanRemaining` have no callers → payments ignored (F20).
- `loanRemaining` doesn't exclude soft-deleted payments.
- `resolvePersonId` can create duplicate people; person/loan writes not transactional (F26/A4).
- Migrations 16→17 and 18→19 drop/rebuild `loans` while child tables cascade-delete; only empty-DB tests exist (DB1/DB2).
- Sync REPLACE of loan/person can delete payments/attachments or null loan person ids (DB5).
- No tests for loan balance/payment calculations.

## 4. Recommended UI improvements
- Clear borrowing/lending overview with outstanding = principal − payments, due dates and status.
- Payment history list with add/edit/delete, attachment access, person detail screen.
- Accurate reminders for due/upcoming payments; actionable empty/error states.
- Use shared cards/tokens; accessible currency formatting via `money(Long)`.

## 5. Recommended backend and architecture improvements
- Expose paid totals via Flow `SUM(amountMinorUnits) WHERE deleted = 0`; compute outstanding in minor units; filter tombstones everywhere.
- Wrap person resolution + loan/payment writes in `withTransaction`; add normalized-name lookup/index (new migration, dedupe plan for existing duplicates).
- Repair risky migration pattern with an **additive** migration and data-bearing tests; do not rewrite shipped migrations for existing users.
- Change sync upserts to non-destructive updates preserving child rows.

## 6. Useful features to add
- Partial repayment schedule and remaining-balance forecast.
- Payment reminders with snooze/quiet-hours behavior consistent with prompt 14.
- Attachments preview/size limits consistent with prompt 08.

## 7. Unnecessary elements to simplify or remove
- Unused `loanRemaining`/`loanReminderText` helpers — wire them correctly or remove after replacing with tested implementations.
- Duplicate person entries in UI if dedupe migration is applied (document impact).
- Redundant status fields only after data audit.

## 8. Data safety requirements
- Never drop `loans`, `loan_payments`, or `loan_attachments` without migration tests containing real rows.
- Preserve principal and payment amounts exactly in minor units; no rounding changes.
- Soft-delete with tombstones; preserve UUID/`updatedAt` for sync; backups must remain compatible.

## 9. Security considerations
- Loan records include names/phones/addresses (PII) and financial details: no logging of these values.
- Cloud sync/backup of loan PII must be clearly described in Settings/Cloud UI.
- Attachment URIs may grant access to sensitive documents; handle persistable permissions explicitly (prompt 08).

## 10. Required tests
- Data-bearing migration tests at v16 and v18 with loans + payments + attachments asserting survival at v22.
- Unit/repository tests for outstanding balances with partial/multiple/deleted payments.
- Transaction tests for person+loan creation and duplicate-name handling.
- Sync tests: remote loan update preserves payments/attachments; person update doesn't null references.
- Compose tests for totals/payment flows; numeric large-font layout check.

## 11. Dependencies on other modules
- `core/data` migrations/DAO (prompt 21), sync resolver (prompt 17), backup (prompt 18), notifications (prompt 14), design system (prompt 20), Phase7 logic, Files attachment handling (prompt 08).

## 12. Acceptance criteria
- Outstanding balances subtract all non-deleted payments and are unit-tested.
- Data-bearing migration tests pass; no payment/attachment loss.
- No duplicate-person regressions; all executed tests pass.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` shipped migrations (add new migration instead); `app/schemas/*`.
- `SupabaseSyncRepository.kt` (prompt 17), `MomentumBackup.kt` (prompt 18).
- `app/Navigation.kt` (prompt 01), `core/ui/Theme.kt` (prompt 20).
