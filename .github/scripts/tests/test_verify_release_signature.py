"""Unit tests for the release-signature gate (docs/adr/0002).

Android only installs an update when the new APK is signed by the same certificate as the
installed one. `verify_release_signature.py` runs before any release-signed APK is uploaded or
attached and fails unless the APK carries exactly one non-debug certificate, which must also equal
the pin from the optional `ANDROID_KEYSTORE_SHA256` secret when that is set. These tests need no
Android SDK, no keystore and no APK. Ported from derekwinters/chores-web-android.

The single-signer apksigner transcripts are captured, not written. See `fixtures/README.md`.
"""

import os
import subprocess
import sys
import unittest
from contextlib import redirect_stdout
from io import StringIO
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import verify_release_signature as gate  # noqa: E402

FIXTURES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")


def _fixture(name):
    with open(os.path.join(FIXTURES, name), encoding="utf-8") as handle:
        return handle.read()


# Real output, captured with the exact flags the gate passes (`--print-certs`, no `--verbose`).
NON_VERBOSE_OUTPUT = _fixture("apksigner-verify-print-certs-nonverbose.txt")
VERBOSE_OUTPUT = _fixture("apksigner-verify-print-certs-verbose.txt")
SCHEME_SIGNER_OUTPUT = _fixture("apksigner-verify-print-certs-verbose-scheme-signer.txt")

FIXTURE_SHA256 = "10d315258da7c4c4830814ae6f876e84145f2195cfb077bc0bb04e3df0a61ed8"
FIXTURE_DN = "CN=Doggiehood Release, O=Derek Winters, L=Somewhere, C=US"
SCHEME_FIXTURE_SHA256 = "2f596b227b890f5fcec72c176f0e325623e6261f00ddb102c4f936e9da108e09"

# The pin in keytool's form: uppercase, colon-separated.
FIXTURE_SHA256_KEYTOOL = ":".join(
    FIXTURE_SHA256[i:i + 2] for i in range(0, len(FIXTURE_SHA256), 2)).upper()

OTHER_SHA256 = "ab" * 32
DEBUG_SHA256 = "cd" * 32

# Hand-written: a debug-signed APK and a two-key APK could not be captured.
DEBUG_OUTPUT = (
    "Signer #1 certificate DN: C=US, O=Android, CN=Android Debug\n"
    "Signer #1 certificate SHA-256 digest: {0}\n"
    "Signer #1 certificate SHA-1 digest: {1}\n"
    "Signer #1 certificate MD5 digest: {2}\n".format(DEBUG_SHA256, "ef" * 20, "01" * 16))

TWO_SIGNER_OUTPUT = (
    NON_VERBOSE_OUTPUT
    + "Signer #2 certificate DN: CN=Someone Else\n"
    + "Signer #2 certificate SHA-256 digest: {0}\n".format(OTHER_SHA256))


class NormalizeFingerprintTests(unittest.TestCase):

    def test_apksigner_bare_lowercase_hex_is_unchanged(self):
        self.assertEqual(gate.normalize_fingerprint(FIXTURE_SHA256), FIXTURE_SHA256)

    def test_keytool_colon_uppercase_form_normalizes(self):
        self.assertEqual(gate.normalize_fingerprint(FIXTURE_SHA256_KEYTOOL), FIXTURE_SHA256)

    def test_a_sha256_label_and_whitespace_are_ignored(self):
        self.assertEqual(
            gate.normalize_fingerprint("  SHA256: {0}\n".format(FIXTURE_SHA256_KEYTOOL)),
            FIXTURE_SHA256)

    def test_wrong_length_is_rejected(self):
        with self.assertRaises(gate.MalformedFingerprint):
            gate.normalize_fingerprint("ab" * 20)  # a SHA-1, not a SHA-256

    def test_non_hex_is_rejected(self):
        with self.assertRaises(gate.MalformedFingerprint):
            gate.normalize_fingerprint("zz" * 32)

    def test_empty_or_missing_is_rejected(self):
        for value in (None, "", "   ", "::"):
            with self.subTest(value=value), self.assertRaises(gate.MalformedFingerprint):
                gate.normalize_fingerprint(value)


