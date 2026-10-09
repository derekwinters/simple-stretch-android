# Scheduling

- **SCHED-001** The user can create any number of schedules. Each schedule has a name, an
  enabled toggle, a set of days of the week (picked with chips), and a mode: **set times** (a
  list of reminder times) or **repeating** (SCHED-011).
- **SCHED-002** In a set-times schedule each reminder time has its own set of stretches (zero or
  more; picking stretches is optional). A time with no stretches still reminds, with a generic
  "Time to stretch" message and today's unmet goals (NOTIF-001).
- **SCHED-003** A reminder's next occurrence is the first date-time strictly after *now*, at the
  reminder's time, on a day the schedule selects, and not on a skipped date (SKIP-004).

  **Invariant — A reminder never fires on a day its schedule does not select.** This is checked
  twice: when the alarm is computed, and again by the receiver when the alarm is delivered.
- **SCHED-004** A disabled schedule, or one with no days selected, has no alarms set.
- **SCHED-005** Each reminder time, and each repeating schedule, has at most one alarm. Its
  *alarm key* is the reminder's id for a reminder time and the negated schedule id for a
  repeating schedule (ids start at 1, so the two never collide). The key is the PendingIntent
  request code and the notification id. Setting an alarm again replaces the previous one; alarms
  of keys that no longer exist are cancelled.
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

### Repeating schedules

- **SCHED-011** A repeating schedule has a start time *S*, an end time *E*, an interval of *N*
  minutes (5 to 720, default 60) and a minute past the hour *M* (0 to 59, default 0). It has no
  per-time stretches; the stretch session suggests stretches from goals instead (SESS-002).
- **SCHED-012** A repeating schedule's slots on a day are: the first time at or after *S* whose
  minute is *M*, then every *N* minutes after it, for as long as the slot is at or before *E*.
  **The end time is inclusive: a slot exactly at *E* fires.** Slots never cross midnight. If
  *E* is before *S*, or no slot fits, the schedule has no slots.

  Example: 08:00 to 17:00, every 60 minutes at :50 gives 08:50, 09:50, ... 16:50 (17:50 is past
  the end). 08:00 to 17:00 every 60 minutes at :00 gives 08:00 ... 17:00, including 17:00.
  With an interval that does not divide 60 the minute only anchors the first slot: 08:00 to
  12:00 every 45 minutes at :10 gives 08:10, 08:55, 09:40, 10:25, 11:10, 11:55.
- **SCHED-013** A repeating schedule's next occurrence is the first slot strictly after *now*,
  on a day the schedule selects and not on a skipped date. The SCHED-003 invariant applies: the
  receiver re-checks the day, the skip list and that the schedule is still enabled and
  repeating before notifying.
- **SCHED-014** Saving a schedule as repeating removes its set reminder times (and their
  stretch choices); saving a set-times schedule ignores the repeating fields. Every timing
  change reschedules (SCHED-006).

### Snooze

- **SCHED-015** Snoozing a reminder (NOTIF-008) sets a one-shot alarm 5 minutes later that
  posts the same reminder again. It is a separate alarm (its own intent action) and is not
  remembered by the scheduler.

  **Invariant — Snoozing never moves the schedule.** The schedule's own next alarm stays where
  it was; a snooze is never written to the scheduler's remembered alarms and no reschedule
  cancels or replaces it. A snoozed reminder that comes due on a skipped date, or whose schedule
  was disabled or deleted, is not shown (SKIP-003). A pending snooze does not survive a reboot.

## Build checklist

- [x] `NextOccurrence.compute` is pure (no Android types) and unit tested for days, skips,
      "strictly after now" and the no-days case (SCHED-003, SCHED-004, SCHED-009).
- [x] Request code = reminder id; stale alarms cancelled from the remembered set (SCHED-005).
- [x] Every repository write that affects timing calls `rescheduleAll` (SCHED-006).
- [x] Receiver re-checks schedule enabled + day of week before notifying (SCHED-003 invariant).
- [x] SecurityException from exact alarms falls back to inexact (SCHED-007 invariant).
- [x] `RepeatingSlots.forDay` and `NextOccurrence.computeAny` are pure and unit tested: the
      :50 example, inclusive end, end before start, non-dividing interval, skips and days
      (SCHED-012, SCHED-013).
- [x] Alarm keys (reminder id / negated schedule id) are pure and unit tested (SCHED-005).
- [x] Snooze uses its own intent action and never touches the remembered alarms (SCHED-015).
- [ ] Instrumented test of alarm registration (not yet; needs an emulator in CI).
