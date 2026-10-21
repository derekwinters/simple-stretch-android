package com.derekwinters.stretch.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI

/**
 * Draws a [RingModel] into a square bitmap (WIDGET-001 … WIDGET-006). Layout only: what to draw
 * is decided by [GoalRing], colours by [WidgetPalette].
 */
object GoalRingRenderer {
    private const val STROKE = 0.08f // ring thickness, as a fraction of the side
    private const val MARGIN = 0.02f
    private const val FIGURE_WIDTH = 0.30f
    private const val DOTS = 36

    // Launcher icon foreground (drawable/ic_launcher_foreground.xml), in its 108-unit viewport.
    private const val FIGURE_UNITS_WIDTH = 49f // 29.5 .. 78.5 including the round line caps
    private const val FIGURE_CENTRE_X = 54f
    private const val FIGURE_CENTRE_Y = 53.25f // 30 .. 76.5 including the round line caps

    fun render(model: RingModel, colors: WidgetColors, side: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = side.toFloat()
        val c = s / 2f
        val stroke = s * STROKE
        val r = c - stroke / 2f - s * MARGIN
        val oval = RectF(c - r, c - r, c + r, c + r)

        // WIDGET-005: the see-through disc up to the ring's inner edge.
        canvas.drawCircle(c, c, r - stroke / 2f, fill(colors.disc))

        if (model.dottedTrack) {
            // WIDGET-006: no goals, a ring of round dots.
            val dot = stroke * 0.6f
            val spacing = (2f * PI.toFloat() * r) / DOTS
            val paint = line(colors.track, dot).apply { pathEffect = DashPathEffect(floatArrayOf(0.1f, spacing - 0.1f), 0f) }
            canvas.drawPath(Path().apply { addCircle(c, c, r, Path.Direction.CW) }, paint)
        } else {
            drawArcs(canvas, model.arcs, oval, r, stroke, colors)
        }

        val hasText = model.centre != RingCentre.None
        val figureCentreY = if (hasText) s * 0.43f else c
        drawFigure(canvas, c, figureCentreY, s * FIGURE_WIDTH / FIGURE_UNITS_WIDTH, colors, model.figureDimmed)

        when (val centre = model.centre) {
            is RingCentre.Count -> {
                val text = fill(colors.text).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = s * 0.13f
                    typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText("${centre.done}/${centre.total}", c, s * 0.70f, text)
            }
            RingCentre.Check -> {
                // WIDGET-004: a plain check mark in the text colour, no circle or badge.
                val w = s * 0.11f
                val cy = s * 0.655f
                val check = Path().apply {
                    moveTo(c - w * 0.5f, cy)
                    lineTo(c - w * 0.15f, cy + w * 0.33f)
                    lineTo(c + w * 0.5f, cy - w * 0.33f)
                }
                canvas.drawPath(check, line(colors.text, s * 0.03f))
            }
            RingCentre.None -> Unit
        }
        return bitmap
    }

    /**
     * WIDGET-002/003. Model angles are clockwise from 12 o'clock; the canvas's 0° is 3 o'clock.
     * Round caps reach half the stroke past each end, so each arc is drawn inset by that much to
     * keep its round ends inside its share and the visible gap at [GoalRing.GAP_DEG].
     */
    private fun drawArcs(canvas: Canvas, arcs: List<RingArc>, oval: RectF, r: Float, stroke: Float, colors: WidgetColors) {
        val capDeg = Math.toDegrees((stroke / 2f / r).toDouble()).toFloat()
        val track = line(colors.track, stroke)
        val filled = line(colors.arc, stroke)
        for (arc in arcs) {
            val start = arc.startDeg - 90f
            if (arc.sweepDeg >= 360f) {
                canvas.drawArc(oval, start, 360f, false, track)
                when {
                    arc.fill >= 1f -> canvas.drawArc(oval, start, 360f, false, filled)
                    arc.fill > 0f -> canvas.drawArc(oval, start + capDeg, (arc.fillSweepDeg - 2f * capDeg).coerceAtLeast(0.1f), false, filled)
                }
            } else {
                canvas.drawArc(oval, start + capDeg, (arc.sweepDeg - 2f * capDeg).coerceAtLeast(0.1f), false, track)
                if (arc.fill > 0f) {
                    canvas.drawArc(oval, start + capDeg, (arc.fillSweepDeg - 2f * capDeg).coerceAtLeast(0.1f), false, filled)
                }
            }
        }
    }

    /** The launcher icon's figure and mat, scaled by [k] per icon unit and centred on (cx, cy). */
    private fun drawFigure(canvas: Canvas, cx: Float, cy: Float, k: Float, colors: WidgetColors, dimmed: Boolean) {
        fun x(u: Float) = cx + (u - FIGURE_CENTRE_X) * k
        fun y(u: Float) = cy + (u - FIGURE_CENTRE_Y) * k
        val figureColor = if (dimmed) colors.dimmedFigure else colors.arc

        // Mat: keeps its colour when the figure is dimmed (WIDGET-006).
        canvas.drawLine(x(32f), y(74f), x(76f), y(74f), line(colors.mat, 5f * k))
        // Head.
        canvas.drawCircle(x(70f), y(47.5f), 6f * k, fill(figureColor))
        // Body: legs apart, low hand on the mat, top arm reaching up.
        val body = Path().apply {
            moveTo(x(34f), y(70.5f)); lineTo(x(44f), y(53f)); lineTo(x(54f), y(70.5f))
            moveTo(x(44f), y(53f)); lineTo(x(61f), y(50f))
            moveTo(x(61f), y(50f)); lineTo(x(56.5f), y(32.5f))
            moveTo(x(61f), y(50f)); lineTo(x(62.5f), y(71f))
        }
        canvas.drawPath(body, line(figureColor, 5f * k))
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun line(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
}
