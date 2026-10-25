#!/usr/bin/env python3
"""Release gate: every release APK and AAB is signed by the one stable release key.

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

The release Android App Bundle (`.aab`, the upload format for Google Play) is
held to the same invariant by the same `assess`. An AAB carries only a JAR (v1)
signature, which apksigner does not read, so it goes through the JDK instead,
chosen by the `.aab` file extension (everything else is treated as an APK):

- `jarsigner -verify -strict` checks integrity. Its exit code is a bit set;
  bit 4 is set for every signed AAB, because an Android signing key is a
  self-signed certificate that chains to no CA, so 0 and 4 pass and any other
  bit (1 a broken signature, 16 unsigned entries, ...) fails, as does output
  saying "jar is unsigned" or lacking "jar verified". Bit 4 also covers an
  expired or not-yet-valid signer certificate, so the bit alone proves
  nothing: every line jarsigner prints under its `Error:` and `Warning:`
  headings must be on this allowlist, taken from a real capture of a correctly
  signed jar (`tests/fixtures/jarsigner-verify-strict-signed.txt`), and on
  exit 4 the self-signed line must be present:

    This jar contains entries whose signer certificate is self-signed.
    This jar contains entries whose certificate chain is invalid. Reason: PKIX
      path building failed: sun.security.provider.certpath.SunCertPathBuilderException:
      unable to find valid certification path to requested target
      (one line; the untrusted chain of a self-signed key, and only that reason)
    This jar contains signatures that do not include a timestamp. Without a
      timestamp, users may not be able to validate this jar after any of the
      signer certificates expire (as early as YYYY-MM-DD).
      (one line; Android signing never timestamps)
    POSIX file permission and/or symlink attributes detected. These
      attributes are ignored when signing and are not protected by the
      signature.
      (one line; seen on the real AGP-built AAB in CI, JDK 17.0.20:
      `tests/fixtures/jarsigner-verify-strict-aab-ci.txt`)

  The same CI capture adds a section headed "This JAR file contains internal
  inconsistencies that may result in different contents when reading via
  JarFile and JarInputStream:". bundletool writes META-INF/MANIFEST.MF after
  the content entries, so the streaming JarInputStream reader cannot see the
  manifest, while JarFile (the central directory, which verification uses)
  finds every entry signed. Under that header, up to the next blank line,
  only "- Manifest is missing when reading via JarInputStream" and
  "- Entry <name> is signed in JarFile but is not signed in JarInputStream"
  are accepted. The reverse direction (not signed, or missing, in JarFile)
  would be content outside the verified signature and fails, as does any
  other line in the section.

  Any other line fails closed, and a line saying a certificate has expired or
  is not yet valid fails by name.
- `keytool -printcert -jarfile` lists the signer certificates, read by
  `parse_keytool_jarfile_certs`. The first certificate after each `Signer #N:`
  is the signer's own; any further ones are its chain. Every line must be one
  the parser recognizes, except inside an `Extensions:` block: X.509 extension
  dumps are free-form (hex, nested brackets), so the parser recognizes the
  block's structure instead, skipping it up to the next `Signer #N:` or
  `Certificate #N:` header, and never reads a DN or digest from it. keytool's
  own "Not a signed jar file" is the only way to get an empty signer list,
  which `assess` then rejects as unsigned. The tests use output captured with
  exactly these flags (`tests/fixtures/README.md`). As a backstop to
  jarsigner, the signer certificate's `Valid from: ... until: ...` line is
  parsed and `assess` fails a certificate whose window does not contain the
  current time. The dates are Java's `Date.toString()` form (`Sun Oct 25
  10:46:58 UTC 2026`), read with a fixed English month table rather than
  `strptime`, whose `%a`/`%b` follow the locale and whose `%Z` accepts only a
  few zone names; the zone token is ignored and the time read as UTC (the
  runners' zone), so the window is exact on CI and at most a day off elsewhere.
  An unreadable or missing validity line fails closed.

The decisions are pure functions (`normalize_fingerprint`,
`parse_apksigner_certs`, `parse_keytool_jarfile_certs`, `assess`) so they
unit-test with no Android SDK or JDK. `main` wires them to apksigner, the JDK
tools and the environment. Standard library only.
"""

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
from collections import namedtuple
from datetime import datetime, timezone

