# Momentum — Phase 1 audit and delivery plan

**Date:** 2026-10-29  
**Scope:** Read-only product and engineering audit. This document proposes the work and review gates; it does not change application code.

## Executive summary

Momentum already has a broad set of personal productivity modules: habits and reminders, tasks, money and loans, files, a password vault, screen-time/Reels tracking, network usage, and battery/charging tools. The app is a native Kotlin/Jetpack Compose application with Room persistence and a single-activity shell. The modules are present, but the next step toward dependable, enterprise-quality software is to improve the foundations: protect user data, prove behavior with repeatable checks, clarify permission/background behavior, and make the structure and release process maintainable.

The highest-priority risks from this audit are:

1. **Data loss risk:** Room has migrations through version 9 but also enables destructive migration fallback. An unhandled schema upgrade can erase the local database.
2. **Vault backup/key lifecycle needs a decision:** vault entries are encrypted with AES-GCM and a PBKDF2-derived key, while Android Auto Backup is enabled for the app and no backup rules were found. Decide and document whether encrypted vault records should transfer, and how users recover them, before calling the vault production-ready.
3. **Release is not production-signed:** the release build uses the debug signing config.
4. **Reliability evidence is missing:** no automated test source set was found. Core scheduling, migrations, calculations, crypto, and permission-denied paths need tests and a device verification checklist.
5. **Background features depend on special Android access:** exact alarms, usage access, overlay access, accessibility service, and foreground services all need clear user explanations, graceful fallback, and release-policy review.

## Current product and engineering shape

### Product modules found

| Area | Current capability observed | Main review question |
| --- | --- | --- |
| Habits | Habit library, schedules, completions, multiple reminders, insights | Can users understand reminder dates, recurrence, and missed notifications? |
| To-dos | Due dates, categories, priority, completion, reminders | Are recurring tasks, edits, and notification rescheduling consistent? |
| Money and loans | Expense records, categories, lending/borrowing, settlement | Are totals, currency, edits, and export behavior clear and correct? |
| Files | Document library using content URIs, in-app preview/basic editing | What formats are supported, and what happens when a URI grant expires? |
| Vault | Encrypted records using PBKDF2-HMAC-SHA256 (310,000 iterations) and AES-256-GCM | How do lock timeout, clipboard clearing, backup/restore, and recovery work? |
| Screen time | Usage access and optional Instagram scroll/Reels counter accessibility service | Are estimates labeled honestly and access explained before settings are opened? |
| Network | Device network usage, Wi-Fi/mobile breakdown, persistent indicator | How are missing carrier/usage stats represented across devices? |
| Battery | Charging monitor and selectable charging overlay | Does the user understand the persistent notification and overlay/background requirements? |

### Architecture and release facts

- Kotlin 2.0.20, Android Gradle Plugin 8.5.2, Compose, Room 2.6.1, Java 17; `minSdk 26`, `targetSdk 34`, `compileSdk 34`.
- One `MainActivity` hosts the Compose application. Source files are physically grouped under `app`, `core`, and `features`, but the inspected Kotlin files still declare the same `com.faheem.habits` package. This gives a useful directory map without enforcing module boundaries.
- Room database version 9 has explicit migrations from 4→5 through 8→9, and `fallbackToDestructiveMigration()` is also configured.
- Release minification/resource shrinking are enabled, but release signing points at the debug signing config. `versionCode`/`versionName` are still `1`/`1.0`.
- The manifest includes usage stats, package visibility (`QUERY_ALL_PACKAGES`), notifications, exact alarms, overlays, foreground services, and an exported accessibility service protected by `BIND_ACCESSIBILITY_SERVICE`.
- `allowBackup="true"` is set; no backup/data-extraction rule resources were found in the inspected resource tree. Android Auto Backup includes most app data by default, so explicitly decide the database/preferences backup policy, including vault salt/verifier and encrypted records.
- No automated test source files were found during the project inventory. `NOTES.md` does not match the current source layout/database version and should not be treated as authoritative documentation.

## Prioritized backlog

### P0 — Protect user data and release integrity

1. **Remove silent destructive migration behavior.** Define a migration policy for every database version, retain and test migration fixtures, and provide an explicit recovery/export path for unrecoverable upgrades. Do not ship a migration gap that can silently wipe habits, transactions, tasks, or vault entries.
2. **Decide backup and restore behavior.** Explicitly include/exclude database and preferences for cloud backup and device transfer. Design the vault flow together with this decision: determine if ciphertext is backed up, whether the key/verifier material transfers, and what restore experience is safe and understandable. Avoid claiming vault portability until restore is tested.
3. **Establish release signing/versioning.** Use a protected release key or Play App Signing workflow, keep debug and release keys separate, define version-code increments, and verify a signed release artifact.
4. **Write and approve a data inventory.** Document each stored category, sensitive permissions, local/network behavior, retention, export, and deletion behavior. Confirm the app’s privacy copy and store declarations against actual behavior before distribution.

**P0 acceptance gate:** Existing user records survive every supported upgrade path; backup/restore behavior is intentional and documented; the vault has a tested unlock/restore story; a release artifact is signed with a non-debug key.

