package com.derekwinters.stretch.store

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Requirement IDs refer to docs/spec/store-listing.md. Renders the Google Play store icon on the
 * STORE-SHOT-006 device, and records or verifies it as the Roborazzi Gradle task asks
 * (STORE-ICON-004); the feature graphic is [FeatureGraphicTest]'s:
 *
 * - `./gradlew verifyRoborazziDebug` (what CI runs) compares the image with the committed
 *   fastlane/metadata/android/en-US/images/icon.png, exactly, and fails on any change or a
 *   missing file;
 * - `./gradlew recordRoborazziDebug` re-records it, only when a person asks;
 * - plain `./gradlew test` renders it and writes or compares nothing.
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
}
