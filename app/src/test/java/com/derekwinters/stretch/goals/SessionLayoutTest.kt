package com.derekwinters.stretch.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Requirement IDs refer to docs/spec/. The library is the mockup's (issue #29). */
class SessionLayoutTest {
    private val hamstring = StretchRef(1, "Hamstring stretch")
    private val hipFlexor = StretchRef(2, "Hip flexor lunge")
    private val catCow = StretchRef(3, "Cat-cow")
    private val lean = StretchRef(4, "Achilles Wall Lean")
    private val push = StretchRef(5, "Achilles Wall Push")
    private val butterfly = StretchRef(6, "Butterfly")
    private val calf = StretchRef(7, "Calf stretch")
    private val childsPose = StretchRef(8, "child's pose")
    private val neck = StretchRef(9, "Neck roll")

    // Library order deliberately scrambled: the layout must sort, not trust the input.
    private val library = listOf(neck, catCow, push, hamstring, childsPose, calf, lean, butterfly, hipFlexor)
    private val goals = listOf(GoalRef(1, 3), GoalRef(2, 2), GoalRef(4, 2), GoalRef(5, 2), GoalRef(6, 2))
    private val counts = mapOf(1L to 3, 4L to 1, 6L to 2)
    private val morning = listOf(1L, 2L, 3L)

    // SESS-002: a reminder's stretches lead, in the reminder's order; the rest are alphabetical,
    // ignoring case.
    @Test
    fun reminderStretchesLeadInReminderOrder() {
        val layout = GoalMath.sessionLayout(library, goals, counts, morning)
        assertEquals(SessionTop.FROM_REMINDER, layout.top)
        assertEquals(listOf("Hamstring stretch", "Hip flexor lunge", "Cat-cow"), layout.topEntries.map { it.name })
        assertEquals(
            listOf("Achilles Wall Lean", "Achilles Wall Push", "Butterfly", "Calf stretch", "child's pose", "Neck roll"),
            layout.others.map { it.name },
        )
    }

    // SESS-002: every stretch appears exactly once across both sections.
    @Test
    fun everyStretchListedOnce() {
        val layout = GoalMath.sessionLayout(library, goals, counts, morning)
        val ids = (layout.topEntries + layout.others).map { it.stretchId }
        assertEquals(library.map { it.id }.sorted(), ids.sorted())
    }

    // SESS-002: a reminder stretch whose goal is met stays in the reminder section with its progress.
    @Test
    fun metReminderStretchStaysInReminderSection() {
        val layout = GoalMath.sessionLayout(library, goals, counts, morning)
        val ham = layout.topEntries.first()
        assertEquals(1L, ham.stretchId)
        assertEquals(3, ham.target)
        assertEquals(3, ham.done)
        assertNull(layout.topEntries.first { it.stretchId == 3L }.target)
    }

    // SESS-002: a deleted named stretch is dropped silently; LIB-003.
    @Test
    fun deletedNamedStretchIsDropped() {
        val layout = GoalMath.sessionLayout(library, goals, counts, listOf(1L, 99L, 3L))
        assertEquals(SessionTop.FROM_REMINDER, layout.top)
        assertEquals(listOf(1L, 3L), layout.topEntries.map { it.stretchId })
    }

    // SESS-002: when every named stretch is gone, the screen behaves as if opened with no named list.
    @Test
    fun allNamedStretchesDeletedFallsBackToGoalsShort() {
        val layout = GoalMath.sessionLayout(library, goals, counts, listOf(98L, 99L))
        assertEquals(GoalMath.sessionLayout(library, goals, counts, null), layout)
        assertEquals(SessionTop.GOALS_SHORT, layout.top)
    }

    // SESS-002: an empty named list (a repeating schedule, SCHED-011) is the same as none.
    @Test
    fun emptyNamedListIsNoNamedList() {
        assertEquals(
            GoalMath.sessionLayout(library, goals, counts, null),
            GoalMath.sessionLayout(library, goals, counts, emptyList()),
        )
    }