class ParseApksignerCertsTests(unittest.TestCase):

    def test_reads_the_real_non_verbose_capture(self):
        signers = gate.parse_apksigner_certs(NON_VERBOSE_OUTPUT)
        self.assertEqual(len(signers), 1)
        self.assertEqual(signers[0].dn, FIXTURE_DN)
        self.assertEqual(signers[0].sha256, FIXTURE_SHA256)
        self.assertEqual(signers[0].label, "Signer #1")

    def test_reads_the_real_verbose_capture_too(self):
        signers = gate.parse_apksigner_certs(VERBOSE_OUTPUT)
        self.assertEqual([s.sha256 for s in signers], [FIXTURE_SHA256])

    def test_reads_build_tools_35_scheme_labelled_blocks(self):
        signers = gate.parse_apksigner_certs(SCHEME_SIGNER_OUTPUT)
        self.assertEqual([s.sha256 for s in signers], [SCHEME_FIXTURE_SHA256])
        self.assertEqual(signers[0].label, "V2 Signer")

    def test_reads_the_per_sdk_range_form(self):
        text = (
            "Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate DN: CN=Chores\n"
            "Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: "
            "{0}\n".format(FIXTURE_SHA256))
        signers = gate.parse_apksigner_certs(text)
        self.assertEqual([s.sha256 for s in signers], [FIXTURE_SHA256])

    def test_uppercase_digests_are_normalized(self):
        signers = gate.parse_apksigner_certs(NON_VERBOSE_OUTPUT.replace(
            FIXTURE_SHA256, FIXTURE_SHA256.upper()))
        self.assertEqual(signers[0].sha256, FIXTURE_SHA256)

    def test_reads_every_signer(self):
        signers = gate.parse_apksigner_certs(TWO_SIGNER_OUTPUT)
        self.assertEqual([s.sha256 for s in signers], [FIXTURE_SHA256, OTHER_SHA256])

    def test_empty_output_is_unparseable(self):
        with self.assertRaises(gate.MalformedApksignerOutput):
            gate.parse_apksigner_certs("")

    def test_an_unrecognized_line_is_unparseable_not_skipped(self):
        # A format change must fail closed, never shorten the signer list silently.
        with self.assertRaises(gate.MalformedApksignerOutput):
            gate.parse_apksigner_certs(
                NON_VERBOSE_OUTPUT + "Signer 2 has moved: CN=Someone Else\n")

    def test_a_signer_without_a_sha256_digest_is_unparseable(self):
        text = "\n".join(
            line for line in NON_VERBOSE_OUTPUT.splitlines() if "SHA-256" not in line)
        with self.assertRaises(gate.MalformedApksignerOutput):
            gate.parse_apksigner_certs(text)

    def test_a_signer_without_a_dn_is_unparseable(self):
        text = "\n".join(
            line for line in NON_VERBOSE_OUTPUT.splitlines() if "DN:" not in line)
        with self.assertRaises(gate.MalformedApksignerOutput):
            gate.parse_apksigner_certs(text)

    def test_a_declared_signer_count_that_disagrees_is_unparseable(self):
        with self.assertRaises(gate.MalformedApksignerOutput):
            gate.parse_apksigner_certs(
                VERBOSE_OUTPUT.replace("Number of signers: 1", "Number of signers: 2"))

    def test_two_certificates_for_one_signer_block_is_unparseable(self):
        with self.assertRaises(gate.MalformedApksignerOutput):
            gate.parse_apksigner_certs(
                NON_VERBOSE_OUTPUT
                + "Signer #1 certificate SHA-256 digest: {0}\n".format(OTHER_SHA256))

    def test_unparseable_errors_carry_the_raw_output(self):
        with self.assertRaises(gate.MalformedApksignerOutput) as caught:
            gate.parse_apksigner_certs("something apksigner never printed\n")
        self.assertIn("something apksigner never printed", str(caught.exception))