# `valid` is the certificate's (not before, not after) window as UTC datetimes, or None where the
# tool does not print it (apksigner without --verbose).
SignerFacts = namedtuple("SignerFacts", "label dn sha256 valid", defaults=(None,))
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

# `keytool -printcert -jarfile` structure. Field lines are only valid inside a certificate.
_KEYTOOL_UNSIGNED_LINE = "Not a signed jar file"
_KEYTOOL_SIGNER_LINE = re.compile(r"^Signer #(\d+):$")
_KEYTOOL_CERTIFICATE_LINE = re.compile(r"^Certificate #(\d+):$")
_KEYTOOL_SIGNATURE_LINE = "Signature:"
_KEYTOOL_EXTENSIONS_LINE = re.compile(r"^Extensions:\s*$")
_KEYTOOL_FIELD_LINE = re.compile(
    r"^(?P<field>Owner|Issuer|Serial number|Valid from|Certificate fingerprints|SHA1|SHA256|"
    r"Signature algorithm name|Subject Public Key Algorithm|Version):\s*(?P<value>.*)$")

# keytool's validity line, in Java's Date.toString() form; the zone token is ignored (see above).
_KEYTOOL_DATE = r"\w{3} (\w{3}) (\d{1,2}) (\d{2}):(\d{2}):(\d{2}) \S+ (\d{4})"
_KEYTOOL_VALIDITY = re.compile(r"^{0} until: {0}$".format(_KEYTOOL_DATE))
_MONTHS = ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

# jarsigner -strict exit bit set for a signer certificate that is self-signed or chains to no
# trusted CA. Every Android signing key is such a certificate, so this bit is expected; but an
# expired or not-yet-valid certificate sets it too, so the lines below decide (module docstring).
_JARSIGNER_SELF_SIGNED_BIT = 4
_JARSIGNER_SELF_SIGNED_LINE = "This jar contains entries whose signer certificate is self-signed."
_JARSIGNER_ALLOWED_LINES = (
    re.compile(re.escape(_JARSIGNER_SELF_SIGNED_LINE) + "$"),
    re.compile(re.escape(
        "This jar contains entries whose certificate chain is invalid. Reason: PKIX path "
        "building failed: sun.security.provider.certpath.SunCertPathBuilderException: "
        "unable to find valid certification path to requested target") + "$"),
    re.compile(re.escape(
        "This jar contains signatures that do not include a timestamp. Without a timestamp, "
        "users may not be able to validate this jar after any of the signer certificates "
        "expire (as early as ") + r"\d{4}-\d{2}-\d{2}\)\.$"),
    # The zip stores POSIX permission or symlink bits. jarsigner does not sign them and Android
    # ignores them, so their presence says nothing about the signed contents.
    re.compile(re.escape(
        "POSIX file permission and/or symlink attributes detected. These attributes are ignored "
        "when signing and are not protected by the signature.") + "$"),
)
# A section, not a single line: this header, then one "- ..." line per finding, ended by a blank
# line (or the "Re-run with" footer). bundletool writes META-INF/MANIFEST.MF after the content
# entries, so the streaming JarInputStream reader never sees the manifest and reports every entry
# as unsigned. The signature is verified through JarFile (the central directory), which found
# every entry signed ("jar verified"), and Play reads the bundle as a zip. Only that direction is
# accepted: signed in JarFile, not in JarInputStream. The reverse ("not signed in JarFile",
# "missing when reading via JarFile") would mean content the verified signature does not cover,
# and it, like any other line in the section, fails closed.
_JARSIGNER_INCONSISTENCIES_HEADER = (
    "This JAR file contains internal inconsistencies that may result in different contents when "
    "reading via JarFile and JarInputStream:")
_JARSIGNER_INCONSISTENCY_LINES = (
    re.compile(re.escape("- Manifest is missing when reading via JarInputStream") + "$"),
    re.compile(r"^- Entry (\S.*) is signed in JarFile but is not signed in JarInputStream$"),
)
_JARSIGNER_RERUN_LINE = "Re-run with the -verbose and -certs options for more details."
# Lines that frame jarsigner's report rather than state a finding.
_JARSIGNER_FRAME_LINES = (
    "jar verified.", "jar verified, with signer errors.", "Error:", "Warning:",
    _JARSIGNER_RERUN_LINE)
