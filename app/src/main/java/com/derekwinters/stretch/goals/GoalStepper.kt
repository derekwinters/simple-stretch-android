package com.derekwinters.stretch.goals

/**
 * Pure (Android-free) rules for the goals screen's − N + stepper (GOAL-001, GOAL-002). The
 * stepper runs from 0 to [GoalStepper.MAX]; 0 means "no goal" and is never stored.
 */

/** A tap on one of the stepper's buttons. */
enum class StepperTap { Plus, Minus }

/** What one tap asks the repository to do: at most one GOAL-009 edit (GOAL-002 invariant). */
sealed interface StepperEdit {
    /** The button is at its bound (− at 0, + at [GoalStepper.MAX]): nothing changes. */
    data object None : StepperEdit

    /** Add a goal or change its count: one `setGoal`. */
    data class Set(val timesPerDay: Int) : StepperEdit

    /** − on 1: one `removeGoal`; the stretch's completion history is kept (GOAL-006). */
    data object Remove : StepperEdit
}

object GoalStepper {
    /** GOAL-001: the largest count the stepper allows. */
    const val MAX = 20

    /** GOAL-002: − is disabled at 0. */
    fun canDecrease(count: Int): Boolean = count > 0

    /** GOAL-002: + is disabled at [MAX]. */
    fun canIncrease(count: Int): Boolean = count < MAX

    /** GOAL-002: maps a tap on a stepper showing [count] (0 = no goal) to one edit. */
    fun plan(count: Int, tap: StepperTap): StepperEdit = when (tap) {
        StepperTap.Plus -> if (canIncrease(count)) StepperEdit.Set(count.coerceAtLeast(0) + 1) else StepperEdit.None
        StepperTap.Minus -> when {
            !canDecrease(count) -> StepperEdit.None
            count == 1 -> StepperEdit.Remove
            else -> StepperEdit.Set((count - 1).coerceAtMost(MAX))
        }
    }
}

/**
 * GOAL-002: one row on the goals screen. [count] is the current goal, 0 when the stretch has
 * none; [done] is today's completions (GOAL-003).
 */
data class GoalRow(val stretchId: Long, val stretchName: String, val count: Int, val done: Int) {
    val hasGoal: Boolean get() = count > 0
    val met: Boolean get() = hasGoal && done >= count
}
