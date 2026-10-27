@file:OptIn(ExperimentalRoborazziApi::class)

package com.derekwinters.stretch.store

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

/**
 * Requirement IDs refer to docs/spec/store-listing.md. Checks the committed store icon and
 * feature graphic in fastlane/metadata/android/en-US/images/ (STORE-ICON-005).
 */
class StoreImageFilesTest {

    /** STORE-ICON-005: skipped only in a recording run, which writes the files itself. */
    @Before
    fun notRecording() {
        assumeFalse("a recording run writes the store images", roborazziSystemPropertyTaskType().isRecording())
    }

    // STORE-ICON-005 / STORE-ICON-001: icon.png is committed as a 512 x 512 RGBA PNG.
    @Test
    fun committedIconIs512SquareRgba() = assertCommitted(StoreImage.ICON)

    // STORE-ICON-005 / STORE-ICON-001: icon.png is within Google Play's 1024 KB store icon limit.
    @Test
    fun committedIconIsWithinPlaysSizeLimit() {
        val file = StoreImages.file(StoreImage.ICON)
        assertTrue("$file is missing (${StoreImage.ICON.id})", file.isFile)
        assertTrue(
            "${StoreImage.ICON.id}: ${file.name} is ${file.length()} bytes, over Google Play's " +
                "$ICON_MAX_BYTES-byte (1024 KB) limit",
            file.length() <= ICON_MAX_BYTES,
        )
    }

    // STORE-ICON-005 / STORE-GRAPHIC-001: featureGraphic.png is committed as a 1024 x 500 RGB PNG.
    @Test
    fun committedFeatureGraphicIs1024x500Rgb() = assertCommitted(StoreImage.FEATURE_GRAPHIC)

    private companion object {
        /** STORE-ICON-001: Google Play's store icon limit, 1024 KB. */
        const val ICON_MAX_BYTES = 1024L * 1024L
    }

    private fun assertCommitted(image: StoreImage) {
        val file = StoreImages.file(image)
        assertTrue("$file is missing (${image.id}); record it with ./gradlew recordRoborazziDebug", file.isFile)
        assertEquals("${image.id}: ${image.fileName}", image.header, Png.header(file.readBytes()))
    }
}
