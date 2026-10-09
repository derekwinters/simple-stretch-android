# 0001: Debug-sign release builds until a release keystore exists

Status: superseded by [0002](0002-release-signing-with-a-stable-keystore.md)

> **Superseded.** Release builds are now signed with the stable release keystore; see
> [ADR 0002](0002-release-signing-with-a-stable-keystore.md). This record is kept for history.

The `release` build type uses `signingConfig = signingConfigs.debug`, and the release-please
workflow builds and attaches that APK to each GitHub Release without referencing any secrets.

There is no release keystore yet, and no Play Store listing to install against. Setting up key
custody (where the keystore lives, who can read it, how it is rotated) has no payoff until the
app is published somewhere that needs a stable signing identity, so it is deferred.

## Consequences

- Release APKs are debug-signed. CI runners each generate their own debug key, so two releases
  may be signed by different keys: updating in place can fail with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the fix is to uninstall and reinstall (which clears
  the app's schedules and skipped days).
- When a stable keystore is introduced, a new ADR supersedes this one; the release build type then
  reads the keystore from environment variables (never a committed file), as the sibling Android
  repos do, and every existing install needs one uninstall/reinstall.
