package com.derekwinters.stretch.store

import android.app.Application
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Requirement IDs refer to docs/spec/store-listing.md. Composes the Google Play feature graphic
 * on the STORE-SHOT-006 device with the shots' frozen inputs, and records or verifies it as the
 * Roborazzi Gradle task asks (STORE-ICON-004):
 *
 * - `./gradlew verifyRoborazziDebug` (what CI runs) compares it with the committed
 *   fastlane/metadata/android/en-US/images/featureGraphic.png, exactly, and fails on any change
 *   or a missing file;
 * - `./gradlew recordRoborazziDebug` re-records it, only when a person asks;
 * - plain `./gradlew test` renders it and writes or compares nothing.
 *
 * Its two phones show [ShowcaseShotScreen], the same screens as the shots (STORE-GRAPHIC-007), so
 * a change to the Home or session screen, or to [ShowcaseFixture], fails the verify until the
 * graphic is re-recorded.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [StoreShotDevice.SDK], qualifiers = StoreShotDevice.QUALIFIERS, application = Application::class)
class FeatureGraphicTest : ShowcaseComposeTest() {

    private val blue = 0xFF1361A7.toInt()
    private val bezel = 0xFF0A0A0B.toInt()
    private val supportingColour = 0xFFCFE1F5.toInt()

    private val committedLines = CaptionLines(headline = 3, supporting = 2)

    // STORE-GRAPHIC-001 to STORE-GRAPHIC-007, STORE-ICON-004: render the feature graphic, then
    // record or verify it.
    @Test
    fun featureGraphic() {
        StoreImageRecorder.fromRoborazzi().apply(StoreImage.FEATURE_GRAPHIC, render())
    }

    // STORE-GRAPHIC-001 / STORE-GRAPHIC-002: 1024 x 500, opaque, the background colour at the
    // edges the phones and their shadows do not reach, and nothing left of the text.
    @Test
    fun backgroundRunsToTheEdges() {
        val graphic = render()
        assertEquals(1024, graphic.width)
        assertEquals(500, graphic.height)
        assertTrue("every pixel is opaque", graphic.pixels.all { it ushr 24 == 0xFF })
        for ((x, y) in listOf(0 to 0, 1023 to 0, 0 to 499, 0 to 250)) {
            assertEquals("corner or edge ($x, $y)", blue, graphic[x, y])
        }
        for (x in 0..400) {
            assertEquals("top edge ($x, 0)", blue, graphic[x, 0])
            assertEquals("bottom edge ($x, 499)", blue, graphic[x, 499])
        }
        for (y in 0 until 500) for (x in 0 until FeatureGraphicSpec.TEXT_LEFT_PX - 4) {
            assertEquals("left of the text ($x, $y)", blue, graphic[x, y])
        }
    }

    // STORE-GRAPHIC-004: the block is centred vertically: 3 + 2 lines make a 232 px block whose
    // top is at y = 134.
    @Test
    fun textBlockIsCentredVertically() {
        assertEquals(232, FeatureGraphicText.blockHeight(committedLines))
        assertEquals(134, FeatureGraphicText.top(committedLines))
        assertEquals(174, FeatureGraphicText.top(CaptionLines(headline = 2, supporting = 1)))
    }

    // STORE-GRAPHIC-003 / STORE-GRAPHIC-004: the headline is drawn in white and the supporting
    // line in #CFE1F5 inside the block, and nothing above or below the block.
    @Test
    fun textIsDrawnInTheBlock() {
        val text = Captions.featureGraphic()
        val lines = FeatureGraphicText.lines(context, text)
        val graphic = render()
        val top = FeatureGraphicText.top(lines)
        val headlineBottom = top + lines.headline * FeatureGraphicSpec.HEADLINE_LINE_HEIGHT_PX
        val supportingTop = headlineBottom + FeatureGraphicSpec.HEADLINE_TO_SUPPORTING_PX
        val bottom = top + FeatureGraphicText.blockHeight(lines)
        val left = FeatureGraphicSpec.TEXT_LEFT_PX
        val right = left + FeatureGraphicSpec.TEXT_WIDTH_PX

        var white = 0
        for (y in top until headlineBottom) for (x in left until right) if (graphic[x, y] == 0xFFFFFFFF.toInt()) white++
        assertTrue("the headline is drawn in white ($white px)", white > 1000)

        var supporting = 0
        for (y in supportingTop until bottom) for (x in left until right) if (near(graphic[x, y], supportingColour, 6)) supporting++
        assertTrue("the supporting line is drawn in #CFE1F5 ($supporting px)", supporting > 30)

        for (x in left until 400) {
            for (y in 0 until top) assertEquals("above the text ($x, $y)", blue, graphic[x, y])
            for (y in bottom until 500) assertEquals("below the text ($x, $y)", blue, graphic[x, y])
        }
    }

