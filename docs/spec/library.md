# Stretch library

- **LIB-001** The user can add, edit and delete stretches. A stretch has a name, optional
  instructions, and an optional duration in seconds.
- **LIB-002** On first run the library is seeded with common stretches (neck rolls, shoulder
  shrugs, chest opener, hamstring, hip flexor, wrist, upper back, quad, spinal twist, calf).

  **Invariant — Seeding never re-adds a stretch the user deleted.** It runs only when the
  database is first created, never on later launches.
- **LIB-003** Deleting a stretch removes it from any reminder times that use it; the reminder
  times themselves remain.
- **LIB-004** Editing a stretch changes what future notifications say; it does not change any
  schedule's timing.
- **LIB-005** Deleting a stretch also deletes its goal (GOAL-001) and its completion history
  (SESS-004).
- **LIB-006** The library screen is titled "My stretches". It shows a hint at the top, "Add your
  own stretches with +", above the list, and its + button opens the stretch editor for a new
  stretch.
- **LIB-007** Every stretch picker offers a "New stretch…" entry: the goal editor's stretch
  picker (GOAL-002) and a set-times reminder's stretch picker in the schedule editor (SCHED-002).
  It opens the same stretch editor the library uses (name, optional instructions, optional
  duration). Saving adds the stretch to the library and selects it in the picker; cancelling
  returns to the picker unchanged.

  **Invariant — A stretch created from a picker is a normal library stretch**, saved at once
  whether or not the picker is then confirmed, and the picker keeps the selections the user had
  already made.

## Build checklist

- [x] Seed lives in `RoomDatabase.Callback.onCreate` (LIB-002 invariant).
- [x] Cross-reference table cascades on stretch delete (LIB-003).
- [x] `goals` and `completions` foreign keys cascade on stretch delete (LIB-005).
- [x] One shared `StretchEditDialog` composable serves the library and both pickers (LIB-007).
- [x] The repository's insert returns the new id so the picker can select it (LIB-007).
