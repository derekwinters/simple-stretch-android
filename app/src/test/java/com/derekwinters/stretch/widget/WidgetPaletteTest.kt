package com.derekwinters.stretch.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Requirement IDs refer to docs/spec/widgets.md. */
class WidgetPaletteTest {

    private val tones = DynamicTones(
        primaryLight = 0xFF112233.toInt(), primaryDark = 0xFF445566.toInt(),
        surfaceLight = 0xFFFAFAFA.toInt(), surfaceDark = 0xFF1A1A1A.toInt(),
        onSurfaceLight = 0xFF101010.toInt(), onSurfaceDark = 0xFFEEEEEE.toInt(),
        outlineLight = 0xFF777777.toInt(), outlineDark = 0xFF999999.toInt(),
    )

    // WIDGET-008: the fixed values are the mockup's.
    @Test
    fun fixedValuesMatchTheMockup() {
        val dark = WidgetPalette.FIXED_DARK
        assertEquals(0xFF3D86D1.toInt(), dark.arc)
        assertEquals(0x8C121212.toInt(), dark.disc) // 55%
        assertEquals(0xFFF5F5F5.toInt(), dark.text)
        assertEquals(0x2EFFFFFF, dark.track) // white at 18%
        assertEquals(0xFF818589.toInt(), dark.mat)
        assertEquals(0x66F5F5F5, dark.dimmedFigure) // text at 40%

        val light = WidgetPalette.FIXED_LIGHT
        assertEquals(0xFF1361A7.toInt(), light.arc)
        assertEquals(0x8CFFFFFF.toInt(), light.disc)
        assertEquals(0xFF1C1B1F.toInt(), light.text)
        assertEquals(0x24000000, light.track) // black at 14%
        assertEquals(0xFF818589.toInt(), light.mat)
        assertEquals(0x661C1B1F, light.dimmedFigure)
    }

    // WIDGET-008 invariant: fixed colours only without dynamic colour; dynamic tones are not
    // even read then.
    @Test
    fun fixedOnlyWithoutDynamicColour() {
        assertSame(WidgetPalette.FIXED_LIGHT, WidgetPalette.select(dynamicAvailable = false, dark = false) { error("read") })
        assertSame(WidgetPalette.FIXED_DARK, WidgetPalette.select(dynamicAvailable = false, dark = true) { error("read") })
    }

    // WIDGET-008: with dynamic colour every part comes from the dynamic roles.
    @Test
    fun dynamicRoles() {
        val light = WidgetPalette.select(dynamicAvailable = true, dark = false) { tones }
        assertEquals(WidgetColors(
            arc = 0xFF112233.toInt(),
            disc = 0x8CFAFAFA.toInt(),
            text = 0xFF101010.toInt(),
            track = 0x24101010,
            mat = 0xFF777777.toInt(),
            dimmedFigure = 0x66101010,
        ), light)

        val dark = WidgetPalette.select(dynamicAvailable = true, dark = true) { tones }
        assertEquals(WidgetColors(
            arc = 0xFF445566.toInt(),
            disc = 0x8C1A1A1A.toInt(),
            text = 0xFFEEEEEE.toInt(),
            track = 0x2EEEEEEE,
            mat = 0xFF999999.toInt(),
            dimmedFigure = 0x66EEEEEE,
        ), dark)
    }
}
