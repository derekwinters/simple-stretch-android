package com.derekwinters.stretch.widget

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * HOME-006 invariant (docs/spec/screens.md): the ring's figure and mat come from one shared
 * definition of the launcher icon's colours, so [LauncherIconColors] must be the colours the
 * launcher icon itself is drawn with. Unit tests run with app/ as the working directory.
 */
class LauncherIconColorsTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun parse(path: String): org.w3c.dom.Document {
        val file = File(path)
        assertTrue("$path not found at ${file.absolutePath}", file.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun colourResources(): Map<String, String> {
        val nodes = parse("src/main/res/values/colors.xml").getElementsByTagName("color")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent.trim() }
    }

    private fun argb(hex: String): Int {
        val digits = hex.removePrefix("#")
        return (if (digits.length == 6) "FF$digits" else digits).toLong(16).toInt()
    }

    /** Each path's (fillColor, strokeColor) in the icon foreground, in document order. */
    private fun iconPathColours(): List<Pair<String, String>> {
        val nodes = parse("src/main/res/drawable/ic_launcher_foreground.xml").getElementsByTagName("path")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .map { it.getAttributeNS(androidNs, "fillColor") to it.getAttributeNS(androidNs, "strokeColor") }
    }

    // HOME-006 invariant: the Kotlin definition equals the icon's colour resources.
    @Test
    fun kotlinDefinitionMatchesTheColourResources() {
        val colours = colourResources()
        assertEquals(LauncherIconColors.FIGURE, argb(colours.getValue("launcher_icon_figure")))
        assertEquals(LauncherIconColors.MAT, argb(colours.getValue("launcher_icon_mat")))
    }

    // HOME-006 invariant: the icon draws its mat, head and body with those resources, so the
    // icon and the ring cannot drift apart.
    @Test
    fun launcherIconDrawsWithTheSharedColourResources() {
        val transparent = "#00000000"
        assertEquals(
            listOf(
                transparent to "@color/launcher_icon_mat", // mat
                "@color/launcher_icon_figure" to "", // head
                transparent to "@color/launcher_icon_figure", // body
            ),
            iconPathColours(),
        )
    }
}
