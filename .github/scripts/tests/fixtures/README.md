# Captured apksigner output

These files are **verbatim apksigner stdout**, copied byte for byte from
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
