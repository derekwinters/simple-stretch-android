#!/usr/bin/env python3
"""Store listing: compile the Google Play listing text and check it against the spec.

Why this exists. The Play listing promises what the app does, and the specification
(`docs/spec/`) says what it does. The listing is hand-written in `docs/store/listing.md`, one
claim per line, each claim ending with the requirement IDs it rests on; this script turns it into
fastlane's text files and fails when the two have drifted apart (docs/spec/store-listing.md,
STORE-LIST-001 to STORE-LIST-007).

What it does.

- Without `--check` it compiles `docs/store/listing.md` into
  `fastlane/metadata/android/en-US/title.txt`, `short_description.txt` and
  `full_description.txt`, with the ID tags stripped. It writes nothing when any check other than
  the comparison with the committed files fails. Run it after editing the listing and commit the
  three files with it.
- With `--check` (CI, on every pull request and at the release tag) it writes nothing and fails
  when:
  - a spec page (every `docs/spec/*.md` except `README.md`) does not start with the marker line
    `<!-- store: headline -->` or `<!-- store: omit -->`;
  - a tag cites an ID that no spec page defines, or one the spec marks `_Retired._`;
  - a `headline` page has no claim citing any of the IDs it defines;
  - a compiled file is over Google Play's limit (title 30, short description 80, full
    description 4,000 characters);
  - a committed file is missing or differs from what this script would write.

The listing's format. Three `##` sections, exactly these and in this order: `## Title`,
`## Short description`, `## Full description`. Anything before `## Title` is the page's own
explanation and is not compiled. The title and the short description are one line each. Every
non-blank line of the full description is a claim: its text (a paragraph line, or a `- ` list
item, kept as written) followed by one tag, `<!-- ID, ID -->`, at the very end of the line. The
title and the short description may carry a tag too. Blank lines separate paragraphs; repeated
ones collapse into one. Each compiled file ends with one newline, which the limits do not count.

The decisions are pure functions (`parse_listing`, `compile_listing`, `parse_spec_page`) so they
unit-test without a repository; `check` and `main` wire them to the files. Standard library only.
"""

import argparse
import glob
import os
import re
import sys
from collections import namedtuple

LISTING_PATH = "docs/store/listing.md"
SPEC_DIR = "docs/spec"
# The spec's index page: it defines no requirements and needs no marker.
SPEC_INDEX = "README.md"
OUTPUT_DIR = "fastlane/metadata/android/en-US"
TITLE_FILE = OUTPUT_DIR + "/title.txt"
SHORT_FILE = OUTPUT_DIR + "/short_description.txt"
FULL_FILE = OUTPUT_DIR + "/full_description.txt"

# Google Play's limits, in characters.
LIMITS = {TITLE_FILE: 30, SHORT_FILE: 80, FULL_FILE: 4000}

SECTIONS = ("Title", "Short description", "Full description")
MARKERS = ("headline", "omit")

_TITLE = "Store listing"
_REGENERATE = "run `python3 .github/scripts/store_listing.py` and commit the result"

_ID = r"[A-Z]+(?:-[A-Z]+)*-[0-9]{3}"
_MARKER_LINE = re.compile(r"^<!-- store: ({0}) -->$".format("|".join(MARKERS)))
# A requirement is defined by a top-level list item that opens with its bold ID.
_DEFINITION = re.compile(r"^- \*\*({0})\*\*(.*)$".format(_ID))
_RETIRED = re.compile(r"^\s*_Retired\._")
_TAG_AT_END = re.compile(r"^(.*?)\s*<!--(.*?)-->\s*$")
_TAG_IDS = re.compile(r"^\s*{0}(?:\s*,\s*{0})*\s*$".format(_ID))
_HEADING = re.compile(r"^## (.*?)\s*$")

Claim = namedtuple("Claim", "line text ids")
Listing = namedtuple("Listing", "title short_description claims full_lines")
SpecPage = namedtuple("SpecPage", "marker live_ids retired_ids")


class ListingError(Exception):
    """The listing cannot be read in the format this script compiles."""


# --- Pure decisions ---------------------------------------------------------