### P1 — Make critical behavior testable and dependable

5. **Add a quality baseline.** Add unit tests for date/recurrence scheduling, money and loan totals, network unit formatting, vault crypto round trips/wrong-key/tamper rejection, and repository transformations. Add Room migration tests from supported prior schemas. Add a small UI smoke flow for create/edit/delete/complete and permission-denied states.
6. **Make permission and background states first-class.** Create one permission readiness/status surface that explains notification permission, exact alarm access, usage access, overlay access, accessibility access, and notification channel settings. Every feature should show a useful disabled/limited state and a direct route to the relevant Android setting.
7. **Validate notification reliability.** Test reboot, time/timezone changes, app update, exact-alarm denial/revocation, notification denial/channel mute, date-only reminders, multiple daily habit reminders, and task edits/deletions. Android can deny exact alarms on fresh installs and restored devices, so scheduling must check permission and clearly report whether it is exact or approximate. [Android exact-alarm guidance](https://developer.android.com/develop/background-work/services/alarms)
8. **Review long-running services and store eligibility.** The battery monitor declares `specialUse`; Android requires a manifest explanation and Google Play reviews the use case. Verify the user-visible purpose, start/stop lifecycle, notification, Android-version behavior, and store eligibility before shipping. [Android foreground-service type guidance](https://developer.android.com/about/versions/14/changes/fgs-types-required)
9. **Document sensitive-feature limits.** Treat the Reels count as an estimate based on accessibility scroll events, not a verified view count. Explain local processing, opt-in, and how to disable it. Review `QUERY_ALL_PACKAGES` necessity and data access before any public release.

**P1 acceptance gate:** Critical domain logic and supported migrations have automated coverage; each optional permission can be denied without crashing or blocking unrelated modules; the reminder/device matrix passes; all persistent services explain their purpose and can be controlled by the user.

### P2 — Make the codebase easier to extend

10. **Introduce enforced feature boundaries incrementally.** Move shared persistence/domain contracts into `core`, then give features their own Kotlin packages, UI/state/repository boundaries, and navigation contracts. Keep this incremental so existing behavior remains reviewable; do not split into Gradle modules without a build-time or team need.
11. **Centralize app state and navigation.** Define one navigation model, app-level permission state, and shared error/loading/empty-state patterns. Keep screens from owning long-lived persistence and scheduling logic directly.
12. **Review data scale and query placement.** Several flows return full tables and screens calculate summaries in memory. Add indexes/aggregate queries/paging where real usage warrants, and do not optimize speculatively before measuring representative local datasets.
13. **Create authoritative project documentation.** Replace stale `NOTES.md` guidance with the current source map, architecture, setup/build steps, supported permissions, data/backup policy, and release checklist.

**P2 acceptance gate:** New features can be added within a documented boundary; navigation and permission handling are consistent; documentation describes the actual build and schema.

### P3 — Polish and product quality

14. **Accessibility and responsive layouts:** Verify TalkBack labels/order, font scaling, contrast, touch targets, compact phones, landscape, and large-screen behavior.
15. **Unify visual behavior:** Establish reusable spacing/type/color/components and consistent refresh, empty, loading, error, and confirmation patterns across modules.
16. **Add user-controlled portability:** After backup policy is settled, add explicit export/import for user-owned data with schema versioning, validation, and a clear encrypted-vault choice.
17. **Measure performance with real devices/data:** Profile cold start, scrolling, database reads, and service overhead; optimize measured bottlenecks.

## Recommended delivery sequence and review checkpoints

1. **Checkpoint A — Product/data decisions:** Confirm personal-first scope, data inventory, backup/restore behavior, vault recovery expectations, and intended Android distribution channel.
2. **Checkpoint B — Safety baseline:** Close P0, add migration/crypto/domain tests, and agree on the Android/device matrix. Review the migration and vault design before broad UI redesign.
3. **Checkpoint C — Reliability:** Complete permission readiness, reminder/service lifecycle handling, denial/fallback states, and device verification. Review each feature’s behavior with permissions both granted and denied.
4. **Checkpoint D — Maintainability:** Refactor feature boundaries and shared UI conventions incrementally, with behavior parity checks at each module.
5. **Checkpoint E — Product polish and release candidate:** Finish accessibility/responsive review, user export/import where approved, profiling, signed release, and release checklist.

## Explicit assumptions

- “Enterprise-level” means predictable data handling, clear permissions, maintainable engineering, testable behavior, and a dependable release process. It does not by itself imply team accounts, cloud sync, or a backend.
- Momentum remains a local-first personal organizer until a later product decision says otherwise.
- The existing workspace contains prior source moves and staged/unstaged Git changes. This audit does not alter or reset any of them.

## References

- Android Auto Backup defaults and explicit backup rules: [Back up user data with Auto Backup](https://developer.android.com/identity/data/autobackup)
- Exact alarm behavior and permission checks: [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- Android 14 foreground-service type requirements and `specialUse` review: [Foreground service types are required](https://developer.android.com/about/versions/14/changes/fgs-types-required)
