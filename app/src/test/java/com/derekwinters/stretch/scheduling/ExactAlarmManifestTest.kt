package com.derekwinters.stretch.scheduling

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Requirement IDs refer to docs/spec/scheduling.md. */
class ExactAlarmManifestTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    /**
     * Unit tests run with the module directory (app/) as the working directory.
     *
     * This reads the source manifest only, not the merged one: a dependency's manifest could in
     * theory merge USE_EXACT_ALARM in, and that is not covered here.
     */
    private fun usesPermissions(): List<Element> {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("manifest not found at ${manifest.absolutePath}", manifest.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val nodes = factory.newDocumentBuilder().parse(manifest).getElementsByTagName("uses-permission")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun named(name: String) =
        usesPermissions().filter { it.getAttributeNS(androidNs, "name") == name }

    // SCHED-007: USE_EXACT_ALARM is deliberately not declared (Play restricts it).
    @Test
    fun manifestDoesNotDeclareUseExactAlarm() {
        assertEquals(emptyList<Element>(), named("android.permission.USE_EXACT_ALARM"))
    }

    // SCHED-007: SCHEDULE_EXACT_ALARM is the only exact-alarm permission, on every API level.
    @Test
    fun scheduleExactAlarmIsDeclaredWithoutMaxSdkVersion() {
        val declared = named("android.permission.SCHEDULE_EXACT_ALARM")
        assertEquals(1, declared.size)
        assertNull(declared.single().getAttributeNodeNS(androidNs, "maxSdkVersion"))
    }
}
