#!/usr/bin/env python3
"""Release guard: a release's versionCode is higher than the previous release's.

Why this exists. Google Play refuses an upload whose versionCode is not higher
than every code it has seen for the app, and a device refuses to install an
update with a lower code than the installed one. The code is derived from the
version name (`docs/adr/0003-version-code-derived-from-version-name.md`), so
it should rise with every release; this guard proves it did, on the APK that
was actually built, before that APK is attached to a release (release-please
workflow, `build-and-attach`) or while a release PR is still open (the
`release-pr-guard` job, which posts the `release-version-code` status).

What it compares.

- The previous release is the newest GitHub release by publish time that is
  neither a draft nor a pre-release, leaving out `--exclude-tag` (the tag
  being released, which release-please has already published). The listing
  comes from `gh release list --json tagName,isDraft,isPrerelease,publishedAt`.
- Its APK is the one asset named `simple-stretch-*-release.apk`, downloaded
  with `gh release download <tag> --pattern 'simple-stretch-*-release.apk'`.
  None, or more than one, fails: the message names the tag and points to the
  workflow's `backfill_tag` input, which re-attaches a release's APK.
- Both codes are read with `aapt2 dump badging`. The new code is never
  recomputed from the formula: the guard checks what was built.
- It passes only when the new code is strictly greater, and otherwise fails
  naming both codes and both versions. With no published release at all it
  passes with a notice.

`aapt2 dump badging` prints a `package:` line first and then many lines this
guard does not need. The parser fails closed where it matters: the first
non-blank line must be the one `package:` line, made only of `key='value'`
attributes, with a `name`, a `versionName` and an all-digit `versionCode`. The
tests use output captured from this repository's real release APKs
(`tests/fixtures/README.md`).

Google Play's own record is the authority, and this guard cannot see it: an
upload made outside a GitHub release is invisible here (docs/adr/0003).

The decisions are pure functions (`previous_release`, `parse_badging`,
`single_release_apk`, `compare`) so they unit-test with no Android SDK or
network. `main` wires them to gh and aapt2. Standard library only.
"""

import argparse
import fnmatch
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections import namedtuple
from datetime import datetime

ApkVersion = namedtuple("ApkVersion", "package version_code version_name")

# The release APK the workflow attaches; the AAB and any debug APK do not match.
RELEASE_APK_PATTERN = "simple-stretch-*-release.apk"

_TITLE = "Release version code"
_BACKFILL_HINT = (
    "re-attach it by running the release-please workflow by hand (workflow_dispatch) with "
    "backfill_tag={0}")

# `package: name='...' versionCode='...' ...`: nothing but quoted key='value' attributes.
_PACKAGE_LINE = re.compile(r"^package:((?: [A-Za-z][A-Za-z0-9_-]*='[^']*')+)$")
_ATTRIBUTE = re.compile(r" ([A-Za-z][A-Za-z0-9_-]*)='([^']*)'")


class GuardError(Exception):
    """The guard cannot reach a trustworthy verdict, or the verdict is a failure."""


def _with_raw_output(summary, raw_output, tool="aapt2"):
    """An error message carrying the verbatim tool output, so a format change
    is diagnosable from the one log it produces."""
    if not raw_output.endswith("\n"):
        raw_output += "\n"
    return "{0}\n----- {2} output begin -----\n{1}----- {2} output end -----".format(
        summary, raw_output, tool)


# --- Pure decisions ---------------------------------------------------------


def _published_at(text, tag):
    try:
        published = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except (AttributeError, ValueError):
        published = None
    if published is None or published.tzinfo is None:  # naive and aware times cannot be ordered
        raise GuardError("release {0} has an unreadable publish time {1!r}".format(tag, text))
    return published