_JARSIGNER_VALIDITY_PROBLEM = re.compile(
    r"\b(?:has expired|is not yet valid|not yet valid)\b", re.IGNORECASE)


class MalformedFingerprint(ValueError):
    """A certificate fingerprint that is not 32 hex-encoded bytes."""


class MalformedApksignerOutput(ValueError):
    """apksigner output this parser cannot read with confidence."""


class MalformedKeytoolOutput(ValueError):
    """`keytool -printcert -jarfile` output this parser cannot read with confidence."""


class JarVerificationFailed(ValueError):
    """`jarsigner -verify -strict` did not verify an AAB's JAR signature."""


def _with_raw_output(summary, raw_output, tool="apksigner"):
    """An error message that carries the verbatim tool output, so a format
    change is diagnosable from the one log it produces."""
    if not raw_output.endswith("\n"):
        raw_output += "\n"
    return (
        "{0}\n----- {2} output begin -----\n{1}"
        "----- {2} output end -----".format(summary, raw_output, tool))


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


def parse_keytool_jarfile_certs(text):
    """The signers in `keytool -printcert -jarfile` output, in order.

    Each signer's DN and SHA-256 come from its first certificate (`Owner:` and
    `SHA256:`); later certificates of the signer are its chain. Returns an empty
    list only for keytool's "Not a signed jar file". Raises
    `MalformedKeytoolOutput` (with the raw output attached) when: the output is
    empty; a non-blank line outside an `Extensions:` block is not one this
    parser recognizes; a certificate or field appears outside a signer; a
    validity line is unreadable; or a signer lacks its owner, validity or
    SHA-256 fingerprint.
    """

    def malformed(summary):
        return MalformedKeytoolOutput(_with_raw_output(summary, text, tool="keytool"))

    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if not lines:
        raise malformed("keytool printed nothing; expected signers or \"{0}\"".format(
            _KEYTOOL_UNSIGNED_LINE))
    if lines == [_KEYTOOL_UNSIGNED_LINE]:
        return []

    signers = []  # [label, dn, sha256]
    certificate = None  # number of the current signer's current certificate
    in_extensions = False

    for line in lines:
        signer_match = _KEYTOOL_SIGNER_LINE.match(line)
        certificate_match = _KEYTOOL_CERTIFICATE_LINE.match(line)
        if signer_match:
            signers.append(["Signer #{0}".format(signer_match.group(1)), "", "", None])
            certificate = None
            in_extensions = False
            continue
        if certificate_match:
            if not signers:
                raise malformed("a certificate appears before any signer")
            number = int(certificate_match.group(1))
            if certificate is None and number != 1:
                raise malformed("{0} does not start with certificate #1".format(signers[-1][0]))
            certificate = number
            in_extensions = False
            continue
        # The skip ends only at the next `Signer #N:` or `Certificate #N:` header, which assumes
        # `Extensions:` is the last block of a certificate in keytool's layout. That was verified
        # on JDK 21 captures; CI runs JDK 17. A field line keytool printed after `Extensions:`
        # would be skipped here, harmlessly: no DN or digest is ever read from this block.
        if in_extensions:
            continue  # free-form X.509 extension dump; see the module docstring
        if line == _KEYTOOL_SIGNATURE_LINE and signers and certificate is None:
            continue
        if _KEYTOOL_EXTENSIONS_LINE.match(line) and certificate is not None:
            in_extensions = True
            continue

        field = _KEYTOOL_FIELD_LINE.match(line)
        if not field:
            raise malformed(
                "unrecognized keytool output line {0!r}; the format this gate reads has "
                "changed, so its verdict cannot be trusted".format(line))
        if certificate is None:
            raise malformed("{0!r} appears outside a signer's certificate".format(line))
        if certificate != 1:
            continue  # a chain certificate, not the signer's own
        signer = signers[-1]
        name, value = field.group("field"), field.group("value").strip()
        if name == "Owner":
            if signer[1]:
                raise malformed("{0} has two owners".format(signer[0]))
            signer[1] = value
        elif name == "Valid from":
            signer[3] = _parse_keytool_validity(value)
            if signer[3] is None:
                raise malformed("{0}: unreadable validity {1!r}".format(signer[0], value))
        elif name == "SHA256":
            if signer[2]:
                raise malformed("{0} has two SHA-256 fingerprints".format(signer[0]))
            try:
                signer[2] = normalize_fingerprint(value)
            except MalformedFingerprint as exc:
                raise malformed("{0}: unreadable SHA-256 fingerprint ({1})".format(signer[0], exc))
        # Other fields (issuer, SHA-1, algorithms ...) are not needed.

    if not signers:
        raise malformed("keytool output has no signers")
    for label, dn, sha256, valid in signers:
        if not dn or not sha256 or not valid:
            raise malformed(
                "{0} is missing its owner, validity or SHA-256 fingerprint".format(label))

    return [SignerFacts(*signer) for signer in signers]


