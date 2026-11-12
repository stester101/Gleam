# Gleam 2.0

Android APK version of Gleam for Ant's Pixel.

## Product rule

Gleam 2 is an upgrade to the existing app, not a replacement workflow.

- Same five tabs: Today, Schedule, Customers, Money, Texts.
- Same button locations and core interaction patterns.
- Same job completion, payment, scheduling, round, customer, finance and texting logic.
- Same Aworka CSV support.
- Same Gleam JSON backup format for migration.
- Cleaner colour system and reduced visual noise.
- Native Android features are deliberately kept out of the main workflow.

The current web app on `main` is unchanged.

## Architecture

The Android app packages the current production `index.html` inside a native Android shell. This avoids feature regressions while adding Android capabilities around it.

The web layer continues to own Gleam's business logic and IndexedDB data. A JavaScript bridge mirrors the full state to Android whenever Gleam saves.

The native layer provides:

- persistent app-local WebView/IndexedDB storage;
- native JSON/CSV export into `Downloads/Gleam`;
- Android file picker for Gleam JSON and Aworka CSV import;
- completion haptics;
- three home-screen widgets (Today, Next job, Quick Complete);
- configurable work-reminder notifications (WorkManager, not exact alarms).

No earnings figures are used in widgets or notifications.

### Widget changes and sync

Quick Complete writes a clean into the native mirror and queues a per-job operation. The mirror ignores web syncs while operations are pending. When Gleam next opens, the web layer waits for boot, then replays each operation onto its own state (one clean per job per day, keyed by entry id). It saves to IndexedDB and only then acknowledges the operations it applied. Widget changes are never applied by replacing the whole web state.

Quick Complete only completes a job that is due today or left behind and has not already been done today. Widgets and the reminder worker recompute today/tomorrow from the stored state, so a new day shows correctly even if Gleam has not been opened.

## Existing Gleam migration

On the old Gleam:

1. Settings.
2. Backup JSON now.
3. Keep the generated `gleam-backup-YYYY-MM-DD.json`.

On Gleam 2:

1. Settings.
2. Restore JSON.
3. Select the old Gleam backup.

The existing `migrateState()` and ledger migration code is retained inside the APK, so older Gleam save shapes remain supported.

## Native extras

Settings shows Notifications and Widgets groups in the APK only.

- **Notifications:** master switch; Today briefing (jobs due, next address) at a chosen time; Tomorrow briefing (job count, rounds) at a chosen time; left-behind warning. Delivery time is approximate (WorkManager). Nothing is sent when there is nothing relevant.
- **Widgets:** Today (remaining count, next and following jobs, left-behind); Next job (address, price); Quick Complete (next job with Paid / Not paid).

## Visual refresh

The refresh is intentionally conservative:

- darker, flatter teal palette;
- reduced glass blur and shadows;
- quieter chips/borders;
- less visual competition in progress cards;
- simplified calendar state styling;
- existing squeegee completion animation retained;
- brief water/bubble response added to completion.

Layout and navigation remain in their existing positions.

## Build

GitHub Actions builds the debug APK whenever Android files change on `gleam-2-native`.

Signing: the APK is signed with a stable key stored as the `GLEAM_DEBUG_KEYSTORE_B64` repository secret (base64 PKCS12, alias `androiddebugkey`, password `android`). Without the secret, CI only compiles and produces no APK, so the signing identity can never rotate silently. The Alpha 2 key was held in an Actions cache that expired, so the first build signed with the secret key needs one backup → uninstall → install → restore on the phone.

Local equivalent:

```bash
gradle -p android assembleDebug
```

APK output:

`android/app/build/outputs/apk/debug/app-debug.apk`


## Alpha 2 changes

- Today calendar button opens the full Calendar view immediately.
- Today progress card no longer shows round pills.
- Completed-day message is `all done — nice one G`.
- Completion animation now includes visible water droplets plus the existing squeegee wipe.
- Bottom navigation and quick-add button auto-hide while scrolling down and return when scrolling up.
- Customers now supports Full and Compact views.
- Settings save automatically.
- Message templates, Notifications, Widgets, and Data & backup are separate collapsible settings groups.
- Settings grabber is a real swipe-down gesture target.
- Notifications can independently configure:
  - master enable/disable;
  - Today briefing;
  - Today briefing time;
  - Tomorrow briefing;
  - Tomorrow briefing time;
  - left-behind warnings.
- Widgets:
  - Today;
  - Next job;
  - Quick Complete.
- Quick Complete can mark the next job Paid or Not paid directly from the home screen.
- Native widget writes are protected from stale WebView state and reconciled into IndexedDB on app resume.
- Backup metadata now includes `schemaVersion: 2`.
- Remote Google Fonts requests were removed from the APK; the app is fully local/offline.
- CI now validates embedded JavaScript before compiling Android.
- CI caches the debug signing identity so future alpha builds can install as upgrades.

## Architecture review

Alpha 2 intentionally keeps the existing Gleam HTML/JavaScript UI because preserving Ant's learned workflow is the priority. Native Android owns capabilities the web app cannot provide well: widgets, notifications, app-level file handling, haptics, and cross-surface state reconciliation.

The native state mirror is safe for the current feature set, but it should be treated as an intermediate architecture. Before a long-term production release, the recommended next data-layer step is moving jobs, rounds, ledger entries, settings, and scheduling records into Room and making that database the single source of truth. The existing UI can remain visually unchanged while reading/writing through a bridge during that migration.

Recommended production hardening:

1. Move persistent structured data to Room with explicit migration tests.
2. Add rolling internal recovery snapshots independently of user-exported JSON backups.
3. Use a release signing key stored in GitHub Secrets rather than debug signing.
4. Add automated tests for schedule advancement, ledger balance calculation, old-backup migration, and widget quick-complete transactions.
5. Keep WorkManager for non-exact reminders; only move to AlarmManager if exact-clock notification delivery becomes a real requirement.
6. Add a native data-integrity screen only if support/debugging becomes necessary; do not add more day-to-day UI.
