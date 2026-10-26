"""Unit tests for the store listing compiler and drift check (docs/spec/store-listing.md).

The Google Play listing text is hand-written in `docs/store/listing.md`, one claim per line, each
claim ending with the requirement IDs it rests on. `store_listing.py` compiles it into fastlane's
`title.txt`, `short_description.txt` and `full_description.txt`, and its `--check` mode fails
when the text and the specification have drifted apart. These tests build small repositories in
a temporary directory; they need no network and nothing from the real docs.
"""

import os
import shutil
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from io import StringIO

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import store_listing  # noqa: E402

HEADLINE = "<!-- store: headline -->\n\n"
OMIT = "<!-- store: omit -->\n\n"

GOALS_PAGE = HEADLINE + """# Goals

- **GOAL-001** A goal says how many times a day the user wants to do one stretch.
- **GOAL-002** _Retired._ It described something that was removed.
- **GOAL-003** Progress is counted from the completion log (GOAL-001).

## Build checklist

- [x] Store listing reviewed.
"""

SCREENS_PAGE = OMIT + """# Screens

- **HOME-006** A "Today's goals" card shows each goal's progress today.
"""

LISTING = """# Store listing text

Explanation for the reader, ignored by the compiler.

## Title

Simple Stretch

## Short description

Stretch reminders on your own schedule.

## Full description

Simple Stretch reminds you to stretch. <!-- GOAL-001 -->

- Set a daily goal for each stretch. <!-- GOAL-001, HOME-006 -->
- Watch today's progress. <!--GOAL-003-->
"""

COMPILED_FULL = (
    "Simple Stretch reminds you to stretch.\n"
    "\n"
    "- Set a daily goal for each stretch.\n"
    "- Watch today's progress.\n")


class _Repo(unittest.TestCase):
    """A throwaway repository root with a spec, a listing and (optionally) compiled files."""

    def setUp(self):
        self.root = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.root)
        self.write("docs/spec/README.md", "# Specification\n\nNo marker: the index is exempt.\n")
        self.write("docs/spec/goals.md", GOALS_PAGE)
        self.write("docs/spec/screens.md", SCREENS_PAGE)
        self.write("docs/store/listing.md", LISTING)

    def write(self, relative, text):
        path = os.path.join(self.root, relative)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(text)

    def read(self, relative):
        with open(os.path.join(self.root, relative), encoding="utf-8") as handle:
            return handle.read()

    def problems(self):
        return store_listing.check(self.root)

    def assertProblem(self, fragment):
        problems = self.problems()
        self.assertTrue(any(fragment in problem for problem in problems),
                        "no problem mentioning {0!r} in {1!r}".format(fragment, problems))

    def run_main(self, *args):
        out = StringIO()
        with redirect_stdout(out):
            code = store_listing.main(["--root", self.root] + list(args))
        return code, out.getvalue()


class ParseListingTest(unittest.TestCase):
    # STORE-LIST-001: the three sections, one claim per line, the tag stripped.

    def test_reads_the_title_short_description_and_claims(self):
        listing = store_listing.parse_listing(LISTING)
        self.assertEqual("Simple Stretch", listing.title)
        self.assertEqual("Stretch reminders on your own schedule.", listing.short_description)
        self.assertEqual(
            [("Simple Stretch reminds you to stretch.", ["GOAL-001"]),
             ("- Set a daily goal for each stretch.", ["GOAL-001", "HOME-006"]),
             ("- Watch today's progress.", ["GOAL-003"])],
            [(claim.text, claim.ids) for claim in listing.claims])

    def test_compiles_to_the_three_fastlane_files_without_tags(self):
        files = store_listing.compile_listing(store_listing.parse_listing(LISTING))
        self.assertEqual({
            "fastlane/metadata/android/en-US/title.txt": "Simple Stretch\n",
            "fastlane/metadata/android/en-US/short_description.txt":
                "Stretch reminders on your own schedule.\n",
            "fastlane/metadata/android/en-US/full_description.txt": COMPILED_FULL,
        }, files)

    def test_collapses_repeated_and_edge_blank_lines(self):
        text = LISTING.replace("## Full description\n\n", "## Full description\n\n\n\n") \
            .replace("<!-- GOAL-001 -->\n\n", "<!-- GOAL-001 -->\n\n\n\n") + "\n\n"
        files = store_listing.compile_listing(store_listing.parse_listing(text))
        self.assertEqual(COMPILED_FULL,
                         files["fastlane/metadata/android/en-US/full_description.txt"])

    def test_a_full_description_line_without_ids_is_refused(self):
        text = LISTING + "- A claim nobody can trace.\n"
        with self.assertRaises(store_listing.ListingError) as caught:
            store_listing.parse_listing(text)
        self.assertIn("A claim nobody can trace.", str(caught.exception))

    def test_a_tag_that_is_not_a_list_of_ids_is_refused(self):
        text = LISTING + "- Something. <!-- see goals -->\n"
        with self.assertRaises(store_listing.ListingError):
            store_listing.parse_listing(text)

    def test_a_tag_in_the_middle_of_a_line_is_refused(self):
        text = LISTING + "- Some <!-- GOAL-001 --> thing.\n"
        with self.assertRaises(store_listing.ListingError):
            store_listing.parse_listing(text)

    def test_missing_or_out_of_order_sections_are_refused(self):
        for broken in (
                LISTING.replace("## Title\n\nSimple Stretch\n\n", ""),
                LISTING.replace("## Short description", "## Short blurb"),
                LISTING.replace("## Title", "## Swap").replace("## Short description", "## Title")
                .replace("## Swap", "## Short description"),
                LISTING + "\n## Full description\n\n- Again. <!-- GOAL-001 -->\n"):
            with self.subTest(broken=broken):
                with self.assertRaises(store_listing.ListingError):
                    store_listing.parse_listing(broken)

    def test_the_title_and_short_description_are_one_line_each(self):
        for broken in (LISTING.replace("Simple Stretch\n", "Simple\nStretch\n", 1),
                       LISTING.replace("Simple Stretch\n", "", 1)):
            with self.subTest(broken=broken):
                with self.assertRaises(store_listing.ListingError):
                    store_listing.parse_listing(broken)


