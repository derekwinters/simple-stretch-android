# CLAUDE.md

Simple Stretch: a small native Android app (Kotlin, Jetpack Compose, Room, AlarmManager) for
recurring stretch reminders. See README.md for features and docs/spec/ for behaviour.

## Rules

1. **Spec before code.** Behaviour lives in `docs/spec/` with requirement IDs (SCHED-, SKIP-,
   NOTIF-, LIB-, HOME-). Change the spec page first, then the code. Unit tests reference the IDs
   they cover in comments. Where a behaviour could be built technically-correct-but-wrong, the
   spec states a bold `**Invariant — …**` sentence; each spec page keeps a short "Build checklist".
2. **Conventional commits are required.** PRs are squash-merged and the squash title is the
   input to release-please, so the PR title must be a Conventional Commit
   (`type(scope)!: description`). Types: `feat`, `fix`, `chore`, `ci`, `docs`, `build`,
   `refactor`, `test`, `perf`, `revert`. `pr-title-lint.yml` enforces this.
3. **PR bodies** open with a plain-English lead (2-3 sentences, before any file or class names),
   then `## Deviations and Decisions` (`None.` when empty), then a `**Docs:**` line saying what
   documentation changed or why none was needed.
4. **Implementation work is delegated to an agent**; the coordinating session plans, reviews
   and reports.
5. **Human-only tasks** (anything an agent cannot do, such as granting a secret or testing on a
   physical device) each become one small GitHub issue.
6. **Never publish private or session links** (chat, session or artifact URLs) in code, docs,
   commits, PRs or issues.

## Build

- Gradle wrapper 8.11.1, AGP 8.9.1, Kotlin 2.0.21, compileSdk/targetSdk 36, minSdk 26, Java 17.
- `./gradlew test` and `./gradlew assembleDebug`. The Android SDK and Google Maven may be
  unreachable from some environments; CI (`pr.yml`) is then the build gate.
- Version: `VERSION_NAME` in `gradle.properties`, owned by release-please. The version code is
  derived from it (`major*10000 + minor*100 + patch`, by `buildSrc/`'s `VersionCode`; its tests
  are not part of `./gradlew test`, run them with `./gradlew -p buildSrc test`) and checked by the
  release guard against the previous release (docs/adr/0003). APKs are named `simple-stretch-<versionName>-<buildType>.apk`.
- Release builds are signed with the stable release keystore from repository secrets
  (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_ALIAS_PASSWORD`; optional pin `ANDROID_KEYSTORE_SHA256`) via env vars read in
  `app/build.gradle.kts` (docs/adr/0002). Without them `release` is left unsigned, never
  debug-signed. The key must never change. `.github/scripts/verify_release_signature.py` gates
  every release-signed APK and AAB, and `.github/scripts/check_version_code.py` (the release
  guard) every release's version code; their tests run with
  `python3 -m unittest discover -s .github/scripts/tests`.
- Room schemas are exported to `app/schemas/`; commit changes to them with the entity change.
- Store screenshots (docs/spec/store-listing.md) are rendered by Roborazzi on Robolectric from
  `ShowcaseFixture` and committed in `fastlane/metadata/android/en-US/images/phoneScreenshots/`;
  captions are in `docs/store/captions.md`. The Play store icon (`images/icon.png`, drawn from the
  real launcher icon resources by `StoreArtTest`) and feature graphic (`images/featureGraphic.png`:
  a headline from `captions.md`, in the committed Roboto from `app/src/test/resources/store/fonts/`,
  beside two phones showing the Home and session screens composed from `ShowcaseFixture`, never
  the app icon; `FeatureGraphicTest`) are rendered on the same setup; the tests write and compare
  them themselves, honouring Roborazzi's record/verify mode, because Roborazzi writes only RGBA. CI runs `./gradlew verifyRoborazziDebug`, which fails
  on a changed or missing image and uploads the diffs; it never records. Recording is always
  deliberate: apply the `record-store-screenshots` label to the PR (`record-store-screenshots.yml`
  records and pushes the images to the side branch `store-screenshots/pr-<number>`, never to the
  PR branch; bring that commit into the PR with your own push so `pr.yml` verifies it), or run
  `./gradlew recordRoborazziDebug` locally on Linux with JDK 17. Either way the images reach
  `main` only through the PR diff.
- Store listing text is hand-written in `docs/store/listing.md`, each claim citing spec IDs; after
  editing it run `python3 .github/scripts/store_listing.py` and commit the generated
  `fastlane/metadata/android/en-US/*.txt` (CI runs it with `--check`; docs/spec/store-listing.md).

@.ai-sdlc/house-rules.md
