# Skipping days

- **SKIP-001** The home screen has a prominent "Skip today" button. Pressing it suppresses every
  remaining reminder for today across all schedules. It can be undone with "Resume today".
- **SKIP-002** The user can skip any future date, picked with a date picker, from the "Skipped
  days" screen. That screen lists today and future skipped dates, each removable.
- **SKIP-003** **Invariant — A skipped date never produces a notification, even if an alarm for
  it was already scheduled.** The alarm receiver checks the skip list for the occurrence's date
  before notifying; it does not rely on the alarm having been rescheduled.
- **SKIP-004** When computing a reminder's next occurrence, skipped dates are passed over.
- **SKIP-005** Every reminder notification has a "Skip today" action that skips today
  (as SKIP-001) and dismisses the notification.
- **SKIP-006** **Invariant — Skipping a date never modifies, disables or deletes a schedule.**
  Skips are stored separately, per date, and apply to all schedules; the next scheduled day
  reminds as normal.
- **SKIP-007** Past skipped dates are kept (they were pruned before version 3), because trends
  leave skipped days out (TREND-003). The skipped-days screen still lists only today and future
  dates.

## Build checklist

- [x] Skipped dates live in their own Room table keyed by epoch day (SKIP-006).
- [x] `AlarmReceiver` checks `isSkipped(date)` before notifying, for scheduled and snoozed
      deliveries alike (SKIP-003).
- [x] Unit tests: skipped today, several skipped days, skipped non-scheduled day, single-day
      schedule rolling a week (SKIP-001, SKIP-002, SKIP-004, SKIP-006).
- [x] Notification action writes the skip through the repository, which reschedules (SKIP-005).
