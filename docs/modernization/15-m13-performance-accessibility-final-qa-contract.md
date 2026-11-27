# Milestone 13 — Performance, Accessibility, and Final QA Contract

**Status:** Implemented on `impl/m1-data-safety`; local JVM, lint, detekt, compile, and R8-assemble checks executed. A post-M13 responsiveness pass implemented targeted startup/scroll/click fixes and captured a rough manual device cold-start measurement; Macrobenchmark metrics, frame/jank measurements, CI, and remote behavior remain **Unverified**.
**Date:** 2026-11-26.

## 1. Flow collection is lifecycle-aware

All remaining plain `collectAsState()` call sites were replaced with `collectAsStateWithLifecycle()`:

- `features/contacts/ContactsScreen.kt` — contact list and import state.
- `features/language/LanguageScreen.kt` — words, notes, review states, history, translation and proofreading state.
- `core/notifications/CustomNotificationScreen.kt` — custom notification list.

This matches the pattern already used by Home, Loans, Network and the other migrated screens. Collection now stops when the composition is not started, avoiding needless Room/DataStore work in the background.

## 2. Image and file decoding are bounded

`core/data/BoundedInputStream.kt` refuses reads past an explicit byte budget and probes for EOF before reporting that a stream is exhausted. `ImagePreviewPolicy` centralizes source-byte, decode-dimension and PDF-preview limits, and produces power-of-two sample sizes without unbounded loops. `BoundedImageLoader` reads through the limited stream, decodes bounds first, and decodes with a sampled `RGB_565` bitmap.

Every SAF/network preview path now uses the shared policy:

- `features/files/FilesScreen.kt` — image preview and PDF page sizing.
- `features/contacts/ContactsScreen.kt` — stored contact photos and pending avatar preview.
- `features/loans/LoansScreen.kt` — person avatars and attachment previews.
- `features/profile/ProfileScreen.kt` — remote and local avatar decoding.

The old direct `BitmapFactory.decodeStream()` calls and the sample loop with no upper bound were removed. New JVM tests cover exact-budget reads, rejection past the budget, EOF at the exact limit, bounded `skip`, sample-size capping, dimension calculation and PDF scaling.

## 3. Benchmark targets are stable and meaningful

`benchmark/MomentumMacrobenchmark.kt` now measures cold startup with `StartupTimingMetric` and Home/Habits scrolling with `FrameTimingMetric`; the previous `metrics = emptyList()` entries were replaced. Hardcoded swipes target semantic descriptions exposed by the app (`Home records`, `Habits for today`) and skip gracefully when onboarding or module visibility hides the target. `MomentumBaselineProfile.kt` follows the same semantic targets instead of a brittle text-only tap.

Both benchmark variants compile. A connected attempt was made on Xiaomi 2404ARN45A (Android 16, API 36, arm64-v8a) on 2026-11-26: the APKs installed after enabling Xiaomi USB install, but Macrobenchmark did not produce metrics. `coldStartup` failed with `DROP_SHADER_CACHE broadcast was not received` (profileinstaller 1.4.1 is present and the receiver is kept in mapping; manual `adb shell am broadcast ... DROP_SHADER_CACHE ...` returned `result=0`), and `homeScroll`/`habitsTodayScroll` failed with `Unable to confirm activity launch completion`. **No StartupTimingMetric/FrameTimingMetric numbers were captured, and no baseline profile was generated.**

## 4. CI, detekt and R8 are wired with artifacts

`.github/workflows/android.yml` now includes:

- `workflow_dispatch` and cancellation of superseded runs on the same ref.
- Debug and release JVM tests; release tests skip only the dedicated-signing verification task.
- An ephemeral CI keystore for an R8/shrink release assemble; no production key is stored in the repository.
- Lint, detekt, report/APK uploads on every run.
- A connected-Android-test job using an API 34 x86_64 emulator.
- A manual-only benchmark/baseline-profile job with report uploads.