class ParseSpecPageTest(unittest.TestCase):
    # STORE-LIST-002: the marker. STORE-LIST-003: defined and retired IDs.

    def test_reads_the_marker_and_the_defined_ids(self):
        page = store_listing.parse_spec_page(GOALS_PAGE)
        self.assertEqual("headline", page.marker)
        self.assertEqual({"GOAL-001", "GOAL-003"}, page.live_ids)
        self.assertEqual({"GOAL-002"}, page.retired_ids)

    def test_an_id_mentioned_in_prose_is_not_defined(self):
        page = store_listing.parse_spec_page(OMIT + "# X\n\nSee SESS-001 and **SESS-002**.\n")
        self.assertEqual(set(), page.live_ids)

    def test_reads_ids_with_a_two_part_prefix(self):
        page = store_listing.parse_spec_page(OMIT + "# X\n\n- **STORE-SHOT-001** Home.\n")
        self.assertEqual({"STORE-SHOT-001"}, page.live_ids)

    def test_a_page_without_a_marker_on_its_first_line_has_none(self):
        for text in ("# Goals\n\n- **GOAL-001** A goal.\n",
                     "# Goals\n<!-- store: headline -->\n",
                     "<!-- store: maybe -->\n\n# Goals\n",
                     "<!--store:headline-->\n\n# Goals\n"):
            with self.subTest(text=text):
                self.assertIsNone(store_listing.parse_spec_page(text).marker)


