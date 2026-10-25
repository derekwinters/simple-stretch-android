import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// The version-code scheme of docs/adr/0003: major * 10000 + minor * 100 + patch, minor and patch
// at most 99, the code at most Google Play's 2100000000, never wrapped, clamped or truncated.
class VersionCodeTest {

    @Test
    fun `converts a version name to major times 10000 plus minor times 100 plus patch`() {
        assertEquals(200, VersionCode.fromVersionName("0.2.0"))
        assertEquals(300, VersionCode.fromVersionName("0.3.0"))
        assertEquals(10000, VersionCode.fromVersionName("1.0.0"))
        assertEquals(10203, VersionCode.fromVersionName("1.2.3"))
    }

    @Test
    fun `accepts the largest minor and patch the scheme allows`() {
        assertEquals(9999, VersionCode.fromVersionName("0.99.99"))
    }

    @Test
    fun `accepts the largest code Google Play allows and rejects the next one`() {
        assertEquals(2_100_000_000, VersionCode.fromVersionName("210000.0.0"))
        assertRejected("210000.0.1", "2100000000")
    }

    // The ADR invariant: a higher version always gets a higher code, across every carry.
    @Test
    fun `a higher version always has a higher code, including across a carry`() {
        val pairs = listOf(
            "0.2.0" to "0.2.1",
            "0.2.99" to "0.3.0",
            "0.99.99" to "1.0.0",
            "1.99.99" to "2.0.0",
            "1.0.99" to "1.1.0",
            "9.99.99" to "10.0.0",
        )
        for ((lower, higher) in pairs) {
            val lowerCode = VersionCode.fromVersionName(lower)
            val higherCode = VersionCode.fromVersionName(higher)
            assertTrue(
                "$higher ($higherCode) must be higher than $lower ($lowerCode)",
                higherCode > lowerCode,
            )
        }
    }

    @Test
    fun `rejects a minor or patch above 99 rather than carrying it`() {
        assertRejected("0.100.0", "minor")
        assertRejected("0.3.100", "patch")
    }

    @Test
    fun `rejects anything that is not MAJOR dot MINOR dot PATCH`() {
        for (name in listOf("0.3", "0.3.0-beta", "v0.3.0", "", "0.3.0.1", " 0.3.0", "0.3.x", "0..3")) {
            assertRejected(name, "MAJOR.MINOR.PATCH")
        }
    }

    @Test
    fun `rejects leading zeros, which are not semver and would alias another version`() {
        for (name in listOf("0.03.0", "00.3.0", "0.3.00", "01.0.0")) {
            assertRejected(name, "MAJOR.MINOR.PATCH")
        }
        assertEquals(0, VersionCode.fromVersionName("0.0.0"))
    }

    @Test
    fun `rejects a major too large for any integer instead of overflowing`() {
        assertRejected("99999999999999999999.0.0", "2100000000")
    }

    private fun assertRejected(versionName: String, messagePart: String) {
        val error = assertThrows(
            "\"$versionName\" must be rejected",
            IllegalArgumentException::class.java,
        ) { VersionCode.fromVersionName(versionName) }
        val message = error.message.orEmpty()
        assertTrue("message names \"$versionName\": $message", message.contains("\"$versionName\""))
        assertTrue("message mentions $messagePart: $message", message.contains(messagePart))
    }
}
