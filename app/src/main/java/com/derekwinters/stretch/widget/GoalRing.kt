package com.derekwinters.stretch.widget

import com.derekwinters.stretch.goals.GoalMath
import com.derekwinters.stretch.goals.GoalRef
import com.derekwinters.stretch.goals.GoalRow
import com.derekwinters.stretch.goals.StretchRef
import java.time.Instant
import java.time.ZoneId

/**
 * Pure (Android-free) layout of the segmented ring widget (docs/spec/widgets.md), unit tested on
 * the JVM. Angles are degrees clockwise from 12 o'clock. Nothing here stores progress (GOAL-004).
 */

/** One goal's arc (WIDGET-002/003): [fill] is progress / count, capped to 0..1. */
data class RingArc(val stretchId: Long, val startDeg: Float, val sweepDeg: Float, val fill: Float) {
    val fillSweepDeg: Float get() = sweepDeg * fill
}

/** What sits under the figure (WIDGET-004/006). */
sealed interface RingCentre {
    data class Count(val done: Int, val total: Int) : RingCentre
    data object Check : RingCentre
    data object None : RingCentre
}

data class RingModel(val arcs: List<RingArc>, val centre: RingCentre, val figureDimmed: Boolean) {
    /** WIDGET-006: with no goals the ring is a dotted track. */
    val dottedTrack: Boolean get() = arcs.isEmpty()
}

object GoalRing {
    /** WIDGET-002: the visible gap between neighbouring arcs. */
    const val GAP_DEG = 8f

    /**
     * WIDGET-002 … WIDGET-006: the ring for [rows], which must already be in goals-screen order
     * ([GoalMath.goalRows]); rows without a goal are dropped.
     */
    fun model(rows: List<GoalRow>): RingModel {
        val goals = rows.filter { it.hasGoal }
        if (goals.isEmpty()) return RingModel(emptyList(), RingCentre.None, figureDimmed = true)

        val share = 360f / goals.size
        val arcs = goals.mapIndexed { i, g ->
            val fill = (g.done.toFloat() / g.count).coerceIn(0f, 1f)
            if (goals.size == 1) {
                RingArc(g.stretchId, 0f, 360f, fill)
            } else {
                RingArc(g.stretchId, i * share + GAP_DEG / 2f, share - GAP_DEG, fill)
            }
        }
        val total = goals.sumOf { it.count }
        val done = goals.sumOf { minOf(it.done, it.count) }
        val centre = if (goals.all { it.met }) RingCentre.Check else RingCentre.Count(done, total)
        return RingModel(arcs, centre, figureDimmed = done == 0)
    }

    /** WIDGET-009: the ring from the same calculation as the goals screen (GOAL-002/003). */
    fun fromLog(stretches: List<StretchRef>, goals: List<GoalRef>, countsToday: Map<Long, Int>): RingModel =
        model(GoalMath.goalRows(stretches, goals, countsToday))

    /** WIDGET-010: the start of the next local day after [nowMillis]. */
    fun nextRedrawAt(nowMillis: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return GoalMath.dayBounds(today, zone).second
    }
}
