# Zylo Modernization — Testing & Acceptance Checklist

**Status:** Planning checklist for the original audit. **No Gradle build, lint, detekt, unit test, instrumentation test, benchmark, device test or remote Supabase smoke test was executed during that audit.** Every item below starts unchecked. Mark an item complete only after recording an actual command/device/project, date and observed result. Execution results from the current modernization work are recorded in the section at the end of this file.

**Result record template:** `YYYY-MM-DD | command/device/project | PASS/FAIL | evidence link/output | notes`. Do not copy historical "passed" claims from older docs.

---

## 0. Global gates

- [ ] Work is on a dedicated branch off the agreed integration branch; `main` is untouched.
- [ ] Before destructive-path tests, a verified backup/snapshot of the test device and Supabase staging project exists.
- [ ] No `fallbackToDestructiveMigration` anywhere; all new migrations are additive, exported and tested with real rows.
- [ ] No secrets, tokens, passwords, emails, plaintext vault data or raw HTTP bodies in logs, CI output, UI error text or worker state.
- [ ] Signed-out local-only use is verified in every release candidate.
- [ ] RLS ownership and service-role confinement are preserved and staging-verified.
- [ ] Every critical fix has a test that failed before the fix and passes after actual execution.
- [ ] Device/remote items are labelled **Verified** only with date and evidence; otherwise **Unverified**.

---

## 1. Build and static quality

- [ ] `./gradlew clean` (or equivalent documented command) completes on the integration branch.
- [ ] `./gradlew lint` completes; new warnings triaged, no suppressed security/accessibility regressions.
- [ ] `./gradlew detekt` completes; baseline is not silently grown; unbaselined wildcard imports fixed.
- [ ] `./gradlew :app:testDebugUnitTest` completes; report captured.
- [ ] `./gradlew :app:assembleDebug` and `:app:assembleRelease` complete; signing verified without distributing throwaway CI artifacts.
- [ ] CI has `workflow_dispatch`, `concurrency`, connected-test job, artifact/report upload; results actually observed.
- [ ] Detekt rules `LongMethod`, `LargeClass`, `MagicNumber`, `MaxLineLength` re-enabled incrementally where feasible.
- [ ] R8/proguard keep rules narrowed only after a release smoke test.
- [ ] Grep/security check: no service-role key, no hardcoded production secrets, no sensitive `Log.*` arguments, no raw response-body persistence.
- [ ] Documentation "passed" claims updated to match executed results or marked historical/unverified.

---

## 2. Unit tests (JVM)

### Existing baseline — re-run, do not assume
- [ ] `MoneyConversionTest` — half-up rounding/formatting.
- [ ] `SyncModelsTest` — timestamp/device tie-break, cursor round trip, retryable statuses.
- [ ] `ReminderTimeCalculatorTest` — weekly/daily/once/to-do rules.
- [ ] `ContactImportTest` — folded-line behavior.
- [ ] `LanguageStudyLogicTest` — Leitner intervals and retry.
- [ ] `Phase7LogicTest` — math (note: no known production call sites).
- [ ] `ProfileValidationTest` — validation/nonce.
- [ ] `VaultCryptoTest` — round trip, wrong key, tamper, verifier, VMK rewrap, dedupe, legacy migration.

### Required additions
- [ ] `computeStreaks` tests for normal, missed, skipped and boundary cases.
- [ ] Loan balance = principal − non-tombstoned payments, including partial/overpayment.
- [ ] `Util.toMinorUnits()` for NaN, Infinity, overflow, negative, zero and half-up boundaries.
- [ ] `CustomNotificationTimeCalculator` for all 7 weekdays, legacy stored masks, DST/timezone, daily/monthly/once.
- [ ] Sync resolver matrix: dirty local vs newer clean remote; local tombstone; remote tombstone; equal timestamp; explicit `JsonNull`; idempotent duplicate pull; cursor advance only on success.
- [ ] Budget store tests after unification; tombstone filtering explicit.
- [ ] Auth validation shared by all UIs; signup response states; recovery link validation; token-refresh single-flight.
- [ ] Onboarding/permission state tests: unknown/pending/done, dismiss/snooze, revisit.
- [ ] Home repository state mapping: loading/empty/error/permission-denied.
- [ ] Battery estimator, session dedupe, network threshold cap, debounce/package filter for Screen Time.
- [ ] Translator/proofreader decision matrix and disclosure copy tests.