`config/detekt-baseline.xml` was regenerated against the current source instead of retaining stale entries for the deleted `AuthScreen.kt`. Compared with `HEAD`, the baseline has 383 entries (342 before), with 170 additions, 129 removals and zero stale `AuthScreen.kt` entries. This is an explicit ratchet: `detekt` now passes on the current tree, and new violations will fail it.

An R8 assemble smoke build was executed locally with an ephemeral throwaway key in the approved temp directory. It verifies minify/resource shrink/signing plumbing only; it is not a distributable release and no runtime smoke test was performed.

## 5. Accessibility cleanup that can be made in source

Home and Habits scroll containers expose stable semantic descriptions and test tags for benchmark and accessibility tooling. Existing icon buttons already carry `onClickLabel`/`contentDescription`; M12 added the same pattern to Search and Settings actions. `core/ui/Accessibility.kt`, `Motion.kt` and the existing reduced-motion/dynamic-accent policy remain the source of truth.

**Unverified:** TalkBack focus order, contrast ratio measurement, 360dp and 2× font layouts, dark/reduced-motion/dynamic-accent visual review, and any real accessibility-service behavior. These require a device or emulator run and are not claimed here.

## 6. Final QA status of the original critical/high findings

| Original finding | Source-level disposition | Runtime evidence |
|---|---|---|
| Account-switch WAL loss | Additive active-user handling and account-isolation instrumentation source added in M1/M2 | Instrumentation **not executed** |
| Full reset deletes all accounts | Active-account-scoped reset path added in M1/M11 | Instrumentation **not executed** |
| Sync ignores dirty/deleted state | `SyncModels` reconciliation, tombstones and conflict policy in M11 | JVM tests passed; cloud/device **Unverified** |
| REPLACE cascades lose children | Targeted upserts and child-preservation tests in M6/M7/M11 | JVM tests passed; instrumentation **not executed** |
| Production Vault QA reset | Vault QA affordance removed/gated in M9 | Compose/runtime claim **Unverified** |
| Backup omits language tables / plaintext PII | Backup v4, language tables, compatibility readers and optional AES-GCM in M11 | JVM round-trip tests passed; device UX **Unverified** |
| One-time/weekly notification bugs | Shared weekday mapping, deferred quiet-hours and single power-event owner in M10 | JVM tests passed; device alarm/OEM behavior **Unverified** |
| Translation privacy wording | Disclosure/offline-first wording corrected in M6/Language work | Device network observation **Unverified** |
| Vault controls absent | Lifecycle `FLAG_SECURE`, masking, timeout and accurate threat-model copy in M9 | Screenshot/autolock/biometric device checks **Unverified** |
| Token race / raw error body | Single-flight refresh, sanitized errors and hardened token storage in M2/M5 | Remote/device race checks **Unverified** |
| Unbounded file/zip/bitmap reads | M8 bounded import/stream caps and M13 bounded image/PDF decoders | JVM policy tests passed; real hostile SAF file **Unverified** |
| Hidden modules unrecoverable | M12 Settings recovery dialog | Compose runtime **Unverified** |
| CI lacks connected tests/artifacts/benchmark | M13 workflow jobs and artifact uploads added | GitHub CI run **not executed** |

There is no known unresolved critical/high **source-level** defect. Device-, OEM-, remote- and CI-dependent items are explicitly not marked verified.

## 7. Verification record

**Executed (2026-11-26, Windows, Gradle 8.7):**

- `.\\gradlew :app:compileDebugKotlin --no-daemon` — PASS after one fix iteration (Kotlin expression-body `return` in `BoundedImageLoader.kt`); the identical command passed afterward.
- `.\\gradlew :app:testDebugUnitTest --no-daemon` — PASS, 216 tests, 0 failures/errors/skips, 46 suites. The first attempt failed because the new exact-budget expectation forgot to consume the first two bytes; the corrected test and identical command passed.
- `.\\gradlew :app:testReleaseUnitTest -x :app:verifyReleaseSigning --no-daemon` — PASS, 216 tests, 0 failures/errors/skips, 46 suites.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only; no connected execution).
- `.\\gradlew :benchmark:compileBenchmarkReleaseKotlin :benchmark:compileNonMinifiedReleaseKotlin --no-daemon` — PASS after one fix iteration (`UiObject2.click()` returns `Unit`, not `Boolean`); the identical command passed afterward.
- `.\\gradlew detekt --no-daemon` — PASS after regenerating the stale baseline as described above.
- `.\\gradlew :app:lintDebug --no-daemon` — PASS after fixing two `MissingPermission` findings and one `SuspiciousIndentation` finding in `ZyloNotifications.kt`; the identical command passed afterward.
- `.\\gradlew lint --no-daemon` — PASS.
- `.\\gradlew :app:assembleRelease --no-daemon` with ephemeral local signing properties — PASS; R8, resource shrink and package steps completed.