    // SESS-002: with no named list, goals still short today lead by name; the rest alphabetical.
    @Test
    fun noNamedListPutsShortGoalsOnTop() {
        val layout = GoalMath.sessionLayout(library, goals, counts, null)
        assertEquals(SessionTop.GOALS_SHORT, layout.top)
        assertEquals(listOf("Achilles Wall Lean", "Achilles Wall Push", "Hip flexor lunge"), layout.topEntries.map { it.name })
        assertEquals(
            listOf("Butterfly", "Calf stretch", "Cat-cow", "child's pose", "Hamstring stretch", "Neck roll"),
            layout.others.map { it.name },
        )
    }

    // SESS-002: once every goal is met the top section is left out.
    @Test
    fun allGoalsMetLeavesOutTopSection() {
        val allMet = mapOf(1L to 3, 2L to 2, 4L to 2, 5L to 5, 6L to 2)
        val layout = GoalMath.sessionLayout(library, goals, allMet, null)
        assertNull(layout.top)
        assertTrue(layout.topEntries.isEmpty())
        assertEquals(library.size, layout.others.size)
        assertEquals("Achilles Wall Lean", layout.others.first().name)
    }

    // SESS-002: no goals and no named stretches: only the alphabetical list.
    @Test
    fun noGoalsNoNamedListIsJustAlphabetical() {
        val layout = GoalMath.sessionLayout(library, emptyList(), emptyMap(), null)
        assertNull(layout.top)
        assertEquals(
            listOf(
                "Achilles Wall Lean", "Achilles Wall Push", "Butterfly", "Calf stretch", "Cat-cow",
                "child's pose", "Hamstring stretch", "Hip flexor lunge", "Neck roll",
            ),
            layout.others.map { it.name },
        )
    }

    // SESS-002: "Other stretches" ties on name are broken by id so the order is stable (SESS-005).
    @Test
    fun nameTiesBrokenById() {
        val a = StretchRef(20, "Twist")
        val b = StretchRef(10, "twist")
        val layout = GoalMath.sessionLayout(listOf(a, b), emptyList(), emptyMap(), null)
        assertEquals(listOf(10L, 20L), layout.others.map { it.stretchId })
    }

    // SESS-002: a named stretch listed twice appears once.
    @Test
    fun duplicateNamedIdsListedOnce() {
        val layout = GoalMath.sessionLayout(library, goals, counts, listOf(2L, 2L, 3L))
        assertEquals(listOf(2L, 3L), layout.topEntries.map { it.stretchId })
    }

    // SESS-002: the reminder heading names the reminder's time and schedule.
    @Test
    fun reminderHeading() {
        assertEquals("From your 9:00 Morning reminder", SessionHeadings.fromReminder("9:00", "Morning"))
        assertEquals("Goals still short today", SessionHeadings.GOALS_SHORT)
        assertEquals("Other stretches", SessionHeadings.OTHERS)
    }

    // NOTIF-008 invariant: a snoozed reminder keeps its original scheduled time; only an alarm
    // without the extra (armed before it existed) falls back to the trigger time.
    @Test
    fun scheduledTimeSurvivesSnooze() {
        val nine = 1_000_000L
        val nineOhFive = nine + 5 * 60 * 1000L
        assertEquals(nine, ReminderOrigin.scheduledAt(extra = nine, triggerAt = nineOhFive))
        assertEquals(nineOhFive, ReminderOrigin.scheduledAt(extra = null, triggerAt = nineOhFive))
    }

    // SESS-001: the named stretch ids round-trip through the session route argument.
    @Test
    fun stretchIdsRoundTrip() {
        assertEquals(listOf(3L, 1L, 2L), ReminderOrigin.parseIds(ReminderOrigin.formatIds(listOf(3L, 1L, 2L))))
        assertEquals(emptyList<Long>(), ReminderOrigin.parseIds(""))
        assertEquals(emptyList<Long>(), ReminderOrigin.parseIds(null))
        assertEquals(listOf(4L), ReminderOrigin.parseIds("x,4,"))
    }
}
