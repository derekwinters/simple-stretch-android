# 0003: Derive the version code from the version name

Status: accepted

## Context

Android and Google Play order builds by `versionCode`, an integer, not by the version name. Play
rejects any upload whose `versionCode` is not higher than every code it has seen for the app, and
a device refuses to install an update with a lower code than the installed one. Until now the code
was a hand-set `VERSION_CODE=1` in `gradle.properties` that nothing bumped: releases 0.1.0 and
0.2.0 both shipped as code 1, and the second Play upload would have been refused.

release-please already owns the version name (`VERSION_NAME` in `gradle.properties`, rewritten from
Conventional Commit titles). A second number kept in step by hand is a number that drifts.

## Decision

The version code is computed from `VERSION_NAME` at build time; there is no `VERSION_CODE`
property.

```
versionCode = major * 10000 + minor * 100 + patch
```

| Version | Code |
| --- | --- |
| 0.2.0 | 200 |
| 0.3.0 | 300 (the first release under this scheme; 0.1.0 and 0.2.0 were code 1) |
| 0.99.99 | 9999 |
| 1.0.0 | 10000 |

- The conversion lives in `buildSrc/` (`VersionCode.fromVersionName`) and is unit-tested there;
  `app/build.gradle.kts` sets `defaultConfig.versionCode` from it, so debug and release builds of
  one version share one code.
- It accepts only `MAJOR.MINOR.PATCH`, three dot-separated runs of digits: no `v` prefix, no
  pre-release or build suffix. **Minor and patch are capped at 99**, and the code may not exceed
  2100000000, Google Play's largest allowed `versionCode`. Anything else fails the build with a
  message naming the value.
- **Invariant — the conversion never wraps, clamps or truncates.** A version it cannot represent
  fails the build; it never produces some other, smaller code. Under the caps the scheme is
  strictly increasing: a higher version always has a higher code, including across a carry
  (0.2.99 → 299 < 0.3.0 → 300; 0.99.99 → 9999 < 1.0.0 → 10000).
- **Escape hatch.** If a minor or patch ever needs to pass 99, switch to
  `major * 1000000 + minor * 1000 + patch` (caps 999). That switch is always safe: for every
  version the new code is at least the old one (with minor and patch at most 99, the difference is
  `major * 990000 + minor * 900`), so the first build under it is higher than every earlier
  release. Switching the other way is never safe.

### Release guard

`.github/scripts/check_version_code.py` refuses a release whose code is not strictly higher than
the previous release's.

- **The previous release** is the newest GitHub release by publish time that is neither a draft
  nor a pre-release, leaving out the tag being released. It is the newest by publish time, not
  the one with the highest code.
- It downloads that release's `simple-stretch-*-release.apk` with
  `gh release download <tag> --pattern 'simple-stretch-*-release.apk'` and reads both codes with
  `aapt2 dump badging`. **Invariant — the new code is read from the APK that was actually built,
  never recomputed from the formula**, so a build that ignores the formula is still caught.
- It passes only when the new code is strictly greater; otherwise it fails, naming both codes
  and both versions.
- Edge cases:
  - no published release at all: passes with a notice (nothing to compare against);
  - the previous release has no matching APK, or more than one: fails, naming the tag, and
    points to the `backfill_tag` input of the `release-please` workflow, which re-attaches it;
  - the listing, download or `aapt2` fails, or `aapt2` prints output the parser does not
    recognise: fails (it never passes on a reading it is unsure of);
  - a manual backfill (`workflow_dispatch` with `backfill_tag`) of the **newest** published
    release is guarded exactly like a release (`--backfill-tag` decides this with the same
    release selection, then compares against the release before it), so re-attaching cannot
    bypass a guard that failed on release. Only a backfill of an **older** release skips the
    guard, with a notice: it is lower than newer releases by design.
- **On release:** the `build-and-attach` job of `release-please.yml` runs the guard on the
  signed release APK after the signature gate and before the attach step, from the `main`
  checkout like the signature gate. **Invariant — a release, or a backfill of the newest
  release, whose APK's code is not higher than the previous release's gets no APK or AAB
  attached.**
- **The guard reads only the APK.** The AAB is covered only because the APK and the AAB come out
  of one Gradle invocation (`assembleRelease bundleRelease`) with one `versionCode`. **Invariant
  — the release APK and AAB are built in the same Gradle invocation;** splitting the AAB build
  into a separate step or job leaves it unchecked by this guard, and would need the guard run on
  the AAB as well.
- **On the release PR:** the `release-pr-guard` job runs on every push to `main` after
  release-please has updated its PR. When a release PR is open it checks out the PR's head,
  builds the debug APK (same derived code), runs the guard and posts a commit status named
  `release-version-code` on the PR's head commit: `success`, or `failure` with the reason. A
  release PR that would publish a lower or equal code is visible before it is merged.

## Consequences

- **Google Play's own record is the authority, and the guard cannot see it.** It compares against
  the previous GitHub release's APK only. A build uploaded to Play by hand, outside a GitHub
  release, is invisible to it, and a later release could pass the guard yet still be refused by
  Play. Checking Play itself needs a Play Developer API credential, which is part of automated
  upload (#26). Until then, **every Play upload should be a GitHub release asset.**
- Minor and patch stop at 99 until the escape hatch is taken; the build says so rather than
  publishing a wrong code.
- Pre-release versions (`0.3.0-beta`) cannot be built; release-please is configured for plain
  releases only.
- The formula tests are not part of `./gradlew test`: buildSrc is a separate build whose tests
  Gradle does not run for the main build. They run with `./gradlew -p buildSrc test`, which
  `pr.yml` runs on every pull request.
