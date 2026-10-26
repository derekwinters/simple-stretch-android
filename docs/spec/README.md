# Specification

Behaviour of the app, one page per area. Every requirement has an ID; code comments and unit
tests reference these IDs. Change the spec before the code.

| Page | IDs | Covers |
| --- | --- | --- |
| [scheduling.md](scheduling.md) | SCHED-001 … SCHED-016 | Schedules (set times or repeating), next-occurrence maths, alarms, snooze |
| [skipping.md](skipping.md) | SKIP-001 … SKIP-007 | Skip today, skip future dates |
| [notifications.md](notifications.md) | NOTIF-001 … NOTIF-010 | Reminder notification content, actions, permission |
| [library.md](library.md) | LIB-001 … LIB-007 | Stretch library and first-run seed |
| [goals.md](goals.md) | GOAL-001 … GOAL-010 | Daily goals per stretch, progress, goal history, database upgrades |
| [sessions.md](sessions.md) | SESS-001 … SESS-005 | Stretch session screen and the completion log |
| [trends.md](trends.md) | TREND-001 … TREND-010 | Daily score, week/month trends, streak, day detail |
| [screens.md](screens.md) | HOME-001 … HOME-009 | Home screen layout |
| [widgets.md](widgets.md) | WIDGET-001 … WIDGET-010 | Segmented ring home screen widget for today's goals |
| [privacy.md](privacy.md) | PRIV-001 … PRIV-004 | No internet access, on-device data, the permission set, the privacy policy |
| [store-listing.md](store-listing.md) | STORE-SHOT-001 … STORE-SHOT-014 | Google Play phone screenshots: the shots, their frozen inputs, layout, recording (on a deliberate label) and verifying |

A bold **Invariant — …** sentence marks a rule a technically-correct implementation could still
break; treat it as a hard constraint on *how* the behaviour is built, not only what it outputs.
