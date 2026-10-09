# Stretch sessions

- **SESS-001** The stretch session screen is where the user records what they stretched. It
  opens from a reminder notification (tap or "Start", NOTIF-002, NOTIF-003) and from the
  "Stretch now" button on the home screen (HOME-007).
- **SESS-002** It lists every stretch in the library, each with a checkbox (initially
  unchecked). First come stretches whose goal is not yet met today, labelled "n of target
  today"; then every other stretch (met goals, labelled with their progress, and stretches with
  no goal). Each group is ordered by name, ignoring case.
- **SESS-003** "Save" records one completion per checked stretch, all stamped with the moment
  of saving, then closes the screen. Save is disabled while nothing is checked. Leaving the
  screen any other way records nothing.
- **SESS-004** The completion log stores, per completion, the stretch and the time it was
  completed (epoch milliseconds). Deleting a stretch deletes its completions (LIB-005).
- **SESS-005** **Invariant — The list order is fixed when the screen opens.** Checking a box
  never moves a row, and progress changing in the background (e.g. a save from another screen)
  does not reorder the list while it is shown.

  Saving a session only appends to the completion log; it never changes schedules or alarms.

## Build checklist

- [x] Order is computed once by `GoalMath.sessionOrder` from a one-shot read (SESS-002,
      SESS-005).
- [x] All completions of one save are inserted in one transaction with one timestamp (SESS-003).
- [x] `completions` has indices on `stretchId` and `completedAt` (SESS-004, GOAL-003).