def _parse_keytool_validity(value):
    """keytool's `<from> until: <to>` as two UTC datetimes, or None if unreadable."""
    match = _KEYTOOL_VALIDITY.match(value)
    if not match or not set(match.group(1, 7)) <= set(_MONTHS):
        return None
    try:  # each date's groups: month, day, hour, minute, second, year
        return tuple(datetime(
            int(year), _MONTHS.index(month) + 1, int(day), int(hh), int(mm), int(ss),
            tzinfo=timezone.utc)
            for month, day, hh, mm, ss, year in (match.groups()[:6], match.groups()[6:]))
    except ValueError:
        return None


def _utcnow():
    return datetime.now(timezone.utc)


def assess(signers, expected_sha256=None, now=None):
    """Whether these signer blocks are exactly one non-debug release certificate,
    and, when `expected_sha256` is given, exactly the pinned one. A signer whose
    validity window is known must contain `now` (default: the current UTC time).

    Blocks are counted by distinct certificate: apksigner may print one key's
    block once per signature scheme (`V2 Signer:`, `V3 Signer:`), and that is
    still one signing identity.
    """
    expected = None if expected_sha256 is None else normalize_fingerprint(expected_sha256)
    now = now or _utcnow()

    if not signers:
        return Verdict(False, [
            "the artifact is unsigned; a release APK or AAB must carry the release "
            "certificate"])

    reasons = []
    distinct = []
    for signer in signers:
        if signer.sha256 not in [s.sha256 for s in distinct]:
            distinct.append(signer)

    if len(distinct) != 1:
        reasons.append(
            "the artifact has {0} signing certificates; a release APK or AAB is signed "
            "by the release key alone".format(len(distinct)))

    for signer in distinct:
        if ANDROID_DEBUG_DN_MARKER in signer.dn.lower():
            reasons.append(
                "{0} is the Android debug certificate ({1}): the build fell back to "
                "debug signing, so this artifact can never be installed over a release "
                "build. The keystore inputs did not reach Gradle.".format(
                    signer.label, signer.dn))
        elif expected is not None and signer.sha256 != expected:
            reasons.append(
                "{0} certificate is {1}, expected the release certificate {2} "
                "(DN: {3})".format(signer.label, signer.sha256, expected, signer.dn))
        if signer.valid and not signer.valid[0] <= now <= signer.valid[1]:
            reasons.append(
                "{0} certificate is outside its validity window ({1} to {2}; now {3})".format(
                    signer.label, signer.valid[0], signer.valid[1], now.replace(microsecond=0)))

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


def find_jdk_tool(name):
    """A JDK tool (`jarsigner`, `keytool`) from PATH, else from JAVA_HOME/bin."""
    on_path = shutil.which(name)
    if on_path:
        return on_path
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = os.path.join(java_home, "bin", name)
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    raise OSError("{0} not found on PATH or under JAVA_HOME/bin".format(name))


