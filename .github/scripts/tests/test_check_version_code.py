"""Unit tests for the release version-code guard (docs/adr/0003).

Google Play refuses an upload whose versionCode is not higher than every earlier one, and a device
refuses an update with a lower code. `check_version_code.py` compares the code of the APK just
built against the APK attached to the previous GitHub release and fails unless the new one is
strictly higher. These tests need no Android SDK, no network and no APK.

The `aapt2 dump badging` transcripts are captured from this repository's real release APKs (see
`fixtures/README.md`). The `gh release list` listings are hand-written, in the fields the guard
asks gh for.
"""

import os
import subprocess
import sys
import unittest
from contextlib import redirect_stdout
from io import StringIO
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import check_version_code as guard  # noqa: E402

FIXTURES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")


def _fixture(name):
    with open(os.path.join(FIXTURES, name), encoding="utf-8") as handle:
        return handle.read()


# Real output of `aapt2 dump badging` on the APKs attached to v0.2.0 and v0.1.0.
BADGING_0_2_0 = _fixture("aapt2-dump-badging-0.2.0-release.txt")
BADGING_0_1_0 = _fixture("aapt2-dump-badging-0.1.0-release.txt")
# Real stderr of `aapt2 dump badging` on a file that is not an APK (it printed nothing on stdout).
BADGING_NOT_AN_APK = _fixture("aapt2-dump-badging-not-an-apk-stderr.txt")


def _release(tag, published, draft=False, prerelease=False):
    return {"tagName": tag, "publishedAt": published, "isDraft": draft,
            "isPrerelease": prerelease}


def _facts(code, name):
    return guard.ApkVersion(package="com.derekwinters.stretch", version_code=code,
                            version_name=name)


class PreviousReleaseTest(unittest.TestCase):
    """The release to compare against: newest published, not draft, not pre-release, not the
    tag being released."""

    def test_picks_the_newest_published_release(self):
        listing = [
            _release("v0.1.0", "2026-10-11T20:51:07Z"),
            _release("v0.2.0", "2026-10-22T10:15:48Z"),
        ]
        self.assertEqual("v0.2.0", guard.previous_release(listing))

    def test_orders_by_publish_time_not_by_list_order(self):
        listing = [
            _release("v0.1.0", "2026-10-11T20:51:07Z"),
            _release("v0.2.0", "2026-10-22T10:15:48Z"),
            _release("v0.1.1", "2026-10-12T08:00:00Z"),
        ]
        self.assertEqual("v0.2.0", guard.previous_release(listing))

    def test_skips_drafts_and_prereleases(self):
        listing = [
            # gh prints Go's zero time for a draft's missing publish date.
            _release("v0.4.0", "0001-01-01T00:00:00Z", draft=True),
            _release("v0.3.0-rc.1", "2026-10-24T09:00:00Z", prerelease=True),
            _release("v0.2.0", "2026-10-22T10:15:48Z"),
        ]
        self.assertEqual("v0.2.0", guard.previous_release(listing))

    def test_skips_the_tag_being_released(self):
        # On release, release-please has already published the new tag.
        listing = [
            _release("v0.3.0", "2026-10-25T12:00:00Z"),
            _release("v0.2.0", "2026-10-22T10:15:48Z"),
        ]
        self.assertEqual("v0.2.0", guard.previous_release(listing, exclude_tag="v0.3.0"))

    def test_none_when_there_is_no_published_release(self):
        self.assertIsNone(guard.previous_release([]))
        self.assertIsNone(guard.previous_release(
            [_release("v0.1.0", "0001-01-01T00:00:00Z", draft=True)]))
        self.assertIsNone(guard.previous_release(
            [_release("v0.3.0", "2026-10-25T12:00:00Z")], exclude_tag="v0.3.0"))

    def test_rejects_a_listing_it_cannot_read(self):
        for listing in ({"tagName": "v0.2.0"},
                        [{"tagName": "v0.2.0"}],
                        [_release("v0.2.0", "yesterday")],
                        [_release("", "2026-10-22T10:15:48Z")]):
            with self.subTest(listing=listing):
                with self.assertRaises(guard.GuardError):
                    guard.previous_release(listing)