---

## 3. Room migrations and account data (instrumentation)

- [ ] Existing full v3→v22 chain test passes when executed; schemas `3.json`–`22.json` present and unchanged unless a new version is exported.
- [ ] Per-step migration validation still passes; no destructive fallback introduced.
- [ ] **Data-bearing v16→17**: loans with payments/attachments survive; UUID/FK repair verified.
- [ ] **Data-bearing v18→19**: loans, payments and attachments survive the rebuild; PRAGMA behavior observed.
- [ ] Duplicate/blank UUID and orphan `personId` fixtures handled without data loss.
- [ ] Money minor-unit preservation across migration; non-finite legacy value produces controlled failure, not silent corruption.
- [ ] **WAL-pending account switch** (`Data.kt:939-976`): two seeded account DBs, committed-but-uncheckpointed WAL, cold and warm switch; no row loss, no cross-account overwrite, rollback on copy failure, active-user marker consistent.
- [ ] Checkpoint failure causes fail-closed behavior, not continuation with stale files.
- [ ] Account DB file layout and `unassigned-legacy-database.db` first-link path tested with local data.
- [ ] Vocabulary/grammar/review/history rows preserved if language schema changes.

---

## 4. Reset, wipe and sync (instrumentation + staging)

- [ ] **Two-account reset** (`LocalAppDataReset.kt:38-41`): only active-account data removed; other account DB intact; no recursive deletion of `account-databases/`.
- [ ] Reset clears both DataStores, respects allowlist, cancels/reschedules weekly backup and sync work, closes DB before deletion.
- [ ] `ProjectWipeManager`: `UNCHANGED/IN_PROGRESS/APPLIED`, generation ordering, clean-install exemption, failed marker commit, no repeated wipe, DataStore clearing.
- [ ] Admin wipe Edge Function: 401/403/409/CAS behavior, stale lock, auth users preserved (staging, gated).
- [ ] **Parent-child preservation**: remote update of habit/loan/life-rule/to-do parent does not delete completions, skips, reminders, payments, attachments or files.
- [ ] **Dirty/tombstone**: unsynced local edit wins per policy; local tombstone not resurrected; remote tombstone deletes clean local; conflicts recorded.
- [ ] Null JSON columns in pull do not throw; cursor not advanced on failed module.
- [ ] Queued module delete keyed by originating account; sign-out/in as another account does not orphan or misapply it; commit failures checked.
- [ ] Periodic sync enqueued/deduplicated with constraints; one-shot triggers remain (Room invalidation, resume, sign-in, manual, toggle).
- [ ] First-sync backup created exactly once before overwrite; plaintext copy encrypted/expired/deletable.
- [ ] Storage sync: unreadable URI isolated, oversized file rejected/streamed with caps, 404 tolerated, tombstoned object deleted.
- [ ] Pending/conflict counts shown in UI match persisted `sync_state`; no hardcoded `pending = 0`.
- [ ] Raw server error bodies never persisted or displayed; sanitized categories used.
- [ ] Staging RLS smoke: owner can select/insert/update/delete; non-owner denied; anon denied; `TO authenticated`; grants/revokes; deletion RPC exists; Storage prefix/owner policies enforced. Record project ref/date.

---

## 5. Backup and restore (instrumentation)

- [ ] v4/schema22 round-trip includes all 16 original sections plus `vocabulary_words`, `grammar_notes`, `review_states`, `translation_history`.
- [ ] Legacy v1/v2/v3 import still works; wrong format/schema rejected with clear state.
- [ ] Replace clears/resets `sync_state`; no stale cursors after restore.
- [ ] Merge uses timestamp/tombstone comparison, not UUID existence only; newer local data never silently downgraded.
- [ ] Failure injection: invalid UUID, >10 MB, >10k rows, malformed JSON, DataStore failure — DB and settings unchanged or partial receipt explicit.
- [ ] Vault plaintext/secrets never appear in exported bytes; file contents not claimed as restored.
- [ ] Appearance settings and one budget source survive export/import after migration.
- [ ] Weekly backup: folder picker, enable/disable, 7-day schedule, prune to five, SAF permission loss, last-backup time.
- [ ] First-sync backup deletion/expiry control tested.

---

## 6. Auth, profile and account linking

