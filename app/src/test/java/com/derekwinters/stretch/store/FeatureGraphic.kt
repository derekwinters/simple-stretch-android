// Loading a Font from a File has been marked experimental in some Compose releases; the opt-in
// keeps this compiling either way.
@file:OptIn(ExperimentalTextApi::class)

package com.derekwinters.stretch.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * The feature graphic's geometry (docs/spec/store-listing.md, STORE-GRAPHIC-002 to
 * STORE-GRAPHIC-006), in pixels of the final image. Its size and format are in [StoreImage].
 */
object FeatureGraphicSpec {
    const val WIDTH_PX = 1024
    const val HEIGHT_PX = 500

    /** STORE-GRAPHIC-002: the screenshots' background colour (STORE-SHOT-011). */
    val BACKGROUND = StoreShotSpec.BACKGROUND

    /** STORE-GRAPHIC-003: the entry of docs/store/captions.md holding the text. */
    const val TEXT_ID = "STORE-GRAPHIC-003"

    // STORE-GRAPHIC-004: the text block.
    const val TEXT_LEFT_PX = 64
    const val TEXT_WIDTH_PX = 380
    val HEADLINE_COLOR = Color.White
    const val HEADLINE_SIZE_PX = 46f
    const val HEADLINE_LINE_HEIGHT_PX = 52
    const val MAX_HEADLINE_LINES = 3
    const val HEADLINE_TO_SUPPORTING_PX = 18
    val SUPPORTING_COLOR = Color(0xFFCFE1F5)
    const val SUPPORTING_SIZE_PX = 21f
    const val SUPPORTING_LINE_HEIGHT_PX = 29
    const val MAX_SUPPORTING_LINES = 2

    // STORE-GRAPHIC-005: the phones' frames and their shadow.
    const val BEZEL_PX = 9
    const val FRAME_CORNER_PX = 36
    val BEZEL_COLOR = StoreShotSpec.BEZEL_COLOR
    const val SHADOW_COLOR = 0x59000000
    const val SHADOW_DY_PX = 18f
    const val SHADOW_RADIUS_PX = 42f

    /**
     * STORE-GRAPHIC-005: one phone's frame before rotating, and its rotation in degrees
     * (positive clockwise) about the frame's bottom centre.
     */
    data class Phone(
        val shot: StoreShot,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val rotationDegrees: Float,
    ) {
        /** Where the frame's own point ([x], [y]) from its top-left lands in the image, rotated. */
        fun toImage(x: Float, y: Float): Pair<Float, Float> {
            val pivotX = left + width / 2f
            val pivotY = (top + height).toFloat()
            val dx = left + x - pivotX
            val dy = top + y - pivotY
            val angle = Math.toRadians(rotationDegrees.toDouble())
            return Pair(
                (pivotX + dx * cos(angle) - dy * sin(angle)).toFloat(),
                (pivotY + dx * sin(angle) + dy * cos(angle)).toFloat(),
            )
        }
    }

    /** The session screen in the dark theme (STORE-SHOT-002), behind the front phone. */
    val BACK = Phone(StoreShot.SESSION, left = 720, top = 96, width = 250, height = 447, rotationDegrees = 6f)

    /** Home in the light theme (STORE-SHOT-001), in front. */
    val FRONT = Phone(StoreShot.HOME, left = 520, top = 50, width = 270, height = 485, rotationDegrees = -4f)

    /** STORE-GRAPHIC-002: drawing order, back first. */
    val PHONES = listOf(BACK, FRONT)

    // STORE-GRAPHIC-006: the committed fonts and their licence, as test resources.
    const val BOLD_FONT_RESOURCE = "/store/fonts/Roboto-Bold.ttf"
    const val REGULAR_FONT_RESOURCE = "/store/fonts/Roboto-Regular.ttf"
    const val FONT_LICENCE_RESOURCE = "/store/fonts/LICENSE.txt"

    /** Test tag of the whole graphic; its bounds are the captured image. */
    const val TAG = "feature-graphic"

    /** STORE-GRAPHIC-004: the headline's style at [density] (pixels to sp through the density). */
    fun headlineStyle(density: Density): TextStyle = with(density) {
        TextStyle(
            color = HEADLINE_COLOR,
            fontFamily = FeatureGraphicFonts.family,
            fontSize = HEADLINE_SIZE_PX.toSp(),
            fontWeight = FontWeight.Bold,
            lineHeight = HEADLINE_LINE_HEIGHT_PX.toSp(),
            lineHeightStyle = LINE_HEIGHT_STYLE,
            textAlign = TextAlign.Start,
        )
    }

