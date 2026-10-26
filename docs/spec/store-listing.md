<!-- store: omit -->

# Store listing

The Google Play listing: its text (title, short description and full description) and its phone
screenshots. Both are made only in pull requests and reviewed there; a release ships what was
reviewed and makes nothing of its own.

The phone screenshots are rendered from the app's own screens on the JVM (Robolectric, through
Roborazzi), never captured by hand from a device, so they change only when the screens or the
captions do, and every change is reviewed as an image diff in a pull request (STORE-SHOT-001 to
STORE-SHOT-014). The text is hand-written, one claim per line, and each claim names the
requirements it rests on, so the listing can never promise what the specification does not say
(STORE-LIST-001 to STORE-LIST-007).

## The shots

Each shot shows a screen as it exists in the app, with the showcase data of STORE-SHOT-006.

- **STORE-SHOT-001** Home: today's reminders and the goals card with goal progress (HOME-002,
  HOME-006). Light theme.
- **STORE-SHOT-002** The stretch session opened from a reminder, with some of its stretches
  checked (SESS-001 to SESS-003). Dark theme.
- **STORE-SHOT-003** Daily goals, with goals partway done (GOAL-001, GOAL-003). Light theme.
- **STORE-SHOT-004** The schedule editor on a repeating schedule, showing its interval, minute,
  window and slot preview (SCHED-011, SCHED-012). Dark theme.
- **STORE-SHOT-005** Weekly trends with the period summary and the current streak (TREND-004 to
  TREND-006). Light theme.

There is no shot of the skipped-days screen.

## Inputs

- **STORE-SHOT-006** Every shot is rendered from frozen inputs, so the same commit always gives
  the same pixels:
  - the time is Friday 16 October 2026, 9:40 AM, in America/New_York, read from a fixed clock
    (`Clock.fixed`, SCHED-016) whose zone is also set as the JVM's default time zone;
  - the locale is en-US (Robolectric qualifier and the JVM default), with the 12-hour clock;
  - the device is Robolectric's `w411dp-h891dp-xxhdpi` (density 3) on a pinned SDK, with native
    graphics;
  - the colour schemes are the app's fixed light and dark schemes, dynamic colour off;
  - animations are off: each screen is composed straight into its final state, the animator
    duration scale is 0, and the capture waits until composition is idle;
  - the data comes from one `ShowcaseFixture` that builds each screen's state directly: the
    seeded stretch library (LIB-002), three goals partway done today, one set-times schedule
    and one repeating schedule, and 30 days of completion history.

  **Invariant — The showcase never touches a database or randomness.** No Room, no
  `StretchApp`, no `Random`, no system clock: a screen that only takes a ViewModel is rendered
  through its stateless content composable with state the fixture built.
