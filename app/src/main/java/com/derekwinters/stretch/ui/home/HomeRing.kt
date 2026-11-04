package com.derekwinters.stretch.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.derekwinters.stretch.widget.GoalRingRenderer
import com.derekwinters.stretch.widget.RingCentre
import com.derekwinters.stretch.widget.RingModel
import com.derekwinters.stretch.widget.WidgetPalette

/** HOME-006: what Home says and offers around the widget's ring. Pure, unit tested. */
object HomeRing {
    /** HOME-006: the ring's content description, from its centre. */
    fun description(model: RingModel): String = when (val centre = model.centre) {
        is RingCentre.Count -> "Today's goals: ${centre.done} of ${centre.total} done"
        RingCentre.Check -> "Today's goals: all met"
        RingCentre.None -> "No daily goals set"
    }

    /** HOME-006: "Set a goal" is offered beside "Stretch now" only while no goal is set. */
    fun showsSetAGoal(model: RingModel): Boolean = model.arcs.isEmpty()
}

/**
 * HOME-006: the home screen widget's ring, drawn by the widget's own renderer into this
 * composable's canvas, in the app theme's colours (HOME-006 invariant: no ring maths here).
 */
@Composable
fun GoalRingImage(model: RingModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = WidgetPalette.fromTheme(
        primary = scheme.primary.toArgb(),
        surface = scheme.surface.toArgb(),
        onSurface = scheme.onSurface.toArgb(),
        dark = scheme.surface.luminance() < 0.5f,
    )
    val description = HomeRing.description(model)
    Canvas(modifier.semantics { contentDescription = description }) {
        drawIntoCanvas { canvas ->
            GoalRingRenderer.draw(canvas.nativeCanvas, model, colors, size.minDimension)
        }
    }
}