- [ ] Session restore matrix: no session/local-only, authenticated, temporary failure, wipe event, cold/warm `momentum://auth` deep link.
- [ ] All local features usable signed out; no forced sign-in gate; no local data cleared on session failure.
- [ ] Single auth UI; unified validation; weaker duplicate path removed or routed.
- [ ] Signup states: confirmation required, resend, expiry, network error, account exists, unexpected response parsed.
- [ ] Recovery deep link: `type=recovery`, expiry/error validated, intent cleared, PKCE where supported.
- [ ] Concurrent refresh uses single-flight; one 401 retry; network failure preserves encrypted token.
- [ ] Sign-out cancels relevant WorkManager work or records pending logout; offline sign-out behavior explicit.
- [ ] Password change uses session + reauth, not implicit re-sign-in.
- [ ] Account deletion RPC exists in canonical schema; storage cleanup verified; UI warning matches local-data retention.
- [ ] Avatar SAF denial and oversized download/decode handled without logging content.
- [ ] Tokens, passwords, emails and recovery tokens absent from logs/UI errors.

---

## 7. Vault and sensitive data

- [ ] Release configuration has no "Reset Vault QA data" affordance; debug/internal gating test passed when executed.
- [ ] `VaultCryptoTest` still passes; no weakening of AES-256-GCM/PBKDF2 310k/zeroization.
- [ ] `FLAG_SECURE` applied only while unlocked and cleared on lock/picker completion; device-verified screenshots/recents.
- [ ] Idle autolock and picker-close lock verified; single `lockVault()` helper clears key/items/editor state.
- [ ] Biometric/password flows either wired with tested Keystore behavior or removed with docs corrected.
- [ ] v3 encrypted export → restore round-trip; legacy v1/v2 compatibility.
- [ ] TOTP masked by default; reveal/copy intentional; no plaintext in logs.
- [ ] Single clipboard mechanism with worker fallback; duplicate handler/worker race removed.
- [ ] Cloud-key escrow threat model shown in UI; no E2EE claim.
- [ ] Dirty unreadable vault records produce clear pending state, not silent skip/loss.

---

## 8. Notifications, permissions and device features

- [ ] One-time custom notification remains posted after delivery until user action; row state correct; no duplicate after reboot/reschedule.
- [ ] Weekly weekdays fire on selected day for all 7 days; legacy stored masks repaired additively.
- [ ] Quiet hours defer rather than discard; copy matches behavior.
- [ ] Alarm registry cancels main, snooze and action PendingIntents; boot/time/date recovery verified.
- [ ] `POST_NOTIFICATIONS` and exact-alarm states checked before health claims; denied states handled.
- [ ] All notification posts go through guarded `ZyloNotifications`; no unguarded usage/network posts; duplicate channel creation removed.
- [ ] Network alert burst capped/collapsed; no 64-alert storm.
- [ ] Power events have one owner; receiver registration/unregistration verified; diagnostics not duplicated.
- [ ] Overlay granted/denied/revoked and notification fallback verified; overlay copy accurate.
- [ ] FGS start/stop, `START_STICKY`/special-use justification, OEM battery optimization and Android 12+ background start device-checked.
- [ ] Usage access revocation and accessibility toggle detected; screen-time limitation copy accurate.
- [ ] Permission revisit from Settings refreshes on resume; no repeated prompting; local features remain usable when denied.
- [ ] Onboarding cards progress/dismiss/snooze/revisit; system back persists completion; no gate flash.

---

## 9. Compose UI and accessibility

- [ ] Stable test tags for tiles, cards, editors, dialogs and error states.
- [ ] Real `MainActivity` startup smoke; no `setContentView` shortcut where startup/permission behavior matters.
- [ ] Assert persisted outcomes, not only text presence; replace no-assertion tests.
- [ ] Loading, empty, error/retry, permission-denied and hidden-module states visually distinct.
- [ ] 360dp, 2× font, dark theme, reduced-motion, dynamic-accent and glass-intensity previews/device checks.
- [ ] Chart summaries/progress range info available to TalkBack; decorative icons `contentDescription = null`.
- [ ] `clickable(role, onClickLabel)` on interactive rows; focus order checked on device.
- [ ] Contrast of HeroCard/text on gradients measured; no unverified "accessible" claim.
- [ ] User-visible strings extracted; no misleading "Stored on this device"/"on-device"/"synced" copy when state differs.

---

## 10. Performance

