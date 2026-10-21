# Goals

- **GOAL-001** A goal says how many times a day the user wants to do one stretch, e.g. 3
  hamstring stretches a day. It has a stretch and a count of 1 to 20. A stretch has at most one
  current goal (GOAL-008). The goals screen's stepper runs from 0 to 20: 0 is shown for a
  stretch with no goal and is never stored as a goal.
- **GOAL-002** The goals screen lists **every stretch** in the library, in library order (by
  name, ignoring case, as "My stretches" does), under a short intro line. Each row has the
  stretch's name, "*n* of *count* today" when it has a goal (with a ✓ once the goal is met,
  GOAL-003), and a − *N* + stepper showing its current goal, 0 when it has none.
  - \+ on 0 adds a goal of 1; + or − on 1 to 20 changes the count by one; − on 1 removes the
    goal (GOAL-006). − is disabled at 0 and + at 20.
  - Each tap is applied at once through GOAL-009; there is no Save step and no dialog.
  - "New stretch…" at the bottom of the list opens the stretch editor (LIB-007); the new
    stretch then appears in the list at 0.

  It is reached from the home screen's top bar and its goals card (HOME-004, HOME-006).

  **Invariant — A tap is one GOAL-009 edit.** Each stepper tap runs exactly one `setGoal` or
  `removeGoal`, in one transaction. Repeated taps on the same day update today's row in place
  and never leave one-day history rows; the screen never writes goal history of its own.

  **Invariant — The goals screen uses only the theme's colour roles.** A stepper with a goal
  uses `primaryContainer` / `onPrimaryContainer` (as the "New schedule" button); a stepper at 0
  uses `surfaceContainerHighest` (as the "Today's goals" card), with − in the disabled content
  colour. The ✓ and "New stretch…" use `primary` (as "Edit goals"). There are no `Color(…)`
  literals, so the screen follows dynamic colour and dark mode like the rest of the app.
- **GOAL-003** A goal's progress on a day is the number of completions of its stretch (SESS-004)
  whose time falls within that local calendar day, from midnight inclusive to the next midnight
  exclusive. A goal is met when progress is at or above the count; progress above the count is
  shown as is (e.g. 4/3).
- **GOAL-004** **Invariant — Progress is always derived from the completion log, never stored.**
  There is no counter to reset: a new day starts at 0 because no completions fall in it yet, and
  changing or removing a goal never changes the history.
- **GOAL-005** Once every goal for the day is met, reminders are not shown for the rest of that
  day (NOTIF-010); the schedule keeps running and reminders return the next day, or as soon as a
  goal is short again (e.g. after a goal's count is raised). With no goals, goals never suppress
  a reminder. Goals also decide what the session suggests (SESS-002) and what a reminder
  without stretches lists (NOTIF-001).
- **GOAL-006** Removing a goal keeps its stretch's completion history. Deleting the stretch
  removes both (LIB-005).
- **GOAL-007** **Invariant — Upgrading the app never loses data.** The database moves from
  version 1 to 2 with an explicit migration: it adds the repeating-schedule columns to
  `schedules` with defaults that keep every existing schedule a set-times schedule, and creates
  the `goals` and `completions` tables. Version 2 to 3 is GOAL-010. There is no
  destructive-migration fallback.

### Goal history

- **GOAL-008** Goals are versioned so trends can judge each day by the goals of that day
  (TREND-001). Each goal row has a stretch, a count, a first day in force
  (`effectiveFromEpochDay`, inclusive) and an optional end day (`effectiveToEpochDay`,
  exclusive). The row with no end day is the stretch's **current goal**; every screen other
  than trends, and notifications, use current goals only.
- **GOAL-009** Editing and removing goals, on day *T* (today):
  - adding a goal to a stretch without one adds a current row from *T*;
  - changing the count of a current goal that started before *T* closes it with end *T* and
    adds a new current row from *T* with the new count, so the new count applies from today
    and earlier days keep the old one;
  - changing the count of a current goal that started on *T* updates it in place (no one-day
    history rows); setting the same count changes nothing;
  - removing a current goal that started before *T* closes it with end *T* (today is judged
    without it); removing one that started on *T* deletes it.

  **Invariant — A stretch has at most one current goal, and history rows are only ever closed,
  never rewritten.** Each change runs in one transaction that reads the current row and applies
  exactly one of the edits above; nothing changes a closed row's count or range. Deleting the
  stretch deletes all its goal rows (LIB-005).
- **GOAL-010** **Invariant — Upgrading to version 3 keeps every goal.** The migration rebuilds
  `goals` with the two new columns (SQLite cannot add a NOT NULL column without a default or
  turn the old unique index on `stretchId` into a plain one): each existing goal keeps its id,
  stretch and count, becomes a current goal (no end day), and is in force from the local date
  of the **earliest completion in the log**, or from the day of the upgrade if nothing was
  logged yet. Version 2 created goals and completions together, so goals were in use from
  about the first completion; starting there shows those days in trends, while starting from
  the epoch would invent months of 0% days before the app was used.

## Build checklist

- [x] `GoalMath` (progress, day bounds, session order, notification summary) is pure and unit
      tested (GOAL-003, SESS-002, NOTIF-001).
- [x] `goals.stretchId` has an index and a cascading foreign key (GOAL-001, LIB-005); since
      version 3 it is not unique, as a stretch has history rows (GOAL-008).
- [x] `MIGRATION_1_2` SQL matches the entities, including `@ColumnInfo(defaultValue)` on the
      new `schedules` columns, so Room's schema validation passes (GOAL-007).
- [x] `GoalMath.allGoalsMet` is false for an empty goal list (GOAL-005, NOTIF-010).
- [x] `GoalHistory.planSet` / `planRemove` / `migratedStartDay` are pure and unit tested
      (GOAL-009, GOAL-010).
- [x] `GoalDao.setGoal` / `removeGoal` are `@Transaction` methods that read the current row
      and apply one `GoalEdit` (GOAL-009 invariant).
- [x] `MIGRATION_2_3` creates `goals_new` with Room's exact v3 DDL (column order, nullability,
      foreign key, then the plain `index_goals_stretchId`), copies ids, drops and renames
      (GOAL-010).
- [x] Count is clamped to at least 1 in the repository, not only in the UI (GOAL-001).
- [x] `GoalStepper.plan` maps a tap to one edit (add, change, remove or nothing) and keeps the
      stepper within 0 to 20; it is pure and unit tested (GOAL-001, GOAL-002).
- [x] `GoalMath.goalRows` lists every stretch by name with its count (0 without a goal) and
      today's progress; it is pure and unit tested (GOAL-002).
- [x] Each stepper tap calls exactly one repository `setGoal` / `removeGoal`; same-day taps
      leave one current row and no history rows (GOAL-002 invariant, GOAL-009).
- [x] `GoalsScreen.kt` has no `Color(` literal; its colours are the `MaterialTheme.colorScheme`
      roles named in GOAL-002.
- [ ] Instrumented migration tests (1→2, 2→3) with `MigrationTestHelper` (need an emulator
      and the committed `1.json` / `2.json` schemas).