class CheckTest(_Repo):

    def test_a_consistent_repository_has_no_problems_once_compiled(self):
        # STORE-LIST-001, STORE-LIST-006
        self.assertEqual(0, self.run_main()[0])
        self.assertEqual([], self.problems())
        self.assertEqual(COMPILED_FULL,
                         self.read("fastlane/metadata/android/en-US/full_description.txt"))

    def test_a_claim_citing_an_unknown_id_fails(self):
        # STORE-LIST-003
        self.write("docs/store/listing.md", LISTING + "- Trends. <!-- TREND-001 -->\n")
        self.assertProblem("TREND-001")

    def test_a_claim_citing_a_retired_id_fails(self):
        # STORE-LIST-003
        self.write("docs/store/listing.md", LISTING + "- Gone. <!-- GOAL-002 -->\n")
        self.assertProblem("GOAL-002")

    def test_an_unknown_id_in_the_short_description_fails_too(self):
        # STORE-LIST-003
        self.write("docs/store/listing.md", LISTING.replace(
            "own schedule.", "own schedule. <!-- SCHED-999 -->"))
        self.assertProblem("SCHED-999")

    def test_a_headline_page_without_a_claim_fails(self):
        # STORE-LIST-004
        self.write("docs/spec/trends.md",
                   HEADLINE + "# Trends\n\n- **TREND-001** A day is judged by its goals.\n")
        self.assertProblem("trends.md")

    def test_a_claim_citing_only_an_omitted_page_does_not_cover_a_headline_page(self):
        # STORE-LIST-004
        self.write("docs/store/listing.md", LISTING.replace(
            "<!-- GOAL-001 -->", "<!-- HOME-006 -->").replace(
            "<!-- GOAL-001, HOME-006 -->", "<!-- HOME-006 -->").replace(
            "<!--GOAL-003-->", "<!-- HOME-006 -->"))
        self.assertProblem("goals.md")

    def test_an_omitted_page_needs_no_claim(self):
        # STORE-LIST-004
        self.write("docs/spec/privacy.md", OMIT + "# Privacy\n\n- **PRIV-001** No internet.\n")
        self.run_main()
        self.assertEqual([], self.problems())

    def test_a_spec_page_without_a_marker_fails(self):
        # STORE-LIST-002
        self.write("docs/spec/library.md", "# Stretch library\n\n- **LIB-001** Stretches.\n")
        self.assertProblem("library.md")

    def test_the_spec_readme_needs_no_marker(self):
        # STORE-LIST-002
        self.run_main()
        self.assertFalse(any("README.md" in problem for problem in self.problems()))

    def test_a_title_over_30_characters_fails(self):
        # STORE-LIST-005
        self.write("docs/store/listing.md", LISTING.replace("Simple Stretch\n", "S" * 31 + "\n", 1))
        self.assertProblem("title.txt")

    def test_a_title_of_exactly_30_characters_passes(self):
        # STORE-LIST-005
        self.write("docs/store/listing.md", LISTING.replace("Simple Stretch\n", "S" * 30 + "\n", 1))
        self.run_main()
        self.assertEqual([], self.problems())

    def test_a_short_description_over_80_characters_fails(self):
        # STORE-LIST-005
        self.write("docs/store/listing.md", LISTING.replace(
            "Stretch reminders on your own schedule.", "x" * 81))
        self.assertProblem("short_description.txt")

    def test_a_full_description_over_4000_characters_fails(self):
        # STORE-LIST-005
        self.write("docs/store/listing.md",
                   LISTING + "- {0} <!-- GOAL-001 -->\n".format("x" * 4000))
        self.assertProblem("full_description.txt")

    def test_limits_count_characters_not_bytes(self):
        # STORE-LIST-005: "−" and "…" are one character each, three bytes in UTF-8.
        self.write("docs/store/listing.md",
                   LISTING.replace("Simple Stretch\n", "−" * 30 + "\n", 1))
        self.run_main()
        self.assertEqual([], self.problems())

    def test_a_hand_edited_compiled_file_fails(self):
        # STORE-LIST-006
        self.run_main()
        self.write("fastlane/metadata/android/en-US/short_description.txt", "Edited by hand.\n")
        self.assertProblem("short_description.txt")

    def test_a_missing_compiled_file_fails(self):
        # STORE-LIST-006
        self.run_main()
        os.remove(os.path.join(self.root, "fastlane/metadata/android/en-US/title.txt"))
        self.assertProblem("title.txt")

    def test_a_listing_edited_without_recompiling_fails(self):
        # STORE-LIST-006
        self.run_main()
        self.write("docs/store/listing.md", LISTING.replace("Watch", "See"))
        self.assertProblem("full_description.txt")

    def test_a_missing_listing_fails(self):
        # STORE-LIST-001
        os.remove(os.path.join(self.root, "docs/store/listing.md"))
        self.assertProblem("listing.md")


class MainTest(_Repo):

    def test_check_mode_passes_on_a_compiled_repository(self):
        # STORE-LIST-006
        self.run_main()
        code, out = self.run_main("--check")
        self.assertEqual(0, code)
        self.assertIn("OK", out)

    def test_check_mode_fails_with_workflow_errors_and_writes_nothing(self):
        # STORE-LIST-006, STORE-LIST-007: --check never writes listing text.
        code, out = self.run_main("--check")
        self.assertEqual(1, code)
        self.assertIn("::error title=Store listing::", out)
        self.assertFalse(os.path.exists(os.path.join(self.root, "fastlane")))

    def test_compiling_refuses_to_write_when_the_listing_is_inconsistent(self):
        # STORE-LIST-003
        self.write("docs/store/listing.md", LISTING + "- Trends. <!-- TREND-001 -->\n")
        code, out = self.run_main()
        self.assertEqual(1, code)
        self.assertIn("TREND-001", out)
        self.assertFalse(os.path.exists(os.path.join(self.root, "fastlane")))

    def test_compiling_leaves_other_fastlane_files_alone(self):
        # STORE-LIST-001: the screenshots are not the compiler's.
        self.write("fastlane/metadata/android/en-US/images/phoneScreenshots/1_home.png", "png")
        self.run_main()
        self.assertEqual(
            "png", self.read("fastlane/metadata/android/en-US/images/phoneScreenshots/1_home.png"))


class RealRepositoryTest(unittest.TestCase):
    # STORE-LIST-002 to STORE-LIST-006 on this repository's own docs and compiled files.

    def test_this_repository_is_in_step(self):
        root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
            os.path.abspath(__file__)))))
        self.assertEqual([], store_listing.check(root))


if __name__ == "__main__":
    unittest.main()
