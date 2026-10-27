<!-- store: omit -->

# Store listing

The Google Play listing: its text (title, short description and full description), its phone
screenshots, its store icon and its feature graphic. All of them are made only in pull requests
and reviewed there; a release ships what was reviewed and makes nothing of its own. The listing's
category, tags, website and contact email are entered in Play Console by the owner, not compiled
from this repository; they are recorded in [Play Console listing details](../store/play-console.md).

The phone screenshots are rendered from the app's own screens on the JVM (Robolectric, through
Roborazzi), never captured by hand from a device, so they change only when the screens or the
captions do, and every change is reviewed as an image diff in a pull request (STORE-SHOT-001 to
STORE-SHOT-015). The store icon and the feature graphic are rendered the same way, the icon from
the app's own launcher icon resources and the graphic from the same showcase screens as the
shots, and reviewed the same way (STORE-ICON-001 to STORE-ICON-005, STORE-GRAPHIC-001 to
STORE-GRAPHIC-007). The text is hand-written, one claim
per line, and each claim names the requirements it rests on, so the listing can never promise
what the specification does not say (STORE-LIST-001 to STORE-LIST-007). The listing's title is
also the app's name on the phone (STORE-LIST-008).

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
  STORE-SHOT-005), which the screenshot test reads. The file's only other entry is the feature
  graphic's text (STORE-GRAPHIC-003).

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

## Store icon and feature graphic

Two more images, beside the screenshots in fastlane supply's `images` folder. Both are rendered
on the JVM on the STORE-SHOT-006 device (Robolectric, native graphics, SDK and qualifiers
pinned). The store icon is drawn by `StoreArtTest` with no clock, locale or data involved. The
feature graphic is composed by `FeatureGraphicTest` with all of STORE-SHOT-006's frozen inputs,
because it shows two of the app's screens.

### Store icon

- **STORE-ICON-001** The store icon is `fastlane/metadata/android/en-US/images/icon.png`:
  exactly 512 x 512 px, a 32-bit PNG with an alpha channel (8-bit RGBA, PNG colour type 6). It
  is a full square: no rounded corners, mask, shadow or margin of its own (Google Play applies
  its own mask). The file is at most 1024 KB (1,048,576 bytes), Google Play's limit for the
  store icon.