class AssessTests(unittest.TestCase):

    def _signers(self, text):
        return gate.parse_apksigner_certs(text)

    def test_the_pinned_certificate_passes(self):
        verdict = gate.assess(self._signers(NON_VERBOSE_OUTPUT), FIXTURE_SHA256)
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_the_pin_may_be_given_in_keytool_form(self):
        verdict = gate.assess(self._signers(NON_VERBOSE_OUTPUT), FIXTURE_SHA256_KEYTOOL)
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_the_scheme_labelled_capture_passes_against_its_own_certificate(self):
        verdict = gate.assess(self._signers(SCHEME_SIGNER_OUTPUT), SCHEME_FIXTURE_SHA256)
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_one_certificate_reported_once_per_scheme_is_one_signer(self):
        # With v1/v2/v3 all enabled, apksigner may label one key's block once per scheme.
        text = SCHEME_SIGNER_OUTPUT + SCHEME_SIGNER_OUTPUT.split("Number of signers: 1\n")[1] \
            .replace("V2 Signer:", "V3 Signer:")
        verdict = gate.assess(self._signers(text), SCHEME_FIXTURE_SHA256)
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_a_different_certificate_fails_naming_both(self):
        verdict = gate.assess(self._signers(NON_VERBOSE_OUTPUT), OTHER_SHA256)
        self.assertFalse(verdict.ok)
        joined = " ".join(verdict.reasons)
        self.assertIn(FIXTURE_SHA256, joined)
        self.assertIn(OTHER_SHA256, joined)

    def test_a_debug_key_fallback_is_named_explicitly(self):
        verdict = gate.assess(self._signers(DEBUG_OUTPUT), FIXTURE_SHA256)
        self.assertFalse(verdict.ok)
        self.assertIn("debug", " ".join(verdict.reasons).lower())
        self.assertIn("fell back", " ".join(verdict.reasons))

    def test_zero_signers_fails(self):
        verdict = gate.assess([], FIXTURE_SHA256)
        self.assertFalse(verdict.ok)
        self.assertIn("unsigned", " ".join(verdict.reasons))

    def test_an_extra_signer_fails_even_beside_the_release_key(self):
        verdict = gate.assess(self._signers(TWO_SIGNER_OUTPUT), FIXTURE_SHA256)
        self.assertFalse(verdict.ok)
        self.assertIn("2 signing certificates", " ".join(verdict.reasons))

    def test_without_a_pin_a_single_non_debug_certificate_passes(self):
        verdict = gate.assess(self._signers(NON_VERBOSE_OUTPUT), None)
        self.assertTrue(verdict.ok, verdict.reasons)

    def test_without_a_pin_a_debug_key_still_fails(self):
        verdict = gate.assess(self._signers(DEBUG_OUTPUT), None)
        self.assertFalse(verdict.ok)
        self.assertIn("fell back", " ".join(verdict.reasons))

    def test_without_a_pin_an_extra_signer_still_fails(self):
        verdict = gate.assess(self._signers(TWO_SIGNER_OUTPUT), None)
        self.assertFalse(verdict.ok)

    def test_without_a_pin_zero_signers_still_fails(self):
        self.assertFalse(gate.assess([], None).ok)

    def test_a_malformed_pin_raises_rather_than_passing(self):
        with self.assertRaises(gate.MalformedFingerprint):
            gate.assess(self._signers(NON_VERBOSE_OUTPUT), "not-a-fingerprint")


class ApksignerInvocationTests(unittest.TestCase):

    def _run(self, returncode=0, stdout=NON_VERBOSE_OUTPUT, stderr=""):
        completed = subprocess.CompletedProcess([], returncode, stdout=stdout, stderr=stderr)
        with mock.patch.object(gate.subprocess, "run", return_value=completed) as run:
            result = gate.print_certs("app.apk", apksigner="/sdk/apksigner")
        return run, result

    def test_invokes_verify_print_certs_without_verbose(self):
        # The fixtures were captured with exactly these flags; keep them in step.
        run, _ = self._run()
        self.assertEqual(
            run.call_args[0][0], ["/sdk/apksigner", "verify", "--print-certs", "app.apk"])

    def test_returns_stdout(self):
        _, result = self._run()
        self.assertEqual(result, NON_VERBOSE_OUTPUT)

    def test_a_nonzero_exit_is_a_failure(self):
        completed = subprocess.CompletedProcess(
            [], 1, stdout="", stderr="DOES NOT VERIFY\nERROR: Missing META-INF/MANIFEST.MF")
        with mock.patch.object(gate.subprocess, "run", return_value=completed):
            with self.assertRaises(gate.MalformedApksignerOutput) as caught:
                gate.print_certs("app.apk", apksigner="/sdk/apksigner")
        self.assertIn("DOES NOT VERIFY", str(caught.exception))


class FindApksignerTests(unittest.TestCase):

    def test_prefers_apksigner_on_path(self):
        with mock.patch.object(gate.shutil, "which", return_value="/usr/bin/apksigner"):
            self.assertEqual(gate.find_apksigner(), "/usr/bin/apksigner")

    def test_falls_back_to_the_newest_build_tools_by_version(self):
        found = [
            "/sdk/build-tools/9.0.0/apksigner",
            "/sdk/build-tools/36.0.0/apksigner",
            "/sdk/build-tools/35.0.1/apksigner",
        ]
        with mock.patch.object(gate.shutil, "which", return_value=None), \
                mock.patch.dict(os.environ, {"ANDROID_HOME": "/sdk"}, clear=False), \
                mock.patch.object(gate.glob, "glob", return_value=found):
            self.assertEqual(gate.find_apksigner(), "/sdk/build-tools/36.0.0/apksigner")

    def test_not_found_is_an_error(self):
        env = {k: v for k, v in os.environ.items()
               if k not in ("ANDROID_HOME", "ANDROID_SDK_ROOT")}
        with mock.patch.object(gate.shutil, "which", return_value=None), \
                mock.patch.dict(os.environ, env, clear=True):
            with self.assertRaises(OSError):
                gate.find_apksigner()


