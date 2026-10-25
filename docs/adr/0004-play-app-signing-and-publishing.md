# 0004: Enrol the release key as the Play app signing key

Status: accepted (partially supersedes [0002](0002-release-signing-with-a-stable-keystore.md))

## Context

Simple Stretch is going on Google Play as well as being released on GitHub. New apps on Play use
Play App Signing: the developer uploads an Android App Bundle signed with an **upload key**, and
Google re-signs the APKs it delivers with the **app signing key** it holds. What a phone sees is
the app signing key, so that is the key every later update, from any source, must match.

ADR 0002 was written for a sideloaded app with no Play App Signing. Its stable release keystore
already signs both the release APK attached to each GitHub Release and the release AAB beside it
(#40, #73). The open question was how that key relates to the two keys Play needs, and it has to
be settled before the first Play release, because the app signing key cannot be swapped afterwards
without breaking updates for everyone who installed from Play.

Options considered:

- **1A. Upload the existing release key as the app signing key, and keep it as the upload key.**
  Play installs and GitHub-released APKs then carry one signature.
- **1B. Keep the existing key as the app signing key, with a separate upload key.** A leaked
  upload key can be reset through Play, but CI would sign the AAB with a second keystore and a
  second set of secrets, while the APK keeps the first: two keys to hold and never lose instead of
  one, for protection that only covers uploads.
- **1C. Let Google generate the app signing key.** Google holds the only copy, so the sideloaded
  APK could never be signed with it: a Play install and a GitHub-released APK could not update
  each other, and moving between them would mean uninstalling and losing the app's data.

## Decision

Option 1A. The stable release keystore of ADR 0002 is uploaded to Play Console as the app signing
key, and the same key stays the upload key.

- Users who install from Play and users who install the APK from a GitHub Release run builds
  signed by the same certificate, so either can update the other in place, keeping its data.
- CI keeps signing the release AAB with the current secrets. The AAB build, the signature gate and
  the secrets of ADR 0002 need no change.
- GitHub Releases keep attaching the release-signed APK next to the AAB, for people who do not use
  Play.
- Enrolling the key in Play App Signing is done in Play Console by the owner (#48), following this
  record.

### What this supersedes in ADR 0002

Only ADR 0002's statements that Simple Stretch is sideloaded with no Play App Signing and no key
recovery, and its consequence that leaves the choice between app signing key and upload key open.
The rest of ADR 0002 stands: the one stable keystore, its secrets, the unsigned-never-debug-signed
invariant, and the signature gate on every APK and AAB.

## Consequences

- **One key both uploads and signs what users install.** If it leaks, the key every install trusts
  is exposed, not only the upload path. It stays only in repository secrets and with the owner.
- **The key must never change**, as in ADR 0002, now for Play installs as well as sideloaded ones.
- **The choice cannot be undone after the first Play release.** Once Play has delivered builds
  signed with this key, the app signing key is fixed for everyone who installed them.
- **If the key is lost,** Play can register a new upload key, and Play releases continue under the
  app signing key Google holds. GitHub-released APKs could then no longer be signed with the
  matching key, so sideloaded installs would have to uninstall once to move to a new key, and
  could no longer update to or from Play installs.
