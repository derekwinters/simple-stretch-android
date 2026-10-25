/**
 * The Android versionCode for a release version name (docs/adr/0003).
 *
 * `major * 10000 + minor * 100 + patch`: 0.3.0 is 300, 1.0.0 is 10000. Minor and patch are at
 * most 99, and the code at most Google Play's largest allowed versionCode. Under those caps a
 * higher version always has a higher code. Anything the scheme cannot represent is rejected with
 * an [IllegalArgumentException], which fails the build: the code is never wrapped, clamped or
 * truncated into some other number.
 */
object VersionCode {
    /** Google Play's largest allowed versionCode. */
    const val PLAY_MAX_CODE = 2_100_000_000L

    /** The largest minor or patch this scheme can hold without colliding with the next field. */
    const val MAX_MINOR_OR_PATCH = 99L

    // Semver's numeric fields: no leading zeros, so "0.03.0" cannot pass as 0.3.0.
    private val VERSION_NAME = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")

    fun fromVersionName(versionName: String): Int {
        val match = VERSION_NAME.matchEntire(versionName)
            ?: throw IllegalArgumentException(
                "Version name \"$versionName\" is not MAJOR.MINOR.PATCH (digits only, no leading " +
                    "zeros, no prefix or suffix), so no versionCode can be derived from it (docs/adr/0003).",
            )
        val (majorText, minorText, patchText) = match.destructured
        val minor = minorText.toLongOrNull() ?: Long.MAX_VALUE
        val patch = patchText.toLongOrNull() ?: Long.MAX_VALUE
        require(minor <= MAX_MINOR_OR_PATCH) {
            "Version name \"$versionName\" has minor $minorText; the versionCode scheme caps " +
                "minor at $MAX_MINOR_OR_PATCH (docs/adr/0003 describes the wider scheme to move to)."
        }
        require(patch <= MAX_MINOR_OR_PATCH) {
            "Version name \"$versionName\" has patch $patchText; the versionCode scheme caps " +
                "patch at $MAX_MINOR_OR_PATCH (docs/adr/0003 describes the wider scheme to move to)."
        }
        // A major with more digits than the limit has cannot fit; checked before multiplying so
        // nothing overflows.
        val major = majorText.toLongOrNull()?.takeIf { it <= PLAY_MAX_CODE / 10000 }
        val code = major?.let { it * 10000 + minor * 100 + patch }
        require(code != null && code <= PLAY_MAX_CODE) {
            "Version name \"$versionName\" gives a versionCode above Google Play's limit of " +
                "$PLAY_MAX_CODE (docs/adr/0003)."
        }
        return code.toInt()
    }
}
