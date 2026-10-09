# Simple Stretch

A small Android app that reminds you to stretch during the day, on your own schedule.

## Features

- **Stretch library**: add, edit and delete stretches (name, instructions, optional duration).
  Ten common stretches are added on first run.
- **Schedules**: as many as you like, each with a name, an on/off switch, the days of the week it
  runs, and a list of reminder times. Every time picks its own stretches, e.g. a "Workday"
  schedule Mon-Fri with neck rolls at 10:00, hip flexors at 14:00 and wrist stretches at 16:30.
- **Skip a day**: one tap on "Skip today" silences every reminder for the rest of the day.
  Future days can be skipped from the calendar screen, and skipped days can be removed again.
- **Notifications** show the stretch and its instructions, with **Done** and **Skip rest of
  today** buttons. Tapping opens the app.
- Reminders survive reboots and time or time-zone changes, and use exact alarms when Android
  allows them (falling back to slightly-less-punctual alarms when it doesn't).

Behaviour is specified in [docs/spec/](docs/spec/README.md).

## Requirements

- Android 8.0 (API 26) or later.
- To build: JDK 17+, Android SDK with platform 35 and build-tools 35.0.0 (set `ANDROID_HOME` or
  `sdk.dir` in `local.properties`).

## Build and install

```sh
./gradlew test             # unit tests
./gradlew assembleDebug    # app/build/outputs/apk/debug/simple-stretch-<version>-debug.apk
adb install -r app/build/outputs/apk/debug/simple-stretch-*-debug.apk
```

Or copy the APK to the phone and open it (allow installs from that source when asked).

Every pull request's CI run also uploads its debug APK as the `simple-stretch-debug-apk`
workflow artifact (kept for 14 days), so a PR can be installed and tried before it is released.

On first launch, allow notifications. On Android 12/12L, also allow "Alarms & reminders" if the
app shows the banner, so reminders arrive on the minute.

## Releases

Merging to `main` runs release-please, which keeps a release PR up to date from Conventional
Commit titles. Merging that PR tags a release and attaches `simple-stretch-<version>-release.apk`.
Release APKs are currently debug-signed; see
[docs/adr/0001](docs/adr/0001-debug-signing-until-release-keystore.md).

## Project layout

```
app/src/main/java/com/derekwinters/stretch/
  data/           Room entities, DAOs, database (+ seed), repository
  scheduling/     next-occurrence maths, AlarmManager scheduling, alarm + reschedule receivers
  notifications/  notification channel/content and the Done / Skip actions
  ui/             Compose screens: home, schedule editor, stretch library, skipped days
app/src/test/     JVM unit tests (next-occurrence calculation)
app/schemas/      exported Room schemas
docs/spec/        requirements with IDs; docs/adr/ decisions
```

## Notes

- `USE_EXACT_ALARM` is declared so exact reminders work on Android 13+ without a prompt. Google
  Play restricts that permission to alarm/calendar apps; for a Play listing, drop it and rely on
  `SCHEDULE_EXACT_ALARM` plus the in-app banner.
- Some manufacturers aggressively stop background apps; if reminders go missing, exempt the app
  from battery optimisation.