- **STORE-SHOT-007** The images are PNGs in `fastlane/metadata/android/en-US/images/phoneScreenshots/`
  (fastlane supply's layout), named in listing order: `1_home.png`, `2_session.png`,
  `3_goals.png`, `4_schedule.png`, `5_trends.png`. Their caption text lives only in
  [docs/store/captions.md](../store/captions.md), one entry per shot ID (STORE-SHOT-001 to
  STORE-SHOT-005), which the screenshot test reads.

## Layout

All sizes are pixels of the final image.

- **STORE-SHOT-010** Each shot is one 1080 x 2340 image: a caption at the top and a framed phone
  screen below it, cut off by the bottom edge.
- **STORE-SHOT-011** The background is solid #1361A7 on every shot, light and dark: no gradient
  and nothing else drawn on it.

  **Invariant — A shot's light or dark theme changes only the screen inside the frame, never the
  background behind it.**
- **STORE-SHOT-012** The caption is left-aligned, 108 px from the top, with 79 px side margins
  (922 px wide). Headline: white, bold, 90 px, 1.18 line height, at most 3 lines. Supporting
  line: #CFE1F5, regular, 50 px, 1.35 line height, 22 px below the headline, at most 2 lines.
- **STORE-SHOT-013** The phone frame's top edge is at y = 650 px on every shot. The frame is
  centred horizontally and 922 px wide. Its bezel is #0A0A0B, 18 px thick on the top and sides,
  with no bottom bezel and 79 px radius top corners. The real screen, laid out at the device's
  natural size (411 x 891 dp), is scaled down to fit inside the bezel.
- **STORE-SHOT-014** There is no status bar, navigation bar or other device chrome.

  **Invariant — A caption that is too long fails the test instead of shrinking the text or
  pushing the frame down.** Lines are counted as rendered (measured text layout at the caption's
  width, font and size), not as characters.

## Recording and verifying

**Invariant — A store screenshot is recorded only when a person asks for it, and reaches the
default branch only through a pull request whose diff shows the new image.**

- **STORE-SHOT-008** Every pull request's build (`pr.yml`) only verifies the committed images
  (`./gradlew verifyRoborazziDebug`, which fails on any changed or missing image) and, when it
  fails, uploads the rendered and diff images as the `store-screenshot-diffs` artifact. It never
  records.
- **STORE-SHOT-009** Recording happens in one of two ways, both started by a person:
  - **In CI:** a person applies the `record-store-screenshots` label to a pull request from a
    branch of this repository (never a fork). `record-store-screenshots.yml` runs
    `./gradlew recordRoborazziDebug` on the pull request's head, commits only the files in the
    `phoneScreenshots` folder as `github-actions[bot]`, and force-pushes that commit to the side
    branch `store-screenshots/pr-<number>`, based on the pull request's head. If no image changed
    it pushes nothing. Its job summary says which commit to bring in, and it always removes the
    label afterwards, so the label can be applied again for another recording.
  - **Locally:** `./gradlew recordRoborazziDebug`, on Linux with JDK 17 as CI runs. Robolectric
    draws with its own fonts and renderer, but the host's JDK formats dates and times, and
    anti-aliasing can differ between operating systems.

  **Invariant — The recording workflow never pushes to the pull request's branch.** A push made
  with the workflow's token starts no `pr.yml` run, so a person brings the side branch's commit
  into the pull request with their own push (`git fetch origin store-screenshots/pr-<number> &&
  git merge --ff-only FETCH_HEAD`, or a cherry-pick). That push runs `pr.yml`, which verifies the
  new images, and the pull request's diff shows them for review.

## Listing text

**Invariant — The listing is never generated at release time.** The release run only verifies and
packages text and images that were already reviewed in pull requests: nothing in it writes,
rewrites or records listing text or screenshots.

- **STORE-LIST-001** The listing text is written by hand in
  [docs/store/listing.md](../store/listing.md), in three sections, exactly these and in this
  order: `## Title`, `## Short description` and `## Full description`. Text before `## Title`
  explains the page and is not part of the listing. The title and the short description are one
  line each. Every non-blank line of the full description is one **claim**: a paragraph line or
  a `- ` list item, ending with one tag that lists the requirement IDs it rests on, for example
  `- Set a daily goal for each stretch. <!-- GOAL-001 -->`. The title and the short description
  may carry a tag as well. `.github/scripts/store_listing.py` compiles the page into
  `fastlane/metadata/android/en-US/title.txt`, `short_description.txt` and
  `full_description.txt` (fastlane supply's layout), with the tags removed, the claims' text
  kept as written, and runs of blank lines collapsed into one paragraph break. The three files
  are committed with the listing change that produced them; they are never edited by hand.
- **STORE-LIST-002** Every page in `docs/spec/` except this folder's `README.md` (the index,
  which defines no requirements) starts with a store marker as its **first line**, followed by a
  blank line, in exactly one of two forms: `<!-- store: headline -->` or
  `<!-- store: omit -->`. The marker is an HTML comment, so MkDocs renders nothing for it. A
  `headline` page describes a feature the listing must mention; an `omit` page need not be
  mentioned (its IDs may still be cited). The headline pages are scheduling, goals, sessions,
  trends and widgets; skipping, notifications, library, screens, privacy and this page are
  `omit` (privacy and the store listing are not app features).
- **STORE-LIST-003** A tag may cite only an ID that a spec page defines (a top-level list item
  opening with the bold ID, `- **GOAL-001** …`) and that is not retired (a definition whose text
  opens with `_Retired._`). An ID mentioned only in prose is not defined.
- **STORE-LIST-004** Every `headline` page has at least one claim citing at least one of the IDs
  it defines.
- **STORE-LIST-005** The compiled files keep within Google Play's limits, counted in characters
  (not bytes), without the one trailing newline each file ends with: title 30, short description
  80, full description 4,000.
- **STORE-LIST-006** `python3 .github/scripts/store_listing.py --check` writes nothing and fails,
  with a workflow error naming each problem, when any of STORE-LIST-001 to STORE-LIST-005 is
  broken or a committed file is missing or differs from what the script compiles. Every pull
  request's build (`pr.yml`) runs it. Without `--check` the script writes the three files, and
  writes nothing when any of STORE-LIST-001 to STORE-LIST-005 is broken.
- **STORE-LIST-007** When release-please publishes a release, `build-and-attach` in
  `release-please.yml` runs the same check on the release tag's checkout (the tag's own script),
  before anything is built or attached, and attaches `store-listing-<version>.zip` to the GitHub
  Release beside the APK and AAB. The zip holds the committed `fastlane/metadata/android` folder
  as it is at the tag (text and screenshots, under that path), and the job then reads back that
  the release carries it. It needs no secret beyond those the job already uses. A manual
  backfill of a tag older than this requirement (one without `docs/store/listing.md`) skips the
  check and the zip with a notice; any other failure of the check fails the job.

