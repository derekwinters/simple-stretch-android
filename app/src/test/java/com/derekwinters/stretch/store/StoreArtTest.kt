package com.derekwinters.stretch.store

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Requirement IDs refer to docs/spec/store-listing.md. Renders the Google Play store icon and
 * feature graphic on the STORE-SHOT-006 device, and records or verifies them as the Roborazzi
 * Gradle task asks (STORE-ICON-004):
 *
 * - `./gradlew verifyRoborazziDebug` (what CI runs) compares each image with the committed
 *   fastlane/metadata/android/en-US/images/icon.png and featureGraphic.png, exactly, and fails on
 *   any change or a missing file;
 * - `./gradlew recordRoborazziDebug` re-records them, only when a person asks;
 * - plain `./gradlew test` renders them and writes or compares nothing.
 *
 * The icon is drawn from the launcher icon's own resources (STORE-ICON-002 invariant), so a
 * change to `ic_launcher_foreground` or `ic_launcher_background` fails the verify until the
 * store icon is re-recorded.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [StoreShotDevice.SDK], qualifiers = StoreShotDevice.QUALIFIERS, application = Application::class)
class StoreArtTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val white = 0xFFFFFFFF.toInt()
    private val blue = 0xFF1361A7.toInt()
    private val matGrey = 0xFF818589.toInt()

    // STORE-ICON-001 to STORE-ICON-004: render the store icon, then record or verify it.
    @Test
    fun icon() {
        StoreImageRecorder.fromRoborazzi().apply(StoreImage.ICON, StoreArt.icon(context))
    }

    // STORE-GRAPHIC-001 to STORE-GRAPHIC-006, STORE-ICON-004: render the feature graphic, then
    // record or verify it.
    @Test
    fun featureGraphic() {
        StoreImageRecorder.fromRoborazzi().apply(StoreImage.FEATURE_GRAPHIC, StoreArt.featureGraphic(context))
    }

    // STORE-ICON-001: a full, opaque square: every corner is the launcher icon's white background,
    // with no rounding or transparency.
    @Test
    fun iconIsAFullOpaqueSquare() {
        val icon = StoreArt.icon(context)
        assertEquals(512, icon.width)
        assertEquals(512, icon.height)
        for ((x, y) in listOf(0 to 0, 511 to 0, 0 to 511, 511 to 511)) {
            assertEquals("corner ($x, $y)", white, icon[x, y])
        }
        assertTrue("every pixel is opaque", icon.pixels.all { it ushr 24 == 0xFF })
    }

    // STORE-ICON-002 / STORE-ICON-003: the launcher foreground is drawn with its 72 dp viewport
    // scaled to 512 px: a launcher point at (x dp, y dp) lands at ((x - 18) * 512 / 72) px.
    @Test
    fun iconDrawsTheLauncherForegroundScaledFromTheSafeZone() {
        val icon = StoreArt.icon(context)
        fun px(dp: Float) = ((dp - 18f) * 512f / 72f).toInt()
        // The head, a #1361A7 disc of radius 6 dp centred at (70, 47.5) dp.
        assertEquals(blue, icon[px(70f), px(47.5f)])
        // The mat, a #818589 line 5 dp thick along y = 74 dp, here at x = 40 dp.
        assertEquals(matGrey, icon[px(40f), px(74f)])
        // Inside the viewport but away from the figure: the white background layer.
        assertEquals(white, icon[px(24f), px(24f)])
    }

    // STORE-GRAPHIC-001 / STORE-GRAPHIC-002: 1024 x 500, opaque, the background colour at every edge.
    @Test
    fun featureGraphicBackgroundRunsToEveryEdge() {
        val graphic = StoreArt.featureGraphic(context)
        assertEquals(1024, graphic.width)
        assertEquals(500, graphic.height)
        for ((x, y) in listOf(0 to 0, 1023 to 0, 0 to 499, 1023 to 499, 512 to 0, 512 to 499, 0 to 250, 1023 to 250)) {
            assertEquals("edge ($x, $y)", blue, graphic[x, y])
        }
        assertTrue("every pixel is opaque", graphic.pixels.all { it ushr 24 == 0xFF })
    }

    // STORE-GRAPHIC-003: a 240 px white tile, vertically centred, with rounded corners.
    @Test
    fun featureGraphicTileIsARoundedWhiteSquare() {
        val layout = StoreArt.featureGraphicLayout()
        val graphic = StoreArt.featureGraphic(context)
        val left = layout.tileLeft
        val top = StoreArtSpec.TILE_TOP_PX
        assertEquals(130, top)
        // The square's corners are cut away by the 54 px radius.
        assertEquals(blue, graphic[left, top])
        assertEquals(blue, graphic[left + 239, top + 239])
        // Just inside the top and bottom edges, mid-way along: the icon's white background.
        assertEquals(white, graphic[left + 120, top + 2])
        assertEquals(white, graphic[left + 2, top + 120])
        // Just outside the tile: the background again.
        assertEquals(blue, graphic[left - 2, top + 120])
        assertEquals(blue, graphic[left + 120, top - 2])
    }

    // STORE-GRAPHIC-004 / STORE-GRAPHIC-005: the name sits 48 px right of the tile and the group is
    // centred horizontally; nothing is drawn to its right.
    @Test
    fun featureGraphicGroupIsCentred() {
        val layout = StoreArt.featureGraphicLayout()
        assertEquals(layout.tileLeft + StoreArtSpec.TILE_PX + StoreArtSpec.NAME_GAP_PX, layout.nameLeft)
        val right = 1024 - (layout.nameLeft + layout.nameWidth)
        assertTrue("left ${layout.tileLeft} vs right $right", abs(layout.tileLeft - right) <= 1f)

        val graphic = StoreArt.featureGraphic(context)
        val nameRight = (layout.nameLeft + layout.nameWidth).roundToInt()
        val nameTop = (StoreArtSpec.NAME_BASELINE_PX - 60f).toInt()
        val nameBottom = StoreArtSpec.NAME_BASELINE_PX.toInt()
        var whiteInName = 0
        for (y in nameTop until nameBottom) {
            for (x in layout.nameLeft until nameRight) if (graphic[x, y] == white) whiteInName++
            for (x in nameRight + 4 until 1024) assertEquals("right of the name ($x, $y)", blue, graphic[x, y])
        }
        assertTrue("the name is drawn in white", whiteInName > 1000)
    }

    // STORE-GRAPHIC-004: the baseline centres Roboto's cap height (1456 / 2048 em) at 80 px on y = 250.
    @Test
    fun nameBaselineCentresTheCapHeight() {
        val capHeight = StoreArtSpec.NAME_SIZE_PX * 1456f / 2048f
        assertEquals((250f + capHeight / 2f).roundToInt().toFloat(), StoreArtSpec.NAME_BASELINE_PX)
        assertEquals(278f, StoreArtSpec.NAME_BASELINE_PX)
        assertEquals("Simple Stretch", StoreArtSpec.NAME)
    }

    // STORE-GRAPHIC-006: the name is drawn in the committed Roboto Bold, not a system font.
    @Test
    fun nameIsInTheCommittedRobotoBold() {
        val typeface = StoreArt.nameTypeface()
        assertNotSame(Typeface.DEFAULT, typeface)
        assertEquals(700, typeface.weight)
    }

    // STORE-GRAPHIC-006: the Roboto Bold file and its Apache 2.0 licence text are committed.
    @Test
    fun robotoAndItsLicenceAreCommitted() {
        assertNotNull(StoreArtTest::class.java.getResource(StoreArtSpec.FONT_RESOURCE))
        val licence = StoreArtTest::class.java.getResource(StoreArtSpec.FONT_LICENCE_RESOURCE)?.readText()
        assertNotNull("${StoreArtSpec.FONT_LICENCE_RESOURCE} is missing", licence)
        assertTrue(licence!!.contains("Apache License") && licence.contains("Version 2.0"))
    }
}
