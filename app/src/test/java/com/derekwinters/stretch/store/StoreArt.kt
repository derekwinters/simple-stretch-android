package com.derekwinters.stretch.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import com.derekwinters.stretch.R

/**
 * The store icon's geometry (docs/spec/store-listing.md), in pixels of the final image. Its size
 * and format are in [StoreImage]; the feature graphic's geometry is in [FeatureGraphicSpec].
 */
object StoreArtSpec {
    // STORE-ICON-003: the launcher icon's canvas and the viewport (its central 72 dp) that fills
    // the store icon.
    const val LAUNCHER_CANVAS_DP = 108
    const val LAUNCHER_VIEWPORT_DP = 72
}

/**
 * Draws the store icon (STORE-ICON-001 to STORE-ICON-003) with Android's own graphics, under
 * Robolectric's native graphics. The feature graphic is composed instead ([FeatureGraphic]).
 */
object StoreArt {

    /** STORE-ICON-001 to STORE-ICON-003: the launcher icon's 72 dp viewport at 512 x 512 px. */
    fun icon(context: Context): ArgbImage =
        render(StoreImage.ICON) { canvas -> drawLauncherIcon(context, canvas, StoreImage.ICON.width) }

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
