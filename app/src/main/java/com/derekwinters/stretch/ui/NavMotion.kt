package com.derekwinters.stretch.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry

/**
 * HOME-012: the one definition of how screens move between one another. Every destination of
 * [StretchNavHost], and the bottom bar, take their motion from here, chosen by [of] from the
 * kinds of the two screens (tab or full-window) and the direction.
 */
enum class NavMotion {
    /** Tab to tab: Material fade through. */
    FADE_THROUGH,

    /** Into a full-window screen: Material shared axis X, forward (new screen from the right). */
    SHARED_AXIS_FORWARD,

    /** Back out of a full-window screen: shared axis X in reverse (old screen out to the right). */
    SHARED_AXIS_BACK,
    ;

    /** The incoming screen's motion. [slidePx] is [SLIDE] in pixels. */
    fun enter(slidePx: Int): EnterTransition = when (this) {
        FADE_THROUGH -> incomingFade() + scaleIn(incomingTween(), initialScale = FADE_THROUGH_SCALE)
        SHARED_AXIS_FORWARD -> slideIn(slidePx) + incomingFade()
        SHARED_AXIS_BACK -> slideIn(-slidePx) + incomingFade()
    }

    /** The outgoing screen's motion. [slidePx] is [SLIDE] in pixels. */
    fun exit(slidePx: Int): ExitTransition = when (this) {
        FADE_THROUGH -> outgoingFade()
        SHARED_AXIS_FORWARD -> slideOut(-slidePx) + outgoingFade()
        SHARED_AXIS_BACK -> slideOut(slidePx) + outgoingFade()
    }

    companion object {
        /** The whole motion, as Material's fade through and shared axis patterns time it. */
        const val DURATION_MILLIS = 300

        /** The outgoing screen fades out over the first 35%... */
        const val OUTGOING_FADE_MILLIS = 105

        /** ...and only then does the incoming one start to fade in (HOME-012 first invariant). */
        const val INCOMING_FADE_DELAY_MILLIS = OUTGOING_FADE_MILLIS

        const val INCOMING_FADE_MILLIS = DURATION_MILLIS - INCOMING_FADE_DELAY_MILLIS

        /** Fade through's incoming screen grows from this scale to full size. */
        const val FADE_THROUGH_SCALE = 0.92f

        /** How far shared axis X slides each screen. */
        val SLIDE = 30.dp

        /**
         * The motion from a screen at route [from] to one at [to]; [pop] when Back (or a pop of
         * the back stack) is what moves between them.
         */
        fun of(from: String?, to: String?, pop: Boolean): NavMotion = when {
            isTab(from) && isTab(to) -> FADE_THROUGH
            pop -> SHARED_AXIS_BACK
            else -> SHARED_AXIS_FORWARD
        }

        /** [of] for a navigation host transition. */
        fun of(scope: AnimatedContentTransitionScope<NavBackStackEntry>, pop: Boolean): NavMotion =
            of(scope.initialState.destination.route, scope.targetState.destination.route, pop)

        private fun isTab(route: String?) = TopTab.forRoute(route) != null

        private fun incomingTween() = tween<Float>(
            durationMillis = INCOMING_FADE_MILLIS,
            delayMillis = INCOMING_FADE_DELAY_MILLIS,
            easing = LinearOutSlowInEasing,
        )

        private fun incomingFade() = fadeIn(incomingTween())

        private fun outgoingFade() =
            fadeOut(tween(durationMillis = OUTGOING_FADE_MILLIS, easing = FastOutLinearInEasing))

        private fun slideIn(fromPx: Int) =
            slideInHorizontally(tween(DURATION_MILLIS, easing = FastOutSlowInEasing)) { fromPx }

        private fun slideOut(toPx: Int) =
            slideOutHorizontally(tween(DURATION_MILLIS, easing = FastOutSlowInEasing)) { toPx }
    }
}
