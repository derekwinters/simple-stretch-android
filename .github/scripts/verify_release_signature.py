#!/usr/bin/env python3
"""Release gate: every release APK is signed by the one stable release key.

Why this exists. Android only installs an update when the new APK is signed by
the same certificate as the installed one; anything else needs an uninstall,
which deletes the app's schedules, goals and skipped days. This app is
sideloaded, so there is no Play Store to re-sign anything and no key recovery.
`docs/adr/0002-release-signing-with-a-stable-keystore.md` records the decision.

The failure this is really built for is the quiet one. If a keystore input is
mis-wired, the build does not fail: the APK comes out unsigned, or (if someone
reintroduces a fallback) signed with a debug key. A debug-signed APK installs
and runs, and the damage only shows at the next release, on a device. So the
gate runs on each just-built APK before anything is uploaded or attached.

The invariant: a published release APK is signed by exactly one certificate,
and that certificate is not the Android debug certificate. When the
`ANDROID_KEYSTORE_SHA256` environment variable (fed from the optional
repository secret of the same name) is non-empty, the certificate's SHA-256
must also equal it. The comparison ignores case and colons, so both
`keytool`'s `2F:59:...` and `apksigner`'s bare hex are accepted. The APK's
actual digest is always printed; it is public data (it ships inside every APK).

Ported from derekwinters/chores-web-android, where the pin is required; here it
is optional because only the four keystore secrets are known to exist.

apksigner is run as `apksigner verify --print-certs` without `--verbose`. The
parser is tested against output captured with exactly those flags
(`tests/fixtures/`). Without `--verbose` there is no `Number of signers:`
header to cross-check, so the parser fails closed in other ways: every
non-blank line must be one it recognizes, and every signer block must carry
both a DN and a SHA-256 digest.

The decisions are pure functions (`normalize_fingerprint`,
`parse_apksigner_certs`, `assess`) so they unit-test with no Android SDK.
`main` wires them to apksigner and the environment. Standard library only.
"""

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
from collections import namedtuple

SignerFacts = namedtuple("SignerFacts", "label dn sha256")
Verdict = namedtuple("Verdict", "ok reasons")

# The environment variable holding the pinned certificate fingerprint.
EXPECTED_ENV = "ANDROID_KEYSTORE_SHA256"

# Subject of the default Android debug certificate: the signature of the exact
# mis-wiring this gate exists to catch, so it is named rather than reported as
# an anonymous mismatch.
ANDROID_DEBUG_DN_MARKER = "cn=android debug"

_SHA256_HEX_DIGITS = 64

# A signer line. apksigner has printed three label shapes for the same command:
#   "Signer #1 certificate DN: ..."                         (numbered)
#   "Signer (minSdkVersion=33, maxSdkVersion=...) ..."      (per SDK range, with or
#                                                            without a "#N")
#   "V2 Signer: certificate DN: ..."                        (build-tools 35: by scheme)
_SIGNER_LINE = re.compile(
    r"^(?P<label>Signer\s*(?:\([^)]*\)\s*(?:#\d+)?|#\d+)|[A-Za-z0-9.]+\s+Signer):?\s+"
    r"(?P<field>\S.*?):\s*(?P<value>.*)$")

# Lines apksigner prints that carry no signer facts (verbose header, warnings).
_IGNORED_LINE = re.compile(
    r"^(?:Verifies|Verified using .*|Verified for SourceStamp.*|WARNING:.*)$")
_SIGNER_COUNT_LINE = re.compile(r"^Number of signers:\s*(\d+)$")

_FINGERPRINT_LABEL = re.compile(r"^\s*SHA-?256\s*:", re.IGNORECASE)


class MalformedFingerprint(ValueError):
    """A certificate fingerprint that is not 32 hex-encoded bytes."""


class MalformedApksignerOutput(ValueError):
    """apksigner output this parser cannot read with confidence."""


def _with_raw_output(summary, raw_output):
    """An error message that carries the verbatim apksigner output, so a format
    change is diagnosable from the one log it produces."""
    if not raw_output.endswith("\n"):
        raw_output += "\n"
    return (
        "{0}\n----- apksigner output begin -----\n{1}"
        "----- apksigner output end -----".format(summary, raw_output))


