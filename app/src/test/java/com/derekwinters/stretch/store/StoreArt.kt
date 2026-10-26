package com.derekwinters.stretch.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import com.derekwinters.stretch.R
import java.io.File
import kotlin.math.roundToInt

/**
 * The store icon's and the feature graphic's geometry (docs/spec/store-listing.md), in pixels of
 * the final image. Sizes and formats are in [StoreImage].
 */
object StoreArtSpec {
    // STORE-ICON-003: the launcher icon's canvas and the viewport (its central 72 dp) that fills
    // the store icon.
    const val LAUNCHER_CANVAS_DP = 108
    const val LAUNCHER_VIEWPORT_DP = 72

    /** STORE-GRAPHIC-002: the one background colour. */
    const val BACKGROUND = 0xFF1361A7.toInt()

    // STORE-GRAPHIC-003: the icon tile, vertically centred.
    const val TILE_PX = 240
    const val TILE_CORNER_PX = 54f
    const val TILE_TOP_PX = (500 - TILE_PX) / 2

    // STORE-GRAPHIC-004: the name.
    const val NAME = "Simple Stretch"
    const val NAME_SIZE_PX = 80f
    const val NAME_COLOR = 0xFFFFFFFF.toInt()
    const val NAME_GAP_PX = 48

    /** Centres Roboto's cap height (1456 / 2048 em, 57 px at 80 px) on y = 250. */
    const val NAME_BASELINE_PX = 278f

    // STORE-GRAPHIC-006: the committed font and its licence, as test resources.
    const val FONT_RESOURCE = "/store/fonts/Roboto-Bold.ttf"
    const val FONT_LICENCE_RESOURCE = "/store/fonts/LICENSE.txt"
}

/** STORE-GRAPHIC-005: where the tile and the name land for the measured name width. */
data class FeatureGraphicLayout(val tileLeft: Int, val nameLeft: Int, val nameWidth: Float)

/**
 * Draws the store icon (STORE-ICON-001 to STORE-ICON-003) and the feature graphic
 * (STORE-GRAPHIC-001 to STORE-GRAPHIC-006) with Android's own graphics, under Robolectric's
 * native graphics.
 */
object StoreArt {

    /** STORE-ICON-001 to STORE-ICON-003: the launcher icon's 72 dp viewport at 512 x 512 px. */
    fun icon(context: Context): ArgbImage =
        render(StoreImage.ICON) { canvas -> drawLauncherIcon(context, canvas, StoreImage.ICON.width) }

