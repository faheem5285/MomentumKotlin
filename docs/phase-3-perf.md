# Phase 3 — Performance and smoothness

## Instrumentation added

- `benchmark/` is an Android test module targeting the app.
- `MomentumMacrobenchmark` covers cold startup, Home scrolling, and Habits
  Today scrolling.
- `MomentumBaselineProfile` covers startup and a core module transition.
- The app consumes the generated baseline profile and includes ProfileInstaller.
- Release R8/resource shrinking remains enabled; keep rules cover Room,
  serialization, the accessibility service, and Vault crypto.
- Debug StrictMode checks disk, network, leaked closable objects, and leaked
  registrations. LeakCanary is debug-only.

## Measurements

Baseline and after numbers are intentionally not fabricated. The current
environment can compile the benchmark module but cannot produce reliable
Macrobenchmark numbers without a supported physical/device or emulator run.

| Scenario | Before | After | Device/build |
|---|---:|---:|---|
| Cold startup | TBD | TBD | Run on a release-like benchmark build |
| Home scroll | TBD | TBD | Run on a release-like benchmark build |
| Habits Today scroll | TBD | TBD | Run on a release-like benchmark build |

## Run locally

1. Connect an API 28+ physical device or emulator and enable profiling/USB
   installation.
2. Run:

   ```text
   gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest
   gradlew.bat :benchmark:collectNonMinifiedReleaseBaselineProfile
   ```

3. Read the benchmark results under
   `benchmark/build/outputs/connected_android_test_additional_output/` and
   commit generated baseline profile output only after reviewing it.

## Deferred

- Replace the network indicator polling loop with lifecycle/callback-driven
  updates; it is a user-enabled foreground service and requires an explicit
  product behavior decision.
- Replace battery listener background behavior with a callback-only design;
  its current service is user-enabled and event-driven rather than polling.
- Full Compose stability metrics and immutable-model migration for every
  feature remain follow-up work.
- Paging for expenses/completions is deferred until realistic row volumes and
  UX requirements are measured.
