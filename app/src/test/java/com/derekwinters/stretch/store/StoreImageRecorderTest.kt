@file:OptIn(ExperimentalRoborazziApi::class)

package com.derekwinters.stretch.store

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziTaskType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Requirement IDs refer to docs/spec/store-listing.md. How the store icon and the feature graphic
 * are encoded, written and compared, on plain JVM images (no Robolectric).
 */
class StoreImageRecorderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val white = 0xFFFFFFFF.toInt()
    private val blue = 0xFF1361A7.toInt()

    private fun solid(image: StoreImage, argb: Int) =
        ArgbImage(image.width, image.height, IntArray(image.width * image.height) { argb })

    private fun recorder(taskType: RoborazziTaskType, images: File = tmp.newFolder(), compare: File = tmp.newFolder()) =
        StoreImageRecorder(images, compare, taskType)

    // STORE-ICON-001: the store icon is a 512 x 512, 8-bit RGBA PNG (colour type 6).
    @Test
    fun iconIsEncodedAsRgba() {
        val png = Png.encode(solid(StoreImage.ICON, white), alpha = StoreImage.ICON.alpha)
        assertEquals(PngHeader(width = 512, height = 512, bitDepth = 8, colorType = 6), Png.header(png))
    }

    // STORE-GRAPHIC-001: the feature graphic is a 1024 x 500, 8-bit RGB PNG with no alpha (colour type 2).
    @Test
    fun featureGraphicIsEncodedAsRgbWithoutAlpha() {
        val png = Png.encode(solid(StoreImage.FEATURE_GRAPHIC, blue), alpha = StoreImage.FEATURE_GRAPHIC.alpha)
        assertEquals(PngHeader(width = 1024, height = 500, bitDepth = 8, colorType = 2), Png.header(png))
    }

    // STORE-ICON-001: the expected header of each image.
    @Test
    fun expectedHeaders() {
        assertEquals(PngHeader(512, 512, 8, 6), StoreImage.ICON.header)
        assertEquals(PngHeader(1024, 500, 8, 2), StoreImage.FEATURE_GRAPHIC.header)
        assertEquals("icon.png", StoreImage.ICON.fileName)
        assertEquals("featureGraphic.png", StoreImage.FEATURE_GRAPHIC.fileName)
    }

    // STORE-ICON-004: pixels survive the PNG round trip exactly, so a verify can compare exactly.
    @Test
    fun pngRoundTripKeepsEveryPixel() {
        val pixels = IntArray(512 * 512) { i -> if (i % 7 == 0) blue else 0xFF000000.toInt() or ((i * 2654435761L).toInt() and 0xFFFFFF) }
        val image = ArgbImage(512, 512, pixels)
        assertArrayEquals(pixels, Png.decode(Png.encode(image, alpha = true)).pixels)
    }

    // STORE-ICON-004: a committed file re-saved by another PNG writer, with rows filtered by
    // Sub, Up, Average and Paeth as well as None, still decodes to the same pixels.
    @Test
    fun decodeUndoesEveryStandardRowFilter() {
        val width = 7
        val height = 10
        for (alpha in listOf(true, false)) {
            val pixels = IntArray(width * height) { i ->
                val a = if (alpha) (i * 37) and 0xFF else 0xFF
                (a shl 24) or (((i * 53) and 0xFF) shl 16) or (((i * 91) and 0xFF) shl 8) or ((i * 13) and 0xFF)
            }
            val png = FilteredPng.encode(ArgbImage(width, height, pixels), alpha)
            assertArrayEquals("alpha=$alpha", pixels, Png.decode(png).pixels)
        }
    }

    @Test
    fun notAPngIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Png.header("not a png".toByteArray()) }
    }

    // STORE-ICON-004: a recording run writes the file in its format.
    @Test
    fun recordWritesTheFileInItsFormat() {
        val images = tmp.newFolder()
        recorder(RoborazziTaskType.Record, images).apply(StoreImage.FEATURE_GRAPHIC, solid(StoreImage.FEATURE_GRAPHIC, blue))
        val written = File(images, "featureGraphic.png").readBytes()
        assertEquals(StoreImage.FEATURE_GRAPHIC.header, Png.header(written))
        assertTrue(Png.decode(written).pixels.all { it == blue })
    }

    // STORE-ICON-004: a verify run passes on an unchanged image and writes nothing.
    @Test
    fun verifyPassesOnTheSameImage() {
        val images = tmp.newFolder()
        val compare = tmp.newFolder()
        recorder(RoborazziTaskType.Record, images).apply(StoreImage.ICON, solid(StoreImage.ICON, white))
        val before = File(images, "icon.png").readBytes()

        recorder(RoborazziTaskType.Verify, images, compare).apply(StoreImage.ICON, solid(StoreImage.ICON, white))

        assertArrayEquals(before, File(images, "icon.png").readBytes())
        assertTrue(compare.listFiles().orEmpty().isEmpty())
    }

    // STORE-ICON-004: one changed pixel fails the verify, which writes the rendered image to the
    // compare folder and leaves the committed file untouched.
    @Test
    fun verifyFailsOnOneChangedPixel() {
        val images = tmp.newFolder()
        val compare = tmp.newFolder()
        recorder(RoborazziTaskType.Record, images).apply(StoreImage.ICON, solid(StoreImage.ICON, white))
        val before = File(images, "icon.png").readBytes()
        val changed = solid(StoreImage.ICON, white).also { it.pixels[1234] = blue }

        assertThrows(AssertionError::class.java) {
            recorder(RoborazziTaskType.Verify, images, compare).apply(StoreImage.ICON, changed)
        }

        assertArrayEquals(before, File(images, "icon.png").readBytes())
        val actual = File(compare, "icon_actual.png")
        assertTrue(actual.isFile)
        assertEquals(blue, Png.decode(actual.readBytes()).pixels[1234])
    }

    // STORE-ICON-004: a missing committed image fails the verify.
    @Test
    fun verifyFailsOnAMissingFile() {
        val compare = tmp.newFolder()
        assertThrows(AssertionError::class.java) {
            recorder(RoborazziTaskType.Verify, compare = compare)
                .apply(StoreImage.FEATURE_GRAPHIC, solid(StoreImage.FEATURE_GRAPHIC, blue))
        }
        assertTrue(File(compare, "featureGraphic_actual.png").isFile)
    }

    // STORE-ICON-004: a committed image of another size fails the verify.
    @Test
    fun verifyFailsOnADifferentSize() {
        val images = tmp.newFolder()
        File(images, "icon.png").writeBytes(Png.encode(ArgbImage(2, 2, IntArray(4) { white }), alpha = true))
        assertThrows(AssertionError::class.java) {
            recorder(RoborazziTaskType.Verify, images).apply(StoreImage.ICON, solid(StoreImage.ICON, white))
        }
    }

    // STORE-ICON-004: a plain test run renders but writes and compares nothing.
    @Test
    fun plainTestRunWritesNothing() {
        val images = tmp.newFolder()
        val compare = tmp.newFolder()
        recorder(RoborazziTaskType.None, images, compare).apply(StoreImage.ICON, solid(StoreImage.ICON, white))
        assertFalse(File(images, "icon.png").exists())
        assertTrue(compare.listFiles().orEmpty().isEmpty())
    }

    // STORE-ICON-001 / STORE-GRAPHIC-001: a rendered image of the wrong size is a bug in the renderer.
    @Test
    fun wrongRenderedSizeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            recorder(RoborazziTaskType.Record).apply(StoreImage.ICON, solid(StoreImage.FEATURE_GRAPHIC, blue))
        }
    }
}

