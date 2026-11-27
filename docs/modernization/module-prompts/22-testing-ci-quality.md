# Module Prompt 22 — Testing, CI & Quality Gates

Standalone OpenCode prompt. This is a verification module: add failing tests before fixes and report only tests you actually executed. Read audit sections 10, 11, 14, 15 and `docs/modernization/03-testing-and-acceptance-checklist.md` once available, plus this prompt. Do not claim "passed" from stale artifacts or historical docs. Do not add secrets or PII to CI logs. Never weaken quality gates silently.

## 1. Actual source files involved
- Unit tests: `app/src/test/java/com/faheem/habits/...` — 8 classes / 35 `@Test`: `MoneyConversionTest`, `SyncModelsTest`, `ReminderTimeCalculatorTest`, `ContactImportTest`, `LanguageStudyLogicTest`, `Phase7LogicTest`, `ProfileValidationTest`, `VaultCryptoTest`.
- Instrumentation: `app/src/androidTest/java/com/faheem/habits/...` — 5 classes / 15 `@Test`: `AppDatabaseMigrationTest`, `MomentumBackupRoundTripTest`, `MomentumSettingsMigrationTest`, `Phase9UiTest`, `MomentumReminderReceiverTest`.
- Benchmark: `benchmark/src/main/java/com/faheem/habits/benchmark/MomentumMacrobenchmark.kt`, `MomentumBaselineProfile.kt`; empty `app/src/release/generated/baselineProfiles/`.
- CI: `.github/workflows/android.yml:3-64`; build rules `app/build.gradle.kts:78-82,93-101,114,150`; `config/detekt.yml:1-14`, `config/detekt-baseline.xml` (342 entries); `app/proguard-rules.pro:3-17`.
- Critical source under test: `Data.kt`, `LocalAppDataReset.kt`, `ProjectWipeManager.kt`, `SupabaseSyncRepository.kt`, `SyncModels.kt`, `CustomNotificationScheduler.kt`, `CustomNotificationTimeCalculator.kt`, `MomentumBackup.kt`, `SupabaseAuthApi.kt`, `SecureTokenStore.kt`, `VaultScreen.kt`.
- Docs: `README.md`, `docs/phase-*.md`, `docs/vault-security.md`, `docs/sync-design.md`, `docs/supabase-setup.md`.

## 2. Existing functionality (verified)
- CI runs checkout, JDK 17 setup, Android SDK setup, `lint`, `detekt`, `:app:testDebugUnitTest`, a generated throwaway keystore, then `assembleDebug`/`assembleRelease`.
- Migration, backup, settings-migration and vault crypto tests contain meaningful assertions; `MoneyConversionTest`, `ReminderTimeCalculatorTest`, `LanguageStudyLogicTest` and `ProfileValidationTest` are also meaningful.
- Cached unit XML from 2026-11-25 shows 35 tests / 0 failures, but those results were not produced in this audit and do not prove current CI success.
- No connected instrumentation job, benchmark job, artifact upload, `workflow_dispatch` or `concurrency` block exists.
- R8 minification is enabled with broad keep rules; no release smoke test exists.

## 3. Problems discovered
- `MomentumReminderReceiverTest.kt:11-15` has no assertion; `Phase9UiTest.kt:44-50` has no final assertion and bypasses real `MainActivity` startup via `setContentView` plus `MomentumNavigationHost`.
- No tests for `computeStreaks`, loan balances used by the UI, `activateAccount`, WAL/multi-account, `LocalAppDataReset`, `ProjectWipeManager`, token rotation, dirty/tombstone conflict, `CustomNotificationTimeCalculator`, backup v4/language tables or permission-denied flows.
- `AppDatabaseMigrationTest` 16→17 and 18→19 cases use empty DBs; cascade loss with payments/attachments is undetected.
- `SyncModelsTest` covers only basic non-deleted tie-break; vault payload test is a weak string check.
- `MomentumMacrobenchmark.kt` scroll tests use `metrics = emptyList()`, hardcoded swipes and a "Momentum" text wait that may not exist; baseline profile directory is empty.
- Detekt disables `LongMethod`, `LargeClass`, `MagicNumber`, `MaxLineLength` and has a 342-entry baseline with wildcard imports.
- CI uses hard-coded throwaway keystore passwords; artifacts are not distributable and no real signing-secret strategy is defined.
- Historical docs claim tests/builds "passed" with contradictory later notes; all are unverified for this branch.
- Audit claims 22 module prompts; ensure the full set exists before closing this prompt.