class MainTests(unittest.TestCase):
    """Exit codes and messages of the gate as the workflows call it."""

    def _main(self, outputs, argv=None, env_pin=FIXTURE_SHA256):
        env = dict(os.environ)
        env.pop(gate.EXPECTED_ENV, None)
        if env_pin is not None:
            env[gate.EXPECTED_ENV] = env_pin
        out = StringIO()
        with mock.patch.dict(os.environ, env, clear=True), \
                mock.patch.object(gate, "print_certs", side_effect=outputs), \
                redirect_stdout(out):
            code = gate.main(argv or ["app-{0}.apk".format(i) for i in range(len(outputs))])
        return code, out.getvalue()

    def test_a_correctly_signed_apk_exits_zero_and_prints_its_digest(self):
        code, out = self._main([NON_VERBOSE_OUTPUT])
        self.assertEqual(code, 0)
        self.assertIn(FIXTURE_SHA256, out)

    def test_the_pin_is_read_from_the_secret_env_var_in_keytool_form(self):
        code, _ = self._main([NON_VERBOSE_OUTPUT], env_pin=FIXTURE_SHA256_KEYTOOL)
        self.assertEqual(code, 0)

    def test_a_missing_pin_still_checks_but_does_not_compare(self):
        # The pin secret is optional here (docs/adr/0002); its absence is reported, not fatal.
        for pin in (None, "", "   "):
            with self.subTest(pin=pin):
                code, out = self._main([NON_VERBOSE_OUTPUT], env_pin=pin)
                self.assertEqual(code, 0)
                self.assertIn(gate.EXPECTED_ENV, out)
                self.assertIn(FIXTURE_SHA256, out)

    def test_a_missing_pin_does_not_let_a_debug_signed_apk_through(self):
        code, out = self._main([DEBUG_OUTPUT], env_pin=None)
        self.assertEqual(code, 1)
        self.assertIn("Android Debug", out)

    def test_a_malformed_pin_fails(self):
        code, out = self._main([NON_VERBOSE_OUTPUT], env_pin="not-a-sha256")
        self.assertEqual(code, 1)
        self.assertIn("::error", out)

    def test_a_wrongly_signed_apk_fails_with_an_error_annotation(self):
        code, out = self._main([NON_VERBOSE_OUTPUT], env_pin=OTHER_SHA256)
        self.assertEqual(code, 1)
        self.assertIn("::error title=Release signature::", out)

    def test_a_debug_signed_apk_fails_and_says_so(self):
        code, out = self._main([DEBUG_OUTPUT])
        self.assertEqual(code, 1)
        self.assertIn("Android Debug", out)

    def test_unparseable_output_fails_rather_than_passing(self):
        code, out = self._main(["nothing recognizable\n"])
        self.assertEqual(code, 1)
        self.assertIn("nothing recognizable", out)

    def test_apksigner_failure_fails(self):
        code, _ = self._main([gate.MalformedApksignerOutput("DOES NOT VERIFY")])
        self.assertEqual(code, 1)

    def test_every_apk_is_checked_and_one_bad_one_fails_the_gate(self):
        code, out = self._main([DEBUG_OUTPUT, NON_VERBOSE_OUTPUT])
        self.assertEqual(code, 1)
        self.assertIn("OK: app-1.apk", out)


class StandardLibraryOnlyTests(unittest.TestCase):
    """The gate runs on the runner's bare python3, with nothing installed."""

    def test_the_gate_imports_only_the_standard_library(self):
        allowed = {"argparse", "glob", "os", "re", "shutil", "subprocess", "sys", "collections"}
        with open(gate.__file__, encoding="utf-8") as handle:
            imported = set()
            for line in handle:
                stripped = line.strip()
                if stripped.startswith("import "):
                    imported.add(stripped.split()[1].split(".")[0])
                elif stripped.startswith("from ") and " import " in stripped:
                    imported.add(stripped.split()[1].split(".")[0])
        self.assertLessEqual(imported, allowed)


if __name__ == "__main__":
    unittest.main()
