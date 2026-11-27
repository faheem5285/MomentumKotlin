# Module Prompt 21 — Data Layer & Architecture

Standalone OpenCode prompt. This is a very high-risk foundation module. Work only on a dedicated branch with a verified backup of test devices. Read audit sections 1, 5 (A4/A6), 6 (DB1–DB5/DB11/DB12), 7, 10 (T2–T7), 11, 12, 13 and this prompt. Never add `fallbackToDestructiveMigration`, never edit shipped migration SQL, and never lose UUIDs, FKs, tombstones, dirty flags or money minor units. Report only tests actually executed.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/core/data/Data.kt` — 23 entities, `@Database(version = 22, exportSchema = true)` (`:910-914`), DAOs, 25 `@Insert(REPLACE)` upserts (e.g. `:392-399,569-572,705`), migrations `3_4`…`21_22` (`:988-1408`), `activateAccount` (`:939-976`), minor-unit migration helper (`:1410-1413`), builder (`:1415-1442`).
- `core/data/Util.kt` — currency formatting and `Double.toMinorUnits()` (`:9-27`).
- `core/di/AppModule.kt:21-51` — partial Hilt providers (8 DAOs).
- Repositories: `features/habits/data/HabitRepository.kt:31-125`, `features/loans/data/LoanRepository.kt:44-108`, `features/money/data/ExpenseRepository.kt:15-19`.
- `core/data/sync/SupabaseSyncRepository.kt:414-506,516-525` and `SyncModels.kt:22-24` — conflict/upsert callers.
- `core/data/supabase/SecureTokenStore.kt`, `features/vault/VaultCrypto.kt`, docs SQL/vault threat model.
- Tests: `AppDatabaseMigrationTest.kt:13-152`, `MoneyConversionTest.kt:7-19`.
- Exported schemas: `app/schemas/com.faheem.habits.core.data.AppDatabase/3.json`…`22.json`.
- Build/quality: `app/build.gradle.kts:78-82,93-101,114,150`, `config/detekt.yml`, `config/detekt-baseline.xml`, `.github/workflows/android.yml`.

## 2. Existing functionality (verified)
- Room v22 with explicit migrations from v3, exported schemas for every version, and a strong instrumentation test covering the full chain, money preservation, FK survival and UUID backfill. No destructive migration fallback exists — preserve this.
- Habit, loan, money, contacts, files, life-rule, custom-notification, vault and language entities are defined with UUIDs, `updatedAt`, `deleted`/dirty fields for sync.
- Sync uses transactions in several places, but DAO conflict handling uses `REPLACE`.
- Hilt provides a subset of DAOs; 27 direct `AppDatabase.get(...)` usages remain in main source.
- Money is stored in `Long` minor units; `toMinorUnits()` uses `longValueExact()` and migration rejects non-finite doubles.

## 3. Problems discovered
- `activateAccount` checkpoints WAL inside `runCatching` and ignores failure; cold start can copy the main DB without pending WAL, delete WAL/SHM and overwrite the previous account copy non-atomically (`Data.kt:939-976`).
- `MIGRATION_16_17` and `MIGRATION_18_19` rebuild `loans` with cascading children; existing tests use empty DBs, so payment/attachment loss is undetected.
- 25 `REPLACE` upserts delete/reinsert parent rows and can cascade-delete local children when remote parents are applied.
- Habit and loan multi-step writes (`saveWithReminders`, `delete`, `toggle`, `add`, `updateLoan`, `remove`) are outside transactions; partial writes and sync observing half-updated state are possible.
- `ExpenseDao.getBudgets()` does not filter `deleted = 0`; UI queries generally do, sync intentionally includes tombstones.
- Budgets have two sources of truth: Room `category_budgets` is synced/backed up, but the UI reads DataStore `money_limit_*`.
- DI is partial; repository logic and Room access are inconsistent.
- `toMinorUnits()` can throw on extreme input; no NaN/Infinity/overflow tests exist.
- Migration tests do not run in CI; detekt has a 342-entry baseline with disabled `LongMethod`, `LargeClass`, `MagicNumber`, `MaxLineLength`.

## 4. Recommended API/state improvements
- Make account switching fail closed: require successful checkpoint, use SQLite backup API or atomic temp-file replacement with rollback, keep DB file and active-user preference consistent.
- Replace `REPLACE` sync upserts with targeted update-then-insert or `@Upsert` semantics that preserve local PK/UUID/FK identity and child rows.
- Wrap multi-step repository writes in `withTransaction`; schedule side effects only after commit; suppress sync invalidation until commit where possible.
- Centralize tombstone policy: UI queries filter `deleted = 0`, sync queries explicitly include tombstones; decide and document budget query behavior.
- Complete Hilt DAO providers and move direct `AppDatabase.get(...)` usages behind repositories/providers, preserving existing singleton behavior during startup.
- Unify budget storage with a migration and write-through if both stores must temporarily coexist.
- Add range validation for monetary input with user-facing errors instead of crashes; keep `Long` minor units.
- Never rewrite shipped migrations. New changes must be additive migrations with exported schemas and data-bearing tests.

## 5. Recommended backend and architecture improvements
- Coordinate conflict semantics with prompt 17; keep Vault crypto/key format and RLS unchanged (prompts 11/17).
- Ratchet detekt baseline and re-enable disabled rules gradually; avoid new wildcard imports.
- Connect instrumentation migration tests to CI (prompt 22) without weakening gates.
- Keep sign-in optional: local DBs must remain usable without an account.

## 6. Useful features to add
- Data integrity diagnostics: orphaned child rows, duplicate UUIDs, tombstone counts, checkpoint status (read-only, no auto-repair without consent).
- Repository-level transaction helpers and a documented sync-state contract.
- Budget migration receipt showing old DataStore values moved to Room.

## 7. Unnecessary elements to simplify or remove
- Direct DB access outside DI after migration.
- Legacy `budget` backup key after import migration (prompt 15/18).
- Duplicate tombstone filtering logic scattered across DAOs.
- Broad R8 keeps after a release smoke test (prompt 22).

## 8. Data safety requirements
- Preserve all existing rows, IDs/UUIDs, FKs, tombstones, dirty flags, sync cursors, money minor units and account mapping.
- No destructive Room fallback; no recursive file deletion in this module; coordinate account files with prompt 17.
- Every schema change requires an additive migration, exported schema JSON and a data-bearing migration test.
- Vault plaintext never written to Room/logs; token ciphertext format unchanged without prompt 16 migration.

## 9. Security considerations
- Do not weaken RLS SQL, service-role confinement, Keystore token encryption or vault crypto parameters.
- Do not log user content, money values, tokens, keys or vault plaintext.
- Hilt/DI changes must not introduce secrets into generated code or logs.

## 10. Required tests
- Account switching: pending WAL cold/warm, checkpoint failure, copy failure rollback, previous account preserved, active-user marker consistent.
- Data-bearing v16→17 and v18→19 with loans, payments and attachments; full v3→latest chain preserved.
- `REPLACE`→upsert regression: parent update preserves children by UUID/id, FK identity and row count.
- Transaction rollback tests for habit/reminder and loan/person/attachment writes.
- Tombstone tests: UI excludes deleted, sync includes them, budget policy explicit.
- Money tests for NaN/Infinity, overflow, negatives, half-up rounding.
- Hilt graph compile test; sync resolver/tombstone tests coordinated with prompt 17. No tests were executed in this audit.

## 11. Dependencies on other modules
- Prompt 17 sync/reset/wipe, prompt 18 backup schema, prompt 16 auth/account identity, prompts 06–12 repository call sites, prompt 22 CI and quality gates.

## 12. Acceptance criteria
- No user data lost in any reproduction test; no destructive migrations; account switching atomic/fail-closed.
- Parent-child rows survive sync updates; transactions are atomic; one budget source; tests executed and passing.

## 13. Files that must not be changed without justification
- `Data.kt` shipped schema/migration history (`:988-1442`).
- Exported schema JSON under `app/schemas/...` — regenerate via Room, do not hand-edit.
- `docs/supabase-schema.sql`, `docs/supabase-wipe-state.sql`, `docs/vault-security.md`.
- `VaultCrypto.kt`, `SecureTokenStore.kt`, `SupabaseRestClient.kt` auth/key handling.
- Existing `account-databases` file layout without a recovery plan.