## 4. Recommended strategy
- **Unit (JVM):** pure resolvers/calculators first — `computeStreaks`, `CustomNotificationTimeCalculator`, `SyncCandidate`/`accept` with `dirty`/`deleted`/`JsonNull`, loan balance = principal − non-tombstoned payments, validation unification, minor-unit boundaries, repository state mapping. Add only lightweight test deps needed.
- **Instrumentation:** data-bearing migrations, two-account WAL/reset, `REPLACE` child preservation, backup v4, SAF failure, WorkManager enqueue, notification delivery/cancel, real `MainActivity` startup.
- **Compose/UI:** stable test tags; assert persisted outcomes, not only text; cover 360dp, 2× font, dark, reduced motion, permission-denied, loading/error/retry and hidden-module recovery.
- **Remote staging smoke (gated):** RLS ownership per table, anon denial, `TO authenticated`, grants, deletion RPC, admin wipe 401/403/409/CAS, Storage policies. Keep out of public PR CI; record project/date.
- **Device/manual:** offline/reconnect, expired session, restart mid-sync, permission grant/revoke, alarms/Doze/reboot, SAF persistence, vault lifecycle, TalkBack/contrast, OEM battery optimization, API matrix.
- **Performance:** add `FrameTimingMetric` or jank metric, stable tags, permission-gate handling; collect and review a baseline profile before committing generated files.
- **Security regression:** grep/CI checks for no token/password/email/vault plaintext logs, no raw HTTP bodies, no secrets, no vault plaintext in JSON backup; release smoke for R8 and vault `FLAG_SECURE`/autolock if implemented.

## 5. Useful features to add
- `workflow_dispatch`, `concurrency`, emulator job with `connectedDebugAndroidTest`, artifact/test-report upload, manual staging gate.
- Test fixtures/fakes for auth, sync and permissions; a single instrumentation helper for seeded databases.
- A concise "current verification status" page listing exactly what ran and when.

## 6. Unnecessary elements to simplify or remove
- No-assertion tests after replacing them with real assertions.
- `setContentView` UI shortcut when real startup behavior is under test.
- Empty benchmark metrics after adding meaningful ones.
- Stale "passed" claims until a real green run is captured.

## 7. Guardrails
1. No destructive migrations or `fallbackToDestructiveMigration`; test data preservation first.
2. No secrets/PII in logs, CI output, artifacts, backups or error messages.
3. No direct commits to `main`; use dedicated branches and reviewed PRs.
4. Report only tests actually executed; distinguish "source contains a test" from "test passed".
5. Throwaway CI keystore is not a release key; never distribute those artifacts.
6. Device/remote outcomes remain unverified until explicitly executed and recorded.

## 8. Required test additions (prioritized)
- WAL-pending account switch and two-account reset (`Data.kt:939-976`, `LocalAppDataReset.kt:38-41`).
- Dirty/tombstone resolver and null JSON columns (`SupabaseSyncRepository.kt:516-525,703-709`).
- Parent-child preservation under `REPLACE`/upsert (`Data.kt:392-399,569-572,705`).
- One-time notification retention and all seven weekdays (`CustomNotificationScheduler.kt:118-131`, `CustomNotificationScreen.kt:264`).
- Backup v4/schema22 with four language tables and `sync_state` reset (`MomentumBackup.kt:19-24,168-177`).
- Data-bearing v16→17/v18→19 migrations (`Data.kt:1298-1315,1350-1377`).
- Auth matrix: local-only, restore failure, session expiry, token rotation single-flight, sign-out work cancellation, recovery validation.
- Vault QA-reset gating, `FLAG_SECURE`/autolock lifecycle, encrypted export round-trip.
- Permission denial/revocation, boot/time change, quiet-hour deferral.
- CI connected tests, benchmark metrics and detekt ratchet.

## 9. Dependencies on other modules
- Every module prompt supplies failing scenarios and expected outcomes; prompts 17/21 own highest-risk fixtures; prompt 18 owns backup fixtures; prompt 14 owns notification fixtures; prompt 16 owns auth fakes.

## 10. Acceptance criteria
- Each critical fix lands with a test that failed before and passes after actual execution.
- CI reports real lint/detekt/unit/connected results with artifacts; no fabricated pass claims.
- Benchmark numbers come only from executed runs; remote/device items are labelled with date and evidence.
- Documentation matches current source and verification state.

## 11. Files that must not be changed without justification
- `Data.kt`, `LocalAppDataReset.kt`, `ProjectWipeManager.kt`, `SupabaseSyncRepository.kt`, `SyncModels.kt` (prompts 17/21).
- `MomentumBackup.kt`, `WeeklyBackupWorker.kt` (prompt 18).
- `SupabaseAuthApi.kt`, `SecureTokenStore.kt`, `SupabaseRestClient.kt` (prompt 16).
- `CustomNotificationScheduler.kt`, `CustomNotificationScreen.kt`, `CustomNotificationTimeCalculator.kt` (prompt 14).
- `VaultScreen.kt`, `VaultCrypto.kt` (prompt 11).
- `app/build.gradle.kts`, `proguard-rules.pro`, `config/detekt.yml`, `config/detekt-baseline.xml`, `.github/workflows/android.yml` — changes alter what CI proves.
- `app/schemas/.../*.json` — regenerate via Room, do not hand-edit.
- `docs/modernization/*` — source-of-truth plan; update intentionally only.