def _split_tag(line, number, required):
    """(text, ids) for one listing line. Raises `ListingError` on a malformed tag, a stray
    comment, or (when `required`) a line with no tag."""
    match = _TAG_AT_END.match(line)
    if match is None:
        if "<!--" in line or "-->" in line:
            raise ListingError(
                "{0} line {1}: a tag must be one `<!-- ID, ID -->` at the end of the line: "
                "{2!r}".format(LISTING_PATH, number, line))
        if required:
            raise ListingError(
                "{0} line {1}: every full description line is a claim and must end with the "
                "requirement IDs it rests on, as `<!-- ID, ID -->`: {2!r}".format(
                    LISTING_PATH, number, line))
        return line.rstrip(), []
    text, inside = match.group(1), match.group(2)
    if "<!--" in text or "-->" in text or not _TAG_IDS.match(inside):
        raise ListingError(
            "{0} line {1}: a tag must be one `<!-- ID, ID -->` at the end of the line, holding "
            "only requirement IDs: {2!r}".format(LISTING_PATH, number, line))
    if not text.strip():
        raise ListingError("{0} line {1}: a tag with no claim before it".format(
            LISTING_PATH, number))
    return text.rstrip(), [part.strip() for part in inside.split(",")]


def parse_listing(text):
    """The `Listing` in `docs/store/listing.md`'s text. Raises `ListingError` unless it has
    exactly the three sections in order, a one-line title and short description, and a tag at
    the end of every full description line."""
    sections = {}
    order = []
    current = None
    for number, line in enumerate(text.splitlines(), start=1):
        heading = _HEADING.match(line)
        if heading:
            name = heading.group(1)
            if name not in SECTIONS:
                raise ListingError("{0} line {1}: unexpected section {2!r}; the sections are "
                                   "{3}".format(LISTING_PATH, number, line,
                                                ", ".join("## " + s for s in SECTIONS)))
            if name in sections:
                raise ListingError("{0} line {1}: section {2!r} appears twice".format(
                    LISTING_PATH, number, line))
            sections[name] = []
            order.append(name)
            current = name
        elif current is not None:
            sections[current].append((number, line))
    if tuple(order) != SECTIONS:
        raise ListingError("{0}: expected the sections {1} in that order, found {2}".format(
            LISTING_PATH, ", ".join("## " + s for s in SECTIONS),
            ", ".join("## " + s for s in order) or "none"))

    claims = []

    def one_line(name):
        lines = [(number, line) for number, line in sections[name] if line.strip()]
        if len(lines) != 1:
            raise ListingError("{0}: `## {1}` must hold exactly one line, found {2}".format(
                LISTING_PATH, name, len(lines)))
        number, line = lines[0]
        text_part, ids = _split_tag(line.strip(), number, required=False)
        if ids:
            claims.append(Claim(number, text_part, ids))
        return text_part

    title = one_line("Title")
    short = one_line("Short description")

    full_lines = []
    for number, line in sections["Full description"]:
        if not line.strip():
            if full_lines and full_lines[-1] != "":
                full_lines.append("")
            continue
        text_part, ids = _split_tag(line, number, required=True)
        claims.append(Claim(number, text_part, ids))
        full_lines.append(text_part)
    while full_lines and full_lines[-1] == "":
        full_lines.pop()
    if not full_lines:
        raise ListingError("{0}: `## Full description` is empty".format(LISTING_PATH))
    return Listing(title, short, claims, full_lines)


def compile_listing(listing):
    """{repository-relative path: content} of the three fastlane files."""
    return {
        TITLE_FILE: listing.title + "\n",
        SHORT_FILE: listing.short_description + "\n",
        FULL_FILE: "\n".join(listing.full_lines) + "\n",
    }


def parse_spec_page(text):
    """The `SpecPage` in one spec page's text: its store marker (None without a valid one on the
    first line) and the IDs it defines, live and retired."""
    lines = text.splitlines()
    marker = None
    if lines:
        match = _MARKER_LINE.match(lines[0])
        if match:
            marker = match.group(1)
    live, retired = set(), set()
    for line in lines:
        match = _DEFINITION.match(line)
        if match:
            (retired if _RETIRED.match(match.group(2)) else live).add(match.group(1))
    return SpecPage(marker, live, retired - live)