def previous_release(listing, exclude_tag=None):
    """The tag of the newest published release that is not a draft, not a
    pre-release and not `exclude_tag`, or None when there is none.

    `listing` is gh's `release list --json tagName,isDraft,isPrerelease,publishedAt`
    output, parsed. Ordered by publish time, not by list order. Raises
    `GuardError` on an entry it cannot read.
    """
    if not isinstance(listing, list):
        raise GuardError("the release listing is not a list: {0!r}".format(listing))
    newest = None
    for entry in listing:
        if not isinstance(entry, dict):
            raise GuardError("unreadable release listing entry {0!r}".format(entry))
        tag = entry.get("tagName")
        draft = entry.get("isDraft")
        prerelease = entry.get("isPrerelease")
        if not isinstance(tag, str) or not tag or not isinstance(draft, bool) \
                or not isinstance(prerelease, bool):
            raise GuardError("unreadable release listing entry {0!r}".format(entry))
        if draft or prerelease or tag == exclude_tag:
            continue
        published = _published_at(entry.get("publishedAt"), tag)
        if newest is None or published > newest[0]:
            newest = (published, tag)
    return None if newest is None else newest[1]


def parse_badging(text):
    """The package name, versionCode and versionName from `aapt2 dump badging`.

    Raises `GuardError` (with the raw output attached) unless the first
    non-blank line is the output's only `package:` line, made of key='value'
    attributes including `name`, an all-digit `versionCode` and `versionName`.
    """
    lines = [line for line in text.splitlines() if line.strip()]
    package_lines = [line for line in lines if line.startswith("package:")]

    def fail(summary):
        return GuardError(_with_raw_output(
            "{0}; aapt2's output is not in the form this guard reads, so it cannot be "
            "trusted".format(summary), text))

    if not lines:
        raise fail("aapt2 printed nothing")
    if len(package_lines) != 1 or lines[0] != package_lines[0]:
        raise fail("expected exactly one package: line, first, found {0}".format(
            len(package_lines)))
    match = _PACKAGE_LINE.match(lines[0])
    if not match:
        raise fail("unrecognized package line {0!r}".format(lines[0]))
    attributes = dict(_ATTRIBUTE.findall(match.group(1)))
    for key in ("name", "versionCode", "versionName"):
        if not attributes.get(key):
            raise fail("the package line has no {0}".format(key))
    if not re.fullmatch(r"[0-9]+", attributes["versionCode"]):
        raise fail("versionCode {0!r} is not a decimal number".format(attributes["versionCode"]))
    return ApkVersion(attributes["name"], int(attributes["versionCode"]),
                      attributes["versionName"])


def single_release_apk(tag, asset_names):
    """The one asset of release `tag` that is a release APK. Raises
    `GuardError` naming the tag, and how to fix it, for none or several."""
    matches = sorted(name for name in asset_names if fnmatch.fnmatchcase(name, RELEASE_APK_PATTERN))
    if len(matches) == 1:
        return matches[0]
    found = "no asset" if not matches else "{0} assets ({1})".format(
        len(matches), ", ".join(matches))
    raise GuardError(
        "the previous release {0} has {1} matching {2}, so there is nothing certain to "
        "compare the new versionCode against; {3}".format(
            tag, found, RELEASE_APK_PATTERN, _BACKFILL_HINT.format(tag)))


def compare(new, previous, previous_tag):
    """(ok, message): ok only when the new versionCode is strictly greater."""
    new_part = "versionCode {0} ({1})".format(new.version_code, new.version_name)
    previous_part = "versionCode {0} ({1}) of the previous release {2}".format(
        previous.version_code, previous.version_name, previous_tag)
    if new.version_code > previous.version_code:
        return True, "OK: the new APK's {0} is higher than {1}.".format(new_part, previous_part)
    return False, (
        "the new APK's {0} is not higher than {1}. Google Play refuses such an upload and a "
        "device refuses such an update (docs/adr/0003).".format(new_part, previous_part))


# --- Wiring -----------------------------------------------------------------


def _run(cmd, **kwargs):
    return subprocess.run(cmd, capture_output=True, text=True, check=False, **kwargs)