class NewestReleaseTest(unittest.TestCase):
    """Whether a backfill tag is the newest published release, so the guard must run on it."""

    LISTING = [
        _release("v0.1.0", "2026-10-11T20:51:07Z"),
        _release("v0.2.0", "2026-10-22T10:15:48Z"),
    ]

    def test_the_newest_published_release_is_newest(self):
        self.assertTrue(guard.is_newest_release(self.LISTING, "v0.2.0"))

    def test_an_older_release_is_not_newest(self):
        self.assertFalse(guard.is_newest_release(self.LISTING, "v0.1.0"))

    def test_drafts_and_prereleases_do_not_count_as_newer(self):
        listing = self.LISTING + [
            _release("v0.4.0", "0001-01-01T00:00:00Z", draft=True),
            _release("v0.3.0-rc.1", "2026-10-24T09:00:00Z", prerelease=True),
        ]
        self.assertTrue(guard.is_newest_release(listing, "v0.2.0"))

    def test_a_draft_or_prerelease_tag_is_not_newest(self):
        listing = self.LISTING + [
            _release("v0.3.0-rc.1", "2026-10-24T09:00:00Z", prerelease=True)]
        self.assertFalse(guard.is_newest_release(listing, "v0.3.0-rc.1"))

    def test_a_tag_not_in_the_listing_is_not_newest(self):
        self.assertFalse(guard.is_newest_release(self.LISTING, "v9.9.9"))
        self.assertFalse(guard.is_newest_release([], "v0.2.0"))


class ParseBadgingTest(unittest.TestCase):

    def test_reads_the_code_and_name_from_a_real_release_apk(self):
        self.assertEqual(
            guard.ApkVersion("com.derekwinters.stretch", 1, "0.2.0"),
            guard.parse_badging(BADGING_0_2_0))
        self.assertEqual(1, guard.parse_badging(BADGING_0_1_0).version_code)

    def test_reads_a_multi_digit_code(self):
        text = BADGING_0_2_0.replace("versionCode='1'", "versionCode='300'", 1)
        self.assertEqual(300, guard.parse_badging(text).version_code)

    def test_fails_closed_on_unrecognised_output(self):
        first_line, rest = BADGING_0_2_0.split("\n", 1)
        cases = {
            "empty": "",
            "aapt2 error text": BADGING_NOT_AN_APK,
            "no package line": rest,
            "package line not first": rest + first_line + "\n",
            "two package lines": first_line + "\n" + BADGING_0_2_0,
            "no versionCode": BADGING_0_2_0.replace(" versionCode='1'", "", 1),
            "non-numeric versionCode": BADGING_0_2_0.replace(
                "versionCode='1'", "versionCode='0x1'", 1),
            "empty versionCode": BADGING_0_2_0.replace("versionCode='1'", "versionCode=''", 1),
            "unquoted attribute": BADGING_0_2_0.replace("versionCode='1'", "versionCode=1", 1),
            "no versionName": BADGING_0_2_0.replace(" versionName='0.2.0'", "", 1),
        }
        for label, text in cases.items():
            with self.subTest(label):
                with self.assertRaises(guard.GuardError) as raised:
                    guard.parse_badging(text)
                # The raw output is carried in the message, so a format change is diagnosable.
                self.assertIn("aapt2 output begin", str(raised.exception))


class CompareTest(unittest.TestCase):

    def test_a_higher_code_passes(self):
        ok, message = guard.compare(_facts(300, "0.3.0"), _facts(200, "0.2.0"), "v0.2.0")
        self.assertTrue(ok)
        self.assertIn("300", message)
        self.assertIn("200", message)

    def test_compares_numbers_not_text(self):
        # "300" < "1" as text would be a false failure; 300 > 1 is the first release under
        # the derived scheme (0.2.0 shipped as code 1).
        self.assertTrue(guard.compare(_facts(300, "0.3.0"), _facts(1, "0.2.0"), "v0.2.0")[0])
        self.assertFalse(guard.compare(_facts(9, "0.0.9"), _facts(10, "0.0.10"), "v0.0.10")[0])

    def test_an_equal_code_fails_naming_both_codes_and_versions(self):
        ok, message = guard.compare(_facts(1, "0.3.0"), _facts(1, "0.2.0"), "v0.2.0")
        self.assertFalse(ok)
        for part in ("0.3.0", "0.2.0", "v0.2.0", "versionCode 1"):
            self.assertIn(part, message)

    def test_a_lower_code_fails_naming_both_codes_and_versions(self):
        ok, message = guard.compare(_facts(299, "0.2.99"), _facts(300, "0.3.0"), "v0.3.0")
        self.assertFalse(ok)
        for part in ("299", "300", "0.2.99", "0.3.0", "v0.3.0"):
            self.assertIn(part, message)


