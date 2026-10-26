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
release-signed APK cannot be installed over each other. Those pull requests also upload
`simple-stretch-release-aab`, the release Android App Bundle signed with the same key; the AAB is
the upload format for Google Play and cannot be installed directly, so sideload the APK.

On first launch, allow notifications. On Android 12/12L, also allow "Alarms & reminders" if the
app shows the banner, so reminders arrive on the minute.

## Store listing

The Google Play screenshots are rendered from the app's screens on the JVM, not captured on a
phone ([store listing specification](spec/store-listing.md)), and committed under
`fastlane/metadata/android/en-US/images/phoneScreenshots/`; their captions are in
[store/captions.md](store/captions.md). The store icon (`images/icon.png`, drawn from the app's
launcher icon) and the feature graphic (`images/featureGraphic.png`) are rendered and checked the
same way, by the same commands.

```sh
./gradlew verifyRoborazziDebug   # compare with the committed store images (what CI runs)
./gradlew recordRoborazziDebug   # re-record them, deliberately, after changing a screen, caption or the launcher icon
```

Every pull request's CI run verifies them; a changed or missing image fails the run and uploads
the new and diff images as the `store-screenshot-diffs` workflow artifact. That run never records
them. To re-record, either:

- apply the `record-store-screenshots` label to the pull request. CI records the images and pushes
  them as one commit to the branch `store-screenshots/pr-<number>` (never to the pull request's
  branch), says so in its job summary and removes the label. Bring that commit in with your own
  push (`git fetch origin store-screenshots/pr-<number> && git merge --ff-only FETCH_HEAD`), so the
  pull request's CI run verifies it; or
- run `./gradlew recordRoborazziDebug` locally, on Linux with JDK 17 as CI runs, and commit the
  images.

Either way the new images are reviewed in the pull request's diff.

The listing's text (title, short description and full description) is written by hand in
[store/listing.md](store/listing.md), one claim per line, each citing the requirement IDs it rests
on. After editing it, compile it into `fastlane/metadata/android/en-US/` and commit the result:

```sh
python3 .github/scripts/store_listing.py           # write title.txt, short_description.txt, full_description.txt
python3 .github/scripts/store_listing.py --check   # what CI runs; writes nothing
```

Every pull request's CI run fails when a claim cites a missing or retired requirement, a headline
feature page (marked `<!-- store: headline -->`) has no claim, a text is over Google Play's limit,
or the committed files differ from the listing.

## Releases

Merging to `main` runs release-please, which keeps a release PR up to date from Conventional
Commit titles. Merging that PR tags a release and attaches three files:

- `simple-stretch-<version>-release.apk`, for installing directly onto a phone (sideloading);
- `simple-stretch-<version>-release.aab`, the Android App Bundle for uploading to Google Play;
- `store-listing-<version>.zip`, the reviewed Google Play listing (text, screenshots, store icon
  and feature graphic) as
  committed at the release, under `fastlane/metadata/android/`. The release only checks and
  packages it; it never writes listing text.

Both are signed with a stable release key
([ADR 0002](adr/0002-release-signing-with-a-stable-keystore.md)) and checked by the same
signature gate before they are attached, so each release from 0.3.0 on upgrades the installed app
in place and keeps its data. The same key is Google Play's app signing key
([ADR 0004](adr/0004-play-app-signing-and-publishing.md)), so an install from Google Play and a
sideloaded release APK share one signature and can update each other in place to the same or a
newer version.

Each release's version code is derived from its version (0.3.0 is 300; see
[ADR 0003](adr/0003-version-code-derived-from-version-name.md)), so it rises with every release,
as Google Play and Android require. Before a release is attached, a guard checks that its code is
higher than the previous release's. The same check runs on the open release PR after every merge
to `main`: a release PR whose code is not higher than the last release's gets a failing
`release-version-code` status.

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
                  trends, widget ring and palette) and the store image tests (store/)
app/schemas/      exported Room schemas
docs/spec/        requirements with IDs; docs/adr/ decisions; docs/store/ listing text and
                  screenshot captions
fastlane/         Google Play listing text (compiled from docs/store/listing.md) and images
```

## Notes

- Exact reminders need the user-granted `SCHEDULE_EXACT_ALARM` ("Alarms & reminders") on
  Android 12 and later. On Android 14 and later it starts denied on a new install, so reminders
  are inexact (may arrive a few minutes late) until the user taps **Allow** on the home-screen
  banner. `USE_EXACT_ALARM` is deliberately not declared: Google Play restricts it to alarm,
  timer and calendar apps.
- Some manufacturers aggressively stop background apps; if reminders go missing, exempt the app
  from battery optimisation.