    /** STORE-GRAPHIC-004: the supporting line's style at [density]. */
    fun supportingStyle(density: Density): TextStyle = with(density) {
        TextStyle(
            color = SUPPORTING_COLOR,
            fontFamily = FeatureGraphicFonts.family,
            fontSize = SUPPORTING_SIZE_PX.toSp(),
            fontWeight = FontWeight.Normal,
            lineHeight = SUPPORTING_LINE_HEIGHT_PX.toSp(),
            lineHeightStyle = LINE_HEIGHT_STYLE,
            textAlign = TextAlign.Start,
        )
    }

    /** Each line is exactly its line height, the text centred in it, nothing trimmed. */
    private val LINE_HEIGHT_STYLE = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )
}

/**
 * STORE-GRAPHIC-006: Roboto Bold and Roboto Regular, loaded from the committed files, never a
 * system font. Each file is copied out of the test resources once, since a font loads from a file.
 */
object FeatureGraphicFonts {
    val boldFile: File by lazy { extract(FeatureGraphicSpec.BOLD_FONT_RESOURCE) }
    val regularFile: File by lazy { extract(FeatureGraphicSpec.REGULAR_FONT_RESOURCE) }

    val family: FontFamily by lazy {
        FontFamily(Font(boldFile, FontWeight.Bold), Font(regularFile, FontWeight.Normal))
    }

    /**
     * The typeface in [file]. A file that fails to load would silently become
     * [Typeface.DEFAULT], so that fails here instead.
     */
    fun typeface(file: File): Typeface {
        val typeface = Typeface.createFromFile(file)
        check(typeface !== Typeface.DEFAULT) { "${file.name} did not load (STORE-GRAPHIC-006)" }
        return typeface
    }

    private fun extract(resource: String): File {
        val stream = FeatureGraphicFonts::class.java.getResourceAsStream(resource)
            ?: error("$resource is not on the test classpath (STORE-GRAPHIC-006)")
        val name = resource.substringAfterLast('/')
        val file = File.createTempFile(name.substringBeforeLast('.'), ".ttf").apply { deleteOnExit() }
        stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        typeface(file)
        return file
    }
}

/**
 * STORE-GRAPHIC-004: the text as laid out: its rendered line counts and the two texts' laid-out
 * heights in pixels, from which the block's height and its vertically centred top follow.
 */
data class FeatureGraphicTextLayout(val lines: CaptionLines, val headlineHeight: Int, val supportingHeight: Int) {
    val blockHeight: Int get() = headlineHeight + FeatureGraphicSpec.HEADLINE_TO_SUPPORTING_PX + supportingHeight

    /** The block's top, centring it vertically (rounded down). */
    val top: Int get() = (FeatureGraphicSpec.HEIGHT_PX - blockHeight) / 2
}

/**
 * STORE-GRAPHIC-004: the text measured as the graphic renders it (the same styles, at the
 * block's width, laid out by the platform's text engine at the device density), and the limits
 * on its lines.
 */
object FeatureGraphicText {
    fun measure(context: Context, text: Caption): FeatureGraphicTextLayout {
        val density = Density(context)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val width = Constraints(maxWidth = FeatureGraphicSpec.TEXT_WIDTH_PX)
        val headline = measurer.measure(text = text.headline, style = FeatureGraphicSpec.headlineStyle(density), constraints = width)
        val supporting = measurer.measure(text = text.supporting, style = FeatureGraphicSpec.supportingStyle(density), constraints = width)
        return FeatureGraphicTextLayout(
            lines = CaptionLines(headline = headline.lineCount, supporting = supporting.lineCount),
            headlineHeight = headline.size.height,
            supportingHeight = supporting.size.height,
        )
    }

    fun lines(context: Context, text: Caption): CaptionLines = measure(context, text).lines

    /**
     * STORE-GRAPHIC-004 invariant: text over either limit fails the test. Nothing shrinks it,
     * widens the block or moves the phones; the text must be shortened.
     */
    fun require(text: Caption, lines: CaptionLines) {
        if (lines.headline > FeatureGraphicSpec.MAX_HEADLINE_LINES) {
            throw AssertionError(
                "${text.id} headline is ${lines.headline} lines as rendered, at most " +
                    "${FeatureGraphicSpec.MAX_HEADLINE_LINES} fit (STORE-GRAPHIC-004); shorten it in " +
                    "docs/store/captions.md: \"${text.headline}\"",
            )
        }
        if (lines.supporting > FeatureGraphicSpec.MAX_SUPPORTING_LINES) {
            throw AssertionError(
                "${text.id} supporting line is ${lines.supporting} lines as rendered, at most " +
                    "${FeatureGraphicSpec.MAX_SUPPORTING_LINES} fit (STORE-GRAPHIC-004); shorten it in " +
                    "docs/store/captions.md: \"${text.supporting}\"",
            )
        }
    }
}