class ReleaseApkAssetTest(unittest.TestCase):

    def test_the_one_release_apk_among_other_assets(self):
        names = ["simple-stretch-0.2.0-release.aab", "simple-stretch-0.2.0-release.apk",
                 "simple-stretch-0.2.0-debug.apk", "notes.txt"]
        self.assertEqual("simple-stretch-0.2.0-release.apk",
                         guard.single_release_apk("v0.2.0", names))

    def test_no_release_apk_fails_naming_the_tag_and_the_backfill(self):
        for names in ([], ["simple-stretch-0.2.0-release.aab", "simple-stretch-0.2.0-debug.apk"]):
            with self.subTest(names=names):
                with self.assertRaises(guard.GuardError) as raised:
                    guard.single_release_apk("v0.2.0", names)
                self.assertIn("v0.2.0", str(raised.exception))
                self.assertIn("backfill_tag", str(raised.exception))

    def test_two_release_apks_fail_naming_the_tag_and_the_backfill(self):
        names = ["simple-stretch-0.2.0-release.apk", "simple-stretch-0.2.1-release.apk"]
        with self.assertRaises(guard.GuardError) as raised:
            guard.single_release_apk("v0.2.0", names)
        message = str(raised.exception)
        self.assertIn("v0.2.0", message)
        self.assertIn("backfill_tag", message)
        self.assertIn("simple-stretch-0.2.1-release.apk", message)


