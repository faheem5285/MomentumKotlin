# Phase 9 - Quality, CI, and repository

## Completed foundations

- Added the Phase 9 GitHub Actions workflow for lint, Detekt, unit tests,
  debug assembly, and release assembly with an ephemeral CI signing key.
- Added monthly Dependabot updates for Gradle and GitHub Actions.
- Added Compose UI-test dependencies and smoke coverage for habit creation,
  habit navigation/completion controls, expense entry, and vault unlock
  controls.
- Added accessible progress semantics to the shared progress ring and guarded
  test notifications with the Android 13 notification permission.
- Added `.kotlin/` to `.gitignore`, a current README, current NOTES, and a
  CHANGELOG.
- Corrected migration test coverage to validate the current Room schema.
- Added the R8 rule for the optional SLF4J binding lookup used by the sync
  client.

## Changes in this pass

- Configured Android CI to install only `platform-tools`, Android platform 34,
  and build-tools 34.0.0. The action's default legacy `tools` package is no
  longer available and was preventing SDK setup.
- Corrected the tracked `gradlew` file mode to executable (`100755`) so Linux
  CI can invoke `./gradlew`.

## Verification

- Previous run failed during Android SDK setup because the action attempted to
  install the retired `tools` package.
- The next run passed SDK setup but exposed the non-executable Gradle wrapper.
- The current run for commit `13ffe77447031a26dc156ff4b6ea0886d223b51e`
  passed SDK setup; the lint step was still running when this note was written.
  Do not treat the full CI suite as passed until the run completes:
  https://github.com/faheem5285/MomentumKotlin/actions
- Instrumented tests require an emulator/device; compiling them is not the same
  as executing them.

## Open review and known limits

- `faheem5285/MomentumKotlin#1` remains a draft with no reviews or status
  checks. Keep it unmerged. Its permission change should be split into a
  focused PR; unrelated historical asset/UI/data changes should be closed or
  moved to separate reviewed commits.
- This branch diverges from `main` (31 commits ahead and 9 behind at review
  time). No merge or rebase was performed; reconcile that history separately.
- The instructions reference `docs/PHASES.md`, but that file is not present on
  this branch. The existing roadmap and phase notes were used instead.
- Physical-device UI/accessibility checks, isolated end-to-end Compose
  assertions, and repository metadata cleanup remain deferred.