    // STORE-GRAPHIC-004 invariant: the committed text fits its lines.
    @Test
    fun committedTextFits() {
        val text = Captions.featureGraphic()
        FeatureGraphicText.require(text, FeatureGraphicText.lines(context, text))
    }

    // STORE-GRAPHIC-004 invariant: lines are measured as rendered, not counted as characters, and
    // text that does not fit fails instead of shrinking.
    @Test
    fun tooLongTextFails() {
        fun caption(headline: String, supporting: String) = Caption(FeatureGraphicSpec.TEXT_ID, headline, supporting)
        val narrow = FeatureGraphicText.lines(context, caption("i".repeat(40), "Short."))
        val wide = FeatureGraphicText.lines(context, caption("W".repeat(40), "Short."))
        assertTrue("narrow: $narrow", narrow.headline <= FeatureGraphicSpec.MAX_HEADLINE_LINES)
        assertTrue("wide: $wide", wide.headline > FeatureGraphicSpec.MAX_HEADLINE_LINES)

        val longHeadline = caption("Your stretch routine, on your schedule, at home, at work and everywhere else", "Short.")
        val headlineError = assertThrows(AssertionError::class.java) {
            FeatureGraphicText.require(longHeadline, FeatureGraphicText.lines(context, longHeadline))
        }
        assertTrue(headlineError.message!!, headlineError.message!!.contains("headline"))

        val longSupporting = caption("Short", "Reminders, daily goals and weekly trends, with your streak, every stretch you did and more.")
        val supportingError = assertThrows(AssertionError::class.java) {
            FeatureGraphicText.require(longSupporting, FeatureGraphicText.lines(context, longSupporting))
        }
        assertTrue(supportingError.message!!, supportingError.message!!.contains("supporting"))
    }

    // STORE-GRAPHIC-005: after rotating, each frame's bottom edge lies below the image and its top
    // corners inside it, so the phones run off the bottom edge only.
    @Test
    fun phonesRunOffTheBottomEdgeOnly() {
        for (phone in FeatureGraphicSpec.PHONES) {
            for (x in listOf(0f, phone.width.toFloat())) {
                val (bottomX, bottomY) = phone.toImage(x, phone.height.toFloat())
                assertTrue("${phone.shot} bottom corner at ($bottomX, $bottomY)", bottomY > 500f)
                val (topX, topY) = phone.toImage(x, 0f)
                assertTrue("${phone.shot} top corner at ($topX, $topY)", topX in 0f..1024f && topY in 0f..500f)
            }
        }
        assertEquals(StoreShot.SESSION, FeatureGraphicSpec.BACK.shot)
        assertEquals(StoreShot.HOME, FeatureGraphicSpec.FRONT.shot)
        assertEquals(listOf(FeatureGraphicSpec.BACK, FeatureGraphicSpec.FRONT), FeatureGraphicSpec.PHONES)
        // The height shows the same part of the screen as the shot does: bezel + width x 1672 / 886.
        for (phone in FeatureGraphicSpec.PHONES) {
            val screenWidth = phone.width - 2 * FeatureGraphicSpec.BEZEL_PX
            assertEquals(FeatureGraphicSpec.BEZEL_PX + (screenWidth * 1672f / 886f).roundToInt(), phone.height)
        }
    }

