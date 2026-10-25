package com.derekwinters.stretch.privacy

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element

/**
 * Requirement IDs refer to docs/spec/privacy.md. The privacy policy (docs/privacy.md) and its
 * Play data safety answers describe exactly this permission set; a change here must change them
 * in the same pull request.
 */
class ManifestPermissionsTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    /**
     * Unit tests normally run with the module directory (app/) as the working directory; the
     * repository root is tried too. Reads the source manifest, not the merged one.
     */
    private fun manifest(): File {
        val candidates = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .map { File(it) }
        val found = candidates.firstOrNull { it.isFile }
        assertNotNull(
            "manifest not found; tried ${candidates.joinToString { it.absolutePath }}",
            found,
        )
        return found!!
    }

    private fun parseManifest() =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest())

    private fun declaredPermissions(): Set<String> {
        val doc = parseManifest()
        return listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNs, "name") }
        }.toSet()
    }

    // PRIV-001: the app declares no INTERNET permission, so nothing can leave the device.
    @Test
    fun internetPermissionIsNotDeclared() {
        assertFalse(
            "INTERNET must not be declared (PRIV-001); update docs/privacy.md first",
            "android.permission.INTERNET" in declaredPermissions(),
        )
    }

    // PRIV-003: the declared permission set is exactly notifications, boot completed and exact
    // alarms. Adding or removing any permission fails here until the spec and policy change.
    @Test
    fun permissionSetIsExactlyTheDocumentedThree() {
        assertEquals(
            setOf(
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.SCHEDULE_EXACT_ALARM",
            ),
            declaredPermissions(),
        )
    }

    // PRIV-004: the policy says Android's device backup covers all of the app's data. That holds
    // only while allowBackup is "true" with no backup rules; changing either changes the policy.
    @Test
    fun backupIsAllowedWithNoBackupRules() {
        val application = parseManifest().getElementsByTagName("application").item(0) as Element
        assertEquals(
            "allowBackup must stay \"true\" (PRIV-004); update docs/privacy.md first",
            "true",
            application.getAttributeNS(androidNs, "allowBackup"),
        )
        assertFalse(
            "fullBackupContent must not be set (PRIV-004); update docs/privacy.md first",
            application.hasAttributeNS(androidNs, "fullBackupContent"),
        )
        assertFalse(
            "dataExtractionRules must not be set (PRIV-004); update docs/privacy.md first",
            application.hasAttributeNS(androidNs, "dataExtractionRules"),
        )
    }
}