# --- Pure decisions ---------------------------------------------------------


def normalize_fingerprint(text):
    """A SHA-256 certificate fingerprint as 64 lowercase hex digits.

    Accepts keytool's `SHA256: 2F:59:...` and apksigner's bare lowercase hex:
    the same 32 bytes written two ways.
    """
    if text is None:
        raise MalformedFingerprint("no fingerprint given")
    bare = re.sub(r"[\s:]", "", _FINGERPRINT_LABEL.sub("", text)).lower()
    if not bare:
        raise MalformedFingerprint("no fingerprint given")
    if len(bare) != _SHA256_HEX_DIGITS:
        raise MalformedFingerprint(
            "expected {0} hex digits for a SHA-256 fingerprint, got {1}".format(
                _SHA256_HEX_DIGITS, len(bare)))
    if not re.fullmatch(r"[0-9a-f]+", bare):
        raise MalformedFingerprint("fingerprint is not hexadecimal")
    return bare


def parse_apksigner_certs(text):
    """The signer blocks in `apksigner verify --print-certs` output, in order.

    Raises `MalformedApksignerOutput` (with the raw output attached) when:
    there are no signer blocks; a non-blank line is not one this parser
    recognizes; a block lacks its DN or SHA-256 digest; one block reports two
    different certificates; or a `Number of signers:` header (verbose mode
    only) disagrees with the distinct certificates found.
    """
    blocks = {}
    order = []
    declared = None

    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or _IGNORED_LINE.match(line):
            continue

        count = _SIGNER_COUNT_LINE.match(line)
        if count:
            declared = int(count.group(1))
            continue

        match = _SIGNER_LINE.match(line)
        if not match:
            raise MalformedApksignerOutput(_with_raw_output(
                "unrecognized apksigner output line {0!r}; the format this gate "
                "reads has changed, so its verdict cannot be trusted".format(line), text))

        label = re.sub(r"\s+", " ", match.group("label")).strip()
        field = match.group("field").strip()
        value = match.group("value").strip()
        if label not in blocks:
            blocks[label] = {"dn": "", "sha256": ""}
            order.append(label)
        block = blocks[label]

        if field == "certificate DN":
            block["dn"] = value
        elif field == "certificate SHA-256 digest":
            try:
                digest = normalize_fingerprint(value)
            except MalformedFingerprint as exc:
                raise MalformedApksignerOutput(_with_raw_output(
                    "{0}: unreadable SHA-256 digest ({1})".format(label, exc), text))
            if block["sha256"] and block["sha256"] != digest:
                raise MalformedApksignerOutput(_with_raw_output(
                    "{0} is reported with two different certificates".format(label), text))
            block["sha256"] = digest
        # Other fields (SHA-1, MD5, key algorithm, public key ...) are not needed.

    if not order:
        raise MalformedApksignerOutput(_with_raw_output(
            "apksigner output has no signer blocks", text))

    for label in order:
        if not blocks[label]["dn"] or not blocks[label]["sha256"]:
            raise MalformedApksignerOutput(_with_raw_output(
                "{0} is missing its certificate DN or SHA-256 digest".format(label), text))

    signers = [
        SignerFacts(label, blocks[label]["dn"], blocks[label]["sha256"]) for label in order]

    if declared is not None and declared != len({s.sha256 for s in signers}):
        raise MalformedApksignerOutput(_with_raw_output(
            "apksigner declared {0} signer(s) but {1} distinct certificate(s) were "
            "parsed".format(declared, len({s.sha256 for s in signers})), text))

    return signers


