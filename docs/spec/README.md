# Specification

Behaviour of the app, one page per area. Every requirement has an ID; code comments and unit
tests reference these IDs. Change the spec before the code.

| Page | IDs | Covers |
| --- | --- | --- |
| [scheduling.md](scheduling.md) | SCHED-001 … SCHED-010 | Schedules, reminder times, next-occurrence maths, alarms |
| [skipping.md](skipping.md) | SKIP-001 … SKIP-007 | Skip today, skip future dates, skip from a notification |
| [notifications.md](notifications.md) | NOTIF-001 … NOTIF-007 | Reminder notification content, actions, permission |
| [library.md](library.md) | LIB-001 … LIB-004 | Stretch library and first-run seed |
| [screens.md](screens.md) | HOME-001 … HOME-005 | Home screen layout |

A bold **Invariant — …** sentence marks a rule a technically-correct implementation could still
break; treat it as a hard constraint on *how* the behaviour is built, not only what it outputs.