/**
 * STORE-GRAPHIC-001 to STORE-GRAPHIC-005: the 1024 x 500 feature graphic: the solid background,
 * the text block ([text], already measured as [layout] and checked to fit) and the two phones, back then
 * front, each showing [screen] for its shot. [screen] is the app screen in its theme
 * ([ShowcaseShotScreen], STORE-GRAPHIC-007); nothing else here is themed. Nothing draws the app
 * icon (STORE-GRAPHIC-002 invariant) or reads an image file (STORE-GRAPHIC-007 invariant).
 */
@Composable
fun FeatureGraphic(text: Caption, layout: FeatureGraphicTextLayout, screen: @Composable (StoreShot) -> Unit) {
    val density = LocalDensity.current
    fun px(value: Int): Dp = with(density) { value.toDp() }

    Box(
        Modifier
            .requiredSize(px(FeatureGraphicSpec.WIDTH_PX), px(FeatureGraphicSpec.HEIGHT_PX))
            .clipToBounds()
            .background(FeatureGraphicSpec.BACKGROUND)
            .testTag(FeatureGraphicSpec.TAG),
    ) {
        Column(
            Modifier
                .offset(x = px(FeatureGraphicSpec.TEXT_LEFT_PX), y = px(layout.top))
                .width(px(FeatureGraphicSpec.TEXT_WIDTH_PX)),
        ) {
            // No maxLines: text that does not fit is caught by FeatureGraphicText, never cut or shrunk.
            Text(text.headline, style = FeatureGraphicSpec.headlineStyle(density))
            Spacer(Modifier.height(px(FeatureGraphicSpec.HEADLINE_TO_SUPPORTING_PX)))
            Text(text.supporting, style = FeatureGraphicSpec.supportingStyle(density))
        }

        for (phone in FeatureGraphicSpec.PHONES) {
            TiltedPhone(phone) { screen(phone.shot) }
        }
    }
}

/**
 * STORE-GRAPHIC-005: one phone: the shots' frame ([PhoneFrame]) at this phone's size, rotated
 * about its bottom centre, over a drop shadow that rotates with it.
 */
@Composable
private fun TiltedPhone(phone: FeatureGraphicSpec.Phone, screen: @Composable () -> Unit) {
    val density = LocalDensity.current
    fun px(value: Int): Dp = with(density) { value.toDp() }

    Box(
        Modifier
            .offset(x = px(phone.left), y = px(phone.top))
            .requiredSize(px(phone.width), px(phone.height))
            .graphicsLayer {
                rotationZ = phone.rotationDegrees
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .drawBehind {
                // The frame's outline, filled in the bezel colour with the shadow layer; the frame
                // itself is drawn over it.
                val corner = FeatureGraphicSpec.FRAME_CORNER_PX.toFloat()
                val outline = Path().apply {
                    addRoundRect(
                        0f,
                        0f,
                        size.width,
                        size.height,
                        floatArrayOf(corner, corner, corner, corner, 0f, 0f, 0f, 0f),
                        Path.Direction.CW,
                    )
                }
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = FeatureGraphicSpec.BEZEL_COLOR.toArgb()
                    setShadowLayer(
                        FeatureGraphicSpec.SHADOW_RADIUS_PX,
                        0f,
                        FeatureGraphicSpec.SHADOW_DY_PX,
                        FeatureGraphicSpec.SHADOW_COLOR,
                    )
                }
                drawIntoCanvas { it.nativeCanvas.drawPath(outline, paint) }
            },
    ) {
        PhoneFrame(
            Modifier.fillMaxSize(),
            bezelPx = FeatureGraphicSpec.BEZEL_PX,
            frameCornerPx = FeatureGraphicSpec.FRAME_CORNER_PX,
            bezelColor = FeatureGraphicSpec.BEZEL_COLOR,
            screen = screen,
        )
    }
}

/** A captured bitmap as non-premultiplied ARGB pixels. */
fun Bitmap.toArgbImage(): ArgbImage {
    val source = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    return ArgbImage(source.width, source.height, pixels)
}