    // STORE-GRAPHIC-005 / STORE-GRAPHIC-007: each phone has its #0A0A0B top bezel where the
    // rotation puts it, the front phone shows the light Home screen and the back phone the dark
    // session screen.
    @Test
    fun phonesAreFramedAndShowTheirScreensInTheirThemes() {
        val graphic = render()
        for (phone in FeatureGraphicSpec.PHONES) {
            val (x, y) = phone.toImage(phone.width / 2f, FeatureGraphicSpec.BEZEL_PX / 2f)
            val pixel = graphic[x.roundToInt(), y.roundToInt()]
            assertTrue("${phone.shot} top bezel at ($x, $y) is ${hex(pixel)}", near(pixel, bezel, 2))
        }
        val front = FeatureGraphicSpec.FRONT
        val back = FeatureGraphicSpec.BACK
        assertFalse(front.shot.dark)
        assertTrue(back.shot.dark)
        val homeLuminance = meanLuminance(graphic, front.toImage(front.width / 2f, 250f))
        assertTrue("the Home screen is light ($homeLuminance)", homeLuminance > 170)
        val sessionLuminance = meanLuminance(graphic, back.toImage(back.width / 2f, 300f))
        assertTrue("the session screen is dark ($sessionLuminance)", sessionLuminance < 110)
    }

    // STORE-GRAPHIC-005: the front phone casts a shadow: just outside its left edge, near the
    // bottom, the background is darkened.
    @Test
    fun frontPhoneCastsAShadow() {
        val graphic = render()
        val front = FeatureGraphicSpec.FRONT
        val (x, y) = front.toImage(-12f, front.height - 50f)
        val pixel = graphic[x.roundToInt(), y.roundToInt()]
        assertTrue("($x, $y) is ${hex(pixel)}, not darker than the background", (pixel and 0xFF) < (blue and 0xFF) - 6)
    }

    // STORE-GRAPHIC-002 and STORE-GRAPHIC-007 invariants: the graphic's code never draws the
    // launcher or store icon, and never reads an image file for its screens.
    @Test
    fun graphicDrawsNoIconAndPastesNoImage() {
        val source = repoFile("app/src/test/java/com/derekwinters/stretch/store/FeatureGraphic.kt").readText()
        for (forbidden in listOf("ic_launcher", "drawLauncherIcon", "StoreArt.", "BitmapFactory", "Png.decode", "phoneScreenshots", "painterResource")) {
            assertFalse("FeatureGraphic.kt uses $forbidden", source.contains(forbidden))
        }
    }

    // STORE-GRAPHIC-006: both fonts load from the committed files, Bold at weight 700 and Regular
    // at 400, and their Apache 2.0 licence text is committed beside them.
    @Test
    fun fontsAreTheCommittedRoboto() {
        assertEquals(700, FeatureGraphicFonts.typeface(FeatureGraphicFonts.boldFile).weight)
        assertEquals(400, FeatureGraphicFonts.typeface(FeatureGraphicFonts.regularFile).weight)
        val licence = FeatureGraphicTest::class.java.getResource(FeatureGraphicSpec.FONT_LICENCE_RESOURCE)?.readText()
        assertNotNull("${FeatureGraphicSpec.FONT_LICENCE_RESOURCE} is missing", licence)
        assertTrue(licence!!.contains("Apache License") && licence.contains("Version 2.0"))
    }

    /**
     * STORE-GRAPHIC-004 / STORE-GRAPHIC-007: checks the text fits, composes the graphic with the
     * shots' screens and captures it.
     */
    private fun render(): ArgbImage {
        val text = Captions.featureGraphic()
        val lines = FeatureGraphicText.lines(context, text)
        FeatureGraphicText.require(text, lines)
        composeRule.setContent {
            FeatureGraphic(text, lines) { shot -> ShowcaseShotScreen(shot, context) }
        }
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag(FeatureGraphicSpec.TAG).captureToImage().asAndroidBitmap().toArgbImage()
    }

    private fun near(a: Int, b: Int, tolerance: Int): Boolean =
        listOf(16, 8, 0).all { shift -> abs(((a ushr shift) and 0xFF) - ((b ushr shift) and 0xFF)) <= tolerance }

    private fun meanLuminance(image: ArgbImage, at: Pair<Float, Float>): Double {
        val cx = at.first.roundToInt()
        val cy = at.second.roundToInt()
        var sum = 0.0
        var n = 0
        for (y in cy - 4..cy + 4) for (x in cx - 4..cx + 4) {
            val p = image[x, y]
            sum += 0.2126 * ((p ushr 16) and 0xFF) + 0.7152 * ((p ushr 8) and 0xFF) + 0.0722 * (p and 0xFF)
            n++
        }
        return sum / n
    }

    private fun hex(pixel: Int) = "#%08X".format(pixel)
}
