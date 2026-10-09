# Specification

Behaviour of the app, one page per area. Every requirement has an ID; code comments and unit
tests reference these IDs. Change the spec before the code.

| Page | IDs | Covers |
| --- | --- | --- |
| [scheduling.md](scheduling.md) | SCHED-001 … SCHED-015 | Schedules (set times or repeating), next-occurrence maths, alarms, snooze |
| [skipping.md](skipping.md) | SKIP-001 … SKIP-007 | Skip today, skip future dates, skip from a notification |
| [notifications.md](notifications.md) | NOTIF-001 … NOTIF-010 | Reminder notification content, actions, permission |
| [library.md](library.md) | LIB-001 … LIB-005 | Stretch library and first-run seed |
| [goals.md](goals.md) | GOAL-001 … GOAL-007 | Daily goals per stretch, progress, database upgrade |
| [sessions.md](sessions.md) | SESS-001 … SESS-005 | Stretch session screen and the completion log |
| [screens.md](screens.md) | HOME-001 … HOME-008 | Home screen layout |

A bold **Invariant — …** sentence marks a rule a technically-correct implementation could still
break; treat it as a hard constraint on *how* the behaviour is built, not only what it outputs.
