# Stretch sessions

- **SESS-001** The stretch session screen is where the user records what they stretched. It
  opens from a reminder notification (tap or "Start", NOTIF-002, NOTIF-003), from the
  "Stretch now" button on the home screen (HOME-007) and from a row of the home screen's
  Today list, which opens it as that reminder's notification would (HOME-009). A reminder
  opens it carrying the reminder's named stretches, its schedule name and its original
  scheduled time (NOTIF-002, NOTIF-008); "Stretch now" and a reminder with no stretches
  (every repeating schedule, SCHED-011) carry no named list.
- **SESS-002** It lists every stretch in the library once, each with a checkbox and a progress
  label: "n of target today" for an unmet goal, "Goal met: n of target today" for a met one,
  "No goal" otherwise. The list has two sections:
  - **Top section, opened from a reminder that names stretches:** headed "From your {time}
    {schedule} reminder" (e.g. "From your 9:00 Morning reminder"; the time in the user's 12/24
    hour format), it lists that reminder's stretches in the order the reminder names them, on a
    highlighted background. A reminder stretch whose goal is already met stays here with its
    progress. A named stretch that no longer exists (deleted, LIB-003) is left out without any
    note; if none of the named stretches still exists, the screen opens as if with no named
    list.
  - **Top section, opened with no named list:** headed "Goals still short today", it lists the
    stretches whose goal is not yet met today, by name ignoring case, on a highlighted
    background. It is left out when no goal is short (every goal met, or no goals at all).
  - **"Other stretches":** every stretch not in the top section, by name ignoring case (ties by
    id), always expanded. With no top section the screen is just this alphabetical list.

  **Invariant — Nothing is pre-checked.** Every checkbox, in both sections, starts unchecked,
  whichever way the screen was opened, so only what the user taps is logged.
- **SESS-003** "Save" records one completion per checked stretch, in either section, all
  stamped with the moment of saving, then closes the screen. Save is disabled while nothing is
  checked in either section. Leaving the
  screen any other way records nothing.
- **SESS-004** The completion log stores, per completion, the stretch and the time it was
  completed (epoch milliseconds). Deleting a stretch deletes its completions (LIB-005).
- **SESS-005** **Invariant — The list order is fixed when the screen opens.** Both sections,
  and which stretch is in which, are decided once. Checking a box never moves a row, and progress changing in the background (e.g. a save from another screen)
  does not reorder the list while it is shown.

  Saving a session only appends to the completion log; it never changes schedules or alarms.

## Build checklist

- [x] Both sections are computed once by `GoalMath.sessionLayout` from a one-shot read
      (SESS-002, SESS-005); it is pure and unit tested, including deleted named stretches.
- [x] The reminder's stretch ids, schedule name and scheduled time travel as session route
      arguments, so they survive process death (SESS-001).
- [x] A Today row and a notification build the session route with one shared function
      (SESS-001, HOME-009).
- [x] The checked set starts empty for every way of opening (SESS-002 invariant).
- [x] All completions of one save are inserted in one transaction with one timestamp (SESS-003).
- [x] `completions` has indices on `stretchId` and `completedAt` (SESS-004, GOAL-003).
