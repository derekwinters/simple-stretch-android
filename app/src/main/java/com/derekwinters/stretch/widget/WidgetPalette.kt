package com.derekwinters.stretch.widget

import kotlin.math.roundToInt

/** ARGB colours for one drawing of the widget (WIDGET-008). */
data class WidgetColors(
    /** Arcs and the figure. */
    val arc: Int,
    /** The disc, already at 55% (WIDGET-005). */
    val disc: Int,
    /** The count and the check mark. */
    val text: Int,
    /** The empty track. */
    val track: Int,
    val mat: Int,
    /** The figure when dimmed: the text colour at 40% (WIDGET-006). */
    val dimmedFigure: Int,
)

/** Opaque dynamic-colour tones for both modes, read from the system palette (WIDGET-008). */
data class DynamicTones(
    val primaryLight: Int, val primaryDark: Int,
    val surfaceLight: Int, val surfaceDark: Int,
    val onSurfaceLight: Int, val onSurfaceDark: Int,
    val outlineLight: Int, val outlineDark: Int,
)

object WidgetPalette {
    private const val DISC_ALPHA = 0.55f
    private const val TRACK_ALPHA_LIGHT = 0.14f
    private const val TRACK_ALPHA_DARK = 0.18f
    private const val DIMMED_ALPHA = 0.40f
    private const val MAT_GREY = 0xFF818589.toInt()

    /** WIDGET-008: the mockup's values, used only without dynamic colour. */
    val FIXED_LIGHT = colors(
        arc = 0xFF1361A7.toInt(), surface = 0xFFFFFFFF.toInt(), onSurface = 0xFF1C1B1F.toInt(),
        track = withAlpha(0xFF000000.toInt(), TRACK_ALPHA_LIGHT), mat = MAT_GREY,
    )
    val FIXED_DARK = colors(
        arc = 0xFF3D86D1.toInt(), surface = 0xFF121212.toInt(), onSurface = 0xFFF5F5F5.toInt(),
        track = withAlpha(0xFFFFFFFF.toInt(), TRACK_ALPHA_DARK), mat = MAT_GREY,
    )

    /**
     * WIDGET-008 invariant: the one place that chooses between dynamic and fixed colours. [tones]
     * is read only when [dynamicAvailable].
     */
    fun select(dynamicAvailable: Boolean, dark: Boolean, tones: () -> DynamicTones): WidgetColors {
        if (!dynamicAvailable) return if (dark) FIXED_DARK else FIXED_LIGHT
        val t = tones()
        return if (dark) {
            colors(t.primaryDark, t.surfaceDark, t.onSurfaceDark, withAlpha(t.onSurfaceDark, TRACK_ALPHA_DARK), t.outlineDark)
        } else {
            colors(t.primaryLight, t.surfaceLight, t.onSurfaceLight, withAlpha(t.onSurfaceLight, TRACK_ALPHA_LIGHT), t.outlineLight)
        }
    }

    private fun colors(arc: Int, surface: Int, onSurface: Int, track: Int, mat: Int) = WidgetColors(
        arc = arc,
        disc = withAlpha(surface, DISC_ALPHA),
        text = onSurface,
        track = track,
        mat = mat,
        dimmedFigure = withAlpha(onSurface, DIMMED_ALPHA),
    )

    /** [color] with its alpha replaced by [fraction] of full opacity. */
    fun withAlpha(color: Int, fraction: Float): Int =
        ((fraction * 255f).roundToInt() shl 24) or (color and 0x00FFFFFF)
}
