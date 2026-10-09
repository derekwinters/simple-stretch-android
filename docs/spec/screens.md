# Screens

- **HOME-001** The home screen shows today's date and the "Skip today" card (SKIP-001) at the top.
- **HOME-002** Below it, today's reminders from all enabled schedules, in time order, with the
  time, stretches and schedule name. Reminders already past, or all of them when today is
  skipped, are dimmed.
- **HOME-003** Below that, every schedule with its days, times and an enabled switch. Tapping a
  schedule opens its editor; a button creates a new one.
- **HOME-004** The top bar links to the stretch library and the skipped-days screen.
- **HOME-005** Banners appear when notifications are blocked (NOTIF-005) or exact alarms are not
  allowed (SCHED-007).

  **Invariant — Toggling a schedule's switch on the home screen reschedules immediately**; there
  is no separate save step for it.

## Build checklist

- [x] Home state is derived from Room flows plus a 30-second clock tick (HOME-002).
- [x] Banners re-check permissions on resume (HOME-005).
