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

The web layer continues to own Gleam's business logic and IndexedDB data. A small JavaScript bridge sends an operational snapshot to Android whenever Gleam saves.

The native layer provides:

- persistent app-local WebView/IndexedDB storage;
- native JSON/CSV export into `Downloads/Gleam`;
- Android file picker for Gleam JSON and Aworka CSV import;
- completion haptics;
- Today home-screen widget;
- optional morning and tomorrow notifications;
- left-behind work surfaced in the morning notification.

No earnings figures are used in widgets or notifications.

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

In Settings, APK builds show a small **App extras** section.

### Work reminders

Optional. When enabled:

- 07:00: number of jobs today, next address, and left-behind count when relevant.
- 18:00: number of jobs tomorrow and the affected rounds.

No notification is sent when there is nothing relevant.

### Today widget

Shows:

- number of remaining jobs today;
- next job;
- up to two following jobs;
- left-behind count when today itself is empty.

Tapping the widget opens Gleam. Jobs cannot be completed directly from the widget because the existing completion flow records payment status.

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