**Unverified:** connected/emulator instrumentation, Compose runtime interaction, Macrobenchmark StartupTimingMetric/FrameTimingMetric measurements, baseline profile generation, API 26+ device matrix, offline/reconnect, battery-optimization/OEM/alarm/reboot/time-change behavior, TalkBack/contrast/360dp/2× font/dark/reduced-motion/dynamic-accent visual review, real SAF denial/OOM/hostile-input behavior, real cloud/Supabase/RLS/Edge Function behavior, Play-policy decisions, GitHub CI green status, and any signed distributable release.

## 8. Post-M13 responsiveness pass (2026-11-26)

Targeted source fixes applied for the reported "open/scroll/click response too late" symptom:

- `MainActivity`: moved session restore + remote wipe check off the composition/Main path with `withContext(Dispatchers.IO)`; moved ON_RESUME signed-in identity read into IO and throttled remote wipe checks to once per 5 minutes per process while preserving reminder rescheduling.
- `MomentumApplication`: kept only `AppearancePreferences.load` on Main; deferred battery history recording, usage-limit schedule refresh, Room invalidation observer registration, monitor resume, periodic sync config, and initial dirty-change enqueue to `syncScope` on IO. The post-registration dirty-change check covers writes missed before observer registration.
- `GlobalSearchViewModel`: added `flowOn(Dispatchers.Default)` before `stateIn` so document/search combination and query filtering do not run on Main collection by default.
- `VaultScreen`/`VaultCrypto`: memoized filtered/sorted items, health report, counts, chart slices, and attention IDs; hoisted vault category keyword lists to constants to avoid per-item allocations.
- `FilesScreen`: hoisted extension sets; memoized filtered files, file-mix slices, and 14-day addition counts.
- `HomeScreen`: moved permission snapshot OS checks to IO inside the existing resume/tick effect; hoisted home date formatter; stabilized row keys by module name.
- `Navigation`: memoized per-module `MaterialTheme` color scheme.

**Manual device measurement (rough, not Macrobenchmark):** Xiaomi 2404ARN45A, Android 16 / API 36, `arm64-v8a`, same `benchmarkRelease` APK type signed with the same ephemeral CI keystore, `adb shell am force-stop` + 2s pause + `am start-activity -W -S -n com.faheem.habits/.app.MainActivity`, 5 runs. Before fixes: TotalTime ms `952, 708, 710, 744, 737` (avg 770.2). After fixes: `782, 661, 764, 618, 608` (avg 686.6). `am start -W` reports time to first drawn activity frame; it does not measure restore-to-usable-home time, scroll frame timing, or tap latency. **No Macrobenchmark FrameTimingMetric/StartupTimingMetric was captured.**

**Post-fix verification executed (2026-11-26):**

- `.\\gradlew :app:compileDebugKotlin --no-daemon` — PASS.
- `.\\gradlew testDebugUnitTest testReleaseUnitTest --no-daemon` with ephemeral local signing properties for release-unit path — PASS, 216 tests, 0 failures/errors/skips, 46 suites.
- `.\\gradlew detekt --no-daemon` — PASS.
- `.\\gradlew :app:compileDebugAndroidTestKotlin --no-daemon` — PASS (compilation only).
- `.\\gradlew lint --no-daemon` — PASS.
- `.\\gradlew :app:assembleRelease --no-daemon` with ephemeral local signing properties — PASS (R8/resource shrink/package smoke only; no distributable signed release).