    /** STORE-GRAPHIC-001 to STORE-GRAPHIC-005: the icon tile and the name on #1361A7. */
    fun featureGraphic(context: Context): ArgbImage = render(StoreImage.FEATURE_GRAPHIC) { canvas ->
        val namePaint = namePaint()
        val layout = layout(namePaint)
        val top = StoreArtSpec.TILE_TOP_PX
        val left = layout.tileLeft

        canvas.drawColor(StoreArtSpec.BACKGROUND)

        // STORE-GRAPHIC-003: the store icon's drawing at tile size, filled into an anti-aliased
        // rounded square (a clip path would leave its curved edges jagged).
        val tile = Bitmap.createBitmap(StoreArtSpec.TILE_PX, StoreArtSpec.TILE_PX, Bitmap.Config.ARGB_8888)
        drawLauncherIcon(context, Canvas(tile), StoreArtSpec.TILE_PX)
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(tile, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setTranslate(left.toFloat(), top.toFloat()) })
            }
        }
        canvas.drawRoundRect(
            RectF(left.toFloat(), top.toFloat(), (left + StoreArtSpec.TILE_PX).toFloat(), (top + StoreArtSpec.TILE_PX).toFloat()),
            StoreArtSpec.TILE_CORNER_PX,
            StoreArtSpec.TILE_CORNER_PX,
            tilePaint,
        )

        // STORE-GRAPHIC-004: the name, one line, on its fixed baseline.
        canvas.drawText(StoreArtSpec.NAME, layout.nameLeft.toFloat(), StoreArtSpec.NAME_BASELINE_PX, namePaint)
    }

    /** STORE-GRAPHIC-005: the layout for the name as this font measures it. */
    fun featureGraphicLayout(): FeatureGraphicLayout = layout(namePaint())

    /**
     * STORE-GRAPHIC-006: the committed Roboto Bold, loaded from its file. A file that fails to
     * load would silently become [Typeface.DEFAULT], so that fails here instead.
     */
    fun nameTypeface(): Typeface {
        val stream = StoreArt::class.java.getResourceAsStream(StoreArtSpec.FONT_RESOURCE)
            ?: error("${StoreArtSpec.FONT_RESOURCE} is not on the test classpath (STORE-GRAPHIC-006)")
        val file = File.createTempFile("Roboto-Bold", ".ttf").apply { deleteOnExit() }
        stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        val typeface = Typeface.createFromFile(file)
        check(typeface !== Typeface.DEFAULT) { "${StoreArtSpec.FONT_RESOURCE} did not load (STORE-GRAPHIC-006)" }
        return typeface
    }

    /**
     * STORE-ICON-002 / STORE-ICON-003: draws the real adaptive launcher icon, background layer
     * then foreground layer, with its 72 dp viewport filling [sizePx] x [sizePx] at the canvas
     * origin. The 108 dp canvas is scaled to sizePx * 108 / 72 and offset so the outer 18 dp on
     * each side falls outside. The icon is inflated from its resources, never copied (the
     * STORE-ICON-002 invariant), and no mask is applied.
     */
    fun drawLauncherIcon(context: Context, canvas: Canvas, sizePx: Int) {
        val canvasPx = sizePx * StoreArtSpec.LAUNCHER_CANVAS_DP / StoreArtSpec.LAUNCHER_VIEWPORT_DP
        require(canvasPx * StoreArtSpec.LAUNCHER_VIEWPORT_DP == sizePx * StoreArtSpec.LAUNCHER_CANVAS_DP) {
            "$sizePx px does not scale the launcher canvas to whole pixels"
        }
        val inset = (canvasPx - sizePx) / 2
        val icon = context.getDrawable(R.mipmap.ic_launcher) as? AdaptiveIconDrawable
            ?: error("@mipmap/ic_launcher is not an adaptive icon")
        for (layer in listOf(icon.background, icon.foreground)) {
            checkNotNull(layer) { "@mipmap/ic_launcher is missing a layer" }
            layer.setBounds(-inset, -inset, canvasPx - inset, canvasPx - inset)
            layer.draw(canvas)
        }
    }

    private fun namePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = nameTypeface()
        textSize = StoreArtSpec.NAME_SIZE_PX
        color = StoreArtSpec.NAME_COLOR
    }

    private fun layout(namePaint: Paint): FeatureGraphicLayout {
        val nameWidth = namePaint.measureText(StoreArtSpec.NAME)
        val groupWidth = StoreArtSpec.TILE_PX + StoreArtSpec.NAME_GAP_PX + nameWidth
        val tileLeft = ((StoreImage.FEATURE_GRAPHIC.width - groupWidth) / 2f).roundToInt()
        return FeatureGraphicLayout(
            tileLeft = tileLeft,
            nameLeft = tileLeft + StoreArtSpec.TILE_PX + StoreArtSpec.NAME_GAP_PX,
            nameWidth = nameWidth,
        )
    }

    /** Draws onto a transparent bitmap of [image]'s size and reads back non-premultiplied ARGB. */
    private fun render(image: StoreImage, draw: (Canvas) -> Unit): ArgbImage {
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap))
        val pixels = IntArray(image.width * image.height)
        bitmap.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        bitmap.recycle()
        return ArgbImage(image.width, image.height, pixels)
    }
}
