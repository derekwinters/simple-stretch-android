# Goals

- **GOAL-001** A goal says how many times a day the user wants to do one stretch, e.g. 3
  hamstring stretches a day. It has a stretch and a count of at least 1 (the editor allows 1 to
  20). A stretch has at most one goal.
- **GOAL-002** The goals screen lists every goal with today's progress. The user can add a goal
  (pick a stretch that has no goal yet, set the count), change a goal's count, and remove a
  goal. It is reached from the home screen's top bar and its goals card (HOME-004, HOME-006).
- **GOAL-003** A goal's progress on a day is the number of completions of its stretch (SESS-004)
  whose time falls within that local calendar day, from midnight inclusive to the next midnight
  exclusive. A goal is met when progress is at or above the count; progress above the count is
  shown as is (e.g. 4/3).
- **GOAL-004** **Invariant — Progress is always derived from the completion log, never stored.**
  There is no counter to reset: a new day starts at 0 because no completions fall in it yet, and
  changing or removing a goal never changes the history.
- **GOAL-005** Goals never suppress reminders: reminders keep firing after every goal is met
  (NOTIF-010). Goals only decide what the session suggests (SESS-002) and what a reminder
  without stretches lists (NOTIF-001).
- **GOAL-006** Removing a goal keeps its stretch's completion history. Deleting the stretch
  removes both (LIB-005).
- **GOAL-007** **Invariant — Upgrading the app never loses data.** The database moves from
  version 1 to 2 with an explicit migration: it adds the repeating-schedule columns to
  `schedules` with defaults that keep every existing schedule a set-times schedule, and creates
  the `goals` and `completions` tables. There is no destructive-migration fallback.

## Build checklist

- [x] `GoalMath` (progress, day bounds, session order, notification summary) is pure and unit
      tested (GOAL-003, SESS-002, NOTIF-001).
- [x] `goals.stretchId` has a unique index and a cascading foreign key (GOAL-001, LIB-005).
- [x] `MIGRATION_1_2` SQL matches the entities, including `@ColumnInfo(defaultValue)` on the
      new `schedules` columns, so Room's schema validation passes (GOAL-007).
- [x] Count is clamped to at least 1 in the repository, not only in the UI (GOAL-001).
- [ ] Instrumented migration test with `MigrationTestHelper` (needs an emulator and the
      committed `1.json` schema).