- [ ] Replace `metrics = emptyList()` with `FrameTimingMetric`/jank metric; stable tags instead of hardcoded swipes.
- [ ] Benchmark handles permission gate and missing text; cold startup and core scrolls measured.
- [ ] Baseline profile collected, reviewed and committed to `app/src/release/generated/baselineProfiles/` only after real run.
- [ ] Large-file/import/bitmap paths bounded; no OOM on crafted XLSX/oversized SAF input.
- [ ] Non-lifecycle `collectAsState()` usages replaced; giant composables split without behavior change.
- [ ] Performance numbers published only from executed runs; otherwise marked TBD/Unverified.

---

## 11. Manual device matrix

For each row, record device/API/OEM, date and observed result. None were run in this audit.

- [ ] Fresh install: local-only use, onboarding cards, permission grant/deny/revoke, system back.
- [ ] Offline → reconnect: sync runs, accurate status, no overwrite of dirty edits.
- [ ] Expired session / network failure: clear recovery, local data intact.
- [ ] Account switch with pending WAL and multiple local accounts: no loss.
- [ ] Restart mid-sync and mid-backup: no partial/corrupt state.
- [ ] Alarm delivery: reboot, timezone/date change, Doze, exact-alarm denial, quiet hours.
- [ ] SAF: grant/revoke folder and file URIs; missing-file states; weekly backup persistence.
- [ ] Vault: screenshot/recents, autolock, picker completion, clipboard clear, encrypted export/restore.
- [ ] Device feature: overlay, accessibility service, usage access, notification channel blocked, battery optimization.
- [ ] Language: offline-only translation, model availability, cloud opt-in disclosure.
- [ ] Dark mode, large font, TalkBack, reduced motion, narrow screen.
- [ ] API 26+ representative devices and at least one OEM with aggressive battery management.

---

## 12. Final release acceptance

- [ ] No unresolved Critical or High issue from `04-critical-issues-immediate-attention.md`.
- [ ] All executed unit/instrumentation/Compose tests pass; failures triaged with evidence.
- [ ] CI green on final integration branch with artifacts/reports uploaded.
- [ ] Staging RLS smoke and admin wipe checks executed and recorded.
- [ ] Device matrix completed or remaining items explicitly labelled Unverified with rationale.
- [ ] Docs updated: README, vault/security, sync, phase docs marked historical or corrected.
- [ ] Branch merged only via reviewed PR; no direct `main` commits.

---

## 13. Audit baseline (not a pass claim)

At audit time, source contained 8 unit test classes / 35 `@Test`, 5 instrumentation classes / 15 `@Test`, and 2 benchmark classes / 4 `@Test`. Cached unit XML from 2026-11-25 showed 35 tests / 0 failures, but **that artifact was not produced during this audit** and is not evidence for this branch. Every checkbox above must be independently executed and recorded.

---

## 14. Execution record — modernization implementation (2026-11-26)

Branch `impl/m1-data-safety`; all changes uncommitted. Commands were run from `C:\Projects\Momentum` on Windows with Gradle 8.7. These entries record only commands that were actually run; they do not check off device, remote, or release-signing items above.

- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M1 baseline) | 45 tests, 0 failures | M1 data-safety work`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest --no-daemon | PASS (M1 baseline) | 45 tests, 0 failures | M1 data-safety work`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | M1 instrumentation added, not executed`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M2) | 65 tests, 0 failures/errors/skips | M1+M2`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M2) | 65 tests, 0 failures/errors/skips | release signing unconfigured; no APK`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | M2 instrumentation not executed`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon --tests com.faheem.habits.features.contacts.ContactImportTest | PASS | first attempt hit a transient ksp file-lock; identical retry succeeded | SAF bounded-import fixtures`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M3) | 75 tests, 0 failures/errors/skips | 10 new JVM tests (MomentumRoutes, MotionAndToken)`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M3) | 75 tests, 0 failures/errors/skips | new JVM tests included in both variants`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | includes new DesignSystemUiTest and HiddenModuleDeepLinkTest`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M4) | 85 tests, 0 failures/errors/skips | onboarding reducer, permission summary, exact signed-out copy`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M4) | 85 tests, 0 failures/errors/skips | release signing unconfigured; no APK`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | includes OnboardingProgressStoreTest and OnboardingCardsUiTest`
- `2026-11-26 | .\gradlew :app:compileDebugKotlin --no-daemon | PASS (M5) | main sources compile | auth/signup/recovery/link/deletion changes`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M5) | 102 tests, 0 failures/errors/skips | signup matrix, recovery links, link decisions, deletion copy, backup naming`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M5) | 102 tests, 0 failures/errors/skips | release signing unconfigured; no APK`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | includes LocalBackupCryptoTest`
- `2026-11-26 | .\gradlew :app:compileDebugKotlin --no-daemon | PASS (M6) | main sources compile | final habit/task and quick-add wiring`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M6) | 113 tests, 0 failures/errors/skips, 23 suites | streak and reminder-policy tests included`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M6) | release signing unconfigured; no APK | same JVM tests included`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | M6 instrumentation/Compose tests were not executed`
- `2026-11-26 | .\gradlew :app:compileDebugKotlin --no-daemon | PASS (M7) | main sources compile | Room budget/payment flows and minor-unit UI wiring`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M7) | 123 tests, 0 failures/errors/skips, 24 suites | one initial assertion was wrong; corrected and rerun successfully`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M7) | release signing unconfigured; no APK | same JVM tests included`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | includes Room legacy-budget migration and restored-budget assertions; not executed`
- `2026-11-26 | .\gradlew :app:compileDebugKotlin --no-daemon | PASS (M8) | main sources compile | bounded parser/import and file-link changes`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M8) | 143 tests, 0 failures/errors/skips, 27 suites | first attempt hit the command timeout before a result; a bounded-read test expectation was corrected and the identical command passed`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M8) | 143 tests, 0 failures/errors/skips, 27 suites | release signing unconfigured; no APK`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | includes SAF denial, size cap, re-grant, Room rollback and denied-file UI copy tests; not executed`
- `2026-11-26 | .\gradlew :app:compileDebugKotlin --no-daemon | PASS (M9) | main sources compile | vault masking/lock/backup and Screen Time policy wiring`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M9) | 157 tests, 0 failures/errors/skips, 29 suites | first run failed on threat-model copy wording; corrected and identical command rerun successfully`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M9) | 157 tests, 0 failures/errors/skips, 29 suites | release signing unconfigured; no APK`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | includes Vault threat-model, TOTP masking and usage-revocation copy assertions; not executed`
- `2026-11-26 | .\gradlew :app:compileDebugKotlin --no-daemon | PASS (M10) | main sources compile | shared channels, collapsed alerts, monitor recovery and single power-event owner`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M10) | 173 tests, 0 failures/errors/skips, 32 suites | first two attempts found incorrect threshold expectations in a new test; corrected and identical command passed`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M10) | 173 tests, 0 failures/errors/skips, 32 suites | release signing unconfigured; no APK`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | includes usage channel creation and quiet-hours/restart copy assertions; not executed`
- `2026-11-26 | .\gradlew :app:compileDebugKotlin --no-daemon | PASS (M11) | main sources compile | sync reconciliation, pending deletes, storage tombstones and backup v4 changes`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M11) | 187 tests, 0 failures/errors/skips, 38 suites | first run found JVM key-destroy and UUID-case issues; corrected and identical command passed`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M11) | 187 tests, 0 failures/errors/skips, 38 suites | release signing unconfigured; no APK`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | includes v2/v3/v4 backup round trips, language tables and encrypted import/export; not executed`
- `2026-11-26 | .\gradlew :app:compileDebugKotlin --no-daemon | PASS (M12) | main sources compile | first run found a missing KeyboardOptions import; corrected and identical command passed`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M12) | 209 tests, 0 failures/errors/skips, 44 suites | appearance/settings/search pure-policy tests included`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M12) | 209 tests, 0 failures/errors/skips, 44 suites | release signing unconfigured; no APK`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | M12 settings/search sources compile; instrumentation not executed`

- `2026-11-26 | .\gradlew :app:compileDebugKotlin --no-daemon | PASS (M13) | main sources compile | first run found an expression-body return in the bounded decoder; corrected and identical command passed`
- `2026-11-26 | .\gradlew :app:testDebugUnitTest --no-daemon | PASS (M13) | 216 tests, 0 failures/errors/skips, 46 suites | first run found an incorrect exact-budget expectation; corrected and identical command passed`
- `2026-11-26 | .\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon | PASS (M13) | 216 tests, 0 failures/errors/skips, 46 suites | release signing unconfigured for this command; no distributable APK`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | M13 sources compile; instrumentation not executed`
- `2026-11-26 | .\gradlew :benchmark:compileBenchmarkReleaseKotlin :benchmark:compileNonMinifiedReleaseKotlin --no-daemon | PASS | benchmark sources compile | first run found UiObject2.click() returns Unit; corrected and identical command passed; no measurement`
- `2026-11-26 | .\gradlew detekt --no-daemon | PASS (M13) | baseline regenerated against current source | stale AuthScreen entries removed; ratchet now clean`
- `2026-11-26 | .\gradlew :app:lintDebug --no-daemon | PASS (M13) | 0 errors | first run found 2 MissingPermission and 1 SuspiciousIndentation errors; corrected and identical command passed`
- `2026-11-26 | .\gradlew lint --no-daemon | PASS (M13) | root lint task green | warnings remain, no errors`
- `2026-11-26 | .\gradlew :app:assembleRelease --no-daemon with ephemeral local key | PASS (M13) | R8/resource shrink/package smoke only | throwaway key in temp; no distributable signed release; no runtime smoke`
- `2026-11-26 | manual `am start-activity -W -S` cold start, Xiaomi 2404ARN45A Android 16/API 36, benchmarkRelease signed with ephemeral CI key | MEASURED (rough) | Before responsiveness fixes: TotalTime ms 952,708,710,744,737 avg 770.2; After: 782,661,764,618,608 avg 686.6 | Not Macrobenchmark; measures first drawn activity frame only, not restore-to-usable-home, scroll jank, or tap latency`
- `2026-11-26 | .\gradlew :benchmark:connectedBenchmarkReleaseAndroidTest --no-daemon with ephemeral local signing properties | FAIL (no metrics) | USB install restriction, then DROP_SHADER_CACHE broadcast not received / Unable to confirm activity launch completion | No StartupTimingMetric/FrameTimingMetric/baseline profile captured`
- `2026-11-26 | .\gradlew :app:compileDebugKotlin --no-daemon | PASS (post-M13 responsiveness) | main sources compile | startup/vault/files/home/search/navigation responsiveness fixes`
- `2026-11-26 | .\gradlew testDebugUnitTest testReleaseUnitTest --no-daemon with ephemeral local signing properties | PASS (post-M13 responsiveness) | 216 tests, 0 failures/errors/skips, 46 suites | same suite count as M13`
- `2026-11-26 | .\gradlew detekt --no-daemon | PASS (post-M13 responsiveness) | clean | no new baseline entries required`
- `2026-11-26 | .\gradlew :app:compileDebugAndroidTestKotlin --no-daemon | PASS | compilation only | instrumentation not executed`
- `2026-11-26 | .\gradlew lint --no-daemon | PASS (post-M13 responsiveness) | root lint task green | warnings remain, no errors`
- `2026-11-26 | .\gradlew :app:assembleRelease --no-daemon with ephemeral local signing properties | PASS (post-M13 responsiveness) | R8/resource shrink/package smoke only | throwaway key in temp; no distributable signed release; no runtime smoke`

