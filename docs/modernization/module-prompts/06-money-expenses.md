# Module Prompt 06 — Money, Expenses & Budgets

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 2, 6 (DB9/DB11/DB12), 9, 10 and this prompt. Financial correctness and preservation of historical amounts are paramount. Report only tests actually executed.

## 1. Actual source files involved
- `features/money/ExpensesScreen.kt` — overview/activity/budget sections, editors, charts; `data/ExpenseRepository.kt` — DataStore budget reads.
- `core/data/Data.kt` — `Expense` entity (`:114-129`, `amountMinorUnits`), `CategoryBudget` (`:347-361`), DAOs `:490-515`.
- `core/data/Util.kt` — `CURRENCY`, `money(Double)`, `money(Long)`, `Double.toMinorUnits()`.
- `core/settings/MomentumSettingsRepository.kt` — `budget_minor_units`, legacy `"budget"`, `money_limit_*` keys.
- `core/data/backup/MomentumBackup.kt` — budget export/import; `core/data/sync/SupabaseSyncRepository.kt` — budget sync.

## 2. Existing functionality (verified)
- Expense/income records stored as `Long` minor units; categories/accounts/recurring rules; charts and budgets.
- `money(minorUnits: Long)` formats correctly with sign and 2 decimals; migration converts legacy REAL with `BigDecimal` HALF_UP.
- Two budget stores exist: Room `category_budgets` (synced/backed up) and DataStore `money_limit_*`/`budget_minor_units` (used by UI).

## 3. Problems discovered
- Two divergent budget sources; cloud/restored Room budgets do not appear in UI (DB11).
- `money(Double)` uses different format (`Rs 100` vs `Rs 100.00`); UI converts minor units to Double for charts/totals (F21).
- `Double.toMinorUnits()` throws on huge input; UI validates only `> 0` (F22).
- Backup schema excludes v22 tables (separate prompt 18) and includes legacy `"budget"` with no reader.
- No unit tests for budgets/charts/expense logic.

## 4. Recommended UI improvements
- Clear financial overview (income, expenses, net), activity list, category breakdown with accessible chart summaries.
- Category/account management and recurring transactions with plain-language schedules.
- Form validation with friendly range errors; consistent `money(Long)` formatting everywhere.
- Shared loading/empty/error states; avoid duplicated charts/cards.

## 5. Recommended backend and architecture improvements
- Choose one authoritative budget store (recommend Room `category_budgets` since it syncs/backups); migrate DataStore values into it once with explicit preference version, then write-through or read-only from Room.
- Keep arithmetic in `Long` minor units; format only at UI boundary; make `money(Double)` delegate or remove.
- Validate input range before `toMinorUnits()` and return user-facing error.
- Efficient aggregation queries (SUM/GROUP BY) instead of in-memory Double sums.

## 6. Useful features to add
- Monthly budget progress per category and overall, using authoritative store.
- Recurring transaction preview and "skip next occurrence".
- Exportable category summary (respecting backup/privacy rules).

## 7. Unnecessary elements to simplify or remove
- Legacy `"budget"` preference export (keep import-time migration only).
- Duplicate budget UI controls or unused `getBudgets()` DAO method if superseded (document reason).
- `money(Double)` overload after migration.

## 8. Data safety requirements
- Never alter historic `amountMinorUnits`; conversion helpers must remain HALF_UP and tested.
- Any preference→Room migration must be one-time, idempotent, and preserve user values; no destructive fallback.
- Backup compatibility must be retained; coordinate schema changes with prompt 18.

## 9. Security considerations
- Financial data is sensitive: no logging of amounts/categories tied to user identity.
- Cloud sync sends financial records; UI must accurately state when sync is active/unavailable.
- Avoid exposing detailed financial info in notifications/lockscreen previews.

## 10. Required tests
- Unit tests for minor-unit conversion, formatting, range errors, budget migration.
- Repository tests for SUM/aggregation and recurring expansion.
- Sync/restore tests for budgets (Room authoritative), including no duplicate writes.
- Compose tests for forms, totals, chart summaries, error states.
- Verify against a backup created before changes (round-trip).

## 11. Dependencies on other modules
- `core/data` DAO/migration (prompt 21), backup format (prompt 18), sync resolver (prompt 17), settings DataStore (prompt 15), design system (prompt 20), Home quick action (prompt 02).

## 12. Acceptance criteria
- All money math in minor units; displayed totals/balances correct in tests.
- One budget source of truth; budgets sync/restore visibly.
- No crash on extreme input; all executed tests pass; historic amounts unchanged.

## 13. Files that must not be changed without justification
- `core/data/Data.kt` shipped migrations; `app/schemas/*`.
- `MomentumBackup.kt` schema handling (prompt 18).
- `SupabaseSyncRepository.kt` (prompt 17).
- `core/data/Util.kt` conversion rounding policy without tests.
