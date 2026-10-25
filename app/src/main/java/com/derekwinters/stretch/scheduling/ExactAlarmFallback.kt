package com.derekwinters.stretch.scheduling

/**
 * SCHED-007: sets an exact alarm when [exactAllowed], otherwise an inexact one. If the exact
 * alarm is refused with a [SecurityException] (the grant can be revoked between the check and
 * the call), [onExactRefused] is told and the inexact alarm is set instead, so losing the
 * permission degrades timing, never delivery. Pure (no Android types) so it is unit tested.
 */
internal fun armAlarm(
    exactAllowed: Boolean,
    setExact: () -> Unit,
    setInexact: () -> Unit,
    onExactRefused: (SecurityException) -> Unit = {},
) {
    if (!exactAllowed) {
        setInexact()
        return
    }
    try {
        setExact()
    } catch (e: SecurityException) {
        onExactRefused(e)
        setInexact()
    }
}