def assess(signers, expected_sha256=None):
    """Whether these signer blocks are exactly one non-debug release certificate,
    and, when `expected_sha256` is given, exactly the pinned one.

    Blocks are counted by distinct certificate: apksigner may print one key's
    block once per signature scheme (`V2 Signer:`, `V3 Signer:`), and that is
    still one signing identity.
    """
    expected = None if expected_sha256 is None else normalize_fingerprint(expected_sha256)

    if not signers:
        return Verdict(False, [
            "the APK is unsigned; a release APK must carry the release certificate"])

    reasons = []
    distinct = []
    for signer in signers:
        if signer.sha256 not in [s.sha256 for s in distinct]:
            distinct.append(signer)

    if len(distinct) != 1:
        reasons.append(
            "the APK has {0} signing certificates; a release APK is signed by the "
            "release key alone".format(len(distinct)))

    for signer in distinct:
        if ANDROID_DEBUG_DN_MARKER in signer.dn.lower():
            reasons.append(
                "{0} is the Android debug certificate ({1}): the build fell back to "
                "debug signing, so this APK can never be installed over a release "
                "build. The keystore inputs did not reach Gradle.".format(
                    signer.label, signer.dn))
        elif expected is not None and signer.sha256 != expected:
            reasons.append(
                "{0} certificate is {1}, expected the release certificate {2} "
                "(DN: {3})".format(signer.label, signer.sha256, expected, signer.dn))

    return Verdict(not reasons, reasons)


# --- Wiring -----------------------------------------------------------------


def _version_key(path):
    version = os.path.basename(os.path.dirname(path))
    return tuple(int(part) if part.isdigit() else 0 for part in re.split(r"[.-]", version))


def find_apksigner():
    """apksigner from PATH, else the newest build-tools copy in the Android SDK."""
    on_path = shutil.which("apksigner")
    if on_path:
        return on_path
    for root in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if not root:
            continue
        candidates = glob.glob(os.path.join(root, "build-tools", "*", "apksigner"))
        if candidates:
            return max(candidates, key=_version_key)
    raise OSError(
        "apksigner not found on PATH or under ANDROID_HOME/ANDROID_SDK_ROOT build-tools")


def print_certs(apk, apksigner=None):
    """`apksigner verify --print-certs` output for one APK.

    No `--verbose`: the tests' captured fixture uses exactly these flags. A
    nonzero exit (unsigned or invalid APK) is a failure, never an empty parse.
    """
    tool = apksigner or find_apksigner()
    result = subprocess.run(
        [tool, "verify", "--print-certs", apk],
        capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise MalformedApksignerOutput(
            "apksigner could not verify {0} (exit {1}): {2}".format(
                os.path.basename(apk), result.returncode,
                (result.stderr or result.stdout).strip()))
    return result.stdout


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("apk", nargs="+", help="release APK(s) to check")
    args = parser.parse_args(argv)

    pin = (os.environ.get(EXPECTED_ENV) or "").strip()
    expected = None
    if pin:
        try:
            expected = normalize_fingerprint(pin)
        except MalformedFingerprint as exc:
            print("::error title=Release signature::{0} is not a usable SHA-256 certificate "
                  "fingerprint: {1}".format(EXPECTED_ENV, exc))
            return 1
    else:
        print("::notice title=Release signature::{0} is not set, so the certificate is "
              "checked for being a single non-debug signer but not compared against a "
              "pin.".format(EXPECTED_ENV))

    failed = False
    for apk in args.apk:
        name = os.path.basename(apk)
        try:
            signers = parse_apksigner_certs(print_certs(apk))
        except (MalformedApksignerOutput, OSError) as exc:
            print("::error title=Release signature::{0}: {1}".format(name, exc))
            failed = True
            continue

        for signer in signers:
            print("{0}: {1} certificate SHA-256 {2}\n  DN: {3}".format(
                name, signer.label, signer.sha256, signer.dn))

        verdict = assess(signers, expected)
        if verdict.ok:
            print("OK: {0} is signed by {1}.".format(
                name, "the pinned release certificate" if expected else
                "a single non-debug certificate"))
            continue
        failed = True
        for reason in verdict.reasons:
            print("::error title=Release signature::{0}: {1}".format(name, reason))

    if failed:
        print(
            "\nA release APK is not signed by the release certificate; refusing to "
            "publish it. Installing it would force an uninstall (and the loss of the "
            "app's data) on the next update. See "
            "docs/adr/0002-release-signing-with-a-stable-keystore.md.")
        return 1

    print("\nOK: every release APK carries the release certificate.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