def limit_problems(files):
    """One problem per compiled file over Google Play's character limit."""
    problems = []
    for path, limit in sorted(LIMITS.items()):
        length = len(files[path].rstrip("\n"))
        if length > limit:
            problems.append("{0} would be {1} characters; Google Play allows at most {2} "
                            "(STORE-LIST-005)".format(path, length, limit))
    return problems


def spec_problems(pages, claims):
    """Problems between the spec pages ({name: SpecPage}) and the listing's claims."""
    problems = []
    live, retired = {}, {}
    for name, page in sorted(pages.items()):
        if page.marker is None:
            problems.append(
                "docs/spec/{0} has no store marker: its first line must be "
                "`<!-- store: headline -->` or `<!-- store: omit -->` (STORE-LIST-002)".format(name))
        for requirement in page.live_ids:
            live[requirement] = name
        for requirement in page.retired_ids:
            retired[requirement] = name
    for claim in claims:
        for requirement in claim.ids:
            if requirement in live:
                continue
            if requirement in retired:
                problems.append(
                    "{0} line {1} cites {2}, which docs/spec/{3} marks retired "
                    "(STORE-LIST-003)".format(LISTING_PATH, claim.line, requirement,
                                              retired[requirement]))
            else:
                problems.append(
                    "{0} line {1} cites {2}, which no page in docs/spec/ defines "
                    "(STORE-LIST-003)".format(LISTING_PATH, claim.line, requirement))
    cited = {requirement for claim in claims for requirement in claim.ids}
    for name, page in sorted(pages.items()):
        if page.marker == "headline" and not (page.live_ids & cited):
            problems.append(
                "docs/spec/{0} is a headline page, but no claim in {1} cites any of its IDs "
                "(STORE-LIST-004)".format(name, LISTING_PATH))
    return problems


# --- Wiring -----------------------------------------------------------------


def _read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def _spec_pages(root):
    pages = {}
    for path in sorted(glob.glob(os.path.join(root, SPEC_DIR, "*.md"))):
        name = os.path.basename(path)
        if name != SPEC_INDEX:
            pages[name] = parse_spec_page(_read(path))
    return pages


def _compile(root):
    """(files, problems): the compiled files (None when the listing cannot be read) and every
    problem other than a difference from the committed files."""
    path = os.path.join(root, LISTING_PATH)
    if not os.path.isfile(path):
        return None, ["{0} is missing (STORE-LIST-001)".format(LISTING_PATH)]
    try:
        listing = parse_listing(_read(path))
    except ListingError as exc:
        return None, ["{0} (STORE-LIST-001)".format(exc)]
    files = compile_listing(listing)
    return files, spec_problems(_spec_pages(root), listing.claims) + limit_problems(files)


def check(root):
    """Every problem with the repository at `root`, as messages; empty when it is in step."""
    files, problems = _compile(root)
    if files is None:
        return problems
    for path, content in sorted(files.items()):
        full = os.path.join(root, path)
        if not os.path.isfile(full):
            problems.append("{0} is missing; {1} (STORE-LIST-006)".format(path, _REGENERATE))
        elif _read(full) != content:
            problems.append("{0} differs from what {1} compiles to; {2} (STORE-LIST-006)".format(
                path, LISTING_PATH, _REGENERATE))
    return problems


def write(root):
    """Compiles and writes the three files; returns the problems that stopped it, if any."""
    files, problems = _compile(root)
    if problems:
        return problems
    for path, content in sorted(files.items()):
        full = os.path.join(root, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(content)
    return []


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="write nothing; fail when the listing, the spec and the committed "
                             "files are not in step (what CI runs)")
    parser.add_argument("--root", default=os.getcwd(),
                        help="the repository root (default: the current directory)")
    args = parser.parse_args(argv)

    problems = check(args.root) if args.check else write(args.root)
    for problem in problems:
        print("::error title={0}::{1}".format(_TITLE, problem))
    if problems:
        return 1
    if args.check:
        print("OK: {0}, docs/spec/ and {1} are in step.".format(LISTING_PATH, OUTPUT_DIR))
    else:
        print("Wrote {0}.".format(", ".join(sorted(LIMITS))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