class MainTest(unittest.TestCase):
    """The CLI wired to fake gh and aapt2 runs: exit status and what it prints."""

    LISTING = (
        '[{"isDraft":false,"isPrerelease":false,"publishedAt":"2026-10-25T12:00:00Z",'
        '"tagName":"v0.3.0"},'
        '{"isDraft":false,"isPrerelease":false,"publishedAt":"2026-10-22T10:15:48Z",'
        '"tagName":"v0.2.0"}]')
    ASSETS = ('{"assets":[{"name":"simple-stretch-0.2.0-release.apk"},'
              '{"name":"simple-stretch-0.2.0-release.aab"}]}')

    def _run(self, argv, responses, new_badging):
        """Runs main with each external command answered from `responses`
        (keyed by its first two words) and returns (exit status, stdout)."""
        calls = []

        def fake_run(cmd, **kwargs):
            calls.append(cmd)
            key = " ".join(os.path.basename(part) for part in cmd[:3])
            if cmd[0] == "aapt2":
                apk = cmd[-1]
                text = new_badging if apk.endswith("new.apk") else BADGING_0_2_0
                if text == BADGING_NOT_AN_APK:  # aapt2 exits 1 with the error on stderr
                    return subprocess.CompletedProcess(cmd, 1, "", text)
                return subprocess.CompletedProcess(cmd, 0, text, "")
            if cmd[:3] == ["gh", "release", "download"]:
                answer = responses.get("gh release download", (0, ""))
                if answer[0] == 0:
                    directory = cmd[cmd.index("--dir") + 1]
                    for name in responses.get("downloaded", ["simple-stretch-0.2.0-release.apk"]):
                        open(os.path.join(directory, name), "wb").close()
                return subprocess.CompletedProcess(cmd, answer[0], "", answer[1])
            code, out = responses[key]
            return subprocess.CompletedProcess(cmd, code, out, "" if code == 0 else out)

        stdout = StringIO()
        with mock.patch.object(guard, "_run", side_effect=fake_run), \
                mock.patch.object(guard, "find_aapt2", return_value="aapt2"), \
                redirect_stdout(stdout):
            status = guard.main(argv)
        self.calls = calls
        return status, stdout.getvalue()

    ARGV = ["--repo", "owner/repo", "--exclude-tag", "v0.3.0", "new.apk"]

    def test_passes_when_the_new_code_is_higher(self):
        new = BADGING_0_2_0.replace("versionCode='1' versionName='0.2.0'",
                                    "versionCode='300' versionName='0.3.0'", 1)
        status, out = self._run(self.ARGV, {
            "gh release list": (0, self.LISTING), "gh release view": (0, self.ASSETS)}, new)
        self.assertEqual(0, status, out)
        self.assertIn("v0.2.0", out)
        download = [c for c in self.calls if c[:3] == ["gh", "release", "download"]][0]
        self.assertIn("v0.2.0", download)
        self.assertIn("simple-stretch-*-release.apk", download)

    def test_fails_when_the_new_code_is_not_higher(self):
        new = BADGING_0_2_0.replace("versionName='0.2.0'", "versionName='0.3.0'", 1)
        status, out = self._run(self.ARGV, {
            "gh release list": (0, self.LISTING), "gh release view": (0, self.ASSETS)}, new)
        self.assertEqual(1, status)
        self.assertIn("::error", out)
        self.assertIn("0.3.0", out)

    def test_passes_with_a_notice_when_there_is_no_previous_release(self):
        status, out = self._run(self.ARGV, {
            "gh release list": (0, '[{"isDraft":false,"isPrerelease":false,'
                                   '"publishedAt":"2026-10-25T12:00:00Z","tagName":"v0.3.0"}]')},
            BADGING_0_2_0)
        self.assertEqual(0, status, out)
        self.assertIn("::notice", out)

    def test_fails_when_the_previous_release_has_no_apk(self):
        status, out = self._run(self.ARGV, {
            "gh release list": (0, self.LISTING),
            "gh release view": (0, '{"assets":[{"name":"simple-stretch-0.2.0-release.aab"}]}')},
            BADGING_0_2_0)
        self.assertEqual(1, status)
        self.assertIn("backfill_tag", out)

    def test_fails_when_the_download_fails(self):
        status, out = self._run(self.ARGV, {
            "gh release list": (0, self.LISTING), "gh release view": (0, self.ASSETS),
            "gh release download": (1, "HTTP 502")}, BADGING_0_2_0)
        self.assertEqual(1, status)
        self.assertIn("::error", out)

    def test_fails_when_the_download_does_not_give_exactly_the_one_apk(self):
        status, out = self._run(self.ARGV, {
            "gh release list": (0, self.LISTING), "gh release view": (0, self.ASSETS),
            "downloaded": []}, BADGING_0_2_0)
        self.assertEqual(1, status)
        self.assertIn("::error", out)

    BACKFILL_LISTING = (
        '[{"isDraft":false,"isPrerelease":false,"publishedAt":"2026-10-22T10:15:48Z",'
        '"tagName":"v0.2.0"},'
        '{"isDraft":false,"isPrerelease":false,"publishedAt":"2026-10-11T20:51:07Z",'
        '"tagName":"v0.1.0"},'
        '{"isDraft":false,"isPrerelease":true,"publishedAt":"2026-10-24T09:00:00Z",'
        '"tagName":"v0.3.0-rc.1"}]')
    ASSETS_0_1_0 = '{"assets":[{"name":"simple-stretch-0.1.0-release.apk"}]}'

    def test_a_backfill_of_the_newest_release_runs_the_guard(self):
        # Re-attaching v0.2.0 (code 1) must not bypass the guard: it is compared with v0.1.0.
        status, out = self._run(
            ["--repo", "owner/repo", "--backfill-tag", "v0.2.0", "new.apk"],
            {"gh release list": (0, self.BACKFILL_LISTING),
             "gh release view": (0, self.ASSETS_0_1_0),
             "downloaded": ["simple-stretch-0.1.0-release.apk"]},
            BADGING_0_2_0)
        self.assertEqual(1, status, out)
        self.assertIn("::error", out)
        download = [c for c in self.calls if c[:3] == ["gh", "release", "download"]][0]
        self.assertIn("v0.1.0", download)

    def test_a_backfill_of_the_newest_release_passes_when_higher(self):
        new = BADGING_0_2_0.replace("versionCode='1' versionName='0.2.0'",
                                    "versionCode='200' versionName='0.2.0'", 1)
        status, out = self._run(
            ["--repo", "owner/repo", "--backfill-tag", "v0.2.0", "new.apk"],
            {"gh release list": (0, self.BACKFILL_LISTING),
             "gh release view": (0, self.ASSETS_0_1_0),
             "downloaded": ["simple-stretch-0.1.0-release.apk"]},
            new)
        self.assertEqual(0, status, out)

    def test_a_backfill_of_an_older_release_is_skipped_with_a_notice(self):
        status, out = self._run(
            ["--repo", "owner/repo", "--backfill-tag", "v0.1.0", "new.apk"],
            {"gh release list": (0, self.BACKFILL_LISTING)}, BADGING_0_1_0)
        self.assertEqual(0, status, out)
        self.assertIn("::notice", out)
        self.assertIn("v0.1.0", out)
        self.assertFalse([c for c in self.calls if c[:3] == ["gh", "release", "download"]])

    def test_backfill_and_exclude_tag_are_exclusive(self):
        with self.assertRaises(SystemExit), redirect_stdout(StringIO()), \
                mock.patch("sys.stderr", StringIO()):
            guard.main(["--repo", "o/r", "--backfill-tag", "v0.2.0",
                        "--exclude-tag", "v0.2.0", "new.apk"])

    def test_fails_when_the_release_listing_fails(self):
        status, out = self._run(self.ARGV, {"gh release list": (1, "HTTP 401")}, BADGING_0_2_0)
        self.assertEqual(1, status)
        self.assertIn("::error", out)

    def test_fails_when_aapt2_cannot_read_the_new_apk(self):
        status, out = self._run(self.ARGV, {"gh release list": (0, self.LISTING)},
                                BADGING_NOT_AN_APK)
        self.assertEqual(1, status)
        self.assertIn("::error", out)


if __name__ == "__main__":
    unittest.main()