def _version_key(path):
    version = os.path.basename(os.path.dirname(path))
    return tuple(int(part) if part.isdigit() else 0 for part in re.split(r"[.-]", version))


def find_aapt2():
    """aapt2 from PATH, else the newest build-tools copy in the Android SDK."""
    on_path = shutil.which("aapt2")
    if on_path:
        return on_path
    for root in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if not root:
            continue
        candidates = glob.glob(os.path.join(root, "build-tools", "*", "aapt2"))
        if candidates:
            return max(candidates, key=_version_key)
    raise GuardError("aapt2 not found on PATH or under ANDROID_HOME/ANDROID_SDK_ROOT build-tools")


def _checked(cmd, what):
    result = _run(cmd)
    if result.returncode != 0:
        raise GuardError("{0} failed (exit {1}): {2}".format(
            what, result.returncode, (result.stderr or result.stdout or "").strip()))
    return result.stdout


def read_apk_version(apk, aapt2):
    output = _checked([aapt2, "dump", "badging", apk],
                      "aapt2 dump badging {0}".format(os.path.basename(apk)))
    return parse_badging(output)


def _gh_json(cmd, what):
    output = _checked(cmd, what)
    try:
        return json.loads(output)
    except ValueError:
        raise GuardError("{0} printed something that is not JSON: {1!r}".format(what, output[:200]))


def check(apk, repo, exclude_tag):
    """(ok, message) for one new APK, or `GuardError` when no verdict can be trusted."""
    aapt2 = find_aapt2()
    new = read_apk_version(apk, aapt2)
    print("New APK {0}: {1} versionCode {2} versionName {3}".format(
        os.path.basename(apk), new.package, new.version_code, new.version_name))

    listing = _gh_json(
        ["gh", "release", "list", "--repo", repo, "--limit", "1000",
         "--json", "tagName,isDraft,isPrerelease,publishedAt"],
        "listing the releases of {0}".format(repo))
    tag = previous_release(listing, exclude_tag)
    if tag is None:
        return True, None

    view = _gh_json(["gh", "release", "view", tag, "--repo", repo, "--json", "assets"],
                    "reading the assets of {0}".format(tag))
    try:
        names = [asset["name"] for asset in view["assets"]]
    except (KeyError, TypeError):
        raise GuardError("unreadable asset list for {0}: {1!r}".format(tag, view))
    expected = single_release_apk(tag, names)

    with tempfile.TemporaryDirectory() as directory:
        _checked(["gh", "release", "download", tag, "--repo", repo,
                  "--pattern", RELEASE_APK_PATTERN, "--dir", directory],
                 "downloading the release APK of {0}".format(tag))
        downloaded = sorted(os.listdir(directory))
        if downloaded != [expected]:
            raise GuardError(
                "downloading {0} from {1} gave {2}, expected only {3}; {4}".format(
                    RELEASE_APK_PATTERN, tag, downloaded or "nothing", expected,
                    _BACKFILL_HINT.format(tag)))
        previous = read_apk_version(os.path.join(directory, expected), aapt2)
    print("Previous release {0}, {1}: {2} versionCode {3} versionName {4}".format(
        tag, expected, previous.package, previous.version_code, previous.version_name))
    return compare(new, previous, tag)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk", help="the APK just built")
    parser.add_argument("--repo", required=True, help="OWNER/REPO whose releases to compare")
    parser.add_argument("--exclude-tag", default=None,
                        help="the tag being released, which is not its own previous release")
    args = parser.parse_args(argv)

    try:
        ok, message = check(args.apk, args.repo, args.exclude_tag)
    except GuardError as exc:
        print("::error title={0}::{1}".format(_TITLE, exc))
        return 1

    if message is None:
        print("::notice title={0}::No earlier published release to compare against, so "
              "any versionCode is accepted.".format(_TITLE))
        return 0
    if ok:
        print(message)
        return 0
    print("::error title={0}::{1}".format(_TITLE, message))
    return 1


if __name__ == "__main__":
    sys.exit(main())
