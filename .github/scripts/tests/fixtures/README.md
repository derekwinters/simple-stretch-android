# Captured apksigner, keytool and jarsigner output

The `apksigner-*` files are **verbatim apksigner stdout**, copied byte for byte from
`derekwinters/chores-web-android`, which copied them from `derekwinters/Interval-trainer-android`
(`.github/scripts/tests/fixtures/`, same filenames; the files hash identically). Nothing in them was edited and nothing should be: a comment header would
turn them into authored files that only look captured, which is exactly the failure they exist to
prevent.

| File | Command | Source |
| --- | --- | --- |
| `apksigner-verify-print-certs-nonverbose.txt` | `apksigner verify --print-certs signed.apk` | apksigner 31.0.2, throwaway key, first captured in `derekwinters/lucas-doggiehood` |
| `apksigner-verify-print-certs-verbose.txt` | `apksigner verify --print-certs --verbose signed.apk` | same capture as above |
| `apksigner-verify-print-certs-verbose-scheme-signer.txt` | `apksigner verify --print-certs --verbose` | build-tools 35.0.0, Interval-trainer's real v0.2.2 release APK |

**Why captured, not written.** `lucas-doggiehood` shipped a release with no assets because its
gate's tests used hand-written transcripts of a format the workflow never asked apksigner for.
The rule taken from that: a gate that parses another tool's output is tested against output
captured from that tool **with the exact flags the workflow passes**. This repo's gate runs
`apksigner verify --print-certs` without `--verbose`, so the non-verbose capture is the primary
fixture. The two verbose captures prove the parser also reads the extra header lines and
build-tools 35's `V2 Signer:` block labels.

**None of these certificates is this repository's release certificate.** They pin apksigner's
output *shape*. The real fingerprint comes from the `ANDROID_KEYSTORE_SHA256` secret at release
time, when that optional secret is set.

The two-signer and debug-signed samples in `test_verify_release_signature.py` are hand-written,
because neither kind of APK could be produced to capture from. They are used only for those two
cases.

## AAB fixtures: keytool and jarsigner

The release AAB carries only a JAR (v1) signature, so the gate reads it with the JDK:
`jarsigner -verify -strict` and `keytool -printcert -jarfile`. These files are **verbatim stdout**
of those two commands, with exactly those flags, captured for this repository with OpenJDK 21
(`keytool` and `jarsigner` 21.0.12.1; CI runs 17). Nothing in them was edited.

They were captured from a one-entry jar (an `.aab` is a zip/jar) named `*.aab`, built with
`jar cf` and signed with `jarsigner`. Three throwaway RSA 2048 keys were generated with
`keytool -genkeypair` in a scratch directory outside the repository and then discarded; no key
or jar is committed:

- release-like: `CN=Simple Stretch Test Release, O=Fixture, L=Somewhere, C=US`
- debug: `CN=Android Debug, O=Android, C=US` (the default debug certificate's subject)
- other: `CN=Someone Else, O=Fixture, C=US`

| File | Command | Jar |
| --- | --- | --- |
| `keytool-printcert-jarfile-signed.txt` | `keytool -printcert -jarfile signed.aab` | signed with the release-like key |
| `keytool-printcert-jarfile-unsigned.txt` | `keytool -printcert -jarfile unsigned.aab` | not signed |
| `keytool-printcert-jarfile-debug.txt` | `keytool -printcert -jarfile debug.aab` | signed with the debug key |
| `keytool-printcert-jarfile-two-signers.txt` | `keytool -printcert -jarfile two.aab` | signed twice: release-like key with `-sigfile RELEASE`, then the other key with `-sigfile OTHER` |
| `jarsigner-verify-strict-signed.txt` | `jarsigner -verify -strict signed.aab` (exit 4) | signed with the release-like key; byte-identical for the debug and two-signer jars, which also exit 4 |
| `jarsigner-verify-strict-unsigned.txt` | `jarsigner -verify -strict unsigned.aab` (exit 16) | not signed |
| `jarsigner-verify-strict-expired.txt` | `jarsigner -verify -strict expired.aab` (exit 4) | signed with an expired key (below) |
| `jarsigner-verify-strict-not-yet-valid.txt` | `jarsigner -verify -strict not-yet-valid.aab` (exit 4) | signed with a not-yet-valid key (below) |
| `keytool-printcert-jarfile-expired.txt` | `keytool -printcert -jarfile expired.aab` | signed with the expired key |

The expired and not-yet-valid jars were signed with two more throwaway keys with the release-like
subject, generated with `keytool -genkeypair ... -validity 1` and `-startdate -3d` (expired two
days before the capture) or `-startdate +1y` (valid only from a year after it). jarsigner signs
with either without complaint; both captures' stderr was empty.

**Exit 4 on a correctly signed jar is expected, but exit 4 alone proves nothing.** `-strict` turns
warnings into exit-code bits, and bit 4 is set because the signer certificate is self-signed and
chains to no CA, which is true of every Android signing key. The captures show that an expired or
not-yet-valid certificate sets the same bit 4. The gate therefore accepts exit 0 or 4 and fails on
any other bit (1 for a broken signature, 16 for unsigned entries, and so on), on "jar is
unsigned", and, whatever the exit code, on any line under jarsigner's `Error:`/`Warning:`
headings that is not one of the three in the signed capture (self-signed, the PKIX "unable to find
valid certification path" chain line, and the missing-timestamp line). Exit 4 must also come with
the self-signed line. Exit codes are not in the captured stdout; the tests state them next to the
fixtures.

The JDK prints a `Picked up JAVA_TOOL_OPTIONS` line on stderr when that variable is set; the
gate reads keytool's stdout only, and these captures are stdout only.

**None of these certificates is this repository's release certificate.** Like the apksigner
captures, they pin the tools' output *shape*. Unlike the APK case, the debug-signed and two-signer
AABs could be produced, so those samples are captured rather than hand-written.
