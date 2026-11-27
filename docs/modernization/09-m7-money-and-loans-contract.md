# Milestone 7 — Money and Loans Contract

**Status:** Implemented on `impl/m1-data-safety`; JVM tests executed, instrumentation/device behavior **Unverified**.
**Date:** 2026-11-26.

## 1. One budget store

Room `category_budgets` is the runtime source of truth for both the overall monthly budget and
category limits. The overall budget uses the reserved category `__overall__`; regular category
names cannot use that sentinel. The money screen observes `ExpenseDao.getBudgets()` through
`ExpenseRepository`, and all budget writes happen transactionally through `setBudget` /
`setCategoryBudget` with a `YYYY-MM` month key. Tombstoned rows are hidden from runtime queries.

Legacy DataStore values remain as one-time import staging only. `legacyBudgetsForRoom()` reads the
old values until `budget_migrated_to_room` is set; `legacyBudgetsToRoom` caps, validates, trims and
converts them to Room rows. Existing Room rows win, so a repeated import cannot overwrite a user's
current budget. A replace-style settings restore clears the migration flag so an older backup that
contains only DataStore budgets can still be re-imported; Room budgets are restored separately by
the backup snapshot.

## 2. Minor-unit arithmetic and input bounds

`Util.kt` owns `MAX_MINOR_UNITS = 1_000_000_000_000L`, `parseMinorUnits`, `minorUnitsToInput`,
`saturatingAdd`, `saturatingSubtract`, and `sumMinorUnits`. UI amount fields parse to `Long` minor
units and reject blank, negative, non-numeric and out-of-range values. `Double.toMinorUnits()` is
kept only for trusted numeric conversion and throws `InvalidMoneyInput` with fixed, sanitized
messages for non-finite or out-of-range input.

Expense totals, averages, projections, charts, budget remaining/overage values, loan balances and
account balances are computed in `Long` minor units. Chart-facing floats are derived from those
values at the last moment, and chart formatters use `money(Long)` so whole amounts keep two
decimals. The old `money(Double)` overload is deprecated and no longer used for displayed financial
values.

## 3. Loan balances and payments

`LoanRepository` exposes `payments`, `paymentsForLoan(loanId)` and `paidAmount(loanId)` as Room
Flows. Adding and deleting payments are transactional, and deleting a loan tombstones its payments
and attachments in the same transaction.

Outstanding balance is `principal − sum(non-deleted payments)`, saturated and clamped at zero. The
same rule is used by `Phase7Logic.loanRemaining` and `MoneyRules.outstandingByLoan`; the latter
feeds `LoanViewModel.outstandingBalances`, the loan overview, people list, person details, ledger,
loan rows and `HomeRepository`. Charts and Home totals therefore reflect outstanding amounts after
partial payments rather than original principal. Overpayments stay in payment history but cannot
make a displayed balance negative.

The loan detail sheet now lists recorded payments, allows deleting a payment, and accepts a new
payment amount plus optional note. The editor and account-balance sheet pre-fill from minor units
with `minorUnitsToInput`, preserving cents.

## 4. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**
- `.\\gradlew :app:compileDebugKotlin --no-daemon` — PASS.
- `.\\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 123 tests, 0 failures/errors/skips, 24
  suites. The first run failed on an incorrect test expectation for a bounded sum; the assertion was
  corrected and the identical command was rerun successfully.
- `.\\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS; release
  signing remains unconfigured and no signed APK was produced.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).

New JVM tests cover half-up conversion, parse rejection/bounds, saturated totals, minor-unit
text round-trips, current-month budget selection, deleted-row filtering, clamped budget progress,
deleted-payment and other-loan exclusion, overpayment clamping, legacy import mapping/capping, and
deleted-payment behavior in `Phase7Logic`. Instrumentation sources assert one-time legacy-budget
migration and that a backup restore preserves an active Room budget category, month key and limit.

**Unverified:** no connected/device/emulator execution; no Compose interaction or visual assertion
for payment forms, charts, 360dp/2× font, dark mode or TalkBack; no real backup/restore or Supabase
round trip; no Play-policy, CI, benchmark, signed-release or R8 check.
