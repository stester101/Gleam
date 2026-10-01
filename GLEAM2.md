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