## Build checklist

- [x] One screenshot test per shot, each naming its STORE-SHOT ID (STORE-SHOT-001 to
      STORE-SHOT-005), all on one `ShowcaseFixture` with a fixed clock and zone, en-US,
      `w411dp-h891dp-xxhdpi`, native graphics, fixed colour schemes (STORE-SHOT-006).
- [x] Screens that take a ViewModel are rendered through behaviour-neutral stateless content
      composables (STORE-SHOT-006 invariant); `StretchTheme` takes the dark flag and dynamic
      colour as parameters, defaulting to the system's.
- [x] Roborazzi's output directory is the fastlane `phoneScreenshots` folder, so verify compares
      against the committed images (STORE-SHOT-007).
- [x] Captions are parsed from `docs/store/captions.md`; a test checks it holds exactly the five
      shot IDs (STORE-SHOT-007).
- [x] The canvas is composed at density 3 (1 dp = 3 px) at exactly 1080 x 2340 px; background,
      caption and frame are drawn outside the app theme (STORE-SHOT-010 to STORE-SHOT-013,
      STORE-SHOT-011 invariant).
- [x] A unit test measures captions as rendered lines and fails a too-long one (STORE-SHOT-012,
      STORE-SHOT-014 invariant).
- [x] `pr.yml` runs `verifyRoborazziDebug` and uploads `app/build/outputs/roborazzi/` (the actual
      and `_compare` images) when it fails; it never records (STORE-SHOT-008).
- [x] `record-store-screenshots.yml` runs only on the `record-store-screenshots` label on a
      same-repository pull request, commits only the `phoneScreenshots` folder, pushes to
      `store-screenshots/pr-<number>` and never to the pull request's branch, and always removes
      the label; the label is defined in `.github/labels.repo.yml` (STORE-SHOT-009 and its
      invariant).
- [x] The five images are recorded and committed in the `phoneScreenshots` folder, and
      `verifyRoborazziDebug` passes against them (STORE-SHOT-007).
- [x] Every spec page except `README.md` starts with `<!-- store: headline -->` or
      `<!-- store: omit -->` and a blank line (STORE-LIST-002).
- [x] `store_listing.py` is standard-library Python with unit tests citing the STORE-LIST IDs
      they cover, run by `pr.yml` with the other script tests (STORE-LIST-001 to
      STORE-LIST-006).
- [x] `pr.yml` runs `store_listing.py --check` (STORE-LIST-006).
- [x] `build-and-attach` runs the check at the tag and attaches and reads back
      `store-listing-<version>.zip`; no step in it writes listing text (STORE-LIST-007 and the
      invariant).
- [x] Store listing reviewed: the draft text in `docs/store/listing.md` claims only what the
      cited requirements say (STORE-LIST-003, STORE-LIST-004).
