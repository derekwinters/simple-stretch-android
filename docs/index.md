# Simple Stretch

A small Android app that reminds you to stretch during the day, on your own schedule.

## Features

- **Stretch library** ("My stretches" in the home screen's menu): add, edit and delete stretches
  (name, instructions, optional duration). Ten common stretches are added on first run. You can
  also create a stretch straight from the goals screen and the reminder-time picker with
  "New stretch…".
- **Schedules**: as many as you like, each with a name, an on/off switch and the days of the
  week it runs. A schedule either has **set times**, each optionally with its own stretches
  (e.g. neck rolls at 10:00, hip flexors at 14:00), or **repeats**: every N minutes at a minute
  past the hour between two times, e.g. every hour at :50 from 8:00 to 17:00 (8:50 ... 16:50;
  the end time is inclusive).
- **Daily goals**: e.g. 3 hamstring stretches and 3 calf stretches a day, set with a − / +
  stepper next to every stretch on the goals screen (0 means no goal). The home screen shows
  today's progress ("Hamstring stretch 2/3"). Once every goal for the day is met, reminders
  stay quiet for the rest of the day.
- **Trends** ("Trends" in the home screen's menu, or on the goals card): a week or month bar
  chart of each day's score (goal stretches done, up to each target, out of the day's total
  target), the period's average, how many days met every goal, and your current streak. Tap a
  day to see its goals, the score climbing through the day, and every stretch logged with its
  time. Skipped days and days without goals are gaps, not 0%; changing a goal never rewrites
  past days.
- **Home screen widget** ("Today's goals", 2×2, resizable): a ring with one arc per daily goal,
  each filling as you do that stretch, around the app's stretch figure and today's count (a
  check mark once every goal is met). It follows light/dark mode and your wallpaper colours,
  starts again at 0 at midnight, and opens the app when tapped.
- **Stretch sessions**: from a reminder or the home screen's **Stretch now**, tick the stretches
  you did and save. Opened from a reminder, that reminder's stretches lead in their own section;
  otherwise stretches still short of today's goal do. Everything else follows alphabetically,
  and nothing is pre-checked.
- **Skip a day**: one tap on "Skip today", at the right of the home screen's "Today" heading (or
  in the top bar's menu), silences every reminder for the rest of the day; "Resume today" undoes
  it. Future days can be skipped from the calendar screen, and skipped days can be removed again.
- **Notifications** show the stretches (or today's unmet goals), with **Start** (opens the
  stretch session) and **Snooze 5 min** buttons; swipe to dismiss just this one.
  Tapping also opens the session.
- Reminders survive reboots and time or time-zone changes, and use exact alarms when Android
  allows them (falling back to slightly-less-punctual alarms when it doesn't).

Behaviour is set out in the [specification](spec/README.md).

## Requirements

- Android 8.0 (API 26) or later.
- To build: JDK 17+, Android SDK with platform 36 and build-tools 36.0.0 (set `ANDROID_HOME` or
  `sdk.dir` in `local.properties`).

## Build and install

```sh
./gradlew test             # unit tests
./gradlew assembleDebug    # app/build/outputs/apk/debug/simple-stretch-<version>-debug.apk
adb install -r app/build/outputs/apk/debug/simple-stretch-*-debug.apk
```

Or copy the APK to the phone and open it (allow installs from that source when asked).

**Application ID:** from 0.3.0 the application ID is `io.github.derekwinters.simple_stretch_android`.
An install of 0.1.0 or 0.2.0 (`com.derekwinters.stretch`) is a separate app: it will not update to
new builds and keeps its own data until uninstalled. Nothing is carried over, so the new app starts
empty, and the old one can be uninstalled whenever you like.

Every pull request's CI run also uploads its debug APK as the `simple-stretch-debug-apk`
workflow artifact (kept for 14 days). Pull requests from branches of this repository also upload
`simple-stretch-release-apk`, signed with the release key, which installs in place over a release
of 0.3.0 or later, so a PR can be tried before it is released without losing data. A debug APK and a
release-signed APK cannot be installed over each other.

On first launch, allow notifications. On Android 12/12L, also allow "Alarms & reminders" if the
app shows the banner, so reminders arrive on the minute.

## Releases

Merging to `main` runs release-please, which keeps a release PR up to date from Conventional
Commit titles. Merging that PR tags a release and attaches `simple-stretch-<version>-release.apk`.
Release APKs are signed with a stable release key
([ADR 0002](adr/0002-release-signing-with-a-stable-keystore.md)), so each release
upgrades the installed app in place and keeps its data.

**One-time step:** builds made before release signing (debug-signed releases, CI or local debug
APKs) use a different key. Uninstall those once before installing the first release-signed build;
after that, upgrades are in place. The same applies to moving from the old application ID: 0.1.0
and 0.2.0 (`com.derekwinters.stretch`) do not upgrade to 0.3.0 or later, which install alongside
them as a new, empty app; uninstall the old one when you no longer need it.

## Project layout

```
app/src/main/java/com/derekwinters/stretch/
  data/           Room entities, DAOs, database (+ seed, migrations), repository
  goals/          goal progress, goal history and session ordering maths (pure Kotlin)
  trends/         daily score, periods, streak and intraday series maths (pure Kotlin)
  scheduling/     next-occurrence and repeating-slot maths, AlarmManager scheduling, snooze,
                  alarm + reschedule receivers
  notifications/  notification channel/content and the Snooze / Skip actions
  widget/         segmented ring home screen widget: ring layout and palette (pure Kotlin),
                  bitmap renderer, provider and redraws
  ui/             Compose screens: home, schedule editor, stretch session, goals, trends
                  (Canvas charts) and day detail, stretch library, skipped days
app/src/test/     JVM unit tests (next occurrence, repeating slots, goal maths, goal history,
                  trends, widget ring and palette)
app/schemas/      exported Room schemas
docs/spec/        requirements with IDs; docs/adr/ decisions
```

## Notes

- `USE_EXACT_ALARM` is declared so exact reminders work on Android 13+ without a prompt. Google
  Play restricts that permission to alarm/calendar apps; for a Play listing, drop it and rely on
  `SCHEDULE_EXACT_ALARM` plus the in-app banner.
- Some manufacturers aggressively stop background apps; if reminders go missing, exempt the app
  from battery optimisation.