**Explicitly Unverified:** no connected/device/emulator instrumentation execution; no Compose runtime interaction or visual review of the eight settings groups, hidden-module dialog, account subtext, appearance controls or global search screen; no real SAF re-grant or ContentResolver denial for searchable files, no search latency measurement, no real backup restore appearance-sync UX; no real SAF grant/revoke or ContentResolver denial, device OOM or hostile-image decode measurement, real cloud upload/download or restore; no real clipboard clearing, `FLAG_SECURE`/screenshot, autolock, accessibility-service, overlay, alarm, exact-alarm, notification-channel or foreground-service runtime behavior; no Play-policy decision on the optional Instagram scroll counter; no boot/restart, time/date/timezone change, battery-optimization or OEM background restriction check; no TalkBack, contrast, 360dp/2× font, dark/reduced-motion/dynamic-accent visual review; no Macrobenchmark StartupTimingMetric/FrameTimingMetric or baseline profile generation (device attempt failed to produce metrics; only rough manual `am start -W` cold-start timing was recorded); no scroll/tap jank measurement; no API 26+ device matrix, offline/reconnect, admin-wipe RPC or production backup passphrase flow; no GitHub CI run or green status on the integration branch; no signed distributable release; no Supabase/Edge Function deployment or RLS smoke; no real periodic sync/WorkManager constraint or cloud storage-object deletion. Lint, detekt, JVM suites, source compilation, R8 assemble and benchmark compilation were executed above; remote and device items remain unchecked until executed and recorded.
