@file:OptIn(ExperimentalRoborazziApi::class)

package com.derekwinters.stretch.store

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.roborazziSystemPropertyCompareOutputDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * The two store images beside the screenshots (docs/spec/store-listing.md): their requirement,
 * their file in fastlane's `images` folder, their size and whether the PNG carries alpha.
 */
enum class StoreImage(val id: String, val fileName: String, val width: Int, val height: Int, val alpha: Boolean) {
    /** STORE-ICON-001: 512 x 512, 8-bit RGBA. */
    ICON("STORE-ICON-001", "icon.png", 512, 512, alpha = true),

    /** STORE-GRAPHIC-001: 1024 x 500, 8-bit RGB, no alpha. */
    FEATURE_GRAPHIC("STORE-GRAPHIC-001", "featureGraphic.png", 1024, 500, alpha = false),
    ;

    /** The committed file's PNG header: 8 bits per channel, colour type 6 (RGBA) or 2 (RGB). */
    val header: PngHeader
        get() = PngHeader(width, height, bitDepth = 8, colorType = if (alpha) Png.COLOR_TYPE_RGBA else Png.COLOR_TYPE_RGB)
}

/** Where the store images are committed. */
object StoreImages {
    private const val RELATIVE_DIR = "fastlane/metadata/android/en-US/images"

    fun file(image: StoreImage): File = File(dir(), image.fileName)

    /**
     * Unit tests run with the module directory (app/) as the working directory; the directories
     * above it are tried too, as for the captions file.
     */
    fun dir(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, RELATIVE_DIR)
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("$RELATIVE_DIR not found above ${File("").absolutePath}")
    }
}

/** A rendered image: [pixels] are non-premultiplied ARGB, row by row. */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) {
            "$width x $height image with ${pixels.size} pixels"
        }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
}

/** The fields of a PNG's IHDR chunk that the store images are checked against. */
data class PngHeader(val width: Int, val height: Int, val bitDepth: Int, val colorType: Int)

/** Encodes, decodes and inspects PNGs with the JDK's own encoder (STORE-ICON-004). */
object Png {
    const val COLOR_TYPE_RGB = 2
    const val COLOR_TYPE_RGBA = 6

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

    /**
     * 8-bit RGBA (colour type 6) when [alpha], else 8-bit RGB (colour type 2) with the alpha
     * dropped. The JDK's PNG writer picks the colour type from the image type.
     */
    fun encode(image: ArgbImage, alpha: Boolean): ByteArray {
        val buffered = BufferedImage(
            image.width,
            image.height,
            if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB,
        )
        buffered.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        val out = ByteArrayOutputStream()
        check(ImageIO.write(buffered, "png", out)) { "no PNG writer" }
        return out.toByteArray()
    }

    /** ARGB pixels of a PNG; a PNG without alpha decodes as opaque. */
    fun decode(png: ByteArray): ArgbImage {
        val buffered = ImageIO.read(ByteArrayInputStream(png)) ?: throw IllegalArgumentException("not a readable PNG")
        val pixels = buffered.getRGB(0, 0, buffered.width, buffered.height, null, 0, buffered.width)
        return ArgbImage(buffered.width, buffered.height, pixels)
    }

    /** Reads the IHDR chunk, which the PNG format requires to come first after the signature. */
    fun header(png: ByteArray): PngHeader {
        require(png.size >= 26 && png.copyOfRange(0, 8).contentEquals(SIGNATURE)) { "not a PNG" }
        require(String(png, 12, 4, Charsets.US_ASCII) == "IHDR") { "PNG does not start with IHDR" }
        fun int(at: Int) = ((png[at].toInt() and 0xFF) shl 24) or ((png[at + 1].toInt() and 0xFF) shl 16) or
            ((png[at + 2].toInt() and 0xFF) shl 8) or (png[at + 3].toInt() and 0xFF)
        return PngHeader(width = int(16), height = int(20), bitDepth = png[24].toInt() and 0xFF, colorType = png[25].toInt() and 0xFF)
    }
}

/**
 * STORE-ICON-004: writes and compares the store icon and the feature graphic in the mode the
 * Roborazzi Gradle task sets, since Roborazzi itself writes only RGBA. [imagesDir] holds the
 * committed images; [compareDir] receives `<name>_actual.png` when a verify or compare finds a
 * difference.
 *
 * - Recording (`recordRoborazziDebug`): writes the image, then reads back its header.
 * - Verifying (`verifyRoborazziDebug`): compares exactly and fails on a missing file or any
 *   difference in size or pixels, leaving the committed file untouched (or, in verify-and-record,
 *   re-recording it).
 * - Comparing: writes the actual image on a difference, without failing.
 * - Neither (plain `./gradlew test`): nothing.
 */
class StoreImageRecorder(
    private val imagesDir: File,
    private val compareDir: File,
    private val taskType: RoborazziTaskType,
) {
    fun apply(image: StoreImage, rendered: ArgbImage) {
        require(rendered.width == image.width && rendered.height == image.height) {
            "${image.id}: rendered ${rendered.width} x ${rendered.height}, expected ${image.width} x ${image.height}"
        }
        if (!taskType.isEnabled()) return

        val file = File(imagesDir, image.fileName)
        val matches by lazy { matchesCommitted(file, image, rendered) }

        if ((taskType.isVerifying() || taskType.isComparing()) && !matches) {
            compareDir.mkdirs()
            File(compareDir, "${image.fileName.substringBeforeLast('.')}_actual.png")
                .writeBytes(Png.encode(rendered, image.alpha))
        }
        if (taskType.isRecording() && !(taskType.isVerifying() && matches)) {
            imagesDir.mkdirs()
            file.writeBytes(Png.encode(rendered, image.alpha))
            val written = Png.header(file.readBytes())
            check(written == image.header) { "${image.id}: wrote $written, expected ${image.header}" }
        }
        if (taskType.isVerifying() && !matches) {
            throw AssertionError(
                "${image.id}: $file " + (if (file.isFile) "differs from" else "is missing for") +
                    " the rendered image (see ${compareDir.path}). If the change is intended, re-record " +
                    "it: apply the record-store-screenshots label or run ./gradlew recordRoborazziDebug.",
            )
        }
    }

    /** Exact equality; a PNG without alpha is compared as opaque. */
    private fun matchesCommitted(file: File, image: StoreImage, rendered: ArgbImage): Boolean {
        if (!file.isFile) return false
        val committed = runCatching { Png.decode(file.readBytes()) }.getOrNull() ?: return false
        if (committed.width != rendered.width || committed.height != rendered.height) return false
        val expected = if (image.alpha) rendered.pixels else IntArray(rendered.pixels.size) { rendered.pixels[it] or OPAQUE }
        return committed.pixels.contentEquals(expected)
    }

    companion object {
        private const val OPAQUE = 0xFF000000.toInt()

        /** The recorder for this test run: the committed folder and Roborazzi's mode and compare folder. */
        fun fromRoborazzi(): StoreImageRecorder = StoreImageRecorder(
            imagesDir = StoreImages.dir(),
            compareDir = File(roborazziSystemPropertyCompareOutputDirectory()),
            taskType = roborazziSystemPropertyTaskType(),
        )
    }
}
