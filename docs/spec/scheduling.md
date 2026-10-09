# Scheduling

- **SCHED-001** The user can create any number of schedules. Each schedule has a name, an
  enabled toggle, a set of days of the week (picked with chips), and a list of reminder times.
- **SCHED-002** Each reminder time has its own set of stretches (zero or more). A time with no
  stretches still reminds, with a generic "Time to stretch" message.
- **SCHED-003** A reminder's next occurrence is the first date-time strictly after *now*, at the
  reminder's time, on a day the schedule selects, and not on a skipped date (SKIP-004).

  **Invariant — A reminder never fires on a day its schedule does not select.** This is checked
  twice: when the alarm is computed, and again by the receiver when the alarm is delivered.
- **SCHED-004** A disabled schedule, or one with no days selected, has no alarms set.
- **SCHED-005** Each reminder has at most one alarm, whose PendingIntent request code is the
  reminder's id. Setting a reminder's alarm again replaces the previous one; alarms of reminders
  that no longer exist are cancelled.
- **SCHED-006** All alarms are recomputed after every alarm is handled, after every data change
  (schedule saved, deleted or toggled; date skipped or un-skipped), when the app starts, and on
  `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED` and
  `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
- **SCHED-007** Alarms are exact (`setExactAndAllowWhileIdle`) when the app may schedule exact
  alarms (`USE_EXACT_ALARM` on 13+, user-granted `SCHEDULE_EXACT_ALARM` on 12/12L). Otherwise
  they fall back to `setAndAllowWhileIdle`, and the home screen shows a banner linking to the
  system setting.

  **Invariant — Losing the exact-alarm permission degrades timing, never delivery.** A refused
  exact alarm is always replaced by an inexact one, never dropped.
- **SCHED-008** An alarm whose time has passed but which has not yet been handled (possible with
  inexact alarms) is kept for up to 30 minutes by any reschedule, rather than being moved to the
  next day.

  **Invariant — A reschedule never silently drops an occurrence that was due and not yet
  delivered.**
- **SCHED-009** Reminder times have minute precision; seconds are ignored.
- **SCHED-010** Times are local wall-clock times: 10:00 stays 10:00 after a time-zone or
  daylight-saving change.

## Build checklist

- [x] `NextOccurrence.compute` is pure (no Android types) and unit tested for days, skips,
      "strictly after now" and the no-days case (SCHED-003, SCHED-004, SCHED-009).
- [x] Request code = reminder id; stale alarms cancelled from the remembered set (SCHED-005).
- [x] Every repository write that affects timing calls `rescheduleAll` (SCHED-006).
- [x] Receiver re-checks schedule enabled + day of week before notifying (SCHED-003 invariant).
- [x] SecurityException from exact alarms falls back to inexact (SCHED-007 invariant).
- [ ] Instrumented test of alarm registration (not yet; needs an emulator in CI).