/**
 * A test-only PNG writer that filters row y with filter type y % 5 (None, Sub, Up, Average,
 * Paeth), as other PNG writers do, to check [Png.decode] undoes each.
 */
private object FilteredPng {
    fun encode(image: ArgbImage, alpha: Boolean): ByteArray {
        val channels = if (alpha) 4 else 3
        val rowBytes = image.width * channels
        val rows = Array(image.height) { y ->
            ByteArray(rowBytes).also { row ->
                for (x in 0 until image.width) {
                    val argb = image[x, y]
                    row[x * channels] = (argb ushr 16).toByte()
                    row[x * channels + 1] = (argb ushr 8).toByte()
                    row[x * channels + 2] = argb.toByte()
                    if (alpha) row[x * channels + 3] = (argb ushr 24).toByte()
                }
            }
        }
        val raw = java.io.ByteArrayOutputStream()
        for (y in 0 until image.height) {
            val filter = y % 5
            raw.write(filter)
            val row = rows[y]
            val up = if (y > 0) rows[y - 1] else ByteArray(rowBytes)
            for (i in 0 until rowBytes) {
                val x = row[i].toInt() and 0xFF
                val a = if (i >= channels) row[i - channels].toInt() and 0xFF else 0
                val b = up[i].toInt() and 0xFF
                val c = if (i >= channels) up[i - channels].toInt() and 0xFF else 0
                val predictor = when (filter) {
                    0 -> 0
                    1 -> a
                    2 -> b
                    3 -> (a + b) / 2
                    else -> {
                        val p = a + b - c
                        val pa = kotlin.math.abs(p - a)
                        val pb = kotlin.math.abs(p - b)
                        val pc = kotlin.math.abs(p - c)
                        if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                    }
                }
                raw.write((x - predictor) and 0xFF)
            }
        }
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val ihdr = java.nio.ByteBuffer.allocate(13).putInt(image.width).putInt(image.height)
            .put(8).put((if (alpha) 6 else 2).toByte()).put(0).put(0).put(0).array()
        chunk(out, "IHDR", ihdr)
        val idat = java.io.ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(idat).use { it.write(raw.toByteArray()) }
        // Split the image data over two IDAT chunks, as streaming writers do.
        val data = idat.toByteArray()
        chunk(out, "IDAT", data.copyOfRange(0, data.size / 2))
        chunk(out, "IDAT", data.copyOfRange(data.size / 2, data.size))
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: java.io.ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = java.util.zip.CRC32().apply { update(typeBytes); update(data) }
        out.write(java.nio.ByteBuffer.allocate(4).putInt(data.size).array())
        out.write(typeBytes)
        out.write(data)
        out.write(java.nio.ByteBuffer.allocate(4).putInt(crc.value.toInt()).array())
    }
}
