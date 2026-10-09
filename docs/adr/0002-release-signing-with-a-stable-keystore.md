# 0002: Sign release builds with a stable release keystore

Status: accepted (supersedes [0001](0001-debug-signing-until-release-keystore.md))

## Context

Android only installs an update over an existing app when the new APK is signed by the same
certificate. Anything else is refused (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), and the only way
past it is to uninstall, which deletes the app's schedules, goals, completion log and skipped days.

Under ADR 0001, release APKs were debug-signed, and every CI runner generates its own debug key, so
consecutive releases could not be relied on to upgrade in place. Simple Stretch is sideloaded:
there is no Play App Signing to re-sign uploads and no key recovery. The owner has now added a
release keystore to this repository's secrets, the same way as for the sibling Android apps
(Interval Trainer, Doggiehood, Chores).

## Decision

Release builds are signed with one stable, owned keystore. It reaches a build only as repository
secrets, never as a committed file or Gradle property:

| Secret | Holds | Required |
| --- | --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the keystore file, base64-encoded | yes |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password | yes |
| `ANDROID_KEY_ALIAS` | the alias of the signing key | yes |
| `ANDROID_KEY_ALIAS_PASSWORD` | that key's password | yes |
| `ANDROID_KEYSTORE_SHA256` | the signing certificate's SHA-256 fingerprint | no (pin, if set) |

- `app/build.gradle.kts` reads `ANDROID_KEYSTORE_PATH` (the decoded file),
  `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_ALIAS_PASSWORD` from the
  environment and signs `release` with v1, v2 and v3 schemes stated explicitly.
- **Invariant — when any of those four is missing, the `release` build type is left unsigned,
  never debug-signed.** An unsigned APK cannot be installed, so it cannot be mistaken for a
  release; a debug-signed one would install and only fail at the next upgrade.
- `release-please.yml` fails before building if a required secret is missing (naming it, never
  printing a value), decodes the keystore into the runner temp directory with `umask 077`, builds,
  removes the keystore in an `always()` step, and runs `.github/scripts/verify_release_signature.py`
  (`apksigner verify --print-certs`) before attaching the APK. The gate rejects unsigned,
  debug-signed and multi-signer APKs and, when `ANDROID_KEYSTORE_SHA256` is set, any certificate
  other than the pinned one. A manual `workflow_dispatch` with `backfill_tag` re-signs and
  re-attaches the APK for an existing release.
- `pr.yml` builds a release-signed APK (artifact `simple-stretch-release-apk`, 14 days) for pull
  requests from branches of this repository, so a PR build can be installed over the released app.
  Fork PRs never receive the secrets and skip those steps.
- Debug builds keep the debug key.

## Consequences

- **The key must never change and must not be lost.** A different key means every install has to
  be uninstalled (losing its data) to take the next update. The owner keeps the keystore outside
  this repository.
- Installs of earlier debug- or CI-signed builds must be uninstalled **once** before the first
  release-signed build is installed. From then on, releases and same-repository PR release builds
  upgrade in place.
- The debug APK artifact and a release-signed build cannot be installed over each other (same
  application ID, different keys).
- Local `assembleRelease` without the secrets produces an unsigned APK, by design.
- The secret names above were taken from the sibling repositories; if any differs here, the
  release job fails at its first step and names the missing one.
