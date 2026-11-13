# Phase 6 — release verification checklist

## Release gate

This checklist captures the device, permission, data, and cloud checks still required before calling the app release-ready. The repo already compiles and the sync/Vault foundations are in place, but end-to-end verification remains pending on real devices and staging data.

## Core verification matrix

### 1. Sync and offline recovery
- [ ] Turn off network, make edits in habits/to-dos/money/loans, then reconnect and confirm the sync worker resumes automatically.
- [ ] Confirm a sync job is not triggered repeatedly when the app itself writes sync state rows.
- [ ] Force a slow-network sync and verify the UI stays readable: Waiting to sync, Syncing, Synced, Needs attention.
- [ ] Expire the Supabase session and retry sync; verify the app surfaces a recoverable state instead of silently failing.
- [ ] Retry a failed sync after a temporary server error; confirm it does not duplicate rows or lose newer local edits.
- [ ] Restart the app during an in-flight sync and confirm it resumes or safely re-queues without double-writing.
- [ ] Verify repeated edits to the same row still generate one final sync payload rather than an endless queue.

### 2. Delete, wipe, and admin flows
- [ ] Confirm a regular user cannot trigger project-wide cloud deletion or admin-only controls.
- [ ] Run the admin wipe flow in a staging Supabase project and verify it targets the intended project, not just the current device.
- [ ] Confirm the confirmation dialog clearly explains what is deleted, what is preserved, and that it affects all users.
- [ ] Verify local devices that are offline or with stale sessions do not silently lose data; they should reset only when the wipe state is applied intentionally.

### 3. Vault and restore safety
- [ ] Confirm Vault export/import works with the documented recovery requirements and the expected key material.
- [ ] Test backup restore with an encrypted vault and confirm the app warns users before they create a backup without enough recovery information.
- [ ] Verify an app restart does not clear active Vault session keys prematurely or leave a stale unlock state.
- [ ] Confirm wrong-password and tampered-backup cases fail cleanly without data loss.

### 4. Android permissions and lifecycle
- [ ] Deny notification permission, schedule a reminder, then grant permission later and confirm the schedule is restored.
- [ ] Revoke Usage Access, Screen Time, or overlay permissions after the app has been configured, then confirm the screen explains the state and does not crash.
- [ ] Grant permission later from Android settings and confirm the app refreshes and resumes its functionality without a reinstall.
- [ ] Verify the app remains usable when Android background restrictions are active or battery saver is enabled.
- [ ] Test the app with notification channels disabled globally and confirm the feature degrades gracefully.

### 5. Accessibility and readable UI
- [ ] Test with larger font sizes, landscape mode, and smaller screens to confirm the Home/dashboards, dialogs, and sync status cards do not crop or overlap.
- [ ] Check screen-reader announcements for critical actions: sync now, project delete, Vault unlock, permission setup, backup and restore.
- [ ] Confirm buttons are large enough to tap comfortably and that the primary action is visually distinct from secondary controls.
- [ ] Review empty and error states for progress, retry, and guidance copy; ensure technical detail is kept under expandable sections.

### 6. Data growth and performance
- [ ] Check large habit/to-do/expense sets and large attachment counts for scroll smoothness, search/filter latency, and load times.
- [ ] Confirm no expensive summary is recalculated on every recomposition for the Home or dashboard screens.
- [ ] Review startup and navigation for unnecessary DB or cloud requests during cold launch.
- [ ] Validate that Supabase counts and storage checks are bounded and that large datasets do not make cloud diagnostics feel slow.

### 7. Cross-device and platform coverage
- [ ] Test on at least one low-end device and one modern device.
- [ ] Validate on a supported Android version range, including one API 28+ device and one newer release.
- [ ] Check dark mode, reduced-motion mode, and normal motion mode across the main screens.
- [ ] Confirm behavior under low-memory conditions and when the app is resumed from the background.

### 8. Privacy and consent checks
- [ ] Review contact, file, usage access, accessibility, overlay, and background-monitoring explanations against the actual permission requests.
- [ ] Confirm onboarding/help copy clearly states what the app measures and what it does not collect.
- [ ] Review the app’s local-first and cloud-sync messaging to ensure users understand where their data lives and what deletion or sync will do.

## Prioritized remaining release issues

### P0 — must fix before release
- Real device verification of sync recovery after offline reconnect, expired session, and server failures.
- Verified idempotency of sync retries against a staging Supabase project.
- Correct deletion and wipe behavior for project-wide cloud data with admin enforcement confirmed server-side.
- Vault export/import and recovery flow tested on device with real key material.

### P1 — should fix before release
- Permission lifecycle handling: denied, granted later, and revoked states across Android settings.
- App restart behavior during sync, Vault unlock, and backup/export jobs.
- Large data and large attachment handling on real devices.
- Accessibility and readability checks for enlarged fonts and screen-reader flows.

### P2 — close after release or in next patch cycle
- Additional polish for lighter onboarding text and clearer action hierarchy.
- Further optimization work for expensive lists and startup activity if benchmark evidence shows impact.
- Extra coverage for less-common admin and edge-case states.

## Current evidence summary

- Build verification: Kotlin compilation passes.
- Feature readiness: major navigation, sync, Vault, and settings foundations are implemented.
- Remaining gap: device-level, permission-level, and staging-network verification is still required before calling this release-ready.

## Suggested release decision

Proceed to a staged release only after the P0 list above is verified on a physical device or staging project. Until that happens, the app should be treated as feature-complete but not fully release-validated.