- **STORE-ICON-002** It is drawn from the app's real adaptive launcher icon: the test inflates
  `@mipmap/ic_launcher` and draws its background layer (`@color/ic_launcher_background`,
  #FFFFFF) and then its foreground layer (`@drawable/ic_launcher_foreground`). The monochrome
  layer is not drawn.

  **Invariant — The store icon is always rendered from the launcher icon and never hand-edited.**
  The test inflates the launcher icon's own resources rather than a copy of their geometry or
  colours, so any change to the launcher icon changes the rendered store icon and fails the
  verify (STORE-ICON-004) until the store icon is re-recorded.
- **STORE-ICON-003** The launcher icon's viewport, its central 72 x 72 dp (the most a
  launcher's mask can show; the smaller 66 dp safe zone is what no mask clips), fills the image.
  The layers' 108 x 108 dp canvas is scaled by 512 / 72 px per dp, to 768 x 768 px, and drawn
  offset by -128 px on both axes, so the outer 18 dp on each side falls outside the image.

### Feature graphic

A headline on the left and two of the app's phone screens on the right, tilted towards each
other and running off the bottom edge, drawn like the screenshots' framed phones.

All sizes are pixels of the final image. A rotation is in degrees, positive clockwise, about the
frame's bottom centre (the middle of its bottom edge, before rotating).

- **STORE-GRAPHIC-001** The feature graphic is
  `fastlane/metadata/android/en-US/images/featureGraphic.png`: exactly 1024 x 500 px, a 24-bit
  PNG with no alpha channel (8-bit RGB, PNG colour type 2).
- **STORE-GRAPHIC-002** The background is solid #1361A7, the screenshots' colour
  (STORE-SHOT-011), and runs to every edge: no gradient. On it, in drawing order: the text
  (STORE-GRAPHIC-003, STORE-GRAPHIC-004), the back phone and then the front phone
  (STORE-GRAPHIC-005), so the front phone partly covers the back one. Nothing else is drawn: no
  app icon, app name, logo, badge or device chrome.

  **Invariant — The feature graphic never shows the app icon.** Google Play shows the app icon
  beside the feature graphic and advises against repeating it, so the test draws neither the
  launcher icon nor the store icon, in any form, anywhere on the graphic.
- **STORE-GRAPHIC-003** The text is a headline and a supporting line. Their wording lives only
  in [docs/store/captions.md](../store/captions.md), in its `## STORE-GRAPHIC-003` entry (a
  `Headline:` line and a `Supporting:` line, like the shots' captions, STORE-SHOT-007), which
  the test reads. Both are claims: each of the two lines ends with one tag,
  `<!-- ID, ID -->`, naming the requirements it rests on, and the test fails unless every cited
  ID is defined by a spec page and not retired (the rule of STORE-LIST-003). The tag is not
  part of the drawn text.
- **STORE-GRAPHIC-004** The text block is left-aligned, its left edge at x = 64 px, and 380 px
  wide. Headline: white (#FFFFFF), Roboto Bold (weight 700), 46 px, 52 px line height, at most
  3 lines. Supporting line: #CFE1F5, Roboto Regular (weight 400), 21 px, 29 px line height,
  18 px below the headline, at most 2 lines. Each line is its line height, the text centred in
  it, nothing trimmed (where the font is taller than the line height, as the headline's is, the
  text engine may keep its full ascent on the first line and descent on the last). The block is
  centred vertically: its top is at (500 − block height) / 2 px, rounded down, where the block
  height is the headline's and the supporting line's heights as laid out (measured at the
  block's width, font and size) plus the 18 px gap.

  **Invariant — Text that does not fit its lines fails the test instead of shrinking or
  moving.** As for the shots' captions (STORE-SHOT-014), lines are counted as rendered
  (measured text layout at the block's width, font and size), not as characters; nothing
  shrinks the text, widens the block or moves the phones to make it fit.
- **STORE-GRAPHIC-005** Two phones, each drawn like a shot's frame (STORE-SHOT-013,
  STORE-SHOT-014) at a smaller size: a #0A0A0B bezel 9 px thick on the top and sides, no bottom
  bezel, 36 px radius top corners; the screen inside it has 27 px radius top corners (the
  frame's radius less the bezel), is laid out at the device's natural size (411 x 891 dp) and is
  scaled down to fit the bezel's inside width, top-anchored, and is cut off by the frame's
  bottom edge. There is no status bar, navigation bar or other device chrome. Before rotating:

  | Phone | Screen | Theme | Left | Top | Width | Height | Rotation |
  |---|---|---|---|---|---|---|---|
  | Back | The stretch session (STORE-SHOT-002) | Dark | 720 | 96 | 250 | 447 | +6° |
  | Front | Home (STORE-SHOT-001) | Light | 520 | 50 | 270 | 485 | −4° |

  The heights are the bezel plus the screen's width × 1672 / 886, rounded, so each phone shows
  the same part of its screen as its shot does (a shot's screen area is 886 x 1672 px). After rotating, each frame's bottom edge lies wholly below the image (y > 500) and its
  top and sides inside it, so the phones run off the bottom edge only. Each frame casts a drop
  shadow, drawn under it and rotated with it: black at 35 % opacity (#59000000), offset 18 px
  down, blur radius 42 px (Android's `Paint.setShadowLayer`; a Gaussian sigma of about 25 px).
  The background, text, bezels and shadows are drawn outside the app theme; only the screens
  are themed.
- **STORE-GRAPHIC-006** The fonts are Roboto Bold and Roboto Regular from Roboto 2
  (`Roboto-Bold.ttf` and `Roboto-Regular.ttf`, version 2.001047, from the googlefonts/roboto
  repository), under the Apache License 2.0. The two files and their licence text are committed
  in `app/src/test/resources/store/fonts/`. The test loads those files, never a system font,
  and fails if either cannot be loaded.
- **STORE-GRAPHIC-007** The two screens are the app's real composables, rendered the way their
  shots are: the same stateless content composables with the same `ShowcaseFixture` state as
  STORE-SHOT-001 and STORE-SHOT-002, in the shot's fixed theme with dynamic colour off, and with
  every frozen input of STORE-SHOT-006 (clock, zone, locale, 12-hour clock, device, density,
  no animations). They change whenever those screens or the fixture change.

  **Invariant — The feature graphic's screens are rendered from the showcase fixture, never
  pasted in from an image.** Nothing drawn on the graphic comes from an image file: the test
  does not read, crop or scale a committed screenshot or any other image to make it (a verify
  run reads only the committed feature graphic, to compare). It shares the shots' rendering code
  rather than a copy of it, so a screen cannot drift from its shot.

### Writing and checking the two images

- **STORE-ICON-004** Roborazzi writes every image as RGBA, which the feature graphic must not
  be, so the two tests (`StoreArtTest` for the store icon, `FeatureGraphicTest` for the feature
  graphic) write and compare the images themselves, through one shared recorder, following the
  mode the Roborazzi Gradle task sets:
  - `./gradlew recordRoborazziDebug` writes both files in the formats of STORE-ICON-001 and
    STORE-GRAPHIC-001, then reads each back and checks its size and colour type;
  - `./gradlew verifyRoborazziDebug` (what CI runs) compares each rendered image pixel for pixel
    with the committed file, with no tolerance, and fails on any difference in size or pixels
    or on a missing file. On a failure it writes the rendered image to
    `app/build/outputs/roborazzi/` as `icon_actual.png` or `featureGraphic_actual.png`, where
    CI uploads it with the screenshot diffs (STORE-SHOT-008), and leaves the committed file
    untouched;
  - plain `./gradlew test` renders both images and writes and compares nothing.
- **STORE-ICON-005** On every run except a recording, a test reads the committed `icon.png` and
  `featureGraphic.png` and fails unless each exists with exactly the size and PNG colour type of
  STORE-ICON-001 and STORE-GRAPHIC-001, and unless `icon.png` is within STORE-ICON-001's
  1024 KB limit. The feature graphic has no file size check.

## Recording and verifying

**Invariant — A store screenshot is recorded only when a person asks for it, and reaches the
default branch only through a pull request whose diff shows the new image.** This section applies
to the store icon and the feature graphic exactly as to the screenshots; together they are the
store images.

**Invariant — CI only verifies the store images; it never re-records them.** No check that runs
on a pull request or a release writes a store image. A recording runs only when a person applies
the label (STORE-SHOT-009), and its commit reaches the pull request only through that person's
own push.

- **STORE-SHOT-008** Every pull request's build (`pr.yml`) only verifies the committed store
  images (`./gradlew verifyRoborazziDebug`, which fails on any changed or missing screenshot,
  store icon or feature graphic) and, when it fails, uploads the rendered and diff images as the
  `store-screenshot-diffs` artifact. It never records.
- **STORE-SHOT-009** Recording happens in one of two ways, both started by a person:
  - **In CI:** a person applies the `record-store-screenshots` label to a pull request from a
    branch of this repository (never a fork). `record-store-screenshots.yml` runs
    `./gradlew recordRoborazziDebug` on the pull request's head, commits only the store images
    (the files in the `phoneScreenshots` folder, `icon.png` and `featureGraphic.png`) as
    `github-actions[bot]`, and force-pushes that commit to the side branch
    `store-screenshots/pr-<number>`, based on the pull request's head. If no image changed
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
- **STORE-SHOT-015** When a pull request from a branch of this repository closes, merged or
  not, `delete-store-screenshots-branch.yml` deletes its side branch
  `store-screenshots/pr-<number>` (STORE-SHOT-009). The repository's automatic deletion of head
  branches does not cover it, because the side branch is never a pull request's head. A pull
  request that never recorded has no side branch, and its absence is not a failure: the run
  succeeds when the branch does not exist. Any other failure to delete fails the run. The job's
  token can write contents and nothing else, and it runs for no fork's pull request (a fork's
  pull request never records, so it has no side branch). Reopening a pull request does not bring
  its side branch back; applying the label again records again.

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
  as it is at the tag (text, screenshots, store icon and feature graphic, under that path), and
  the job then reads back that the release carries it. The zip's images are the ones committed under
  `fastlane/metadata/android`; they are verified only in pull requests (`verifyRoborazziDebug`,
  STORE-SHOT-008), and the release run does not re-verify them (it re-checks
  only the listing text). It needs no secret beyond those the job already uses. A manual
  backfill of a tag older than this requirement (one without `docs/store/listing.md`) skips the
  check and the zip with a notice; any other failure of the check fails the job.
- **STORE-LIST-008** The app's name on the phone is the listing title, exactly: "Simple
  Stretch". It is the `app_name` string resource (`app/src/main/res/values/strings.xml`), which
  the manifest's `<application android:label>` names; the launcher activity declares no label of
  its own, so it inherits it. Android shows this label under the launcher icon, in the app's
  system settings and in its notifications' header.

  **Invariant — The app's name is written once, in `app_name`, and equals the compiled
  `title.txt`.** The manifest names the resource, never a literal, and a test fails when
  `app_name` and `fastlane/metadata/android/en-US/title.txt` differ, so the store title and the
  launcher label can only change together.

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
      shot IDs and then the feature graphic's entry (STORE-SHOT-007, STORE-GRAPHIC-003).
- [x] The canvas is composed at density 3 (1 dp = 3 px) at exactly 1080 x 2340 px; background,
      caption and frame are drawn outside the app theme (STORE-SHOT-010 to STORE-SHOT-013,
      STORE-SHOT-011 invariant).
- [x] A unit test measures captions as rendered lines and fails a too-long one (STORE-SHOT-012,
      STORE-SHOT-014 invariant).
- [x] `pr.yml` runs `verifyRoborazziDebug` and uploads `app/build/outputs/roborazzi/` (the actual
      and `_compare` images) when it fails; it never records (STORE-SHOT-008).
- [x] `record-store-screenshots.yml` runs only on the `record-store-screenshots` label on a
      same-repository pull request, commits only the store images (the `phoneScreenshots`
      folder, `icon.png` and `featureGraphic.png`), pushes to
      `store-screenshots/pr-<number>` and never to the pull request's branch, and always removes
      the label; the label is defined in `.github/labels.repo.yml` (STORE-SHOT-009 and its
      invariant).
- [x] `delete-store-screenshots-branch.yml` runs on `pull_request` `closed` for a
      same-repository pull request, with `contents: write` only, deletes
      `store-screenshots/pr-<number>`, and succeeds when that branch does not exist
      (STORE-SHOT-015).
- [x] The five images are recorded and committed in the `phoneScreenshots` folder, and
      `verifyRoborazziDebug` passes against them (STORE-SHOT-007).
- [x] `StoreArtTest` inflates `@mipmap/ic_launcher` and draws its background and foreground
      layers, with the 72 dp viewport scaled to 512 px (STORE-ICON-002, STORE-ICON-003 and the
      invariant).
- [x] The feature graphic is composed on the shots' pinned device and frozen inputs, its
      layout values in one place (`FeatureGraphicSpec`), its text in the committed Roboto Bold
      and Roboto Regular files (STORE-GRAPHIC-002 to STORE-GRAPHIC-006).
- [x] The graphic's headline and supporting line are read from the `STORE-GRAPHIC-003` entry of
      `docs/store/captions.md`, each with a tag; a test fails on a missing tag or on a cited ID
      that is undefined or retired (STORE-GRAPHIC-003).
- [x] A test measures the graphic's text as rendered lines and fails text that does not fit
      (STORE-GRAPHIC-004 and its invariant).
- [x] The two phones' screens are the shots' own composables and fixture state, through code
      shared with `StoreScreenshotTest` (the screen, the theme, the frame and the scaled
      screen), and no image file is read; nothing draws the launcher or store icon
      (STORE-GRAPHIC-002 and STORE-GRAPHIC-007 invariants).
- [x] The recorder writes the icon as RGBA and the feature graphic as RGB, records only in a
      recording run, compares exactly in a verify run and writes `<name>_actual.png` to
      `app/build/outputs/roborazzi/` on a failure (STORE-ICON-004).
- [x] A test checks the committed images' size and PNG colour type, and the icon's 1024 KB
      limit, skipped only in a recording run (STORE-ICON-005).
- [x] `icon.png` is recorded and committed, and `verifyRoborazziDebug` passes against it
      (STORE-ICON-001).
- [x] `featureGraphic.png` is re-recorded for the two-phone design and committed, and
      `verifyRoborazziDebug` passes against it (STORE-GRAPHIC-001).
- [x] The owner chose the two-phone design from mockups and delegated the review of the
      rendered images in the pull request (STORE-GRAPHIC-002 to STORE-GRAPHIC-005).
- [x] Every spec page except `README.md` starts with `<!-- store: headline -->` or
      `<!-- store: omit -->` and a blank line (STORE-LIST-002).
- [x] `store_listing.py` is standard-library Python with unit tests citing the STORE-LIST IDs
      they cover, run by `pr.yml` with the other script tests (STORE-LIST-001 to
      STORE-LIST-006).
- [x] `pr.yml` runs `store_listing.py --check` (STORE-LIST-006).
- [x] `build-and-attach` runs the check at the tag and attaches and reads back
      `store-listing-<version>.zip`; no step in it writes listing text (STORE-LIST-007 and the
      invariant).
- [x] A Robolectric test checks that the manifest's application label is `app_name`, the
      launcher activity has no label of its own, and `app_name` equals the compiled `title.txt`
      (STORE-LIST-008 and its invariant).
- [x] Store listing reviewed: the draft text in `docs/store/listing.md` claims only what the
      cited requirements say (STORE-LIST-003, STORE-LIST-004).
