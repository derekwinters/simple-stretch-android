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

## Build checklist

- [x] Seed lives in `RoomDatabase.Callback.onCreate` (LIB-002 invariant).
- [x] Cross-reference table cascades on stretch delete (LIB-003).
- [x] `goals` and `completions` foreign keys cascade on stretch delete (LIB-005).