def _jarsigner_findings(stdout):
    """(findings, unexplained) for jarsigner's stdout.

    `findings` is every line that is not report framing. `unexplained` is every finding off the
    allowlist: outside the internal-inconsistencies section a line must match
    `_JARSIGNER_ALLOWED_LINES`; inside it (from its exact header to the next blank line or the
    "Re-run with" footer) a line must match `_JARSIGNER_INCONSISTENCY_LINES`, and nothing else.
    """
    findings, unexplained = [], []
    in_section = False
    for raw in stdout.splitlines():
        line = raw.strip()
        if not line or line == _JARSIGNER_RERUN_LINE:
            in_section = False
        if not line or line in _JARSIGNER_FRAME_LINES:
            continue
        findings.append(line)
        if line == _JARSIGNER_INCONSISTENCIES_HEADER:
            in_section = True
            continue
        allowed = _JARSIGNER_INCONSISTENCY_LINES if in_section else _JARSIGNER_ALLOWED_LINES
        if not any(pattern.match(line) for pattern in allowed):
            unexplained.append(line)
    return findings, unexplained


def verify_jar(aab, jarsigner=None):
    """Fails unless `jarsigner -verify -strict` verifies the AAB's JAR signature.

    Exit 0 or the self-signed bit alone passes (see the module docstring); any
    other bit, "jar is unsigned", output without "jar verified", a line off the
    allowlist, an expired or not-yet-valid certificate, or exit 4 without the
    self-signed line fails.
    """
    tool = jarsigner or find_jdk_tool("jarsigner")
    result = subprocess.run(
        [tool, "-verify", "-strict", aab], capture_output=True, text=True, check=False)
    output = "{0}{1}".format(result.stdout or "", result.stderr or "")
    findings, unexplained = _jarsigner_findings(result.stdout or "")
    validity = _JARSIGNER_VALIDITY_PROBLEM.search(output)
    # Every capture prints both phrases on stdout with stderr empty. A failure phrase is looked
    # for in both streams, which can only fail more; the success phrase must come from stdout,
    # where jarsigner prints it, so nothing on stderr (such as the JDK's echo of
    # JAVA_TOOL_OPTIONS) can supply it.
    problem = None
    if "jar is unsigned" in output:
        problem = "the jar is unsigned"
    elif result.returncode & ~_JARSIGNER_SELF_SIGNED_BIT:
        problem = "exit {0}".format(result.returncode)
    elif "jar verified" not in (result.stdout or ""):
        problem = "no \"jar verified\" in its output"
    elif validity:
        problem = "the signer certificate {0}".format(validity.group(0).lower())
    elif unexplained:
        problem = "unrecognized warning {0!r}".format(unexplained[0])
    elif (result.returncode & _JARSIGNER_SELF_SIGNED_BIT
          and _JARSIGNER_SELF_SIGNED_LINE not in findings):
        problem = "exit {0} without the self-signed certificate warning that explains it".format(
            result.returncode)
    if problem:
        raise JarVerificationFailed(_with_raw_output(
            "jarsigner could not verify {0} ({1})".format(os.path.basename(aab), problem),
            output, tool="jarsigner"))


def print_jar_certs(aab, keytool=None):
    """`keytool -printcert -jarfile` output for one AAB. A nonzero exit is a failure."""
    tool = keytool or find_jdk_tool("keytool")
    result = subprocess.run(
        [tool, "-printcert", "-jarfile", aab], capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise MalformedKeytoolOutput(
            "keytool could not read the certificates of {0} (exit {1}): {2}".format(
                os.path.basename(aab), result.returncode,
                (result.stderr or result.stdout).strip()))
    return result.stdout


def signers_of(artifact):
    """The signer facts of one release artifact, by its extension: `.aab` through
    the JDK jar tools, anything else through apksigner."""
    if artifact.lower().endswith(".aab"):
        verify_jar(artifact)
        return parse_keytool_jarfile_certs(print_jar_certs(artifact))
    return parse_apksigner_certs(print_certs(artifact))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("artifact", nargs="+", help="release APK(s) and AAB(s) to check")
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
    for artifact in args.artifact:
        name = os.path.basename(artifact)
        try:
            signers = signers_of(artifact)
        except (MalformedApksignerOutput, MalformedKeytoolOutput, JarVerificationFailed,
                OSError) as exc:
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
            "\nA release APK or AAB is not signed by the release certificate; refusing to "
            "publish it. Installing it would force an uninstall (and the loss of the "
            "app's data) on the next update. See "
            "docs/adr/0002-release-signing-with-a-stable-keystore.md.")
        return 1

    print("\nOK: every release APK and AAB carries the release certificate.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
