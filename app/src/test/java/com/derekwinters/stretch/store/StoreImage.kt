@file:OptIn(ExperimentalRoborazziApi::class)

package com.derekwinters.stretch.store

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.roborazziSystemPropertyCompareOutputDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

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

/**
 * Encodes, decodes and inspects PNGs (STORE-ICON-004) with java.util.zip only: Android unit tests
 * compile against android.jar's API surface, which has no java.awt or javax.imageio.
 */
object Png {
    const val COLOR_TYPE_RGB = 2
    const val COLOR_TYPE_RGBA = 6

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

    /**
     * 8-bit RGBA (colour type 6) when [alpha], else 8-bit RGB (colour type 2) with the alpha
     * dropped. Non-interlaced, every row with filter type 0 (none), one IDAT chunk.
     */
    fun encode(image: ArgbImage, alpha: Boolean): ByteArray {
        val channels = if (alpha) 4 else 3
        val rowBytes = image.width * channels
        val raw = ByteArray((rowBytes + 1) * image.height)
        var at = 0
        for (y in 0 until image.height) {
            raw[at++] = 0 // filter type: none
            for (x in 0 until image.width) {
                val argb = image[x, y]
                raw[at++] = (argb ushr 16).toByte()
                raw[at++] = (argb ushr 8).toByte()
                raw[at++] = argb.toByte()
                if (alpha) raw[at++] = (argb ushr 24).toByte()
            }
        }

        val ihdr = ByteArrayOutputStream()
        DataOutputStream(ihdr).apply {
            writeInt(image.width)
            writeInt(image.height)
            writeByte(8) // bit depth
            writeByte(if (alpha) COLOR_TYPE_RGBA else COLOR_TYPE_RGB)
            writeByte(0) // compression: deflate
            writeByte(0) // filter method: adaptive
            writeByte(0) // interlace: none
        }

        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        writeChunk(out, "IHDR", ihdr.toByteArray())
        writeChunk(out, "IDAT", deflate(raw))
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    /**
     * ARGB pixels of an 8-bit, non-interlaced RGB or RGBA PNG; RGB decodes as opaque. Rows may use
     * any of the five standard filter types, so a file re-saved by another PNG writer still
     * compares. Anything else is rejected with an [IllegalArgumentException].
     */
    fun decode(png: ByteArray): ArgbImage {
        val header = header(png)
        require(header.bitDepth == 8) { "unsupported bit depth ${header.bitDepth}" }
        val channels = when (header.colorType) {
            COLOR_TYPE_RGB -> 3
            COLOR_TYPE_RGBA -> 4
            else -> throw IllegalArgumentException("unsupported colour type ${header.colorType}")
        }
        require(png[28].toInt() == 0) { "interlaced PNGs are not supported" }

        val idat = ByteArrayOutputStream()
        var at = 8
        while (at + 8 <= png.size) {
            val length = readInt(png, at)
            val type = String(png, at + 4, 4, Charsets.US_ASCII)
            require(length >= 0 && at + 12 + length <= png.size) { "truncated $type chunk" }
            if (type == "IDAT") idat.write(png, at + 8, length)
            if (type == "IEND") break
            at += 12 + length
        }

        val width = header.width
        val height = header.height
        val rowBytes = width * channels
        val raw = inflate(idat.toByteArray(), (rowBytes + 1) * height)
        val pixels = IntArray(width * height)
        var previous = ByteArray(rowBytes)
        var current = ByteArray(rowBytes)
        for (y in 0 until height) {
            val rowStart = y * (rowBytes + 1)
            val filter = raw[rowStart].toInt() and 0xFF
            for (i in 0 until rowBytes) {
                val x = raw[rowStart + 1 + i].toInt() and 0xFF
                val a = if (i >= channels) current[i - channels].toInt() and 0xFF else 0
                val b = previous[i].toInt() and 0xFF
                val c = if (i >= channels) previous[i - channels].toInt() and 0xFF else 0
                val value = when (filter) {
                    0 -> x
                    1 -> x + a
                    2 -> x + b
                    3 -> x + (a + b) / 2
                    4 -> x + paeth(a, b, c)
                    else -> throw IllegalArgumentException("unknown filter type $filter in row $y")
                }
                current[i] = value.toByte()
            }
            for (px in 0 until width) {
                val o = px * channels
                val r = current[o].toInt() and 0xFF
                val g = current[o + 1].toInt() and 0xFF
                val bl = current[o + 2].toInt() and 0xFF
                val alpha = if (channels == 4) current[o + 3].toInt() and 0xFF else 0xFF
                pixels[y * width + px] = (alpha shl 24) or (r shl 16) or (g shl 8) or bl
            }
            val swap = previous
            previous = current
            current = swap
        }
        return ArgbImage(width, height, pixels)
    }

    /** Reads the IHDR chunk, which the PNG format requires to come first after the signature. */
    fun header(png: ByteArray): PngHeader {
        require(png.size >= 33 && png.copyOfRange(0, 8).contentEquals(SIGNATURE)) { "not a PNG" }
        require(String(png, 12, 4, Charsets.US_ASCII) == "IHDR") { "PNG does not start with IHDR" }
        return PngHeader(
            width = readInt(png, 16),
            height = readInt(png, 20),
            bitDepth = png[24].toInt() and 0xFF,
            colorType = png[25].toInt() and 0xFF,
        )
    }

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or (bytes[at + 3].toInt() and 0xFF)

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        DataOutputStream(out).apply {
            writeInt(data.size)
            write(typeBytes)
            write(data)
            writeInt(crc.value.toInt())
        }
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(data: ByteArray, expected: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArray(expected)
            var read = 0
            while (read < expected) {
                val n = inflater.inflate(out, read, expected - read)
                if (n == 0) {
                    require(!inflater.finished() && !inflater.needsInput() && !inflater.needsDictionary()) {
                        "image data ends after $read of $expected bytes"
                    }
                }
                read += n
            }
            return out
        } catch (e: DataFormatException) {
            throw IllegalArgumentException("corrupt image data", e)
        } finally {
            inflater.end()
        }
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
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
